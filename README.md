# 蜃楼 Mock（Mirage Mock）—— 接口 Mock 数据生成系统

面向测试环境的统一接口 Mock 平台：HTTP / TCP 双协议 Mock、动态数据生成（含国密）、规则热生效、测试场景自动化与 CI 集成。

> 技术栈：Java 8 + Spring Boot 2.7.x + MyBatis-Plus + Netty + Bouncy Castle（国密）；前端 Vue 3 + Element Plus + Vite
> 存储：本地 H2（MODE=MySQL）/ 生产 OceanBase（MySQL 租户模式）/ MySQL 8，Flyway 管理迁移

## 快速开始

### 构建

```bash
# JDK8 + Maven 3.6+ 环境（本仓库自带 .tools/env.sh 示例，按本机路径调整后 source）
mvn clean install -DskipTests        # 全量构建，产出 mirage-server/target/mirage-mock.jar
mvn test                             # 跑单测（dsl / core / tcp / admin），产出 JaCoCo 覆盖率报告
```

管理端前端（`mirage-ui`）的预构建产物随 jar 打包（`mirage-ui/dist → classpath:/static`），无需 Node 即可产出含 UI 的自包含 jar。改前端源码后：`cd mirage-ui && npm install && npm run build`，再 `mvn package` 刷新。

### 运行

```bash
java -jar mirage-server/target/mirage-mock.jar                     # 默认 local（H2 内存库）
# 生产/测试（OceanBase / MySQL 8）—— 必须显式注入安全密钥，缺失将拒绝启动（fail-fast）：
MIRAGE_PROFILE=prod \
MIRAGE_DB_URL=jdbc:mysql://host:3306/mirage?... MIRAGE_DB_USER=xxx MIRAGE_DB_PASSWORD=xxx \
MIRAGE_JWT_SECRET=<≥32字节随机串> MIRAGE_MASTER_KEY=<密钥落库主密钥> \
  java -jar mirage-server/target/mirage-mock.jar
```

- 管理端 REST：`http://localhost:9080/api/v1`（JWT 认证；登录有频率限制与连续失败锁定）
- 管理端 UI：`http://localhost:9080/`（Vue3 SPA，随 jar 自带；hash 路由）
- HTTP Mock 流量：`http://localhost:19080`
- 默认账号：`admin / admin123`（首次启动自动创建），内置示例项目 `demo`（HTTP + TCP 示例）

### 快速体验

```bash
# 1. 登录拿 token
TOKEN=$(curl -s -XPOST localhost:9080/api/v1/auth/login -H"Content-Type: application/json" \
  -d'{"username":"admin","password":"admin123"}' | python -c"import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 2. 命中兜底规则（NORMAL）
curl -s localhost:19080/api/user/10086

# 3. 命中灰度规则（VIP）
curl -s localhost:19080/api/user/10086 -H"X-Env: gray"
```

## 模块

```
mirage-parent        聚合 + 依赖版本管理
mirage-common        实体 / 枚举 / 工具 / 统一响应 / 错误码
mirage-dsl           表达式引擎（自研递归下降：Lexer→Parser→AST→求值）+ 生成器函数库 + 国密底层 + SPI
mirage-core          规则缓存 / 匹配引擎 / 模板渲染（字段依赖拓扑排序）/ 热刷新 / Mock 内核（HTTP+TCP 两阶段）
mirage-http          HTTP Mock 拦截（独立端口识别 Filter）
mirage-tcp           Netty TCP Mock Server（帧切分/编解码/连接管理/推送）
mirage-admin         REST API / JWT 鉴权 / 密钥管理（落库加密）/ 日志 / 测试场景 / 定时调度 / CI / 文件模板 / 工具
mirage-server        装配启动 + Profile + Flyway DDL
mirage-ui            管理端前端（Vue3 + Element Plus + Vite，构建产物随 server 打包）
```

## 功能总览

### M1 — HTTP Mock 全链路
- 路由：`method + path`，支持 path 变量 `/api/user/{userId}`，模板中引用 `${path.userId}`
- 规则匹配：header / query / body（JSONPath）/ path / form，操作符 `eq ne in gt gte lt lte regex contains exists not_exists`，优先级升序 + 空条件兜底
- 模板渲染：生成器函数 + 字段间相互引用（`${md5(${data.phone})}`），按依赖拓扑排序求值，循环依赖报错
- 延迟/故障注入：`FIXED/RANDOM` 延迟、`ERROR_STATUS/TIMEOUT/RESET`
- 未命中返回 404 `NO_RULE_MATCHED`，日志标红
- 多项目隔离：请求头 `X-Mirage-Project` 指定项目；仅一个项目时免头路由；多项目路径全局唯一亦可命中，歧义时要求显式项目头

### M2 — TCP Mock 全链路（Netty）
- 帧切分 4 种：`length_field`（2/4/8 字节，大小端）/ `delimiter` / `fixed` / `close_end`
- 报文格式：`json` / `key_value` / `fixed_fields` / `hex_string` + `custom:<bean>` SPI
- 路由/流水号提取：`$.x`（JSONPath）/ `field:xxx` / `kv:xxx`
- 长短连接（LONG/SHORT）、故障注入、主动推送（onConnect / cron 定时广播）
- 监听器动态 bind/unbind（start/stop 即时生效）
- **不阻塞事件循环**：规则延迟在 EventLoop 上异步调度，避免慢规则拖垮同监听器全部连接

