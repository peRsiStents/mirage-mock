# 蜃楼 Mock（Mirage Mock）—— 接口 Mock 数据生成系统

面向测试环境的统一接口 Mock 平台。当前为 **M1（HTTP 全链路）+ M2（TCP 全链路）+ M3（管理端前端）** 版本。

> 技术栈：Java 8 + Spring Boot 2.7.x + MyBatis-Plus + Netty（M2）+ Bouncy Castle（国密）；前端 Vue 3 + Element Plus + Vite（M3）

## 构建

> 本仓库自带 `.tools/env.sh` 设置 `JAVA_HOME`(JDK8) 与 Maven 路径，bash 会话需先 `source .tools/env.sh`。
> 阿里云中央仓库镜像已配置在 `~/.m2/settings.xml`。

```bash
source .tools/env.sh
mvn clean install -DskipTests        # 全量构建，产出 mirage-server/target/mirage-mock.jar
mvn test                              # 跑单测（mirage-dsl / mirage-core）
```

> 管理端前端（`mirage-ui`）的预构建产物随 jar 一同打包（`mirage-ui/dist → classpath:/static`），无需 Node 也能产出含 UI 的自包含 jar。
> 若改动前端源码需重新构建：`cd mirage-ui && npm install && npm run build`，再 `mvn package` 即可刷新。

## 运行

```bash
source .tools/env.sh
java -jar mirage-server/target/mirage-mock.jar                 # 默认 local（H2 内存库）
# 生产/测试（OceanBase / MySQL 8）：
MIRAGE_PROFILE=prod MIRAGE_DB_URL=jdbc:mysql://host:3306/mirage... MIRAGE_DB_USER=xxx MIRAGE_DB_PASSWORD=xxx \
  java -jar mirage-server/target/mirage-mock.jar
```

- 管理端 REST：`http://localhost:9080/api/v1`（JWT 认证）
- 管理端 UI：`http://localhost:9080/`（Vue3 SPA，随 jar 自带；hash 路由）
- HTTP Mock 流量：`http://localhost:19080`
- 默认账号：`admin / admin123`（首次启动自动创建），并内置示例项目 `demo` 与接口 `GET /api/user/{userId}`（含灰度/兜底两条规则）

### 快速体验

```bash
# 1. 登录拿 token
TOKEN=$(curl -s -XPOST localhost:9080/api/v1/auth/login -H"Content-Type: application/json" \
  -d'{"username":"admin","password":"admin123"}' | python -c"import sys,json;print(json.load(sys.stdin)['data']['token'])")

# 2. 命中兜底规则（NORMAL）
curl -s localhost:19080/api/user/10086

# 3. 命中灰度规则（VIP，含身份证/金额/SM3 签名）
curl -s localhost:19080/api/user/10086 -H"X-Env: gray"
```

## 模块

```
mirage-parent        聚合 + 依赖版本管理
mirage-common        实体 / 枚举 / 工具 / 统一响应
mirage-dsl           表达式引擎（自研递归下降）+ 生成器函数库 + 国密底层 + SPI
mirage-core          规则缓存 / 匹配引擎 / 模板渲染（字段依赖拓扑排序）/ 热刷新
mirage-http          HTTP Mock Server（独立端口，端口识别 Filter）
mirage-tcp           Netty TCP Mock Server（帧切分/编解码/连接管理/推送）
mirage-admin         REST API / JWT 鉴权 / 密钥管理（落库加密）/ 日志
mirage-server        装配启动 + Profile + Flyway DDL
mirage-ui            管理端前端（Vue3 + Element Plus + Vite，构建产物随 server 打包）
```

## 管理 API（节选，统一前缀 `/api/v1`，`{code,message,data}`）

| 模块 | 端点 |
|---|---|
| 认证 | `POST /auth/login` |
| 项目 | `GET/POST/PUT/DELETE /projects`、`GET/POST /projects/{id}/members` |
| 接口 | `GET/POST /projects/{pid}/interfaces`、`PUT/DELETE /interfaces/{id}` |
| 规则 | `GET/POST /interfaces/{iid}/rules`、`PUT/DELETE /rules/{id}`、`POST /rules/{id}/toggle` |
| TCP监听 | `GET/POST /projects/{pid}/listeners`、`PUT/DELETE /listeners/{id}`、`POST /listeners/{id}/start|stop`、`GET /listeners/{id}/status` |
| 密钥 | `GET/POST /projects/{pid}/keys`、`POST /projects/{pid}/keys/sm2/generate?alias=`、`DELETE /keys/{id}` |
| 日志 | `GET /projects/{pid}/logs?interfaceId=&matched=&from=&to=&page=&size=` |
| 试算 | `POST /template/evaluate` |
| 函数库 | `GET /functions` |

