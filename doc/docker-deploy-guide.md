# Docker 部署手册（手动版，逐步教学）

把 oy-blog 的 7 个微服务打包成 Docker 容器，部署到 Linux 服务器（SSH 别名 `oyk40`，用户 `oy`）。
本手册每一步都有命令，照着执行即可。熟练后可以用 `scripts/deploy-docker.sh` 一键完成 1→3 步。

> **终端说明**：以下命令在 **Windows cmd 或 PowerShell** 里执行（你的实际环境）。
> - 环境变量写法：cmd 用 `set JAVA_HOME=D:\DevelopKit\jdk-21.0.8`；PowerShell 用 `$env:JAVA_HOME = "D:\DevelopKit\jdk-21.0.8"`（每次新开窗口要重设一次）
> - `scp`/`ssh` 直接用系统自带的（Windows OpenSSH，已在 PATH 里）
> - 本机文件路径用反斜杠 `.\xxx.jar`，服务器路径用 `/home/oy/...`
> - 若用 Git Bash：本机路径用 `/g/JavaWorkSpace/oy-blog`，且必须用 Windows 原生 ssh（`/c/Windows/System32/OpenSSH/ssh.exe`）并先 `export MSYS_NO_PATHCONV=1`（原因：中文用户名 + 路径翻译问题）

## 整体流程（一次发版 = 三步循环）

```
本机 Windows（cmd/PowerShell）           Linux 服务器 (oyk40)
① mvn clean package 打 7 个 jar  ──② scp 上传──►  /home/oy/app/oyblogdeploy/
                                                  ├ docker-compose.yml
                                                  ├ .env（密钥，仅第一次配）
                                                  ├ docker/Dockerfile
                                                  ├ oyblog-back/（7 个 jar 平铺）
                                                  └ logs/<服务>/
③ docker compose up -d --build（在服务器上构建镜像并启动）
```

- 服务器上的 MySQL/Redis/Nacos/ES 等中间件**已经在跑**，不用动它们
- **业务容器用 `network_mode: host`（共享宿主机网络）**，通过 `.env` 里的 `SERVER_IP=127.0.0.1` 走回环访问中间件——这台机器的防火墙会拦"容器→宿主机 IP"方向的流量（Tailscale 规则），只有回环不受限制
- 只有网关 8080 对外；MinIO 返回给前端的下载链接用 `STORAGE_MINIOFILEDOMAIN` 指向 Tailscale IP（浏览器可达）

## 第 0 步：一次性准备

### 0.1 本机公钥装到服务器（免密登录，只做一次；已做过则跳过）

cmd 里执行（需要输入一次服务器密码）：

```
type %USERPROFILE%\.ssh\id_rsa.pub | ssh oy@oyk40 "mkdir -p ~/.ssh && chmod 700 ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
```

验证是否成功（不输密码直接返回 SSH-OK 就是好了）：

```
ssh oy@oyk40 "echo SSH-OK"
```

### 0.2 本机确认能编译（每次新开终端窗口先设一次）

```
set JAVA_HOME=D:\DevelopKit\jdk-21.0.8
```

> **JAVA_HOME 的值不能带 `\bin`**！要填 JDK 的根目录（`D:\DevelopKit\jdk-21.0.8`），Maven 会自动去 `%JAVA_HOME%\bin\java.exe` 找 java。填成 `...\jdk-21.0.8\bin` 会去找 `...\bin\bin\java.exe`，报 "JAVA_HOME is not defined correctly"。

### 0.3 服务器确认 docker compose 可用（登录服务器执行一次）

```
ssh oy@oyk40 "docker compose version && free -m"
```

`docker compose version` 有 v2.x 输出即可；`free -m` 看空余内存（部署前应 >= 2G）。

## 第 1 步：本机打包（每次发版都做）

在项目根目录 `G:\JavaWorkSpace\oy-blog` 下执行：

```
set JAVA_HOME=D:\DevelopKit\jdk-21.0.8
mvn clean package -DskipTests
```

