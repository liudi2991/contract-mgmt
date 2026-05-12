# ops/ —— 单机部署脚本

把项目目录整体 SCP 到云主机，再加上**编译产物**（后端 jar、前端 dist），跑一条命令就能拉起完整环境。

> 与 [../部署文档.md](../部署文档.md) 配套阅读：本目录是文档中"方案 A"的可执行实现。

---

## 1. 适配环境

| OS | 验证 |
| --- | --- |
| Ubuntu 20.04 / 22.04 / 24.04 | ✅ 主用 |
| Debian 11 / 12 | ✅ |
| CentOS 7 / Rocky 8 / 9 | ✅ |
| RHEL 8+ | ✅ |
| Alibaba Cloud Linux 3 | ✅（按 RHEL 路径走） |

**资源要求**：≥ 2 vCore / 4 GB RAM / 50 GB SSD。低于此规格 MySQL 缓冲池会被压缩，性能下降。

---

## 2. 三步部署

### 步骤 1 · 准备产物

在 **构建机**（或本地）：

```bash
# 后端
cd backend
./mvnw clean package -DskipTests
ls target/contract-mgmt-backend.jar

# 前端
cd ../frontend
pnpm install --frozen-lockfile
pnpm build
ls dist/
```

### 步骤 2 · 上传到云主机

```bash
# 整个项目 + 产物一次性 SCP（也可只传 ops/ 与产物，更轻量）
rsync -av --exclude node_modules --exclude target \
  ./ root@<云主机 IP>:/root/contract-mgmt-deploy/
scp backend/target/contract-mgmt-backend.jar root@<云主机 IP>:/root/contract-mgmt-deploy/
scp -r frontend/dist root@<云主机 IP>:/root/contract-mgmt-deploy/frontend-dist
```

### 步骤 3 · 一键部署

SSH 到云主机：

```bash
cd /root/contract-mgmt-deploy

# A. 仅 HTTP（最简，用 IP 直连）
sudo bash ops/install.sh \
  --jar    ./contract-mgmt-backend.jar \
  --dist   ./frontend-dist \
  --domain <服务器公网IP或域名> \
  --yes

# B. 启用 HTTPS（推荐，需提前准备证书）
sudo bash ops/install.sh \
  --jar      ./contract-mgmt-backend.jar \
  --dist     ./frontend-dist \
  --domain   contract.example.com \
  --ssl-cert /etc/letsencrypt/live/contract.example.com/fullchain.pem \
  --ssl-key  /etc/letsencrypt/live/contract.example.com/privkey.pem \
  --yes
```

部署过程会自动完成 9 步：

1. 安装系统依赖（curl/openssl/gettext/…）
2. 安装 OpenJDK 17（若未安装）
3. 安装 Docker + Compose v2（若未安装）
4. 创建系统用户 `contract` + 目录骨架
5. 生成 `.env`（密码均为 32 位随机）、`docker-compose.prod.yml`、`application-prod.yml`、`start-backend.sh`、systemd unit
6. 部署后端 jar
7. 启动 MySQL/Redis/MinIO，等待健康，预创建 MinIO bucket
8. 启动后端 systemd 服务，等待 `/actuator/health = UP`
9. 部署前端到 `/var/www/contract-mgmt`，安装并配置 Nginx（自动写 HTTPS / HSTS / 静态缓存 / Swagger 内网限流）

完成后终端会打印**访问地址**、**默认账号**、**.env 路径**、**常用运维命令**。

---

## 3. 脚本清单

| 脚本 | 作用 |
| --- | --- |
| `install.sh` | **一键部署**入口，幂等可重入 |
| `upgrade.sh` | 只升级后端 jar 和/或前端 dist，自动快照备份 + 健康检查；可用旧 jar 回滚 |
| `backup.sh` | MySQL 全量 `mysqldump` + MinIO `mc mirror` + 配置打包；自动清理过期备份 |
| `restore.sh` | 从备份文件恢复（**会覆盖**当前数据） |
| `uninstall.sh` | 卸载，默认保留 `data/` 与 `backup/`；加 `--purge` 彻底清理 |
| `health.sh` | 端到端体检：进程 / 容器 / 端口 / 健康端点 / 磁盘 / 最近日志 |

