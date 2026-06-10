#!/bin/bash
# Launch the packaged desktop client.
# Build it first with:  mvn clean package   (produces target/desktop-app.jar)
# Requires a JRE 21+ on PATH. The jar bundles JavaFX, so no separate JavaFX install is needed.
set -e
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/target/desktop-app.jar"
if [ ! -f "$JAR" ]; then
  echo "desktop-app.jar not found. Build it first:  (cd \"$DIR\" && mvn clean package)"
  exit 1
fi
exec java -jar "$JAR"
