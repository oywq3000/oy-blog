#!/usr/bin/env bash
# oy-blog 后端部署脚本（Windows git-bash / Linux 均可）
# 流程: 本地 mvn 打包(JDK21) -> tar-over-ssh 上传 jar（就地覆盖 + jar.bak 快照）
#       -> 服务器 docker compose 构建并启动
#       默认只部署 jar；传 --sync-config 才同步 compose/Dockerfile/env 模板（MD5 有变化才上传）
# 用法: ./deploy/deploy.sh [--skip-build] [--clean] [--rollback] [--sync-config]
set -euo pipefail

# ============ 配置区（按需修改） ============
# SERVER_HOST="100.110.148.14"          # 首次部署建议先用服务器 IP
SERVER_HOST="192.168.123.152"          # 首次部署建议先用服务器 IP
SERVER_USER="oy"                # 非 root 需对 REMOTE_DIR 有写权限
REMOTE_DIR="/home/oy/app/oyblogdeploy/oyblog-back"
JAVA_HOME="/d/DevelopKit/jdk-21.0.8"  # 本机 JDK21（默认 JDK20 报"不支持发行版本 21"）；已是 21 则留空
# 用 Git Bash 自带的 /usr/bin/ssh(-scp)：System32 版 ssh.exe + Tailscale 虚拟网卡会
# "感知不到 TCP 连接已完成"，每条命令白等满 ConnectTimeout（实测 10s）；自带版实测 <1s。
SSH_BIN="/usr/bin/ssh"
SCP_BIN="/usr/bin/scp"
SSH_KEY=""                    # 默认留空自动找 $HOME/.ssh/id_rsa；非默认密钥时填绝对路径
MAVEN_BIN=""                  # 留空自动探测（优先 IDE 自带 maven）；也可手动填绝对路径
# ===========================================

SSH_TARGET="${SERVER_USER}@${SERVER_HOST}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
JAR_DIR="${REMOTE_DIR}/jar"     # jar 平铺目录（compose 的 build context）
COMPOSE_CMD="docker compose"    # compose v1 改为 "docker-compose"
# ssh 公共选项：BatchMode 禁止交互弹框（认证出问题立即报错而非挂起）；
# ConnectTimeout/ServerAlive 兜底防无声假死。注意 -T 不能放这里（scp 的 -T 含义不同，ssh 调用单独加）
SSH_OPTS=(-o BatchMode=yes -o ConnectTimeout=10 -o ServerAliveInterval=15 -o ServerAliveCountMax=4)
[ -n "$SSH_KEY" ] && SSH_OPTS+=(-i "$SSH_KEY")

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

# ⚠ 全局不要 export MSYS_NO_PATHCONV：一旦设置，mvn 内部启动 java.exe 时的
# /d/DevelopKit/... 等 POSIX classpath 不再转成 Windows 路径 → classworks 启动器
# 找不到 → ClassNotFoundException。实测 git-bash 自带 ssh/scp 远端命令（以 cd/mkdir
# /md5sum 等开头）不触发路径转换，无需该变量。

if [ -n "$JAVA_HOME" ]; then
  export JAVA_HOME
  export PATH="${JAVA_HOME}/bin:${PATH}"
fi

# 探测 Maven：PATH 上可能残留坏副本（启动器类找不到），钉到 IDE 自带的完好份
if [ -z "$MAVEN_BIN" ]; then
  for _cand in /d/DevelopKit/IDE/IntelliJ*/plugins/maven/lib/maven3/bin/mvn; do
    [ -x "$_cand" ] && MAVEN_BIN="$_cand" && break
  done
fi
[ -n "$MAVEN_BIN" ] || MAVEN_BIN="$(command -v mvn)"

# 六个服务：(相对模块目录|jar 文件名)
SERVICES=(
  "oy-blog-gateway|oy-blog-gateway-1.0-SNAPSHOT.jar"
  "oy-blog-service/user-service|user-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/article-service|article-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/file-service|file-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/search-service|search-service-1.0-SNAPSHOT.jar"
  "oy-blog-service/agent-service|agent-service-1.0-SNAPSHOT.jar"
)

# tar 参数在 [3/5] 里按需生成（增量：仅 md5 不一致的 jar 进打包）

if [ "$ROLLBACK" = "1" ]; then
  echo "==> 回滚 jar（恢复上次构建快照并重建镜像）"
  "${SSH_BIN}" -T "${SSH_OPTS[@]}" "$SSH_TARGET" "cd $REMOTE_DIR && [ -d jar.bak ] || { echo '没有可回滚的快照'; exit 1; } && rm -rf jar && cp -a jar.bak jar && $COMPOSE_CMD up -d --build"
  exit 0
fi

echo "==> [1/5] 本地 Maven 构建（JDK21，跳过测试）"
if [ "$SKIP_BUILD" != "1" ]; then
  # 兼容坏全局变量：本机 MAVEN_HOME/M2_HOME 曾指向已删除的旧版 IDEA，会致 classworks 启动失败
  unset MAVEN_HOME M2_HOME
  # 注意: 用 maven.test.skip=true 而非 -DskipTests —— 后者只跳过"运行测试"，
  # 测试代码仍会编译，testCompile 编译失败会直接卡住整个构建
  echo "   用 Maven: ${MAVEN_BIN}"
  (cd "$REPO_ROOT" && "${MAVEN_BIN}" -q clean package -Dmaven.test.skip=true)
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

