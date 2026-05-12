#!/usr/bin/env bash
# =====================================================
# 合同回款管理系统 · 单机部署一键安装脚本
# 适配：Ubuntu 20.04+ / Debian 11+ / CentOS 7+ / Rocky 8+
#
# 使用：
#   sudo bash ops/install.sh \
#     --jar      ./contract-mgmt-backend.jar \
#     --dist     ./frontend-dist \
#     --domain   contract.example.com \
#     [--ssl-cert /path/fullchain.pem --ssl-key /path/privkey.pem] \
#     [--app-home /opt/contract-mgmt] \
#     [--yes]
#
# 也可不带参数交互式运行
# =====================================================

set -euo pipefail

SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
# shellcheck disable=SC1091
. "$SCRIPT_DIR/lib/common.sh"

# ---------- 默认值 ----------
JAR_PATH=""
DIST_PATH=""
APP_DOMAIN=""
SSL_CERT=""
SSL_KEY=""
APP_HOME="/opt/contract-mgmt"
APP_USER="contract"
MINIO_BUCKET="contract-mgmt"
ASSUME_YES=0
SKIP_DOCKER_INSTALL=0
SKIP_NGINX=0

usage() {
  cat <<EOF
合同回款管理系统部署脚本

参数：
  --jar       <path>   后端 jar 文件路径（必填）
  --dist      <path>   前端 dist 目录（必填）
  --domain    <name>   服务域名或公网 IP（必填）
  --ssl-cert  <path>   HTTPS 证书 fullchain（可选；不提供则只配 HTTP）
  --ssl-key   <path>   HTTPS 证书 私钥（可选；与 --ssl-cert 配对）
  --app-home  <path>   安装根目录（默认 /opt/contract-mgmt）
  --app-user  <user>   运行系统用户（默认 contract）
  --bucket    <name>   MinIO bucket（默认 contract-mgmt）
  --skip-docker-install  跳过 Docker 安装步骤（如已自行安装）
  --skip-nginx           跳过 Nginx 配置（如有自定义网关）
  --yes / -y             非交互模式，默认所有确认 = yes
  -h / --help            打印帮助

环境变量：
  ASSUME_YES=1           等价于 --yes
EOF
}

# ---------- 参数解析 ----------
while [[ $# -gt 0 ]]; do
  case "$1" in
    --jar)              JAR_PATH=$2; shift 2 ;;
    --dist)             DIST_PATH=$2; shift 2 ;;
    --domain)           APP_DOMAIN=$2; shift 2 ;;
    --ssl-cert)         SSL_CERT=$2; shift 2 ;;
    --ssl-key)          SSL_KEY=$2; shift 2 ;;
    --app-home)         APP_HOME=$2; shift 2 ;;
    --app-user)         APP_USER=$2; shift 2 ;;
    --bucket)           MINIO_BUCKET=$2; shift 2 ;;
    --skip-docker-install) SKIP_DOCKER_INSTALL=1; shift ;;
    --skip-nginx)       SKIP_NGINX=1; shift ;;
    -y|--yes)           ASSUME_YES=1; shift ;;
    -h|--help)          usage; exit 0 ;;
    *) die "未知参数：$1（-h 查看帮助）" ;;
  esac
done
export ASSUME_YES

require_root

# ---------- 交互式补全 ----------
if [[ -z "$JAR_PATH" ]]; then
  default_jar="$SCRIPT_DIR/../contract-mgmt-backend.jar"
  if [[ -f "$default_jar" ]]; then
    JAR_PATH=$default_jar
  else
    read -r -p "后端 jar 路径: " JAR_PATH
  fi
fi
if [[ -z "$DIST_PATH" ]]; then
  default_dist="$SCRIPT_DIR/../frontend-dist"
  if [[ -d "$default_dist" ]]; then
    DIST_PATH=$default_dist
  else
    read -r -p "前端 dist 目录: " DIST_PATH
  fi
