#!/bin/sh
# E2E ラッパー: CLI distribution の lib/ にある全 JAR (本体 + H2 + JDBC plugin) をクラスパスに含めて Java を起動する。
# 標準の bin/migraphe は CLASSPATH を明示列挙するため、後から lib/ に追加した H2 / JDBC plugin を見ない。
set -eu

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_HOME="${REPO_ROOT}/migraphe-cli/build/install/migraphe"

if [ ! -d "${APP_HOME}/lib" ]; then
  echo "CLI distribution not found at ${APP_HOME}. Run: ./gradlew :migraphe-cli:installDist" >&2
  exit 1
fi

exec java -cp "${APP_HOME}/lib/*" io.github.kakusuke.migraphe.cli.Main "$@"
