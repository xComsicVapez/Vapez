#!/bin/bash
# Runs the server and restarts it whenever it stops, unless a stop was requested
# with `mc <server> stop`. Stops retrying after 3 crashes in a row within 2 minutes
# of starting, so a broken mod cannot cause an endless restart loop.

[ -f ./server.env ] && source ./server.env

MIN_RAM="${MIN_RAM:-16G}"
MAX_RAM="${MAX_RAM:-100G}"
SERVER_JAR="${SERVER_JAR:-$(ls fabric-server-*.jar | head -1)}"

JVM_FLAGS=(
  -Xms"$MIN_RAM" -Xmx"$MAX_RAM"
  -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200
  -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC
  -XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M
  -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4
  -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90
  -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32
  -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1
)

rm -f .stop-requested
quick_crashes=0
while true; do
  started=$(date +%s)
  java "${JVM_FLAGS[@]}" -jar "$SERVER_JAR" nogui
  code=$?

  if [ -f .stop-requested ]; then
    rm -f .stop-requested
    echo "Server stopped."
    break
  fi

  if [ $(( $(date +%s) - started )) -lt 120 ]; then
    quick_crashes=$((quick_crashes + 1))
  else
    quick_crashes=0
  fi
  if [ "$quick_crashes" -ge 3 ]; then
    echo "Server crashed 3 times right after starting (exit code $code). Not restarting."
    echo "Check logs/latest.log and crash-reports/, remove the broken mod, then run: mc $(basename "$PWD") start"
    break
  fi

  echo "Server exited (code $code). Restarting in 5 seconds... (press Ctrl+C to cancel)"
  sleep 5
done
