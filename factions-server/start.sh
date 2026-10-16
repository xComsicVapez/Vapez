#!/usr/bin/env bash
# Starts the server in a detached tmux session named "factions".
# MEMORY=64G ./start.sh     FOREGROUND=1 ./start.sh (no tmux, e.g. for systemd)
set -euo pipefail
cd "$(dirname "$0")"
SESSION="${SESSION:-factions}"
MEMORY="${MEMORY:-100G}"

if [ "${FOREGROUND:-}" != "1" ] && tmux has-session -t "=$SESSION" 2>/dev/null; then
  if [ "$(tmux list-windows -t "=$SESSION" -F '#W')" != proxy ]; then
    echo "Already running. Attach with ./console.sh"
    exit 0
  fi
  # Only a leftover proxy is running (the server was stopped from its console).
  tmux kill-session -t "=$SESSION"
fi

# AlwaysPreTouch claims the whole heap at boot, so the machine/VM needs the heap
# plus a few GB for the OS and JVM overhead, or Linux will kill the server.
heap_gb=${MEMORY%[GgMm]}
case "$MEMORY" in *[Mm]) heap_gb=$(( heap_gb / 1024 )) ;; esac
total_gb=$(awk '/MemTotal/{print int($2/1048576)}' /proc/meminfo)
if [ "$total_gb" -lt $(( heap_gb + 4 )) ]; then
  echo "MEMORY=$MEMORY but this machine only has ${total_gb} GB RAM (need ~$(( heap_gb + 4 )) GB)." >&2
  echo "Give the VM more RAM in Proxmox, or run e.g.  MEMORY=$(( total_gb > 8 ? total_gb - 6 : 2 ))G ./start.sh" >&2
  exit 1
fi

# The pack is tested on Java 21; spark's profiler segfaults on Java 25, so prefer 21 when installed.
JAVA="${JAVA:-$(ls /usr/lib/jvm/java-21-openjdk*/bin/java 2>/dev/null | head -1 || true)}"
JAVA="${JAVA:-java}"

# No core dumps: with a 100 GB heap a single crash would write ~100 GB to disk.
JAVA_FLAGS=(
  "-Xms$MEMORY" "-Xmx$MEMORY"
  -XX:+UseZGC -XX:+ZGenerational -XX:+AlwaysPreTouch -XX:+DisableExplicitGC
  -XX:+PerfDisableSharedMem -XX:-CreateCoredumpOnCrash -Dfile.encoding=UTF-8
)
CMD=("$JAVA" "${JAVA_FLAGS[@]}" -jar fabric-server-launch.jar nogui)

if [ "${FOREGROUND:-}" = "1" ]; then
  exec "${CMD[@]}"
fi

mkdir -p logs
rm -f .stop
tmux new-session -d -s "$SESSION" -n server -c "$PWD" "while true; do ${CMD[*]} 2>&1 | tee -a logs/console.log; [ -f .stop ] && rm -f .stop && break; echo 'Crashed/stopped; restarting in 10s (./stop.sh to prevent)'; sleep 10; done"
echo "Started in tmux session '$SESSION' with $MEMORY RAM ($JAVA). ./console.sh to watch, ./stop.sh to stop."

# Any-version edition: the Velocity proxy (with ViaVersion) takes the public port and
# forwards to this server on localhost. Velocity 4 needs Java 25.
if [ -f proxy/velocity.jar ]; then
  PROXY_JAVA="${PROXY_JAVA:-$(ls /usr/lib/jvm/{java-25-openjdk*,jdk-25*}/bin/java 2>/dev/null | head -1 || true)}"
  PROXY_JAVA="${PROXY_JAVA:-java}"
  PROXY_CMD="$PROXY_JAVA -Xms1G -Xmx2G -XX:+UseG1GC -XX:-CreateCoredumpOnCrash -jar velocity.jar"
  tmux new-window -d -t "=$SESSION:" -n proxy -c "$PWD/proxy" "while true; do $PROXY_CMD; [ -f ../.stop ] && break; echo 'Proxy stopped; restarting in 5s'; sleep 5; done"
  echo "Started the version proxy (Velocity + ViaVersion) in window 'proxy' ($PROXY_JAVA)."
fi