echo "==> [3/5] 上传 jar（tar-over-ssh，增量: 本地与服务器 md5 一致则跳过）"
# 红线: ./jar 是 compose build context，就地覆盖；先快照 jar.bak 供回滚
# 建目录 + 取服务器当前各 jar 的 md5，合并为一次会话（省一次握手延时；
# `md5sum *.jar` 缺文件时输出为空，视为需要上传）
REMOTE_SUM=$("${SSH_BIN}" -T "${SSH_OPTS[@]}" "$SSH_TARGET" "mkdir -p ${JAR_DIR} && cd ${JAR_DIR} && md5sum *.jar 2>/dev/null" | sort || true)

# 逐 jar 比对本地/远端 md5，收集确实变化的（--clean 强制全量，语义不变）
CHANGED_TAR_ARGS=()
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r mod jar <<< "${entry}"
  if [ "$CLEAN" = "1" ]; then
    echo "   上传: ${jar}   # --clean 强制全量"
    CHANGED_TAR_ARGS+=(-C "${REPO_ROOT}/${mod}/target" "${jar}")
    continue
  fi
  LOCAL_SUM=$(md5sum "${REPO_ROOT}/${mod}/target/${jar}" | awk '{print $1}')
  # md5sum 有 "hash  name"（文本）和 "hash *name"（二进制）两种分隔，统一剥掉文件名前缀再比对
  REMOTE_ONE=$(printf '%s\n' "$REMOTE_SUM" | awk -v f="$jar" '{n=$NF; sub(/^\*/,"",n); if (n==f) print $1}')
  if [ "$LOCAL_SUM" != "$REMOTE_ONE" ]; then
    echo "   将于: ${jar}（md5 不一致）"
    CHANGED_TAR_ARGS+=(-C "${REPO_ROOT}/${mod}/target" "${jar}")
  else
    echo "   跳过: ${jar}（与服务器一致）"
  fi
done

if [ "${#CHANGED_TAR_ARGS[@]}" -eq 0 ]; then
  echo "   全部 ${#SERVICES[@]} 个 jar 与服务器一致，本次无需上传"
else
  REMOTE_SCRIPT=$(cat <<EOF
set -e
cd $REMOTE_DIR
[ -d jar.bak ] && rm -rf jar.bak
cp -a jar jar.bak
if [ "$CLEAN" = "1" ]; then find jar -type f -delete; fi
tar -xzf - -C jar
EOF
)
  # 只打变化的 jar 进管道。Windows 下把二进制管道喂给原生 ssh.exe 有假死风险，
  # 增量让单次传输量小很多；若仍卡住，可改为 scp 传单个 tgz
  tar -czf - "${CHANGED_TAR_ARGS[@]}" | "${SSH_BIN}" -T "${SSH_OPTS[@]}" "$SSH_TARGET" "$REMOTE_SCRIPT"
  echo "   已上传 $(( ${#CHANGED_TAR_ARGS[@]} / 2 )) 个 jar"
fi

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
    OLD_MD5=$("${SSH_BIN}" -T "${SSH_OPTS[@]}" "$SSH_TARGET" "md5sum ${REMOTE_DIR}/${remote_rel} 2>/dev/null | awk '{print \$1}'" || true)
    NEW_MD5=$(md5sum "${REPO_ROOT}/${local_rel}" | awk '{print $1}')
    if [ "$OLD_MD5" != "$NEW_MD5" ]; then
      "${SCP_BIN}" -q "${SSH_OPTS[@]}" "${REPO_ROOT}/${local_rel}" "${SSH_TARGET}:${REMOTE_DIR}/${remote_rel}"
      echo "   ${remote_rel} 已更新"
    else
      echo "   ${remote_rel} 未变化，跳过"
    fi
  done
else
  echo "   跳过（默认只部署 jar，compose 文件以服务器现状为准）"
fi

echo "==> [5/5] 服务器构建镜像并启动（up -d --build 幂等）"
"${SSH_BIN}" -T "${SSH_OPTS[@]}" "$SSH_TARGET" "cd ${REMOTE_DIR} && ${COMPOSE_CMD} config -q && ${COMPOSE_CMD} up -d --build"

echo ""
echo "==> 部署完成。验证:"
echo "  1. ssh ${SSH_TARGET} 'cd ${REMOTE_DIR} && docker compose ps'  # 期望全部 Up"
echo "  2. 内存: ssh ${SSH_TARGET} 'free -m && docker stats --no-stream'  # 总 RSS 应 <= 2G"
echo "  3. Nacos 控制台 http://${SERVER_HOST}:8848/nacos 应有 6 个服务、每服务 1 实例"
echo "  4. 网关冒烟: ssh ${SSH_TARGET} \"curl -s -o /dev/null -w '%{http_code}' -X POST http://localhost:8080/user-service/auth/login -H 'Content-Type: application/json' -d '{}'\"  # 期望 4xx 而非 000/503"
