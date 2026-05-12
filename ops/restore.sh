#!/usr/bin/env bash
# =====================================================
# 恢复脚本：从备份文件恢复 MySQL + MinIO
# ⚠️  会**覆盖**当前数据，使用前请务必确认
#
# 用法：
#   sudo bash ops/restore.sh \
#     --mysql /opt/contract-mgmt/backup/mysql/contract_mgmt-2026-05-12.sql.gz \
#     --minio /opt/contract-mgmt/backup/minio/contract-mgmt-bucket-2026-05-12.tar.gz \
#     [--yes]
# =====================================================
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib/common.sh"

APP_HOME="/opt/contract-mgmt"
MYSQL_FILE=""
MINIO_FILE=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --mysql)    MYSQL_FILE=$2; shift 2 ;;
    --minio)    MINIO_FILE=$2; shift 2 ;;
    --app-home) APP_HOME=$2; shift 2 ;;
    -y|--yes)   ASSUME_YES=1; shift ;;
    -h|--help)
      cat <<EOF
从备份恢复合同回款管理系统数据

参数：
  --mysql  <file>   MySQL 备份（.sql.gz）
  --minio  <file>   MinIO 备份（.tar.gz）
  --app-home <path> 安装目录（默认 /opt/contract-mgmt）
  -y, --yes         非交互
EOF
      exit 0
      ;;
    *) die "未知参数：$1" ;;
  esac
done
export ASSUME_YES=${ASSUME_YES:-0}

require_root
[[ -n "$MYSQL_FILE" || -n "$MINIO_FILE" ]] || die "至少指定 --mysql 或 --minio 之一"
[[ -z "$MYSQL_FILE" || -f "$MYSQL_FILE" ]] || die "MySQL 备份不存在：$MYSQL_FILE"
[[ -z "$MINIO_FILE" || -f "$MINIO_FILE" ]] || die "MinIO 备份不存在：$MINIO_FILE"

ENV_FILE="$APP_HOME/config/.env"
[[ -f "$ENV_FILE" ]] || die "找不到 .env：$ENV_FILE"
set -a; . "$ENV_FILE"; set +a

log_warn "==============================================="
log_warn " 即将恢复以下数据，会覆盖当前业务数据！"
log_warn "==============================================="
if [[ -n "$MYSQL_FILE" ]]; then echo "  MySQL：$MYSQL_FILE"; fi
if [[ -n "$MINIO_FILE" ]]; then echo "  MinIO：$MINIO_FILE"; fi
echo
confirm "确认继续？" || die "已取消"

# 停后端，避免恢复期间被写入
log_step "停止后端服务"
systemctl stop contract-mgmt-backend || true

# ---- MySQL ----
if [[ -n "$MYSQL_FILE" ]]; then
  log_step "恢复 MySQL"
  log_info "清空当前库 ..."
  docker exec -i cm-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" -e \
    "DROP DATABASE IF EXISTS contract_mgmt; CREATE DATABASE contract_mgmt CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"
  log_info "导入备份 ..."
  gunzip -c "$MYSQL_FILE" | docker exec -i cm-mysql mysql -uroot -p"$DB_ROOT_PASSWORD" contract_mgmt
  log_ok "MySQL 恢复完成"
fi

# ---- MinIO ----
if [[ -n "$MINIO_FILE" ]]; then
  log_step "恢复 MinIO"
  TMP=$(mktemp -d)
  tar -xzf "$MINIO_FILE" -C "$TMP"
  SRC_DIR=$(find "$TMP" -mindepth 1 -maxdepth 1 -type d | head -1)
  [[ -n "$SRC_DIR" ]] || die "备份包格式异常"

  log_info "清空当前桶 ..."
  docker run --rm --network host \
    -e MC_HOST_local="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@127.0.0.1:9000" \
    minio/mc:latest rm --recursive --force --quiet local/"$MINIO_BUCKET" >/dev/null || true

  log_info "上传备份 ..."
  docker run --rm --network host \
    -e MC_HOST_local="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@127.0.0.1:9000" \
    -v "$SRC_DIR":/backup \
    minio/mc:latest mirror --overwrite --quiet /backup local/"$MINIO_BUCKET" >/dev/null

  rm -rf "$TMP"
  log_ok "MinIO 恢复完成"
fi

# 启动后端
log_step "启动后端服务"
systemctl start contract-mgmt-backend
wait_http "http://127.0.0.1:8080/actuator/health" 120
log_ok "后端已恢复并健康"

log_step "恢复完成 ✓"
