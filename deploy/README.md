# 部署（裸 JAR · Linux · MySQL）

管理端/UI `9080`、Mock HTTP `19080`、TCP demo `9001`。jar 已内置前端，服务器**只需 JDK 8**。

## 1. 服务器准备

```bash
# JDK 8（必须）
sudo yum install -y java-1.8.0-openjdk   # CentOS/RHEL
# 或：sudo apt install -y openjdk-8-jre-headless   # Debian/Ubuntu
java -version                            # 确认 1.8.x

# MySQL 8（持久库）
sudo yum install -y mysql-community-server && sudo systemctl enable --now mysqld
# 或：sudo apt install -y mysql-server && sudo systemctl enable --now mysql
```

## 2. 建库建账号（Flyway 首启自动建表，无需手动建表）

```bash
sudo mysql <<'SQL'
CREATE DATABASE mirage DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'mirage'@'localhost' IDENTIFIED BY '改成强密码';
CREATE USER 'mirage'@'%'        IDENTIFIED BY '改成强密码';
GRANT ALL PRIVILEGES ON mirage.* TO 'mirage'@'localhost';
GRANT ALL PRIVILEGES ON mirage.* TO 'mirage'@'%';
FLUSH PRIVILEGES;
SQL
```

## 3. 上传 jar 与配置（在本地机器执行 scp）

```bash
# 本地 jar（已含前端）：mirage-server/target/mirage-mock.jar
scp mirage-server/target/mirage-mock.jar <user>@<server>:/tmp/
scp deploy/mirage-mock.service deploy/mirage-mock.env.example <user>@<server>:/tmp/
```

## 4. 服务器安装（systemd 托管：开机自启 + 崩溃重启）

```bash
sudo useradd -r -s /sbin/nologin mirage                    # 专用账号（端口>1024，非特权即可）
sudo mkdir -p /opt/mirage-mock /var/log/mirage-mock
sudo mv /tmp/mirage-mock.jar /opt/mirage-mock/
sudo cp /tmp/mirage-mock.env.example /opt/mirage-mock/mirage-mock.env
sudo chown -R mirage:mirage /opt/mirage-mock /var/log/mirage-mock

# 编辑 env：填 DB 密码、生成并填 JWT/MASTER 密钥
sudo vi /opt/mirage-mock/mirage-mock.env
#   MIRAGE_DB_PASSWORD=...
#   MIRAGE_JWT_SECRET=$(openssl rand -base64 48)
#   MIRAGE_MASTER_KEY=$(openssl rand -base64 48)
sudo chmod 600 /opt/mirage-mock/mirage-mock.env             # 含密码，限权

sudo cp /tmp/mirage-mock.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now mirage-mock
sudo systemctl status mirage-mock --no-pager                # active (running) 即成功
sudo journalctl -u mirage-mock -f                           # 看启动日志 / 排错
```

## 5. 防火墙 / 安全组放行端口

```bash
# 按需放行：9080(管理端/UI)、19080(Mock 流量)、9001(TCP demo，可选)
sudo firewall-cmd --permanent --add-port=9080/tcp --add-port=19080/tcp && sudo firewall-cmd --reload
# 云厂商还需在控制台「安全组」放行同样端口
```

## 6. 验证

```bash
curl http://localhost:9080/                                   # 返回前端首页 HTML
# 浏览器：http://<服务器IP>:9080/  登录 admin / admin123（首启自动创建，登录后请改密）
curl http://localhost:19080/api/user/10086                    # 命中 demo 兜底规则
```

## 日常运维

```bash
sudo systemctl restart mirage-mock     # 重启（改配置后）
sudo systemctl stop|start mirage-mock
sudo journalctl -u mirage-mock -n 200   # 查日志
# 升级：scp 新 jar 覆盖 /opt/mirage-mock/mirage-mock.jar 后 sudo systemctl restart mirage-mock
```

## 注意

- 首次启动 Flyway 自动执行 `V1__init.sql`、`V2__tcp_listener.sql` 建表并写入 `admin/admin123` 与 demo 数据。
- `MIRAGE_JWT_SECRET` / `MIRAGE_MASTER_KEY` 一旦设定，**不要随意改**（改 JWT 密钥会使所有已发 token 失效；改 MASTER 密钥会导致历史密钥私钥无法解密）。正式上线前一次配好。
- 规则/接口改动是内存热生效，无需重启；只有改 `mirage-mock.env` 或升级 jar 才需 restart。
