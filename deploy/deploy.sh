#!/usr/bin/env bash
# oy-blog 后端部署脚本（Windows git-bash / Linux 均可）
# 流程: 本地 mvn 打包(JDK21) -> tar-over-ssh 上传 jar（就地覆盖 + jar.bak 快照）
#       -> 服务器 docker compose 构建并启动
#       默认只部署 jar；传 --sync-config 才同步 compose/Dockerfile/env 模板（MD5 有变化才上传）
# 用法: ./deploy/deploy.sh [--skip-build] [--clean] [--rollback] [--sync-config]
set -euo pipefail

# ============ 配置区（按需修改） ============
SERVER_HOST="100.110.148.14"          # 首次部署建议先用服务器 IP
SERVER_USER="oy"                # 非 root 需对 REMOTE_DIR 有写权限
REMOTE_DIR="/home/oy/app/oyblogdeploy/oyblog-back"
JAVA_HOME="/d/DevelopKit/jdk-21.0.8"  # 本机 JDK21（默认 JDK20 报"不支持发行版本 21"）；已是 21 则留空
SSH_BIN="C:\Windows\System32\OpenSSH\ssh.exe"       # 中文用户 home 导致 ssh 读密钥失败时，改 /c/Windows/System32/OpenSSH/ssh.exe
SCP_BIN="C:\Windows\System32\OpenSSH\scp.exe"
# ===========================================

SSH_TARGET="${SERVER_USER}@${SERVER_HOST}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_DIR="${REMOTE_DIR}/jar"     # jar 平铺目录（compose 的 build context）
COMPOSE_CMD="docker compose"    # compose v1 改为 "docker-compose"

SKIP_BUILD=0; CLEAN=0; ROLLBACK=0; SYNC_CONFIG=0
for arg in "$@"; do
  case "$arg" in
    --skip-build)  SKIP_BUILD=1 ;;
    --clean)       CLEAN=1 ;;
    --rollback)    ROLLBACK=1 ;;
    --sync-config) SYNC_CONFIG=1 ;;
    *) echo "未知参数: $arg"; exit 1 ;;
  esac
done

# 用 Windows 原生 OpenSSH 时，MSYS 会把 /home/oy 等远端路径误转成 Windows 路径，必须关掉
case "$SSH_BIN" in
  /c/Windows/*) export MSYS_NO_PATHCONV=1 ;;
esac

if [ -n "$JAVA_HOME" ]; then
  export JAVA_HOME
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

# 六个服务：(相对模块目录|jar 文件名)
SERVICES=(
  "oy-blog-gateway|oy-blog-gateway-1.0-SNAPSHOT.jar"
  "oy-blog-service/user-service|user-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/article-service|article-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/file-service|file-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/search-service|search-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/agent-service|agent-service-1.0-SNAPSHOT.jar"
)

# 由 SERVICES 生成 tar 参数：-C 目录 文件名（每个 jar 从各自 target/ 打包）
TAR_ARGS=()
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r mod jar <<< "${entry}"
  TAR_ARGS+=(-C "${REPO_ROOT}/${mod}/target" "${jar}")
done

if [ "$ROLLBACK" = "1" ]; then
  echo "==> 回滚 jar（恢复上次构建快照并重建镜像）"
  "${SSH_BIN}" "$SSH_TARGET" "cd $REMOTE_DIR && [ -d jar.bak ] || { echo '没有可回滚的快照'; exit 1; } && rm -rf jar && cp -a jar.bak jar && $COMPOSE_CMD up -d --build"
  exit 0
fi

echo "==> [1/5] 本地 Maven 构建（JDK21，跳过测试）"
if [ "$SKIP_BUILD" != "1" ]; then
  # 注意: 用 maven.test.skip=true 而非 -DskipTests —— 后者只跳过"运行测试"，
  # 测试代码仍会编译，testCompile 编译失败会直接卡住整个构建
  (cd "$REPO_ROOT" && mvn -q clean package -Dmaven.test.skip=true)
fi

echo "==> [2/5] 校验 6 个可执行 jar（应包含 BOOT-INF/）"
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r mod jar <<< "${entry}"
  if ! jar tf "${REPO_ROOT}/${mod}/target/${jar}" | grep -q "BOOT-INF/"; then
    echo "!! ${jar} 不是可执行 fat jar（请确认该模块 pom 已加 spring-boot-maven-plugin）"
    exit 1
  fi
  echo "   OK: ${jar}"
done

echo "==> [3/5] 上传 jar（tar-over-ssh）"
# 红线: ./jar 是 compose build context，就地覆盖；先快照 jar.bak 供回滚
"${SSH_BIN}" "$SSH_TARGET" "mkdir -p ${JAR_DIR}"
REMOTE_SCRIPT=$(cat <<EOF
set -e
cd $REMOTE_DIR
[ -d jar.bak ] && rm -rf jar.bak
cp -a jar jar.bak
if [ "$CLEAN" = "1" ]; then find jar -type f -delete; fi
tar -xzf - -C jar
EOF
)
tar -czf - "${TAR_ARGS[@]}" | "${SSH_BIN}" "$SSH_TARGET" "$REMOTE_SCRIPT"

echo "==> [4/5] 同步配置文件"

# --sync-config 时：compose/Dockerfile/env 模板两端 MD5 比对，有变化才上传
if [ "$SYNC_CONFIG" = "1" ]; then
  CONFIG_FILES=(
    "deploy/docker-compose.yml|docker-compose.yml"
    "deploy/docker/Dockerfile|docker/Dockerfile"
    "deploy/docker-compose.env.example|deploy.env.example"
    ".env"
  )
  for entry in "${CONFIG_FILES[@]}"; do
    IFS='|' read -r local_rel remote_rel <<< "${entry}"
    OLD_MD5=$("${SSH_BIN}" "$SSH_TARGET" "md5sum ${REMOTE_DIR}/${remote_rel} 2>/dev/null | awk '{print \$1}'" || true)
    NEW_MD5=$(md5sum "${REPO_ROOT}/${local_rel}" | awk '{print $1}')
    if [ "$OLD_MD5" != "$NEW_MD5" ]; then
      "${SCP_BIN}" -q "${REPO_ROOT}/${local_rel}" "${SSH_TARGET}:${REMOTE_DIR}/${remote_rel}"
      echo "   ${remote_rel} 已更新"
    else
      echo "   ${remote_rel} 未变化，跳过"
    fi
  done
else
  echo "   跳过（默认只部署 jar，compose 文件以服务器现状为准）"
fi

echo "==> [5/5] 服务器构建镜像并启动（up -d --build 幂等）"
"${SSH_BIN}" "$SSH_TARGET" "cd ${REMOTE_DIR} && ${COMPOSE_CMD} config -q && ${COMPOSE_CMD} up -d --build"

echo ""
echo "==> 部署完成。验证:"
echo "  1. ssh ${SSH_TARGET} 'cd ${REMOTE_DIR} && docker compose ps'  # 期望全部 Up"
echo "  2. 内存: ssh ${SSH_TARGET} 'free -m && docker stats --no-stream'  # 总 RSS 应 <= 2G"
echo "  3. Nacos 控制台 http://${SERVER_HOST}:8848/nacos 应有 6 个服务、每服务 1 实例"
echo "  4. 网关冒烟: ssh ${SSH_TARGET} \"curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/user-service/auth/login -H 'Content-Type: application/json' -d '{}'\"  # 期望 4xx 而非 000/503"