fi
if [[ -z "$APP_DOMAIN" ]]; then
  ip_guess=$(hostname -I 2>/dev/null | awk '{print $1}')
  read -r -p "服务域名或 IP [${ip_guess:-required}]: " APP_DOMAIN
  APP_DOMAIN=${APP_DOMAIN:-$ip_guess}
fi

[[ -n "$JAR_PATH"  ]] || die "必须提供后端 jar (--jar)"
[[ -n "$DIST_PATH" ]] || die "必须提供前端 dist 目录 (--dist)"
[[ -n "$APP_DOMAIN" ]] || die "必须提供服务域名或 IP (--domain)"
[[ -f "$JAR_PATH"  ]] || die "找不到 jar：$JAR_PATH"
[[ -d "$DIST_PATH" ]] || die "找不到前端目录：$DIST_PATH"
[[ -f "$DIST_PATH/index.html" ]] || die "前端目录中缺少 index.html"

if [[ -n "$SSL_CERT" || -n "$SSL_KEY" ]]; then
  [[ -f "$SSL_CERT" ]] || die "找不到 SSL 证书：$SSL_CERT"
  [[ -f "$SSL_KEY"  ]] || die "找不到 SSL 私钥：$SSL_KEY"
fi

# 公网访问 URL
if [[ -n "$SSL_CERT" ]]; then
  APP_PUBLIC_URL="https://$APP_DOMAIN"
else
  APP_PUBLIC_URL="http://$APP_DOMAIN"
fi

# ---------- 总览确认 ----------
log_step "部署参数总览"
cat <<EOF
  安装根目录:   $APP_HOME
  运行用户:     $APP_USER
  服务域名/IP:  $APP_DOMAIN
  公网 URL:     $APP_PUBLIC_URL
  HTTPS:        $([[ -n "$SSL_CERT" ]] && echo "启用 ($SSL_CERT)" || echo "未启用")
  后端 jar:     $JAR_PATH
  前端 dist:    $DIST_PATH
  MinIO bucket: $MINIO_BUCKET
  跳过 Docker:  $([[ $SKIP_DOCKER_INSTALL == 1 ]] && echo yes || echo no)
  跳过 Nginx:   $([[ $SKIP_NGINX == 1 ]] && echo yes || echo no)
  操作系统:     $(detect_os) / $(uname -m)
EOF
echo
confirm "确认开始部署？" || die "已取消"

# ---------- 1. 系统依赖 ----------
log_step "1/9  安装系统依赖"
case "$(detect_pm)" in
  apt) pm_install curl wget tar gzip openssl ca-certificates gettext-base lsof jq ;;
  yum|dnf) pm_install curl wget tar gzip openssl ca-certificates gettext lsof jq ;;
  *) log_warn "未知包管理器，跳过依赖检查（请确保 curl/openssl/envsubst 已安装）" ;;
esac
log_ok "系统依赖就绪"

# ---------- 2. Java 17 ----------
log_step "2/9  检查 Java 17"
if ! command -v java >/dev/null 2>&1; then
  log_info "未检测到 Java，安装 OpenJDK 17 ..."
  case "$(detect_pm)" in
    apt) pm_install openjdk-17-jre-headless ;;
    yum|dnf) pm_install java-17-openjdk-headless || pm_install java-17-openjdk ;;
    *) die "请手动安装 OpenJDK 17" ;;
  esac
fi
java_ver=$(java -version 2>&1 | awk -F\" '/version/{print $2}')
log_ok "Java 已就绪：$java_ver"

# ---------- 3. Docker & Compose ----------
log_step "3/9  检查 Docker"
if [[ $SKIP_DOCKER_INSTALL -eq 0 ]]; then
  if ! command -v docker >/dev/null 2>&1; then
    log_info "未检测到 Docker，使用官方脚本安装 ..."
    curl -fsSL https://get.docker.com | sh
    systemctl enable --now docker
  fi
  if ! docker compose version >/dev/null 2>&1; then
    log_info "Docker Compose v2 未安装，尝试通过插件方式安装 ..."
    case "$(detect_pm)" in
      apt) pm_install docker-compose-plugin || true ;;
      yum|dnf) pm_install docker-compose-plugin || true ;;
    esac
    docker compose version >/dev/null 2>&1 || die "Docker Compose v2 未就绪，请手动安装：https://docs.docker.com/compose/install/"
  fi
