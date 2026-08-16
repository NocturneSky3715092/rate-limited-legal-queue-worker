#!/usr/bin/env sh
set -eu
rm -rf build
mkdir -p build/classes build/test-classes
find src/main/java -name '*.java' -print | sort | xargs javac -d build/classes
find src/test/java -name '*.java' -print | sort | xargs javac -cp build/classes -d build/test-classes
java -cp build/classes:build/test-classes dev.infrai.legalworker.domain.LegalJobPriorityTest
if [ "${1:-}" = "worker" ]; then
  java -cp build/classes dev.infrai.legalworker.LegalWorkerExample
fi
