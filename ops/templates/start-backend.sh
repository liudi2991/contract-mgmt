#!/usr/bin/env bash
# 后端启动入口（由 systemd 调用）
# APP_HOME 在 install 阶段已被硬编码替换；如需修改 JVM 参数，直接编辑本文件
set -eo pipefail

cd "${APP_HOME}/app"

# 默认 JVM 参数；如需调整堆内存等，编辑下方 DEFAULT_JAVA_OPTS
DEFAULT_JAVA_OPTS="-server -Xms1g -Xmx2g \
  -XX:+UseG1GC -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=${APP_HOME}/logs/heapdump.hprof \
  -Dfile.encoding=UTF-8 \
  -Duser.timezone=Asia/Shanghai"

if [ -n "$JAVA_OPTS" ]; then
  OPTS="$JAVA_OPTS"
else
  OPTS="$DEFAULT_JAVA_OPTS"
fi

# shellcheck disable=SC2086
exec java $OPTS \
  -Dspring.profiles.active=prod \
  -Dspring.config.additional-location=file:${APP_HOME}/config/ \
  -jar contract-mgmt-backend.jar
