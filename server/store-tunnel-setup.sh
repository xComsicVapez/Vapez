#!/usr/bin/env bash
# Give the web store a permanent address through its own Cloudflare named tunnel.
#
#   1. cloudflared tunnel login        (open the link, pick the domain; writes ~/.cloudflared/cert.pem)
#   2. ./store-tunnel-setup.sh [hostname] [server dir]
#
# Creates the "cosmic-store" tunnel, a DNS record for the hostname, ~/.cloudflared/cosmic-store.yml,
# sets public_url in config/web-store.json and swaps the quick tunnel for the named one. Safe to re-run.
set -euo pipefail

HOSTNAME_="${1:-store.cosmicpixel.org}"
DIR="${2:-$HOME/lifesteal2}"
TUNNEL="cosmic-store"
CF="$HOME/.cloudflared"
CONFIG="$CF/cosmic-store.yml"

[ -f "$CF/cert.pem" ] || { echo "Run 'cloudflared tunnel login' first."; exit 1; }

id="$(cloudflared tunnel list -o json 2>/dev/null | python3 -c "
import json, sys
print(next((t['id'] for t in json.load(sys.stdin) if t['name'] == '$TUNNEL' and not t.get('deleted_at', '').startswith('2')), ''))")"
if [ -z "$id" ]; then
  cloudflared tunnel create "$TUNNEL"
  id="$(cloudflared tunnel list -o json | python3 -c "
import json, sys
print(next(t['id'] for t in json.load(sys.stdin) if t['name'] == '$TUNNEL'))")"
fi
echo "tunnel $TUNNEL: $id"

umask 077
cat > "$CONFIG" <<EOF
tunnel: $id
credentials-file: $CF/$id.json
metrics: 127.0.0.1:20243
ingress:
  - hostname: $HOSTNAME_
    service: http://127.0.0.1:8080
  - service: http_status:404
EOF
cloudflared tunnel --config "$CONFIG" ingress validate

cloudflared tunnel route dns "$TUNNEL" "$HOSTNAME_" || {
  echo "Could not create the DNS record. If $HOSTNAME_ already exists in Cloudflare DNS, delete it there and re-run."
  exit 1
}

python3 - "$DIR/config/web-store.json" "https://$HOSTNAME_" <<'EOF'
import json, os, sys
path, url = sys.argv[1], sys.argv[2]
data = json.load(open(path))
data["public_url"] = url
tmp = path + ".tmp"
with open(tmp, "w") as f:
    json.dump(data, f, indent=2)
os.chmod(tmp, 0o600)
os.replace(tmp, path)
EOF

tmux kill-session -t "=store-tunnel" 2>/dev/null || true
tmux new-session -d -s store-tunnel \
  "while true; do cloudflared tunnel --no-autoupdate --config '$CONFIG' run 2>&1 | tee /tmp/store-tunnel.log; sleep 5; done"
store_pid="$(pgrep -f "^python3 .*/web_store.py $DIR\$" || true)"
[ -n "$store_pid" ] && kill $store_pid
echo "Done: the store is at https://$HOSTNAME_ (the web store restarts by itself in a few seconds)."
