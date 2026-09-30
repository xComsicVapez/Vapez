#!/usr/bin/env bash
# Vapez SMP — Purpur 1.21.8 / Java 21+ / 60G Generational ZGC
set -euo pipefail
cd "$(dirname "$0")"

: "${JAVA_BIN:=java}"
: "${MEMORY:=60G}"
JAR="$(ls -1 purpur-*.jar 2>/dev/null | head -n1 || true)"
if [[ -z "${JAR}" ]]; then
  echo "No purpur jar in $(pwd). Run ../../scripts/bootstrap.sh first."
  exit 1
fi

# Java 21 requires UnlockExperimentalVMOptions for -XX:+ZGenerational on some builds.
# AlwaysPreTouch commits the full 60G at startup so ZGC does not pay page-faults mid-tick.
exec "${JAVA_BIN}" \
  -Xms"${MEMORY}" -Xmx"${MEMORY}" \
  -XX:+UnlockExperimentalVMOptions \
  -XX:+UseZGC \
  -XX:+ZGenerational \
  -XX:+AlwaysPreTouch \
  -XX:+DisableExplicitGC \
  -XX:+ParallelRefProcEnabled \
  -XX:+PerfDisableSharedMem \
  -XX:MaxGCPauseMillis=200 \
  -XX:NmethodSweepActivity=1 \
  -XX:ReservedCodeCacheSize=400M \
  -XX:NonNMethodCodeHeapSize=12M \
  -XX:ProfiledCodeHeapSize=194M \
  -XX:NonProfiledCodeHeapSize=194M \
  -XX:-DontCompileHugeMethods \
  --add-modules=jdk.incubator.vector \
  -Dfile.encoding=UTF-8 \
  -Djava.security.egd=file:/dev/urandom \
  -Dusing.aikars.flags=https://aikar.co/2018/07/02/tuning-the-jvm-g1gc-garbage-collector-flags-for-minecraft/ \
  -Dpaper.playerconnection.kickOnIllegalBehavior=true \
  -jar "${JAR}" nogui
