# 蜃楼 Mock（Mirage Mock）——接口 Mock 数据生成系统技术方案

> 版本：v1.0 | 面向读者：AI 编码助手 / 开发工程师 | 技术栈：Java 17 + Spring Boot 3 + Netty
> 命名寓意：海市蜃楼，以假乱真——测试环境里的仿真接口平台

---

## 1. 系统目标与非目标

### 1.1 目标

为测试环境提供统一的接口 Mock 平台：

- **HTTP 接口 Mock**：拦截 HTTP 请求，按匹配规则返回动态生成的响应
- **TCP 接口 Mock**：支持长连接与短连接，支持多种报文格式，可模拟服务端主动推送
- **动态数据生成**：按规则动态生成手机号、身份证号、金额、随机字符串等数据，支持 MD5、SM2/SM3/SM4 等国密算法
- **团队共用**：独立部署的 Web 服务，多项目隔离，规则热生效

### 1.2 非目标（v1 不做）

- 录制回放（v2 规划，架构预留代理模式扩展点）
- 多步骤状态机场景（如"下单→支付→查询"链路联动，表结构预留，v2 实现）
- 性能压测级流量承载（本系统面向功能测试，非压测桩）

---

## 2. 整体架构

### 2.1 架构图

```
┌────────────────────────────────────────────────────────────┐
│                      管理端 (Vue/React SPA)                  │
│     项目管理 │ HTTP接口 │ TCP监听 │ 模板编辑器 │ 密钥 │ 日志    │
└──────────────────────────┬─────────────────────────────────┘
                           │ REST API (JWT 认证)
┌──────────────────────────▼─────────────────────────────────┐
│                   管理服务 (Spring Boot)                     │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │ 接口/规则管理  │  │ 密钥管理      │  │ 日志查询          │  │
│  └──────┬───────┘  └──────────────┘  └──────────────────┘  │
│         │ 规则变更事件（版本号 + 缓存失效）                    │
└─────────┼──────────────────────────────────────────────────┘
          │
┌─────────▼──────────────────────────────────────────────────┐
│                    Mock 运行时内核                           │
│  ┌──────────────────────────────────────────────────────┐  │
│  │ 规则缓存（内存，版本号控制热刷新）                        │  │
│  └──────────────────────────────────────────────────────┘  │
│  ┌─────────────┐  ┌─────────────┐  ┌────────────────────┐  │
│  │ 规则匹配引擎  │→ │ 模板渲染引擎  │→ │ 数据生成器函数库      │  │
│  └─────────────┘  └─────────────┘  └────────────────────┘  │
├──────────────────────────┬──────────────────────────────────┤
│       HTTP Mock Server    │        TCP Mock Server (Netty)    │
│  (Spring MVC / 独立端口)   │  ┌───────────────────────────┐   │
│                           │  │ FrameDecoder 帧切分(可插拔) │   │
│                           │  │ MessageParser 语义解析(SPI)│   │
│                           │  │ 连接管理/推送调度           │   │
│                           │  └───────────────────────────┘   │
└──────────────────────────┬──────────────────────────────────┘
                           │
┌──────────────────────────▼─────────────────────────────────┐
│  MySQL（接口/规则/密钥/日志）      Redis（可选：分布式缓存）      │
└────────────────────────────────────────────────────────────┘
```

### 2.2 请求处理流程

**HTTP 请求**：请求进入 → 按 `method + path` 定位接口 → 按优先级遍历规则，评估匹配条件（header/query/body JSONPath）→ 命中规则 → 应用延迟/故障注入 → 渲染响应模板（调用生成器函数）→ 返回响应 + 写请求日志。

**TCP 报文**：字节流进入 → FrameDecoder 完成帧切分 → MessageParser 解析为字段 Map → 路由提取规则取交易码 → 定位接口与规则 → 渲染响应模板 → MessageEncoder 序列化 → 写回连接 + 写请求日志。

---

## 3. 核心概念模型（数据表设计）

### 3.1 表结构总览

