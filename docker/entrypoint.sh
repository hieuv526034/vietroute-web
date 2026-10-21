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
sed -i "s/port=\"8080\"/port=\"${PORT:-10000}\"/" "$CATALINA_HOME/conf/server.xml"
exec "$CATALINA_HOME/bin/catalina.sh" run
