#!/usr/bin/env bash
# =====================================================
# 卸载脚本：停止服务、删除容器、清理目录
# ⚠️  默认会保留 backup/ 目录；如需彻底清理传 --purge
# =====================================================
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib/common.sh"

APP_HOME="/opt/contract-mgmt"
APP_USER="contract"
PURGE=0
REMOVE_DOCKER=0

while [[ $# -gt 0 ]]; do
  case "$1" in
    --app-home) APP_HOME=$2; shift 2 ;;
    --app-user) APP_USER=$2; shift 2 ;;
    --purge)    PURGE=1; shift ;;
    --remove-docker) REMOVE_DOCKER=1; shift ;;
    -y|--yes)   ASSUME_YES=1; shift ;;
    -h|--help)
      cat <<EOF
卸载合同回款管理系统

参数：
  --app-home <path>   安装目录（默认 /opt/contract-mgmt）
  --app-user <user>   运行用户（默认 contract）
  --purge             删除全部数据（backup/.env 也删）
  --remove-docker     额外卸载 docker（慎用，主机其它服务会受影响）
  -y / --yes          非交互
EOF
      exit 0
      ;;
    *) die "未知参数：$1" ;;
  esac
done
export ASSUME_YES=${ASSUME_YES:-0}

require_root

log_warn "==============================================="
log_warn " 即将卸载合同回款管理系统："
echo  "   安装目录： $APP_HOME"
echo  "   --purge ：$([[ $PURGE -eq 1 ]] && echo "是（会删除全部数据和备份）" || echo "否（保留数据卷和 backup/）")"
echo  "   --remove-docker：$([[ $REMOVE_DOCKER -eq 1 ]] && echo "是" || echo "否")"
log_warn "==============================================="
confirm "确认继续？" || die "已取消"

# 1. 停止 systemd 服务
log_step "停止 systemd 服务"
systemctl stop  contract-mgmt-backend 2>/dev/null || true
systemctl disable contract-mgmt-backend 2>/dev/null || true
rm -f /etc/systemd/system/contract-mgmt-backend.service
systemctl daemon-reload

# 2. 停止 / 删除容器
log_step "停止容器"
ENV_FILE="$APP_HOME/config/.env"
COMPOSE_FILE="$APP_HOME/docker-compose.prod.yml"
if [[ -f "$COMPOSE_FILE" && -f "$ENV_FILE" ]]; then
  ( cd "$APP_HOME" && docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" down ) || true
else
  for c in cm-mysql cm-redis cm-minio; do
    docker rm -f "$c" 2>/dev/null || true
  done
fi

# 3. Nginx 配置
log_step "清理 Nginx 配置"
rm -f /etc/nginx/sites-available/contract-mgmt.conf \
      /etc/nginx/sites-enabled/contract-mgmt.conf \
      /etc/nginx/conf.d/contract-mgmt.conf
systemctl reload nginx 2>/dev/null || true

# 4. 前端目录
log_step "清理前端静态文件"
rm -rf /var/www/contract-mgmt

# 5. 数据 / 应用目录
if [[ $PURGE -eq 1 ]]; then
  log_step "--purge：删除 $APP_HOME 全部数据"
  rm -rf "$APP_HOME"
  log_ok "已删除 $APP_HOME"
else
  log_step "保留数据：清理 app/ config/ 但保留 data/ backup/ logs/"
  rm -rf "$APP_HOME/app" "$APP_HOME/config" "$APP_HOME/docker-compose.prod.yml"
  log_warn "数据仍保留在 $APP_HOME/data 与 $APP_HOME/backup，如需彻底清理请加 --purge"
fi

# 6. 系统用户
if id "$APP_USER" >/dev/null 2>&1 && [[ $PURGE -eq 1 ]]; then
  userdel -r "$APP_USER" 2>/dev/null || true
  log_info "已删除用户 $APP_USER"
fi

# 7. Docker
if [[ $REMOVE_DOCKER -eq 1 ]]; then
  log_step "卸载 Docker"
  case "$(detect_pm)" in
    apt) DEBIAN_FRONTEND=noninteractive apt-get purge -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin 2>/dev/null || true ;;
    yum|dnf) "$(detect_pm)" remove -y docker-ce docker-ce-cli containerd.io 2>/dev/null || true ;;
  esac
fi

log_step "卸载完成 ✓"