---

## 4. 目录布局

```
/opt/contract-mgmt/                  # 安装根目录（可通过 --app-home 覆盖）
├── app/
│   ├── contract-mgmt-backend.jar    # 后端可执行 jar
│   └── start-backend.sh             # JVM 启动脚本
├── config/
│   ├── .env                         # 密码 / Secret（chmod 600）
│   └── application-prod.yml         # Spring Boot 生产配置
├── data/
│   ├── mysql/                       # MySQL 数据卷
│   ├── redis/                       # Redis AOF
│   └── minio/                       # MinIO 对象
├── logs/
│   ├── app.log                      # 应用日志（rolling 100MB × 30 天）
│   ├── backend.log                  # systemd stdout
│   └── backend-error.log            # systemd stderr
├── backup/
│   ├── mysql/                       # daily sql.gz
│   ├── minio/                       # daily bucket tar.gz
│   ├── config/                      # daily config tar.gz
│   └── upgrade/                     # upgrade 时的快照
└── docker-compose.prod.yml          # 基础设施编排（仅监听 127.0.0.1）
```

`/etc/systemd/system/contract-mgmt-backend.service` 由 install 写入。

Nginx：`/etc/nginx/sites-available/contract-mgmt.conf`（Debian 系）或 `/etc/nginx/conf.d/contract-mgmt.conf`（RHEL 系）。

---

## 5. 安装参数表

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `--jar <path>` | 自动探测 `./contract-mgmt-backend.jar` | 后端 jar 路径 |
| `--dist <path>` | 自动探测 `./frontend-dist` | 前端构建产物目录（含 `index.html`） |
| `--domain <name>` | 交互式询问 | 服务域名或公网 IP；用于 Nginx 与 `APP_PUBLIC_URL` |
| `--ssl-cert <path>` | 空（HTTP only） | HTTPS 证书 fullchain；与 `--ssl-key` 配对使用 |
| `--ssl-key <path>` | 空 | HTTPS 证书私钥 |
| `--app-home <path>` | `/opt/contract-mgmt` | 安装根目录 |
| `--app-user <user>` | `contract` | systemd 运行用户 |
| `--bucket <name>` | `contract-mgmt` | MinIO bucket |
| `--skip-docker-install` | false | 跳过 Docker 安装（已自行装好） |
| `--skip-nginx` | false | 跳过 Nginx 安装/配置（用其它网关） |
| `-y` / `--yes` | false | 全自动 / 非交互 |

---

## 6. 安装产物的安全规约

- `.env`：`chmod 600`，仅 `contract` 用户可读；包含全部密码
- MySQL / Redis / MinIO 端口**只监听** `127.0.0.1`，**不**暴露公网
- Nginx：
  - 默认 HSTS 1 年（启用 HTTPS 时）
  - `/swagger-ui` `/v3/api-docs` `/actuator` `/minio-console` 仅允许 RFC1918 内网 + 127.0.0.1
  - 静态资源 30 天 immutable 缓存
- systemd unit：`NoNewPrivileges`、`PrivateTmp`、`ProtectSystem=full`，仅 `ReadWritePaths=${APP_HOME}`

---

## 7. 升级（≈ 30 秒，零停机不可保证）

```bash
# 把新 jar / 新 dist 上传到 /root/upgrade/
sudo bash /opt/contract-mgmt/ops/upgrade.sh \
  --jar  /root/upgrade/contract-mgmt-backend.jar \
  --dist /root/upgrade/frontend-dist
```

升级流程：