参数解释：
- `clean`：先清空旧的打包产物，避免混入旧文件
- `package`：编译 + 打 jar 包
- `-DskipTests`：跳过运行测试
- 结束看到 `BUILD SUCCESS` 即成功

**确认 7 个 jar 都是"可执行 fat jar"**（大小应都是几十 MB；如果哪个只有几 KB 说明 pom 缺插件）：

| 服务 | jar 路径 | 端口 |
|---|---|---|
| 网关 | oy-blog-gateway\target\oy-blog-gateway-1.0-SNAPSHOT.jar | 8080 |
| user-service | oy-blog-service\user-service\target\user-service-1.0-SNAPSHOT.jar | 8093 |
| article-service | oy-blog-service\article-service\target\article-service-1.0-SNAPSHOT.jar | 8091 |
| file-service | oy-blog-service\file-service\target\file-service-1.0-SNAPSHOT.jar | 8092 |
| message-service | oy-blog-service\message-service\target\message-service-1.0-SNAPSHOT.jar | 8094 |
| search-service | oy-blog-service\search-service\target\search-service-1.0-SNAPSHOT.jar | 8099 |
| agent-service | oy-blog-service\agent-service\target\agent-service-1.0-SNAPSHOT.jar | 8095 |

## 第 2 步：上传到服务器（每次发版都做）

在项目根目录执行。第一次先建目录：

```
ssh oy@oyk40 "mkdir -p /home/oy/app/oyblogdeploy/oyblog-back /home/oy/app/oyblogdeploy/docker /home/oy/app/oyblogdeploy/logs"
```

逐个上传 7 个 jar（user-service 已传过；scp 语法：`scp 本地文件 用户@服务器:目标路径`）：

```
scp .\oy-blog-gateway\target\oy-blog-gateway-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/
scp .\oy-blog-service\article-service\target\article-service-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/
scp .\oy-blog-service\file-service\target\file-service-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/
scp .\oy-blog-service\message-service\target\message-service-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/
scp .\oy-blog-service\search-service\target\search-service-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/
scp .\oy-blog-service\agent-service\target\agent-service-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/
```

上传部署文件（Dockerfile 和编排文件每次发版也传一次，保持最新）：

```
scp .\deploy\docker\Dockerfile          oy@oyk40:/home/oy/app/oyblogdeploy/docker/
scp .\deploy\docker-compose.yml         oy@oyk40:/home/oy/app/oyblogdeploy/
scp .\deploy\docker-compose.env.example oy@oyk40:/home/oy/app/oyblogdeploy/
```

> 提示：日常发版可以只传改过的那个服务的 jar，其余不动。

## 第 3 步：服务器上配 .env（只有第一次做）

登录服务器：

```
ssh oy@oyk40
```

生成 .env 并编辑：

```bash
cd /home/oy/app/oyblogdeploy
cp deploy.env.example .env
vi .env        # 或 nano .env
```

每个变量的含义：

| 变量 | 作用 | 注意事项 |
|---|---|---|
| SERVER_IP | 容器访问中间件的地址 | **保持 127.0.0.1**（host 网络模式走回环；填服务器真实 IP 会被宿主机防火墙拦截导致服务反复重启） |
| STORAGE_MINIOFILEDOMAIN | MinIO 下载链接前缀（给浏览器的） | 填 `http://100.110.148.14:9090`（Tailscale IP，浏览器可达） |
| TZ | 时区 | 保持 Asia/Shanghai |
| JAVA_TOOL_OPTIONS | JVM 内存上限 | 服务器空余约 2G，保持 `-Xms64m -Xmx128m -XX:MaxMetaspaceSize=128m`；gateway/article/search 在 compose 里覆盖为 96m/192m |
| MAIL_USERNAME / MAIL_TOKEN | QQ 邮箱 + SMTP 授权码 | 填真实值，否则发不了邮件；键名必须是 MAIL_TOKEN |
| ES_USERNAME / ES_PASSWORD | ES 账号 | ES 开了认证就要填真实值 |
| AGENT_PYTHON_URL | Python 闲聊服务地址 | 暂未上线，保持默认即可 |