| 表名 | 说明 |
|---|---|
| `project` | 项目（数据隔离单元） |
| `user` / `role` / `project_member` | 用户、角色（管理员/普通成员）、项目成员关系 |
| `api_interface` | 接口定义（HTTP / TCP 统一抽象） |
| `mock_rule` | Mock 规则（匹配条件 + 响应模板 + 故障注入） |
| `mock_scenario` | 场景状态机（v2 预留） |
| `tcp_listener` | TCP 监听配置 |
| `secret_key` | 密钥管理（SM2/SM4/AES/RSA） |
| `mock_request_log` | 请求日志 |

### 3.2 关键表字段

**api_interface（接口定义）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| project_id | bigint | 所属项目 |
| name | varchar(128) | 接口名称 |
| protocol | varchar(16) | `HTTP` / `TCP` |
| http_method | varchar(8) | HTTP 专用：GET/POST/ANY 等 |
| http_path | varchar(256) | HTTP 专用：路径，支持 path 变量 `/user/{id}` |
| tcp_listener_id | bigint | TCP 专用：所属监听器 |
| tcp_route_expr | varchar(256) | TCP 专用：路由匹配表达式，见 §5.4 |
| status | tinyint | 启用/停用 |
| remark | varchar(512) | |

**mock_rule（Mock 规则）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| interface_id | bigint | 所属接口 |
| name | varchar(128) | 规则名 |
| priority | int | 优先级，数值小者先匹配，默认 100 |
| match_condition | text | JSON 数组，匹配条件 DSL，见 §6.2 |
| response_template | text | 响应模板 DSL，见 §6.3 |
| delay_type | varchar(16) | `NONE` / `FIXED` / `RANDOM` |
| delay_ms | int | 固定延迟毫秒数 |
| delay_min_ms / delay_max_ms | int | 随机延迟区间 |
| fault_type | varchar(16) | `NONE` / `ERROR_STATUS` / `RESET` / `TIMEOUT` |
| fault_config | varchar(512) | JSON，如 `{"httpStatus":500}` |
| status | tinyint | 启用/停用 |

**tcp_listener（TCP 监听配置）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| project_id | bigint | |
| name | varchar(128) | |
| port | int | 监听端口 |
| conn_mode | varchar(16) | `LONG`（长连接）/ `SHORT`（短连接，一次问答后关闭） |
| frame_config | text | JSON，帧切分配置，见 §5.2 |
| message_format | varchar(32) | 报文格式：`json`/`xml`/`key_value`/`fixed_fields`/`tlv`/`hex_string`/`custom:*` |
| message_format_config | text | JSON，格式专属配置 |
| route_extract | varchar(256) | 路由提取表达式，见 §5.4 |
| serial_extract | varchar(256) | 流水号提取表达式（异步匹配用） |
| match_mode | varchar(16) | `SYNC`（串行一问一答）/ `ASYNC`（按流水号关联） |
| push_config | text | JSON，主动推送配置，见 §5.5 |
| status | tinyint | 启用/停用（启用即绑定端口） |

**secret_key（密钥）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| project_id | bigint | 项目级隔离 |
| alias | varchar(64) | 别名，模板中引用，如 `key_merchant` |
| algorithm | varchar(16) | `SM2` / `SM4` / `AES` / `RSA` |
| public_key | text | 非对称算法公钥（PEM/Base64） |
| private_key | text | 私钥/对称密钥，**AES 加密后落库** |
| iv_value | varchar(64) | CBC 模式默认 IV（可选） |

**mock_request_log（请求日志）**

| 字段 | 类型 | 说明 |
|---|---|---|
| id | bigint PK | |
| project_id / interface_id / rule_id | bigint | rule_id 为空表示未命中 |
| protocol | varchar(16) | |
| client_addr | varchar(64) | 来源地址 |
| request_raw | text | 请求原文（HTTP 报文或 TCP Hex） |
| request_parsed | text | 解析后的字段 JSON |
| response_raw | text | 实际响应原文 |
| matched | tinyint | 是否命中规则（未命中标红告警） |
| cost_ms | int | 处理耗时 |
| create_time | datetime | |