1. 自动把当前 jar + dist 快照到 `${APP_HOME}/backup/upgrade/`
2. 替换 jar → `systemctl restart` → 等 `/actuator/health = UP`（最长 3 分钟）
3. 替换前端 → `nginx reload`
4. 打印回滚命令

如需回滚：

```bash
sudo bash /opt/contract-mgmt/ops/upgrade.sh \
  --jar  /opt/contract-mgmt/backup/upgrade/jar-<TS>.bak
```

> Flyway 不支持自动 downgrade，**含破坏性 DDL 的版本**回滚前必须先从备份恢复数据库。

---

## 8. 备份

`backup.sh` 适合放 cron：

```cron
# /etc/cron.d/contract-mgmt-backup
0 2 * * *  root  /opt/contract-mgmt/ops/backup.sh >> /opt/contract-mgmt/logs/backup.log 2>&1
```

或一次性手跑：

```bash
sudo bash /opt/contract-mgmt/ops/backup.sh
sudo bash /opt/contract-mgmt/ops/backup.sh --retention-days 60
```

产物路径见 [§4](#4-目录布局)。

异地容灾建议：把 `backup/` 同步到 OSS / S3 / 异地机：

```bash
# 示例：阿里云 OSS
ossutil cp -r /opt/contract-mgmt/backup/ oss://my-bucket/contract-mgmt/ \
  --update --bigfile-threshold 100000000
```

---

## 9. 恢复

```bash
sudo bash /opt/contract-mgmt/ops/restore.sh \
  --mysql /opt/contract-mgmt/backup/mysql/contract_mgmt-2026-05-12.sql.gz \
  --minio /opt/contract-mgmt/backup/minio/contract-mgmt-bucket-2026-05-12.tar.gz
```

会自动：停后端 → 清空当前库 / 桶 → 导入备份 → 启后端 → 健康检查。

恢复前**强烈建议**先备份一次当前状态，避免误操作无法回头。

---

## 10. 健康体检

```bash
bash /opt/contract-mgmt/ops/health.sh
```

输出涵盖：

- systemd 服务状态（backend / nginx）
- 容器状态（cm-mysql / cm-redis / cm-minio）
- 端口监听
- `/actuator/health` 返回值
- 关键挂载点磁盘剩余
- `app.log` 最后 5 行

非 0 退出码表示有异常项，可放入监控的"主机巡检"作业。

---

## 11. 常见问题

| 现象 | 处理 |
| --- | --- |
| `Permission denied` 写 /var/www | `--skip-nginx` 跳过，或检查 SELinux：`setenforce 0` 临时禁用，确认后再写 policy |
| `docker compose` 命令找不到 | 老版本：`apt install docker-compose-plugin` 或重新装 Docker |
| `wait_http` 等待 backend 超时 | 看 `journalctl -u contract-mgmt-backend -f`；常见原因：JVM 内存不够（Xmx > 物理内存）、Flyway 报错 |
| MinIO bucket 创建失败 | install 会容忍此错误，后端启动时会重试；如仍失败，登录 `http://127.0.0.1:9001` 手动创建 |
| 重新跑 install 会丢密码吗 | 不会。检测到 `.env` 存在会**复用其中密码**，仅刷新 yml / unit；如需重置密码请先 `uninstall.sh --purge` |

---

## 12. 与文档的对应关系

| 文档 | 章节 | ops/ 对应 |
| --- | --- | --- |
| [部署文档.md](../部署文档.md) | §5.3 方案 A | `install.sh` |
| 部署文档 | §6 配置文件 | `templates/*` |
| 部署文档 | §8 上线 Checklist | install 完成后照单核对 |
| 部署文档 | §9 备份恢复 | `backup.sh` / `restore.sh` |
| 部署文档 | §10 升级回滚 | `upgrade.sh` |
| 部署文档 | §11 监控告警 | `health.sh`（可接入 Prometheus / Zabbix 主机巡检） |
