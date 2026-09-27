#!/bin/sh
# Headless HO: serves only the MCP tools, no window. See README.md, "Headless MCP server".
#   ./ho-headless.sh authorize        once: Hattrick login (PIN) + first team download
#   ./ho-headless.sh [--port 8765]    serve MCP on 127.0.0.1
# HO keeps db/, logs/ and users.json in the working directory, so run it from its own folder.
cd "$(dirname "$0")" || exit 1
exec java -Djava.awt.headless=true $JAVA_OPTS -cp HO.jar core.HeadlessMain "$@"