**保留策略**：单项目最多 10 万条或保留 7 天，定时任务滚动清理（两者先到为准）。

---

## 4. HTTP Mock 详细设计

### 4.1 监听与路由

- Mock 运行时内核监听独立端口（默认 `19080`，可配），所有 HTTP mock 流量进入该端口
- 路由匹配：`method + path` → 接口；path 支持模板变量 `/api/user/{userId}`，变量值注入模板上下文，可在响应模板中引用 `${path.userId}`

### 4.2 规则匹配

同一接口下多条规则按 `priority` 升序评估，首个 `match_condition` 全部满足者命中；条件为空视为默认规则（兜底）。

匹配条件 DSL（JSON 数组，元素间为 AND 关系）：

```json
[
  {"source": "header",  "key": "X-Env",      "op": "eq",      "value": "gray"},
  {"source": "query",   "key": "type",       "op": "in",      "value": ["1","2"]},
  {"source": "body",    "key": "$.user.age", "op": "gte",     "value": 18},
  {"source": "body",    "key": "$.orderNo",  "op": "regex",   "value": "^ORD\\d{10}$"},
  {"source": "path",    "key": "userId",     "op": "exists" }
]
```

- `source`：`header` / `query` / `body`（JSONPath）/ `path`（路径变量）/ `form`
- `op`：`eq` `ne` `in` `gt` `gte` `lt` `lte` `regex` `contains` `exists` `not_exists`

### 4.3 响应模板

```yaml
# mock_rule.response_template 示例（YAML 存储）
status: 200
headers:
  Content-Type: application/json
  X-Mock-Rule: rule-vip
body:
  code: "0000"
  message: "success"
  data:
    userId: "${path.userId}"
    userName: "${name.cn}"
    phone: "${phone.cn_mobile}"
    idCard: "${idcard.cn}"
    balance: "${decimal(1000, 99999, 2)}"
    registerTime: "${datetime(now-30d, now, yyyy-MM-dd HH:mm:ss)}"
    token: "${string(alpha_num, 32)}"
    sign: "${md5(${data.phone} + '|' + ${data.balance})}"
    orders: "${repeat(3, { orderNo: concat('ORD', ${string(numeric,10)}), amount: ${decimal(1,500,2)} })}"
```

渲染规则：
1. `body` 为对象树，叶子节点中的 `${...}` 表达式逐一求值
2. **字段依赖**：表达式可引用同级/上级已求值字段（`${data.phone}`），引擎按依赖拓扑排序求值，循环依赖时报错并返回 500 + 错误说明
3. `repeat(n, template)` 生成 n 元素数组，元素内同样支持表达式

### 4.4 故障注入

| 类型 | 行为 |
|---|---|
| `NONE` | 正常返回 |
| `ERROR_STATUS` | 忽略模板，直接返回配置的 HTTP 状态码与错误体 |
| `TIMEOUT` | 挂起不响应（超过客户端超时） |
| `RESET` | 直接断开 TCP 连接 |
| 延迟 | 命中规则后先 sleep（FIXED 固定 / RANDOM 区间内随机），再执行上述行为 |

### 4.5 未命中行为

无任何规则命中时：返回 HTTP 404 + JSON `{"error":"NO_RULE_MATCHED","path":...}`，同时记录 `matched=0` 的日志并在管理端标红。

---

## 5. TCP Mock 详细设计

### 5.1 总体设计

基于 **Netty 4.x**，每个启用的 `tcp_listener` 绑定一个 `ServerBootstrap`。核心原则：**帧切分（解决粘包/拆包）与语义解析（报文格式）分离**，两层均可插拔。

```
字节流 → [FrameDecoder] → 完整报文字节 → [MessageParser] → 字段Map
                                                             ↓
连接 ← [MessageEncoder] ← 序列化 ← [模板渲染+规则匹配] ← 路由提取
```

### 5.2 帧切分层（FrameDecoder，`frame_config`）

内置 4 种模式，映射到 Netty 自带解码器：