fi
require_cmd docker
docker compose version >/dev/null 2>&1 || die "未检测到 docker compose v2"
log_ok "Docker $(docker --version | awk '{print $3}' | tr -d ,) / Compose $(docker compose version --short)"

# ---------- 4. 运行用户与目录 ----------
log_step "4/9  创建运行用户与目录"
if ! id "$APP_USER" >/dev/null 2>&1; then
  useradd -m -s /bin/bash "$APP_USER"
  log_ok "已创建系统用户 $APP_USER"
fi
usermod -aG docker "$APP_USER" || true

mkdir -p \
  "$APP_HOME"/{app,config,logs,backup,data} \
  "$APP_HOME"/data/{mysql,redis,minio}
chown -R "$APP_USER:$APP_USER" "$APP_HOME"
chmod 750 "$APP_HOME"
log_ok "目录结构已就绪：$APP_HOME"

# ---------- 5. 渲染 .env / docker-compose / application-prod ----------
log_step "5/9  生成配置文件"
ENV_FILE="$APP_HOME/config/.env"
COMPOSE_FILE="$APP_HOME/docker-compose.prod.yml"
APP_CONFIG="$APP_HOME/config/application-prod.yml"
START_SCRIPT="$APP_HOME/app/start-backend.sh"
SERVICE_FILE="/etc/systemd/system/contract-mgmt-backend.service"

if [[ -f "$ENV_FILE" ]]; then
  log_warn ".env 已存在，复用其中的密码（如需重置请先 uninstall 或手工删除）"
  set -a; . "$ENV_FILE"; set +a
else
  export DB_ROOT_PASSWORD=$(rand_pass 32)
  export DB_PASSWORD=$DB_ROOT_PASSWORD
  export REDIS_PASSWORD=$(rand_pass 32)
  export MINIO_ACCESS_KEY=$(rand_pass 20)
  export MINIO_SECRET_KEY=$(rand_pass 40)
  export JWT_SECRET=$(rand_hex 96)
  log_ok "已生成随机密码（保存在 $ENV_FILE）"
fi
export MINIO_BUCKET APP_HOME APP_DOMAIN APP_PUBLIC_URL
export GEN_TIME=$(date '+%Y-%m-%d %H:%M:%S %z')

ENV_VARS_FOR_ENV='${DB_ROOT_PASSWORD} ${DB_PASSWORD} ${REDIS_PASSWORD} ${MINIO_ACCESS_KEY} ${MINIO_SECRET_KEY} ${MINIO_BUCKET} ${JWT_SECRET} ${APP_HOME} ${APP_DOMAIN} ${APP_PUBLIC_URL} ${GEN_TIME}'

# .env：所有变量都需要替换
backup_if_exists "$ENV_FILE"
render_template "$SCRIPT_DIR/templates/env.template" "$ENV_FILE" "$ENV_VARS_FOR_ENV"
chmod 600 "$ENV_FILE"
chown "$APP_USER:$APP_USER" "$ENV_FILE"

# docker-compose.prod.yml：仅 APP_HOME 在 install 时替换；其它变量交给 --env-file 加载
backup_if_exists "$COMPOSE_FILE"
render_template "$SCRIPT_DIR/templates/docker-compose.prod.yml" "$COMPOSE_FILE" '${APP_HOME} ${APP_PUBLIC_URL}'
chown "$APP_USER:$APP_USER" "$COMPOSE_FILE"

# application-prod.yml：仅 APP_HOME 在 install 时替换；密码占位符交给 Spring Boot 运行时解析
backup_if_exists "$APP_CONFIG"
render_template "$SCRIPT_DIR/templates/application-prod.yml" "$APP_CONFIG" '${APP_HOME}'
chown "$APP_USER:$APP_USER" "$APP_CONFIG"

