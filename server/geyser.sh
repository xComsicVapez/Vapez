#!/bin/bash
# Runs Geyser Standalone from the current folder and restarts it if it stops,
# unless `mc <server> stop` requested a stop.

rm -f .stop-requested
while true; do
  java -Xms512M -Xmx2G -jar Geyser-Standalone.jar --nogui
  if [ -f .stop-requested ]; then
    rm -f .stop-requested
    break
  fi
  echo "Geyser exited. Restarting in 5 seconds..."
  sleep 5
done