| 模式 type | 说明 | 配置示例 |
|---|---|---|
| `length_field` | 长度头（2/4/8 字节，大/小端，偏移与长度修正） | `{"type":"length_field","lenBytes":4,"endian":"big","offset":0,"adjustment":0,"initialStrip":4}` |
| `delimiter` | 分隔符结尾 | `{"type":"delimiter","value":"0x1C"}` 或 `"value":"\\n"` |
| `fixed` | 定长报文 | `{"type":"fixed","length":200}` |
| `close_end` | 不拆包，读到当前缓冲区全部即一次请求（短连接常用） | `{"type":"close_end"}` |

### 5.3 语义解析层（MessageParser，`message_format`）

| 格式 | 说明 | 专属配置示例 |
|---|---|---|
| `json` | 报文体为 JSON 文本 | — |
| `xml` | 报文体为 XML，解析为字段树 | — |
| `key_value` | `K=V` 键值对文本 | `{"pairSep":"&","kvSep":"="}` |
| `fixed_fields` | 定长字段切分 | `{"fields":[{"name":"orgNo","len":10},{"name":"date","len":8}]}` |
| `tlv` | TLV 结构 | `{"tagBytes":2,"lenBytes":2,"tagEncoding":"hex"}` |
| `hex_string` | 不解析，整体以 hex 透传，模板内用 hex 函数处理 | — |
| `custom:<beanName>` | 自定义 SPI，见 §5.6 | 任意 JSON |

编码方向（响应序列化）与解码对称：响应模板求值后得到字段 Map → 按同一格式配置序列化为字节。

### 5.4 路由与流水号提取

- **路由提取 `route_extract`**：从字段 Map 中取交易码定位接口。表达式语法：
  - JSON：`$.transCode`
  - fixed_fields：`field:orgNo`（字段名）或 `pos:16,4`（偏移16取4字节）
  - key_value：`kv:trxnType`
- **流水号提取 `serial_extract`**：语法同上，用于 ASYNC 模式下请求与响应的关联键
- 提取值与 `api_interface.tcp_route_expr` 精确匹配定位接口，之后规则匹配与 HTTP 共用同一套 `match_condition`（`source` 扩展 `field`，引用报文字段）

### 5.5 连接生命周期与主动推送

- **长连接（LONG）**：连接注册到连接管理器；请求处理支持 `SYNC`（严格一问一答串行）与 `ASYNC`（按流水号关联，并发请求）两种模式
- **短连接（SHORT）**：处理完一次问答后由 Mock 端主动关闭连接
- **主动推送 `push_config`**：

```json
{
  "onConnect": [{"template": "欢迎报文模板ID", "delayMs": 500}],
  "schedule":  [{"template": "心跳模板ID", "cron": "*/30 * * * * *", "target": "all"}]
}
```

`target`：`all`（该监听器全部连接）/ 指定连接标识。推送模板同样走生成器函数渲染。

### 5.6 自定义协议 SPI

```java
public interface MessageParser {
    Map<String, Object> parse(byte[] frame, Map<String, Object> formatConfig);
    byte[] encode(Map<String, Object> fields, Map<String, Object> formatConfig);
}
```

实现类打包为 jar 放入部署目录 `plugins/`，启动时扫描加载；`message_format` 配置为 `custom:<beanName>` 即可使用（如 8583、私有二进制协议）。

---

## 6. 数据生成规则 DSL 规范

### 6.1 表达式语法

- 表达式包裹于 `${...}`，可出现在模板任意字符串值中；字符串中可内联多个表达式与普通文本拼接
- 支持函数嵌套、字符串拼接（`+`）、字段引用（`${data.xxx}`、`${path.xxx}`、`${field.xxx}`）
- 求值顺序：按字段依赖拓扑排序，循环依赖报错

### 6.2 内置生成器函数清单

**人员与证件**

