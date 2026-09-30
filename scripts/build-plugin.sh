#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/../plugin"
mvn -DskipTests package
cp -f target/VapezCore-1.0.0.jar ../pack/plugins/VapezCore-1.0.0.jar
echo "Installed pack/plugins/VapezCore-1.0.0.jar"
