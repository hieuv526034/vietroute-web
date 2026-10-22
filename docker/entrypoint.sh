#!/bin/sh
set -eu

# Render supplies PORT. Tomcat must listen on that port on all interfaces.
case "${PORT:-10000}" in
    ''|*[!0-9]*) echo 'PORT must be a number' >&2; exit 1 ;;
esac
if [ "${PORT:-10000}" -lt 1 ] || [ "${PORT:-10000}" -gt 65535 ]; then
    echo 'PORT must be between 1 and 65535' >&2
    exit 1
fi
# Mỗi lần start/restart dựng lại từ template gốc để không chèn lặp thuộc tính vào file đã sửa.
cp "$CATALINA_HOME/conf/server.xml.template" "$CATALINA_HOME/conf/server.xml"
sed -i "s/port=\"8080\"/port=\"${PORT:-10000}\"/" "$CATALINA_HOME/conf/server.xml"
# Giới hạn thread/backlog/header/body ở connector trước khi request vào Java; không dùng default 200 threads cho RAM 512 MB.
# JSON còn được giới hạn stream thật trong TrafficGuardFilter (maxPostSize Tomcat không giới hạn JSON).
sed -i 's/connectionTimeout="20000"/connectionTimeout="10000"/; s/maxParameterCount="1000"/maxParameterCount="100"/; s/protocol="HTTP\/1.1"/protocol="HTTP\/1.1" maxThreads="64" minSpareThreads="4" maxConnections="256" acceptCount="64" keepAliveTimeout="5000" maxKeepAliveRequests="100" maxHttpRequestHeaderSize="16384" maxPostSize="65536" maxSwallowSize="65536"/' "$CATALINA_HOME/conf/server.xml"
exec "$CATALINA_HOME/bin/catalina.sh" run