| 函数 | 说明 | 示例输出 |
|---|---|---|
| `${name.cn}` | 中文姓名 | 张伟 |
| `${name.en}` | 英文姓名 | John Smith |
| `${phone.cn_mobile}` | 中国大陆手机号 | 13812345678 |
| `${idcard.cn}` | 身份证号（含校验位） | 110101199003077758 |
| `${bankcard.cn}` | 银行卡号（Luhn 校验） | 6222021234567890123 |
| `${uscc.cn}` | 统一社会信用代码 | 91110108MA01C3Y37K |
| `${address.cn}` | 中文地址 | 北京市海淀区xx路12号 |
| `${email}` | 邮箱 | abc123@example.com |

**数值与时间**

| 函数 | 说明 |
|---|---|
| `${int(min, max)}` | 区间随机整数 |
| `${decimal(min, max, scale)}` | 区间随机小数，如金额 `${decimal(100,9999,2)}` |
| `${seq(start)}` | 自增序列（项目级持久，规则重启不回退） |
| `${uuid}` / `${uuid(nodash)}` | UUID |
| `${date(min, max, pattern)}` | 随机日期，min/max 支持 `now`、`now-30d`、`now+7d` |
| `${datetime(min, max, pattern)}` | 随机日期时间 |

**字符串**

| 函数 | 说明 |
|---|---|
| `${string(charset, len)}` | 定长随机字符串，charset：`alpha`/`numeric`/`alpha_num`/`hex`/自定义字符集 |
| `${string(charset, minLen, maxLen)}` | 区间长度随机字符串 |
| `${regex('表达式')}` | 按正则生成匹配字符串 |
| `${enum('a','b','c')}` | 枚举随机取一 |
| `${repeat(n, template)}` | 生成 n 元素数组 |

**摘要与编码**

| 函数 | 说明 |
|---|---|
| `${md5(data)}` `${sha1(data)}` `${sha256(data)}` `${sha512(data)}` | 摘要，输出 hex |
| `${sm3(data)}` | SM3 国密摘要 |
| `${base64_encode(data)}` / `${base64_decode(data)}` | Base64 |
| `${hex_encode(data)}` / `${hex_decode(data)}` | Hex |
| `${url_encode(data)}` | URL 编码 |

**加解密（引用 secret_key 别名）**

| 函数 | 说明 |
|---|---|
| `${sm4_encrypt(data, 'keyAlias'[, 'CBC'])}` / `${sm4_decrypt(...)}` | SM4，默认 ECB，可选 CBC（用密钥记录中的 IV） |
| `${sm2_sign(data, 'keyAlias')}` / `${sm2_verify(data, sign, 'keyAlias')}` | SM2 签名/验签 |
| `${sm2_encrypt(data, 'keyAlias')}` / `${sm2_decrypt(...)}` | SM2 加解密 |
| `${aes_encrypt(data, 'keyAlias'[, mode])}` / `${aes_decrypt(...)}` | AES |
| `${rsa_sign(data, 'keyAlias')}` / `${rsa_encrypt(...)}` | RSA |

输出编码约定：加解密与签名结果默认 Base64，可加第三参数 `'hex'` 改为 hex。

### 6.3 函数扩展

新增函数实现 `MockFunction` SPI（`name()` + `eval(args, ctx)`），注册进函数库即可在模板使用；函数市场页面自动读取注册表生成文档。

---

## 7. 管理端 API 设计（REST）

统一前缀 `/api/v1`，JWT 认证（`Authorization: Bearer`），响应包 `{code, message, data}`。

| 模块 | 端点（摘选） |
|---|---|
| 认证 | `POST /auth/login` |
| 项目 | `GET/POST/PUT/DELETE /projects`、`GET/POST /projects/{id}/members` |
| 接口 | `GET/POST/PUT/DELETE /projects/{pid}/interfaces` |
| 规则 | `GET/POST/PUT/DELETE /interfaces/{iid}/rules`、`POST /rules/{id}/toggle` |
| TCP监听 | `GET/POST/PUT/DELETE /projects/{pid}/listeners`、`POST /listeners/{id}/start|stop` |
| 密钥 | `GET/POST/DELETE /projects/{pid}/keys`、`POST /keys/sm2/generate`（服务端生成 SM2 密钥对） |
| 日志 | `GET /projects/{pid}/logs?interfaceId=&matched=&from=&to=&page=` |
| 试算 | `POST /template/evaluate`（入参：模板 + 模拟上下文，返回渲染结果） |
| 函数库 | `GET /functions`（函数市场数据） |