## 数据生成 DSL（`${...}`）

- 人员证件：`name.cn` `name.en` `phone.cn_mobile` `idcard.cn`(校验位) `bankcard.cn`(Luhn) `uscc.cn` `address.cn` `email`
- 数值时间：`int(min,max)` `decimal(min,max,scale)` `seq(name,start)`(项目级持久) `uuid` `date(...)` `datetime(...)` 支持 `now`/`now-30d`
- 字符串：`string(charset,len)` `regex('正则')` `enum(...)` `concat(...)` `repeat(n,{子模板})`
- 摘要编码：`md5` `sha1/256/512` `sm3` `base64_encode/decode` `hex_encode/decode` `url_encode`
- 加解密签名：`sm4_encrypt/decrypt` `sm2_sign/verify/encrypt/decrypt` `aes_encrypt/decrypt` `rsa_sign/encrypt`（密钥按别名引用，结果默认 Base64，可 `'hex'`）
- 上下文变量：`path.*`（HTTP 路径变量）、`field.*`（报文字段/已渲染字段）

字段间可相互引用（如 `${md5(${data.phone})}`），引擎按依赖拓扑排序求值，循环依赖报错。规则/接口变更**即时热生效**（内存缓存失效重建，无需重启）。

## 配置项（application.yml）

| 项 | 默认 | 说明 |
|---|---|---|
| `mirage.http.port` | 19080 | Mock HTTP 端口 |
| `mirage.security.jwt-secret` | 内置 | JWT 密钥（生产用 `MIRAGE_JWT_SECRET` 覆盖） |
| `mirage.security.master-key` | 内置 | 密钥落库主密钥（生产用 `MIRAGE_MASTER_KEY` 覆盖） |

## 已实现

**M1 — HTTP Mock 全链路**：路由（path 变量）、规则匹配（header/query/body JSONPath/path/form，优先级+兜底）、模板渲染（生成器+字段依赖+repeat）、延迟/故障注入（ERROR_STATUS/TIMEOUT/RESET）、未命中 404、请求日志（异步落库+分页+7天清理）、JWT 鉴权、密钥管理（SM2 服务端生成、私钥 AES-GCM 加密落库）、模板试算、函数市场、H2/MySQL 双库兼容（Flyway）。

**M2 — TCP Mock 全链路**（Netty）：4 种帧切分（`length_field`/`delimiter`/`fixed`/`close_end`）、报文格式（`json`/`key_value`/`fixed_fields`/`hex_string` + `custom:<bean>` SPI）、路由与流水号提取（`$.x`/`field:`/`kv:`）、长短连接（LONG/SHORT）、SYNC/ASYNC、主动推送（onConnect/schedule cron 广播）、监听器动态 bind/unbind（start/stop）、未命中错误帧。

**M3 — 管理端前端**（Vue3 + Element Plus）：登录（JWT 持久化）、项目管理、接口与规则编辑（规则匹配器/模板 DSL `${...}` 在线编辑 + 试算实时预览）、TCP 监听器管理（start/stop/status）、密钥管理（SM2 服务端生成）、请求日志查询、函数市场侧栏。构建产物随 `mirage-server` jar 打包，访问根路径即用，无需独立部署前端。

### TCP 快速体验（示例监听器端口 9001）

启动后内置 `demo-tcp` 监听器：长度头 4 字节大端 + JSON，路由 `$.transCode`，交易码 `0200`，金额 ≤ 50000 命中（响应带 SM3 MAC + SM2 签名）。

```python
import socket, json, struct
def call(body):
    s = socket.create_connection(("127.0.0.1", 9001), timeout=5)
    b = json.dumps(body).encode()
    s.sendall(struct.pack(">I", len(b)) + b)      # 4 字节大端长度头 + JSON 体
    s.settimeout(3); data = b""
    try:
        while True:
            c = s.recv(4096)
            if not c: break
            data += c
    except socket.timeout: pass
    s.close(); return data
print(call({"transCode":"0200","serialNo":"S0001","amount":"1000"}))   # 命中
print(call({"transCode":"0200","serialNo":"S0002","amount":"99999"}))  # 不命中
```

## 后续阶段（技术方案 M4）

- **M4**：Docker 化、日志告警完善、tlv/xml 报文格式、小端长度头、close_end 多段聚合；v2：场景状态机、录制回放、Redis 分布式缓存
