#!/usr/bin/env bash
# =====================================================
# 备份脚本：MySQL 全量 + MinIO 对象 + 配置文件
# 适合放进 cron，例如：
#   0 2 * * * /opt/contract-mgmt/ops/backup.sh >> /opt/contract-mgmt/logs/backup.log 2>&1
# =====================================================
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib/common.sh"

APP_HOME="${APP_HOME:-/opt/contract-mgmt}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
BACKUP_DIR="$APP_HOME/backup"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app-home) APP_HOME=$2; BACKUP_DIR="$APP_HOME/backup"; shift 2 ;;
    --retention-days) RETENTION_DAYS=$2; shift 2 ;;
    -h|--help)
      cat <<EOF
备份合同回款管理系统数据

参数：
  --app-home <path>           安装目录（默认 /opt/contract-mgmt）
  --retention-days <n>        保留天数（默认 30）
EOF
      exit 0
      ;;
    *) die "未知参数：$1" ;;
  esac
done

[[ -d "$APP_HOME" ]] || die "未找到安装目录：$APP_HOME"
ENV_FILE="$APP_HOME/config/.env"
[[ -f "$ENV_FILE" ]] || die "未找到 .env：$ENV_FILE"

set -a
# shellcheck disable=SC1091
. "$ENV_FILE"
set +a

TS=$(date +%Y%m%d-%H%M%S)
DATE=$(date +%F)
mkdir -p "$BACKUP_DIR"/{mysql,minio,config}

log_step "[$TS] 开始备份"

# ---- MySQL ----
log_info "导出 MySQL ..."
MYSQL_FILE="$BACKUP_DIR/mysql/contract_mgmt-$DATE.sql.gz"
docker exec cm-mysql sh -c \
  "exec mysqldump --single-transaction --quick --routines --triggers --hex-blob \
     --set-gtid-purged=OFF \
     -uroot -p\"$DB_ROOT_PASSWORD\" contract_mgmt" \
  | gzip > "$MYSQL_FILE"
log_ok "MySQL → $MYSQL_FILE ($(du -h "$MYSQL_FILE" | awk '{print $1}'))"

# ---- MinIO ----
log_info "镜像 MinIO 桶 ..."
MINIO_DIR="$BACKUP_DIR/minio/$DATE"
mkdir -p "$MINIO_DIR"
docker run --rm --network host \
  -e MC_HOST_local="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@127.0.0.1:9000" \
  -v "$MINIO_DIR":/backup \
  minio/mc:latest mirror --overwrite --quiet local/"$MINIO_BUCKET" /backup >/dev/null
MINIO_TAR="$BACKUP_DIR/minio/contract-mgmt-bucket-$DATE.tar.gz"
tar -czf "$MINIO_TAR" -C "$BACKUP_DIR/minio" "$DATE"
rm -rf "$MINIO_DIR"
log_ok "MinIO → $MINIO_TAR ($(du -h "$MINIO_TAR" | awk '{print $1}'))"

# ---- 配置文件 ----
CONFIG_TAR="$BACKUP_DIR/config/config-$DATE.tar.gz"
tar -czf "$CONFIG_TAR" -C "$APP_HOME" config docker-compose.prod.yml 2>/dev/null
log_ok "配置 → $CONFIG_TAR"

# ---- 清理过期 ----
log_info "清理 $RETENTION_DAYS 天前的备份 ..."
find "$BACKUP_DIR/mysql"  -name '*.sql.gz'    -mtime +"$RETENTION_DAYS" -delete
find "$BACKUP_DIR/minio"  -name '*.tar.gz'    -mtime +"$RETENTION_DAYS" -delete
find "$BACKUP_DIR/config" -name '*.tar.gz'    -mtime +"$RETENTION_DAYS" -delete

log_step "[$TS] 备份完成 ✓"
ls -lh "$BACKUP_DIR/mysql"/*.sql.gz 2>/dev/null | tail -5 || true
