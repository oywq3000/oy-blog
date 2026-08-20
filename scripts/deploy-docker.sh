#!/usr/bin/env bash
# ============================================================
# oy-blog 一键构建部署（本机 Windows Git Bash 下运行）
# 流程：本地 mvn 打包(JDK21) → 校验 fat jar → scp jar 与部署文件
#       → 服务器 docker compose 构建并启动 → 状态确认
# 前置：服务器已装 docker + compose 插件；本机 ssh/scp 可用（密钥或密码）
# 用法：bash scripts/deploy-docker.sh
# ============================================================
set -euo pipefail

# ---- 配置区（按需修改）----
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
JAVA_HOME="/d/DevelopKit/jdk-21.0.8"        # 本机 JDK 21（默认 JDK20 会报"不支持发行版本 21"）
SERVER_USER="oy"                             # 服务器 SSH 用户
SERVER_HOST="oyk40"                          # 服务器 SSH 别名（~/.ssh/config 里配置的 oyk40）
REMOTE_DIR="/home/oy/app/oyblogdeploy/oyblog-back"   # 服务器部署根目录（compose 所在）
JAR_DIR="${REMOTE_DIR}/jar"                 # jar 平铺目录（compose 的 build context）

# ---- 环境准备 ----
export JAVA_HOME
export PATH="${JAVA_HOME}/bin:${PATH}"
# 关键：禁止 Git Bash 把 /opt/oy-blog 误转成 Windows 路径（本地侧一律用相对路径）
export MSYS_NO_PATHCONV=1
# 必须用 Windows 原生 OpenSSH：Git Bash 自带的 ssh 读不了中文用户名下的 home 路径
SSH_BIN="/c/Windows/System32/OpenSSH/ssh.exe"
SCP_BIN="/c/Windows/System32/OpenSSH/scp.exe"
SSH="${SERVER_USER}@${SERVER_HOST}"

# 七个服务：(compose 服务名|相对模块目录|jar 文件名)
SERVICES=(
  "oy-blog-gateway|oy-blog-gateway|oy-blog-gateway-1.0-SNAPSHOT.jar"
  "user-service|oy-blog-service/user-service|user-service-1.0-SNAPSHOT.jar"
  "article-service|oy-blog-service/article-service|article-service-1.0-SNAPSHOT.jar"
  "file-service|oy-blog-service/file-service|file-service-1.0-SNAPSHOT.jar"
  "message-service|oy-blog-service/message-service|message-service-1.0-SNAPSHOT.jar"
  "search-service|oy-blog-service/search-service|search-service-1.0-SNAPSHOT.jar"
  "agent-service|oy-blog-service/agent-service|agent-service-1.0-SNAPSHOT.jar"
)

# 把模板里缺失的键补进 .env（跳过注释行；已存在的键不动）
merge_env() {
  local target="$1" template="$2"
  while IFS= read -r line; do
    case "${line}" in ''|\#*) continue ;; esac
    local key="${line%%=*}"
    if ! grep -q "^${key}=" "${target}" 2>/dev/null; then
      echo "${line}" >> "${target}"
      echo "   [.env 补全] ${key}"
    fi
  done < "${template}"
}

# ---- 1. 本地打包 ----
echo "==> [1/5] Maven 打包（JDK21，跳过测试）"
cd "${REPO_DIR}"
mvn -q clean package -DskipTests

# ---- 2. 校验 fat jar（防 pom 漏改，薄 jar 会在这里被拦下）----
echo "==> [2/5] 校验 7 个可执行 jar（应包含 BOOT-INF/）"
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name mod jar <<< "${entry}"
  jar_path="${mod}/target/${jar}"
  if ! jar tf "${jar_path}" | grep -q "BOOT-INF/"; then
    echo "!! ${jar_path} 不是可执行 fat jar（请确认该模块 pom 已加 spring-boot-maven-plugin）"
    exit 1
  fi
  echo "   OK: ${jar_path}"
done

# ---- 3. 上传 jar 与部署文件 ----
echo "==> [3/5] 上传 jar 与部署文件"
"${SSH_BIN}" "${SSH}" "mkdir -p ${JAR_DIR} ${REMOTE_DIR}/docker ${REMOTE_DIR}/logs"
for entry in "${SERVICES[@]}"; do
  IFS='|' read -r name mod jar <<< "${entry}"
  "${SCP_BIN}" -q "${mod}/target/${jar}" "${SSH}:${JAR_DIR}/"
  echo "   OK: ${jar}"
done
"${SCP_BIN}" -q deploy/docker/Dockerfile          "${SSH}:${REMOTE_DIR}/docker/"
"${SCP_BIN}" -q deploy/docker-compose.yml         "${SSH}:${REMOTE_DIR}/"
"${SCP_BIN}" -q deploy/docker-compose.env.example "${SSH}:${REMOTE_DIR}/deploy.env.example"

# 首次部署：服务器 .env 不存在则生成（本机 .env 的真实密钥 + 模板补全缺失键）
if ! "${SSH_BIN}" "${SSH}" "test -f ${REMOTE_DIR}/.env"; then
  tmp_env="deploy/.env.merged.tmp"   # 用仓库内相对路径：Windows 原生 scp 解析不了 /tmp
  if [ -f "${REPO_DIR}/.env" ]; then
    cp "${REPO_DIR}/.env" "${tmp_env}"
    echo "   .env 基础来自本机 .env（含真实密钥）"
  else
    : > "${tmp_env}"
    echo "   !! 本机没有 .env，将用模板占位值，请部署后编辑服务器 .env"
  fi
  merge_env "${tmp_env}" "${REPO_DIR}/deploy/docker-compose.env.example"
  "${SCP_BIN}" -q "${tmp_env}" "${SSH}:${REMOTE_DIR}/.env"
  rm -f "${tmp_env}"
  echo "   .env 已生成到服务器 ${REMOTE_DIR}/.env（如含模板占位值需自行修改）"
fi

# ---- 4. 服务器构建 + 启动 ----
echo "==> [4/5] 服务器构建镜像并启动"
"${SSH_BIN}" "${SSH}" "cd ${REMOTE_DIR} && docker compose config -q && docker compose up -d --build"

# ---- 5. 状态确认 ----
echo "==> [5/5] 容器状态"
"${SSH_BIN}" "${SSH}" "cd ${REMOTE_DIR} && docker compose ps"
echo ""
echo "部署完成，验证清单："
echo "  1. 上面 docker compose ps 全部 Up"
echo "  2. 内存：ssh ${SSH} 'free -m && docker stats --no-stream'（总 RSS 应 <= 2G）"
echo "  3. Nacos 控制台 http://${SERVER_HOST}:8848/nacos 服务列表应有 7 个服务、每服务 1 实例"
echo "  4. 网关冒烟：curl -s -o /dev/null -w '%{http_code}' -X POST http://${SERVER_HOST}:8080/user-service/auth/login -H 'Content-Type: application/json' -d '{}'"
echo "     （期望返回 4xx 而不是 000/503）"