### M3 — 管理端前端
登录（JWT 持久化）、项目管理与成员、HTTP 接口/规则编辑（匹配条件 + 模板 DSL 在线编辑 + 试算实时预览）、TCP 监听器管理、密钥管理（SM2 服务端生成）、请求日志多维查询（支持一键转测试用例）、函数市场侧栏、用户管理。

### M4+ — 测试场景自动化（Stage2）
- **测试用例**（`/testcases`）：从请求日志一键生成（`POST /logs/{id}/testcase`），支持 HTTP/TCP 协议、标签分组（`tags`，如 smoke/slow）、请求断言
- **测试场景**（`/scenarios`）：多步骤编排（`steps`），一键运行，产出运行记录（`/records`）
- **测试环境**（`/environments`）：环境配置管理，运行时可指定 `env`
- **定时调度**（`/schedules`）：cron 定时执行场景，启停/手动触发/cron 预览
- **CI 集成**（`/api/v1/ci`）：免 JWT、凭项目 `ciToken` 调起场景运行，供 CI/CD 流水线使用
- **数据驱动**（`/test-variables`）：测试变量管理
- **文件模板**（`/file-templates`）：模板生成 + 预览（对接工具类能力）
- **工具**（`/tools`）：JSON/XML 格式化、校验、转义等小工具
- **录制回放（代理模式）**：接口可配置 `recordMode`（0 关闭 / 1 录制 / 2 回放）与 `upstreamUrl`。规则未命中时：录制模式转发上游并存响应快照，回放模式优先返回快照（命中计数），后续相同请求（method+path+query+body 签名）不再访问上游。配置方式：`PUT /interfaces/{id}` 携带 `{"recordMode":1,"upstreamUrl":"http://real-service:8080"}`
- **OpenAPI/Swagger 一键导入**：`POST /api/v1/import/openapi` 上传 OpenAPI 3 文档，自动批量创建 HTTP 接口 + 兜底规则（响应模板优先取 200 的 example，无则按 schema 生成字段骨架），已存在的 method+path 自动跳过
- **请求日志 → Mock 规则**：`POST /logs/{logId}/rule` 将一条请求日志生成为未保存的规则草稿——自动定位接口，响应模板按字段名启发式转换（`phone→${phone.cn_mobile}`、`name→${name.cn}`、`amount→${decimal(100,99999,2)}`、日期→`${datetime(...)}` 等），复核后保存
- **Mock 命中追踪头**：所有 Mock 响应附加 `X-Mirage-Project`（项目编码）/ `X-Mirage-Interface` / `X-Mirage-Rule`（命中规则）/ `X-Mirage-Matched` / `X-Mirage-Cost-Ms`，联调时一眼确认走了哪条规则；录制回放响应附加 `X-Mirage-Mode: proxy`
- **断言增强**：jsonPath 断言新增 `regex`（正则全匹配）/ `arrayLength`（数组长度）/ `notEmpty` / `notContains` / `notExists` 操作符；header 断言支持 `regex` / `notContains`
- **调度失败告警**：定时回归未通过或异常时推送钉钉/企业微信/通用 webhook（`mirage.alert` 配置，默认关闭）
- **场景条件分支与重试**：步骤可配置执行条件（`passed` / `failed` / `status==200` / `status!=200`，引用上一步结果，不满足自动跳过）与失败自动重试（`retryCount` + `retryDelayMs`）

### 安全设计
- JWT 鉴权（`Authorization: Bearer`），登录接口限流（每分钟上限 + 连续失败锁定）
- 生产/测试 profile 下 JWT 密钥与落库主密钥**必须**通过 `MIRAGE_JWT_SECRET` / `MIRAGE_MASTER_KEY` 显式注入，缺失或仍为默认值时启动即失败（fail-fast）
- 密钥落库 AES-GCM 加密（私钥/对称密钥不明文存储）
- CI 接口按项目 `ciToken` 鉴权，测试目标防护（`TestTargetGuard`）防误打生产环境
- 请求日志含原始报文，展示层按角色可审计

## 管理 API（统一前缀 `/api/v1`，响应 `{code,message,data}`）