# start-backend.sh：仅 APP_HOME 在 install 时替换；JAVA_OPTS 保留为 bash 运行时变量
backup_if_exists "$START_SCRIPT"
render_template "$SCRIPT_DIR/templates/start-backend.sh" "$START_SCRIPT" '${APP_HOME}'
chmod 750 "$START_SCRIPT"
chown "$APP_USER:$APP_USER" "$START_SCRIPT"

# systemd unit：替换 APP_HOME / APP_USER
backup_if_exists "$SERVICE_FILE"
APP_USER=$APP_USER APP_HOME=$APP_HOME \
  render_template "$SCRIPT_DIR/templates/contract-mgmt-backend.service" "$SERVICE_FILE" '${APP_HOME} ${APP_USER}'
chmod 644 "$SERVICE_FILE"

log_ok "配置文件已生成"

# ---------- 6. 部署后端 jar ----------
log_step "6/9  部署后端 jar"
cp -f "$JAR_PATH" "$APP_HOME/app/contract-mgmt-backend.jar"
chown "$APP_USER:$APP_USER" "$APP_HOME/app/contract-mgmt-backend.jar"
log_ok "后端 jar 已部署到 $APP_HOME/app/"

# ---------- 7. 拉起基础设施 ----------
log_step "7/9  启动 MySQL / Redis / MinIO"
( cd "$APP_HOME" && docker compose --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d )

log_info "等待 MySQL 健康 ..."
wait_tcp 127.0.0.1 3306 90
# 等待真正可登录
for i in {1..30}; do
  if docker exec cm-mysql mysqladmin ping -h127.0.0.1 -uroot -p"$DB_ROOT_PASSWORD" --silent 2>/dev/null; then
    log_ok "MySQL 已就绪"
    break
  fi
  sleep 2
  if [[ $i -eq 30 ]]; then
    die "MySQL 等待超时"
  fi
done

log_info "等待 Redis 健康 ..."
wait_tcp 127.0.0.1 6379 30
log_ok "Redis 已就绪"

log_info "等待 MinIO 健康 ..."
wait_tcp 127.0.0.1 9000 30
log_ok "MinIO 已就绪"

# 创建 MinIO bucket（后端启动也会做，这里提前做避免首次启动告警）
docker run --rm --network host \
  -e MC_HOST_local="http://${MINIO_ACCESS_KEY}:${MINIO_SECRET_KEY}@127.0.0.1:9000" \
  minio/mc:latest mb --ignore-existing local/"$MINIO_BUCKET" >/dev/null || true
log_ok "MinIO bucket：$MINIO_BUCKET"

# ---------- 8. 启动后端 ----------
log_step "8/9  启动后端服务"
systemctl daemon-reload
systemctl enable contract-mgmt-backend >/dev/null
systemctl restart contract-mgmt-backend

log_info "等待后端健康（最长 180 秒）..."
wait_http "http://127.0.0.1:8080/actuator/health" 180
log_ok "后端已健康：$(curl -s http://127.0.0.1:8080/actuator/health)"