改完后 `exit` 退出服务器回到本机。

## 第 4 步：构建镜像并启动（每次发版都做）

```
ssh oy@oyk40 "cd /home/oy/app/oyblogdeploy && docker compose up -d --build"
```

参数解释：
- `up`：创建并启动所有容器
- `-d`：后台运行（detached）
- `--build`：先按 compose 里每个服务的 build 段构建镜像（第一次会拉取 eclipse-temurin:21-jre 基础镜像，之后有缓存）

看到 7 个 `Created` / `Started` 即启动完成。以后只重启不重建（jar 没变的情况下）用 `docker compose up -d`（不带 --build）。

## 第 5 步：检查是否成功

在服务器上执行（先 `ssh oy@oyk40` 登录）：

```bash
# 1. 容器状态：7 个都应该是 Up，STATUS 里没有 Restarting
cd /home/oy/app/oyblogdeploy && docker compose ps

# 2. 内存（贴边跑，重点观察）：free 的 available 列不要长期为 0
free -m && docker stats --no-stream

# 3. 看某个服务日志（Ctrl+C 退出）：
docker compose logs -f gateway        # 或者 user-service 等
# 正常日志里能看到 nacos 注册成功

# 4. 最近 50 行日志（服务起不来时先看这个）：
docker compose logs --tail=50 user-service
```

本机验证：

1. Nacos 控制台：http://<SERVER_IP>:8848/nacos → 服务管理 → 服务列表，应有 7 个服务、每服务 1 个实例（如果某个服务显示 2 个实例，说明本机 IDEA 里还开着同款服务，网关会分流到本机，需要停掉 IDEA 里的）
2. 网关冒烟：

```
curl -s -o /dev/null -w "%%{http_code}\n" -X POST http://<SERVER_IP>:8080/user-service/auth/login -H "Content-Type: application/json" -d "{}"
```

返回 4xx（如 400/401）说明链路通了；返回 000 是连不上、503 是服务没注册成功。

## 常见问题排错

| 现象 | 原因 | 处理 |
|---|---|---|
| 容器 STATUS 一直 Restarting | 启动即崩（配置错/中间件连不上） | `docker compose logs --tail=50 <服务>` 看报错 |
| 日志里 Nacos 连接失败 | .env 的 SERVER_IP 不对或为空 | 编辑 /home/oy/app/oyblogdeploy/.env 后 `docker compose up -d` 重启 |
| 日志里 MySQL/Redis/ES 拒绝连接 | 中间件端口没发布到 0.0.0.0 或账号密码不对 | 检查宿主机中间件容器端口映射 |
| 邮件发不出去 | MAIL_TOKEN 没填或键名不对 | .env 里键名必须是 MAIL_TOKEN |
| free -m 显示内存耗尽、容器被 OOM 杀掉 | 7 个 JVM ≈ 2G 贴边 | 按序降级：① 服务器加 4G swap ② 停掉 agent-service（`docker compose stop agent-service`）③ compose 里把 -Xmx 再调小 ④ 停掉 search-service |
| 更新代码后没生效 | jar 没重新传/没重建镜像 | 重做第 1、2、4 步 |
| 镜像构建报 Dockerfile 在 context 外 | 服务器 docker 版本太旧（< 23，无 BuildKit） | 把 Dockerfile 拷进 oyblog-back 目录并把 compose 里 dockerfile 段改成 `Dockerfile` |

## 日常发版（三步循环）

```cmd
:: ① 本机打包
set JAVA_HOME=D:\DevelopKit\jdk-21.0.8
mvn clean package -DskipTests

:: ② 上传改动的 jar（例如只改了 user-service）
scp .\oy-blog-service\user-service\target\user-service-1.0-SNAPSHOT.jar oy@oyk40:/home/oy/app/oyblogdeploy/oyblog-back/

:: ③ 服务器重建启动
ssh oy@oyk40 "cd /home/oy/app/oyblogdeploy && docker compose up -d --build"
```

熟练后：`bash scripts/deploy-docker.sh` 一键完成上面三步。
