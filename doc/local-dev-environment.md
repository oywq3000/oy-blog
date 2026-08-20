# 本地开发环境：统一 IP 配置（SERVER_IP）

## 一、这是什么

各服务的配置文件（`application.yml` / `bootstrap-dev.yml`）里，所有基础设施地址（MySQL、Redis、Nacos、ES、RabbitMQ、Sentinel、MinIO）的 IP 都写成了占位符：

```yaml
# 以 search-service 为例
url: jdbc:mysql://${SERVER_IP:192.168.200.130}:3306/oyblog?...   # 原来是写死的 IP
server-addr: http://${SERVER_IP:192.168.200.130}:8848            # Nacos 注册中心
```

**类比填空题**：yml 配置是一张试卷，冒号后面的 `192.168.200.130` 是"没人作答时的兜底答案"，而 IDEA 启动时注入的环境变量 `SERVER_IP` 是"标准答案"——Spring 启动时会先看环境变量里有没有这个答案，有就用它，没有才用括号里的兜底答案。

这样所有服务（gateway + 6 个 service）共用**一个**开关：改一处，全局生效。

## 二、原理（30 秒版）

1. **优先级**：进程环境变量 > yml 里的默认值。
2. **bootstrap 阶段同样生效**：Nacos 地址在 `bootstrap-dev.yml` 里，它加载得最早（bootstrap 上下文），但同样读进程级环境变量，所以 `${SERVER_IP:...}` 在那里也能被覆盖。
3. ⚠️ **空值陷阱**：`SERVER_IP=` 留空**不等于**没设置。空串会被当成有效值注入，Nacos 地址会变成 `http://:8848`。想用回默认值，要把整行删掉或注释掉（`# SERVER_IP=...`），而不是清空。

## 三、一次性配置（每个 IDEA 只需做一次）

### 3.1 确认 .env 文件存在

项目根目录 `oy-blog/.env`，内容就是几行开关：

```properties
SERVER_IP=192.168.200.130
MAIL_USERNAME=
MAIL_TOKEN=
# Elasticsearch 账号密码：ES 开启安全认证时填写（留空 = 不认证）
ES_USERNAME=elastic
ES_PASSWORD=
```

（这个文件被 `.gitignore` 忽略，不会提交到仓库；仓库里有 `.env.example` 模板。）

### 3.2 配置 Spring Boot 模板

菜单 **运行(Run) → 编辑配置(Edit Configurations...)** → 左下角 **编辑配置模板…(Edit configuration templates...)** → 左侧选 **Spring Boot** → 右侧 **环境变量(Environment variables)** 输入框 → 点输入框右边的**"浏览 .env 文件和脚本"按钮**（文件夹图标）→ 选项目根的 `.env`（路径会显示为 `$PROJECT_DIR$/.env`）→ **应用/确定**。

> 注意：模板只对**之后新建**的运行配置生效，已经存在的旧配置不会自动变——所以下一步要把旧配置删掉重建。

### 3.3 删除旧的运行配置，重建

在"运行/调试配置"窗口里，把现有 6 个 Spring Boot 运行配置逐个选中删掉（User / Article / File / Gateway / Search / Agent，都是带"临时"标签的；另外还有一个 **Application 类型**的 UserApplication，是多余的重复配置，一起删掉即可）。

然后回到代码里正常 Run 每个服务——IDEA 会按 3.2 的模板自动生成新配置，自动带上根 `.env`。

> 消息服务 `message-service` 没有现成运行配置，直接 Run 就会走模板，不用管。

### 3.4 user-service 的邮件密钥（重要，别漏）

`user-service` 原来的运行配置通过 EnvFile 插件加载 `oy-blog-service/user-service/.env`（里面是 QQ 邮箱授权码 `MAIL_USERNAME` / `MAIL_TOKEN`）。**3.3 删掉旧配置后这段引用会一起消失，邮件会发不出去**。重建后二选一：

- **方案 A（推荐，统一用原生机制）**：打开 UserApplication 运行配置，环境变量输入框里填两个文件，用**分号**分隔：

  ```
  $PROJECT_DIR$/.env;$PROJECT_DIR$/oy-blog-service/user-service/.env
  ```

  同时把 EnvFile 标签页的勾选取消（不取消也没事，两个文件没有同名变量，互不冲突）。

- **方案 B**：保留 EnvFile 标签页原样不动（插件继续加载 user-service/.env），根 `.env` 用 3.2 的原生引用。

总之：`SERVER_IP` 和 `MAIL_*` 两个都得能注入到 user-service。

### 3.5 完成

所有服务重新 Run 一次，收工。以后只需要维护一个文件。

## 四、日常换 IP

只改根 `.env` 里 `SERVER_IP` 那一行 → 重启要用的服务。

每次启动都是新进程、重新读文件，所以**改完立即生效，不用重启 IDEA**。

## 五、排查兜底

1. **改了 .env 没生效**：
   - 先查 Windows 系统环境变量里有没有也叫 `SERVER_IP` 的变量——系统级变量优先级最高，会盖掉 .env 的引用。位置：此电脑 → 右键属性 → 高级系统设置 → 环境变量。
   - 再检查运行配置的"环境变量"字段是否真的选了 `.env` 文件。
2. **.env 格式要求**：UTF-8 编码（别用记事本保存成 ANSI/GBK）、`KEY=VALUE` 等号两侧不加空格、注释用 `#`。
3. **IDEA 版本较旧**，找不到"浏览 .env 文件和脚本"按钮：用本机已装的 **EnvFile 插件**兜底——在运行配置的 EnvFile 标签页里把根 `.env` 加进去即可。注意两套机制别定义同名变量。
4. `SERVER_IP=` 留空陷阱：见第二节第 3 条。

## 六、验证（假 IP 法，最可靠）

1. 把根 `.env` 里 `SERVER_IP` 改成假 IP：`192.168.1.1`；
2. 启动 gateway；
3. 看控制台日志，搜 `nacos`：应出现连接 `192.168.1.1:8848` 的失败/超时日志——**看到假 IP 就说明环境变量注入成功**；
4. 改回 `192.168.200.130`，重启，日志恢复正常注册；
5. 若第 3 步日志里还是 `192.168.200.130`，按第五节第 1、2 条顺序排查。
6. 顺带在 user-service 验证一下邮件功能正常（确认 3.4 合并后 MAIL_* 仍注入成功）。

## 七、备选方案（一句话对比）

| 方案 | 优点 | 缺点 |
|------|------|------|
| **根 .env 文件（当前方案）** | 改一处、立即生效、`.env.example` 随仓库分发 | 每个 IDEA 需一次性配置 |
| Windows 系统环境变量 | 零配置、所有程序全局生效 | 改值要重启 IDEA，污染整台电脑，换机器要重设 |
| 逐个运行配置手填 `SERVER_IP=xxx` | 最简单直接 | 以后每次换 IP 要挨个改一遍 |
