#!/usr/bin/env bash
# =====================================================
# 升级脚本：只替换后端 jar / 前端 dist，不动数据库与基础设施
#
# 使用：
#   sudo bash ops/upgrade.sh \
#     --jar  ./contract-mgmt-backend.jar \
#     --dist ./frontend-dist \
#     [--app-home /opt/contract-mgmt]
# =====================================================
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib/common.sh"

JAR_PATH=""
DIST_PATH=""
APP_HOME="/opt/contract-mgmt"
APP_USER="contract"

usage() {
  cat <<EOF
升级合同回款管理系统

参数：
  --jar       <path>   后端 jar 路径（任选其一或两者都传）
  --dist      <path>   前端 dist 目录
  --app-home  <path>   安装根目录（默认 /opt/contract-mgmt）
  --app-user  <user>   运行用户（默认 contract）
  -y / --yes           非交互
  -h / --help          帮助
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar)      JAR_PATH=$2; shift 2 ;;
    --dist)     DIST_PATH=$2; shift 2 ;;
    --app-home) APP_HOME=$2; shift 2 ;;
    --app-user) APP_USER=$2; shift 2 ;;
    -y|--yes)   ASSUME_YES=1; shift ;;
    -h|--help)  usage; exit 0 ;;
    *) die "未知参数：$1" ;;
  esac
done
export ASSUME_YES=${ASSUME_YES:-0}

require_root
[[ -d "$APP_HOME" ]] || die "未找到安装目录：$APP_HOME"

if [[ -z "$JAR_PATH" && -z "$DIST_PATH" ]]; then
  die "至少需要 --jar 或 --dist 之一"
fi
[[ -z "$JAR_PATH"  || -f "$JAR_PATH"  ]] || die "jar 不存在：$JAR_PATH"
[[ -z "$DIST_PATH" || -d "$DIST_PATH" ]] || die "dist 目录不存在：$DIST_PATH"

TS=$(date +%Y%m%d-%H%M%S)
log_step "升级前快照备份"
mkdir -p "$APP_HOME/backup/upgrade"
if [[ -f "$APP_HOME/app/contract-mgmt-backend.jar" ]]; then
  cp -a "$APP_HOME/app/contract-mgmt-backend.jar" "$APP_HOME/backup/upgrade/jar-$TS.bak"
  log_ok "旧 jar 已备份到 $APP_HOME/backup/upgrade/jar-$TS.bak"
fi
if [[ -d /var/www/contract-mgmt ]]; then
  tar -czf "$APP_HOME/backup/upgrade/dist-$TS.tar.gz" -C /var/www contract-mgmt
  log_ok "旧前端已备份到 $APP_HOME/backup/upgrade/dist-$TS.tar.gz"
fi

confirm "继续升级？" || die "已取消"

# 升级后端
if [[ -n "$JAR_PATH" ]]; then
  log_step "替换后端 jar"
  cp -f "$JAR_PATH" "$APP_HOME/app/contract-mgmt-backend.jar"
  chown "$APP_USER:$APP_USER" "$APP_HOME/app/contract-mgmt-backend.jar"
  systemctl restart contract-mgmt-backend
  log_info "等待后端健康 ..."
  wait_http "http://127.0.0.1:8080/actuator/health" 180
  log_ok "后端已升级并就绪"
fi

# 升级前端
if [[ -n "$DIST_PATH" ]]; then
  log_step "替换前端 dist"
  rm -rf /var/www/contract-mgmt
  mkdir -p /var/www/contract-mgmt
  cp -r "$DIST_PATH"/* /var/www/contract-mgmt/
  if id www-data >/dev/null 2>&1; then chown -R www-data:www-data /var/www/contract-mgmt
  elif id nginx >/dev/null 2>&1; then chown -R nginx:nginx /var/www/contract-mgmt
  fi
  systemctl reload nginx 2>/dev/null || true
  log_ok "前端已升级"
fi

log_step "升级完成 ✓"
echo "回滚（如需）："
echo "  sudo bash $SCRIPT_DIR/upgrade.sh --jar $APP_HOME/backup/upgrade/jar-$TS.bak"
echo "  或手动恢复 dist：tar -xzf $APP_HOME/backup/upgrade/dist-$TS.tar.gz -C /var/www/"
