#!/usr/bin/env bash
# =====================================================
# 健康检查：一眼看清系统状态
# 用法：bash ops/health.sh
# =====================================================
set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib/common.sh"

APP_HOME="${APP_HOME:-/opt/contract-mgmt}"

check() {
  local name=$1 cmd=$2
  if eval "$cmd" >/dev/null 2>&1; then
    printf "  %s[OK]%s    %s\n" "$C_GREEN" "$C_RESET" "$name"
  else
    printf "  %s[FAIL]%s  %s\n" "$C_RED" "$C_RESET" "$name"
    return 1
  fi
}

fails=0
inc() { fails=$((fails + 1)); }

log_step "服务进程"
check "Docker daemon"             "docker info"                                              || inc
check "systemd: backend"          "systemctl is-active --quiet contract-mgmt-backend"        || inc
check "systemd: nginx"            "systemctl is-active --quiet nginx"                        || inc

log_step "容器"
check "container: cm-mysql"       "docker ps --filter name=cm-mysql --filter status=running --format '{{.Names}}' | grep -q cm-mysql"  || inc
check "container: cm-redis"       "docker ps --filter name=cm-redis --filter status=running --format '{{.Names}}' | grep -q cm-redis"  || inc
check "container: cm-minio"       "docker ps --filter name=cm-minio --filter status=running --format '{{.Names}}' | grep -q cm-minio"  || inc

log_step "端口监听"
check "backend  127.0.0.1:8080"   "ss -ltn 'sport = :8080' | grep -q LISTEN"                || inc
check "mysql    127.0.0.1:3306"   "ss -ltn 'sport = :3306' | grep -q LISTEN"                || inc
check "redis    127.0.0.1:6379"   "ss -ltn 'sport = :6379' | grep -q LISTEN"                || inc
check "minio    127.0.0.1:9000"   "ss -ltn 'sport = :9000' | grep -q LISTEN"                || inc
check "nginx    :80 or :443"      "ss -ltn | grep -qE ':80|:443'"                           || inc

log_step "应用健康"
hc=$(curl -sS -m 3 http://127.0.0.1:8080/actuator/health 2>/dev/null || true)
if echo "$hc" | grep -q '"status":"UP"'; then
  printf "  %s[OK]%s    /actuator/health = %s\n" "$C_GREEN" "$C_RESET" "$hc"
else
  printf "  %s[FAIL]%s  /actuator/health = %s\n" "$C_RED" "$C_RESET" "$hc"
  inc
fi

log_step "磁盘空间"
df -h "$APP_HOME" "$APP_HOME/data" / 2>/dev/null | awk 'NR==1 || /\//'

log_step "最近日志（最后 5 行）"
if [[ -f "$APP_HOME/logs/app.log" ]]; then
  tail -n 5 "$APP_HOME/logs/app.log"
else
  log_warn "未找到 app.log"
fi

echo
if [[ $fails -eq 0 ]]; then
  log_ok "全部检查通过 ✓"
  exit 0
else
  log_err "$fails 项异常，请优先处理"
  exit 1
fi