| 模块 | 端点 |
|---|---|
| 认证 | `POST /auth/login` |
| 项目 | `GET/POST/PUT/DELETE /projects`、`GET/POST /projects/{id}/members`、`DELETE /projects/{id}/members/{userId}` |
| 接口 | `GET/POST /projects/{pid}/interfaces`、`GET/PUT/DELETE /interfaces/{id}` |
| 规则 | `GET/POST /interfaces/{iid}/rules`、`GET/PUT/DELETE /rules/{id}`、`POST /rules/{id}/toggle` |
| TCP监听 | `GET/POST /projects/{pid}/listeners`、`GET/PUT/DELETE /listeners/{id}`、`POST /listeners/{id}/start\|stop`、`GET /listeners/{id}/status` |
| 密钥 | `GET/POST /projects/{pid}/keys`、`POST /projects/{pid}/keys/sm2/generate`、`DELETE /keys/{id}` |
| 日志 | `GET /projects/{pid}/logs`、`POST /logs/{logId}/testcase`（转测试用例） |
| 试算 | `POST /template/evaluate` |
| 函数库 | `GET /functions` |
| 测试用例 | `GET/POST /projects/{pid}/testcases`、`PUT/DELETE /testcases/{id}`、`POST /testcases/{id}/run\|run-data`、`GET /testcases/{id}/runs` |
| 测试变量 | `GET/POST /projects/{pid}/test-variables`、`PUT/DELETE /test-variables/{id}` |
| 场景 | `GET/POST /projects/{pid}/scenarios`、`PUT/DELETE /scenarios/{id}`、`GET/PUT /scenarios/{id}/steps`、`POST /scenarios/{id}/run` |
| 环境 | `GET/POST /projects/{pid}/environments`、`PUT/DELETE /environments/{id}` |
| 调度 | `GET/POST /projects/{pid}/schedules`、`PUT/DELETE /schedules/{id}`、`POST /schedules/{id}/toggle\|run`、`GET /schedules/cron-preview` |
| 运行记录 | `GET /projects/{pid}/records`、`GET /records/{id}` |
| 文件模板 | `GET/POST /projects/{pid}/file-templates`、`PUT/DELETE /file-templates/{id}`、`POST /file-templates/preview\|generate` |
| 工具 | `POST /tools/json/*`、`POST /tools/xml/*`（format/minify/validate/escape 等） |
| 用户 | `GET/POST /users`、`PUT/DELETE /users/{id}` |
| 系统 | `GET /system/info` |
| CI | `POST /ci/scenarios/{id}/run?token=&env=`（免 JWT，凭项目 ciToken） |

## 数据生成 DSL（`${...}`）

- 人员证件：`name.cn` `name.en` `phone.cn_mobile` `idcard.cn`(校验位) `bankcard.cn`(Luhn) `uscc.cn` `address.cn` `email`
- 数值时间：`int(min,max)` `decimal(min,max,scale)` `seq(name,start)`(项目级持久) `uuid` `date(...)` `datetime(...)` 支持 `now`/`now-30d`
- 字符串：`string(charset,len)` `regex('正则')` `enum(...)` `concat(...)` `repeat(n,{子模板})`
- 摘要编码：`md5` `sha1/256/512` `sm3` `base64_encode/decode` `hex_encode/decode` `url_encode`
- 加解密签名：`sm4_encrypt/decrypt` `sm2_sign/verify/encrypt/decrypt` `aes_encrypt/decrypt` `rsa_sign/encrypt`（密钥按别名引用，结果默认 Base64，可加 `'hex'`）
- 上下文变量：`path.*`（HTTP 路径变量）、`field.*`（报文字段/已渲染字段）

字段间可相互引用（如 `${md5(${data.phone})}`），引擎按依赖拓扑排序求值，循环依赖报错。规则/接口变更**即时热生效**（内存缓存失效重建，无需重启）。

## 配置项（application.yml）

| 项 | 默认 | 说明 |
|---|---|---|
| `mirage.http.port` | 19080 | Mock HTTP 端口 |
| `mirage.security.jwt-secret` | 内置（仅限 local） | JWT 密钥；prod/test 必须用 `MIRAGE_JWT_SECRET` 覆盖 |
| `mirage.security.jwt-ttl-hours` | 12 | JWT 有效期（小时） |
| `mirage.security.master-key` | 内置（仅限 local） | 密钥落库主密钥；prod/test 必须用 `MIRAGE_MASTER_KEY` 覆盖 |
| `spring.profiles.active` | local | local(H2) / prod(OceanBase/MySQL) |

## 部署

- 单机：`deploy/mirage-mock.service`（systemd）+ `deploy/mirage-mock.env.example`（环境变量模板），DDL 见 `deploy/schema.sql`（Flyway 自动执行）
- 容器：仓库根 `Dockerfile`（多阶段构建）+ `docker-compose.yml`（app + MySQL），见下文

## 测试与质量

- 单元测试：`mvn test`（mirage-dsl 表达式/国密、mirage-core 匹配引擎/渲染/内核两阶段/缓存热刷新、mirage-tcp 编解码/路由提取、mirage-admin 工具/日志/限流/安全校验）
- 覆盖率：JaCoCo 自动产出各模块 `target/site/jacoco/index.html`（当前核心模块已覆盖；check 硬门槛待覆盖率达标后开启）
- 兼容约定：ORM 统一 MyBatis-Plus，禁止手写方言 SQL；DDL 以 MySQL 语法为准（H2/OceanBase 兼容）

## 后续规划

- v2：场景状态机（表结构已预留）、录制回放（代理模式扩展点）、Redis 分布式缓存、TLV/XML 报文格式、小端长度头
- 安全与运维：登录二次验证、操作审计、指标埋点（Prometheus）、日志告警（webhook/邮件）