**热生效机制**：规则/监听器变更时递增项目级 `rule_version`；运行时内核每 3 秒比对版本号（或接收变更事件），失效并重建内存规则缓存，无需重启。TCP 监听端口变更通过动态 `bind/unbind` 生效。

---

## 8. 管理端页面清单

1. **登录 / 项目管理**：项目列表、成员管理（管理员/普通成员）
2. **HTTP 接口管理**：接口列表、规则编辑器（匹配条件 + 响应模板 + 延迟/故障注入配置 + 在线调试）
3. **TCP 监听管理**：端口与编解码可视化配置（帧切分/报文格式/路由提取/长短连接/推送），报文模板编辑
4. **规则模板编辑器**：语法高亮、**生成器函数市场**侧栏（函数说明 + 示例 + 一键插入）、**实时预览（试算）**按钮即时生成样例数据
5. **密钥管理**：SM2 密钥对生成/上传，SM4/AES/RSA 密钥配置
6. **请求日志**：多维筛选；未命中记录标红告警；查看请求/响应原文与解析结果
7. **系统管理**：用户、角色

---

## 9. 技术选型与部署

| 项 | 选型 | 理由 |
|---|---|---|
| 语言/框架 | Java 8 + Spring Boot 2.7.x | 团队主流栈（JDK 1.8 约束下 Spring Boot 最高 2.7） |
| TCP 服务 | Netty 4.1 | 长短连接、编解码器体系成熟 |
| 国密算法 | Bouncy Castle (bcprov-jdk18on) | SM2/SM3/SM4 全覆盖 |
| 表达式解析 | AviatorScript 5 | 支持嵌套函数、字符串拼接、自定义函数注入 |
| 数据生成 | DataFaker + 自研生成器 | 身份证/银行卡/信用代码等自研以保证校验位正确 |
| JSONPath | Jayway JsonPath | 匹配条件与路由提取 |
| 存储 | MySQL 8 | 团队共用标配 |
| 前端 | Vue 3 + Element Plus | 管理端 |
| 部署 | 单机 Jar + OceanBase，提供 Dockerfile；本地测试切 H2 | v1 从简；Redis 预留非必需 |

**数据库兼容性约定（重要）**：

- 生产/测试环境使用 **OceanBase（MySQL 租户模式）**，本地开发与单元测试使用 **H2（`MODE=MySQL`）**
- ORM 统一使用 MyBatis-Plus / JPA，**禁止手写数据库方言 SQL**（如 `ON DUPLICATE KEY`、`LIMIT` 方言差异以外的 MySQL 专属函数），保证双库可切换
- DDL 建表脚本以 MySQL 语法为准（OceanBase MySQL 模式直接兼容），通过 Flyway 管理版本；H2 用同一套脚本初始化
- 字段类型注意：`mediumtext` 在 H2 下映射为 `CLOB`，建表脚本中统一用 `text`/`clob` 兼容写法
- 多环境数据源通过 Spring Profile 切换：`application-local.yml`（H2）/ `application-test.yml` / `application-prod.yml`（OceanBase）

**JDK 8 约束（重要）**：

- 全项目基于 **JDK 1.8** 编译运行（`maven.compiler.source/target = 1.8`），禁止使用 Java 9+ 语法与 API（var、List.of、Optional.isEmpty、新 HttpClient 等）
- Spring Boot 锁定 **2.7.x**：依赖包名为 `javax.*`（非 `jakarta.*`），AI 生成 import 时必须注意
- 依赖版本核验：Bouncy Castle 用 `bcprov-jdk15to18` 或 `bcprov-jdk18on`（均支持 1.8）；Netty 4.1.x、AviatorScript 5.x、DataFaker 1.x 均兼容 1.8

**Maven 模块划分**：