# ---------- 9. 部署前端 + Nginx ----------
log_step "9/9  部署前端与 Nginx"
FRONTEND_ROOT="/var/www/contract-mgmt"
mkdir -p "$FRONTEND_ROOT"
rm -rf "${FRONTEND_ROOT:?}"/* 2>/dev/null || true
cp -r "$DIST_PATH"/* "$FRONTEND_ROOT"/
if id www-data >/dev/null 2>&1; then chown -R www-data:www-data "$FRONTEND_ROOT"
elif id nginx >/dev/null 2>&1; then chown -R nginx:nginx "$FRONTEND_ROOT"
fi
log_ok "前端已部署到 $FRONTEND_ROOT"

if [[ $SKIP_NGINX -eq 0 ]]; then
  if ! command -v nginx >/dev/null 2>&1; then
    log_info "未检测到 Nginx，开始安装 ..."
    pm_install nginx
    systemctl enable --now nginx
  fi

  # 准备渲染变量
  export FRONTEND_ROOT APP_DOMAIN
  if [[ -n "$SSL_CERT" ]]; then
    export LISTEN_DIRECTIVES="    listen 443 ssl http2;\n    listen [::]:443 ssl http2;"
    export SSL_BLOCK="    ssl_certificate     $SSL_CERT;\n    ssl_certificate_key $SSL_KEY;\n    ssl_protocols       TLSv1.2 TLSv1.3;\n    ssl_ciphers         HIGH:!aNULL:!MD5;\n    ssl_session_cache   shared:SSL:10m;\n    add_header Strict-Transport-Security \"max-age=31536000; includeSubDomains\" always;"
    export HTTP_REDIRECT_SERVER="server {\n    listen 80;\n    listen [::]:80;\n    server_name $APP_DOMAIN;\n    return 301 https://\$host\$request_uri;\n}"
  else
    export LISTEN_DIRECTIVES="    listen 80;\n    listen [::]:80;"
    export SSL_BLOCK=""
    export HTTP_REDIRECT_SERVER=""
  fi

  # 找到 Nginx 站点目录
  if [[ -d /etc/nginx/sites-available ]]; then
    NGINX_SITE="/etc/nginx/sites-available/contract-mgmt.conf"
    NGINX_LINK="/etc/nginx/sites-enabled/contract-mgmt.conf"
  else
    NGINX_SITE="/etc/nginx/conf.d/contract-mgmt.conf"
    NGINX_LINK=""
  fi

  backup_if_exists "$NGINX_SITE"
  # 渲染（注意 envsubst 默认会替换 $ 开头变量，需要用 SHELL_FORMAT 限制）
  envsubst '${APP_DOMAIN} ${FRONTEND_ROOT} ${LISTEN_DIRECTIVES} ${SSL_BLOCK} ${HTTP_REDIRECT_SERVER}' \
    < "$SCRIPT_DIR/templates/nginx-contract-mgmt.conf" \
    | sed 's/\\n/\n/g' > "$NGINX_SITE"

  if [[ -n "$NGINX_LINK" ]]; then
    ln -sf "$NGINX_SITE" "$NGINX_LINK"
    # 移除 default 站点避免覆盖
    rm -f /etc/nginx/sites-enabled/default
  fi

  nginx -t
  systemctl reload nginx
  log_ok "Nginx 已配置并 reload"
else
  log_warn "跳过 Nginx 配置（--skip-nginx）"
fi

# ---------- 完成 ----------
log_step "部署完成 ✓"

cat <<EOF

${C_GREEN}${C_BOLD}🎉 合同回款管理系统已部署完成${C_RESET}

  访问地址:        ${C_CYAN}${APP_PUBLIC_URL}${C_RESET}
  默认管理员:      admin / Admin@1234  ${C_YELLOW}(首次登录强制改密)${C_RESET}
  Swagger:         ${APP_PUBLIC_URL}/swagger-ui.html  (仅内网/本机)
  MinIO 控制台:    http://127.0.0.1:9001  (建议 SSH 隧道访问)

${C_BOLD}常用运维命令${C_RESET}：
  systemctl status contract-mgmt-backend
  journalctl -u contract-mgmt-backend -f
  tail -f $APP_HOME/logs/app.log
  cd $APP_HOME && docker compose --env-file config/.env -f docker-compose.prod.yml ps

${C_BOLD}敏感信息${C_RESET}：
  .env 路径:       $ENV_FILE  ${C_YELLOW}(chmod 600，请妥善保管)${C_RESET}
  数据目录:        $APP_HOME/data/{mysql,redis,minio}
  日志目录:        $APP_HOME/logs

${C_BOLD}下一步${C_RESET}：
  1. 用 admin 登录，立即修改默认密码
  2. 配置定时备份：bash $SCRIPT_DIR/backup.sh 加入 cron
  3. 健康检查：    bash $SCRIPT_DIR/health.sh
  4. 升级版本：    sudo bash $SCRIPT_DIR/upgrade.sh --jar new.jar --dist new-dist/

EOF
