#!/usr/bin/env bash
# 公共函数库：日志输出、系统检测、随机串生成、幂等校验
# 由 ops/ 下的脚本 source 使用，不要直接执行

set -euo pipefail

# 颜色
if [[ -t 1 ]]; then
  C_RED=$'\033[0;31m'
  C_GREEN=$'\033[0;32m'
  C_YELLOW=$'\033[0;33m'
  C_BLUE=$'\033[0;34m'
  C_CYAN=$'\033[0;36m'
  C_BOLD=$'\033[1m'
  C_RESET=$'\033[0m'
else
  C_RED= C_GREEN= C_YELLOW= C_BLUE= C_CYAN= C_BOLD= C_RESET=
fi

log_info()  { printf "%s[INFO ]%s %s\n"  "$C_BLUE"  "$C_RESET" "$*"; }
log_ok()    { printf "%s[ OK  ]%s %s\n"  "$C_GREEN" "$C_RESET" "$*"; }
log_warn()  { printf "%s[WARN ]%s %s\n"  "$C_YELLOW" "$C_RESET" "$*" >&2; }
log_err()   { printf "%s[ERROR]%s %s\n"  "$C_RED"   "$C_RESET" "$*" >&2; }
log_step()  { printf "\n%s==> %s%s\n"    "$C_BOLD"  "$*" "$C_RESET"; }

die() { log_err "$*"; exit 1; }

require_root() {
  if [[ $EUID -ne 0 ]]; then
    die "请使用 root 用户或 sudo 执行：sudo bash $0 ..."
  fi
}

require_cmd() {
  local cmd=$1
  command -v "$cmd" >/dev/null 2>&1 || die "缺少命令：$cmd"
}

# 检测发行版：echo "ubuntu" / "debian" / "centos" / "rocky" / "rhel" / "unknown"
detect_os() {
  if [[ -r /etc/os-release ]]; then
    # shellcheck disable=SC1091
    . /etc/os-release
    echo "${ID:-unknown}"
  else
    echo "unknown"
  fi
}

# 包管理器：apt / yum / dnf
detect_pm() {
  if command -v apt-get >/dev/null 2>&1; then echo apt
  elif command -v dnf >/dev/null 2>&1; then echo dnf
  elif command -v yum >/dev/null 2>&1; then echo yum
  else echo unknown
  fi
}

pm_install() {
  local pm
  pm=$(detect_pm)
  case "$pm" in
    apt) DEBIAN_FRONTEND=noninteractive apt-get update -y && DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends "$@" ;;
    dnf) dnf install -y "$@" ;;
    yum) yum install -y "$@" ;;
    *)   die "未识别的包管理器，请手动安装：$*" ;;
  esac
}

# 32 位 base64 随机串（密码用）
rand_pass() {
  local n=${1:-32}
  openssl rand -base64 48 | tr -d '\n=+/' | cut -c1-"$n"
}

# 严格 hex（JWT secret 用）
rand_hex() {
  local n=${1:-64}
  openssl rand -hex 64 | cut -c1-"$n"
}

# 等待 TCP 端口可达
wait_tcp() {
  local host=$1 port=$2 timeout=${3:-60}
  local i=0
  while ! (echo > /dev/tcp/"$host"/"$port") 2>/dev/null; do
    ((i++))
    if (( i >= timeout )); then
      die "等待 ${host}:${port} 可达超时 (${timeout}s)"
    fi
    sleep 1
  done
}

# 等待 HTTP 端点返回 2xx/3xx
wait_http() {
  local url=$1 timeout=${2:-120}
  local i=0
  while ! curl -sS -o /dev/null -w '%{http_code}' --max-time 3 "$url" 2>/dev/null \
    | grep -qE '^(2|3)[0-9]{2}$'; do
    ((i++))
    if (( i >= timeout )); then
      die "等待 ${url} 健康超时 (${timeout}s)"
    fi
    sleep 1
  done
}

# 渲染模板：替换指定的 ${VAR} 为环境变量值
#   render_template <src> <dst> '${VAR1} ${VAR2} ...'
# 不传变量列表则替换所有已定义的环境变量（慎用）
render_template() {
  local src=$1 dst=$2 vars=${3:-}
  [[ -r "$src" ]] || die "模板不存在：$src"
  if ! command -v envsubst >/dev/null 2>&1; then
    pm_install gettext-base 2>/dev/null || pm_install gettext
  fi
  if [[ -n "$vars" ]]; then
    envsubst "$vars" < "$src" > "$dst"
  else
    envsubst < "$src" > "$dst"
  fi
}

# 备份现有文件（如果存在）
backup_if_exists() {
  local f=$1
  if [[ -e "$f" ]]; then
    cp -a "$f" "${f}.bak.$(date +%Y%m%d-%H%M%S)"
    log_info "已备份原文件 $f"
  fi
}

# 确认提示（非交互模式可跳过）
confirm() {
  local prompt=$1
  if [[ "${ASSUME_YES:-0}" == "1" ]]; then return 0; fi
  read -r -p "$prompt [y/N] " ans
  [[ "$ans" =~ ^[Yy]$ ]]
}