```
mock-parent
├─ mock-common      （模型/常量/工具）
├─ mock-dsl         （表达式引擎、生成器函数库、SPI 定义）
├─ mock-core        （规则匹配引擎、模板渲染、规则缓存、热刷新）
├─ mock-http        （HTTP Mock Server）
├─ mock-tcp         （Netty TCP Mock Server、编解码、连接管理、推送调度）
├─ mock-admin       （REST API、认证鉴权、密钥管理、日志）
└─ mock-server      （装配启动模块）
```

---

## 10. 完整配置样例

### 10.1 TCP 监听器（长度头 + JSON 报文，长连接异步）

```json
{
  "name": "支付网关模拟",
  "port": 9001,
  "connMode": "LONG",
  "matchMode": "ASYNC",
  "frameConfig": {"type": "length_field", "lenBytes": 4, "endian": "big", "initialStrip": 4},
  "messageFormat": "json",
  "routeExtract": "$.transCode",
  "serialExtract": "$.serialNo",
  "pushConfig": {"schedule": [{"template": "心跳模板", "cron": "*/30 * * * * *", "target": "all"}]}
}
```

### 10.2 TCP 接口 + 规则（消费交易，响应带 SM2 签名）

```json
{
  "interface": {"name": "消费交易", "protocol": "TCP", "tcpRouteExpr": "0200"},
  "rule": {
    "name": "成功响应",
    "priority": 10,
    "matchCondition": [{"source": "field", "key": "$.amount", "op": "lte", "value": 50000}],
    "responseTemplate": {
      "transCode": "0210",
      "serialNo": "${field.serialNo}",
      "respCode": "00",
      "cardNo": "${bankcard.cn}",
      "amount": "${field.amount}",
      "traceNo": "${string(numeric, 12)}",
      "mac": "${sm3(${field.serialNo} + ${field.amount})}",
      "sign": "${sm2_sign(${field.serialNo} + '|' + ${field.amount}, 'key_gateway')}"
    }
  }
}
```

### 10.3 HTTP 接口 + 灰度规则

```json
{
  "interface": {"name": "查询用户信息", "protocol": "HTTP", "httpMethod": "GET", "httpPath": "/api/user/{userId}"},
  "rules": [
    {"name": "灰度用户", "priority": 10,
     "matchCondition": [{"source": "header", "key": "X-Env", "op": "eq", "value": "gray"}],
     "responseTemplate": {"status": 200, "body": {"code": "0000", "data": {"userId": "${path.userId}", "level": "VIP", "phone": "${phone.cn_mobile}"}}},
     "delayType": "RANDOM", "delayMinMs": 50, "delayMaxMs": 300},
    {"name": "兜底", "priority": 100, "matchCondition": [],
     "responseTemplate": {"status": 200, "body": {"code": "0000", "data": {"userId": "${path.userId}", "level": "NORMAL"}}}}
  ]
}
```

---

## 11. 里程碑建议

| 阶段 | 内容 | 产出 |
|---|---|---|
| M1 | mirage-dsl（表达式+生成器）+ mirage-core + HTTP Mock + 基础管理 API | HTTP 全链路可用 |
| M2 | mirage-tcp（4 种帧切分 + json/kv/fixed 格式 + 长短连接）+ TCP 管理 API | TCP 全链路可用 |
| M3 | 管理端前端全部页面 + 密钥管理 + 函数市场 + 试算 | 团队可用版本 |
| M4 | TLV/custom SPI、主动推送、日志告警完善、Docker 化 | v1.0 发布 |
| v2 | 场景状态机、录制回放（代理模式）、Redis 分布式缓存 | — |

---

## 附录 A：术语

| 术语 | 含义 |
|---|---|
| 接口 | 一个可被 mock 的端点（HTTP path 或 TCP 交易码） |
| 规则 | 接口下的一组"匹配条件→响应模板"映射 |
| 帧切分 | 从 TCP 字节流中划分完整报文边界 |
| 路由提取 | 从报文字段中取出交易码以定位接口 |
| 试算 | 在编辑器中对模板即时求值预览 |
