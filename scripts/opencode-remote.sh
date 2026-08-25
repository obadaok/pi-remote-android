#!/usr/bin/env bash
# opencode-remote — secure OpenCode server for the pi-remote Android app.
#
# Starts `opencode serve` bound to the LAN with a password (HTTP Basic auth),
# then prints a pairing QR (oc-remote://host:port?p=<password>) for the app.
# The password is generated once and persisted in
# ~/.config/pi-remote/opencode-server.json (chmod 600). Never logged.
#
# Usage:
#   ./scripts/opencode-remote.sh            # LAN mode (0.0.0.0), default port 4096
#   PORT=5500 ./scripts/opencode-remote.sh  # custom port
#   HOST=127.0.0.1 ./scripts/opencode-remote.sh   # loopback only
#   SHOW_URI=1 ./scripts/opencode-remote.sh       # also print raw URI (contains password!)
set -euo pipefail

PORT="${PORT:-4096}"
HOST="${HOST:-0.0.0.0}"
CFG_DIR="$HOME/.config/pi-remote"
CFG="$CFG_DIR/opencode-server.json"

# Resolve opencode binary (systemd user services have a minimal PATH).
OPENCODE_BIN="${OPENCODE_BIN:-}"
if [ -z "$OPENCODE_BIN" ]; then
  OPENCODE_BIN="$(command -v opencode || true)"
fi
if [ -z "$OPENCODE_BIN" ] && [ -x "$HOME/.opencode/bin/opencode" ]; then
  OPENCODE_BIN="$HOME/.opencode/bin/opencode"
fi
if [ -z "$OPENCODE_BIN" ] && [ -x "$HOME/.local/bin/opencode" ]; then
  OPENCODE_BIN="$HOME/.local/bin/opencode"
fi
if [ -z "$OPENCODE_BIN" ] && [ -x "$HOME/.bun/bin/opencode" ]; then
  OPENCODE_BIN="$HOME/.bun/bin/opencode"
fi
if [ -z "$OPENCODE_BIN" ]; then
  echo "opencode binary not found" >&2
  exit 1
fi

mkdir -p "$CFG_DIR"
if [ ! -f "$CFG" ]; then
  PASS="$(python3 - <<'PY'
import secrets
print(secrets.token_urlsafe(24))
PY
)"
  umask 177
  printf '{"port": %s, "password": "%s"}\n' "$PORT" "$PASS" >"$CFG"
  unset PASS
fi
chmod 600 "$CFG" 2>/dev/null || true

PASSWORD="$(python3 -c "import json;print(json.load(open('$CFG'))['password'])")"

LAN_IP="$(hostname -I 2>/dev/null | awk '{print $1}')"
QR_IP="${QR_IP:-$LAN_IP}"
[ -n "$QR_IP" ] || { echo "No LAN IP found" >&2; exit 1; }

URI="oc-remote://$QR_IP:$PORT?p=$PASSWORD"

cleanup() { [ -n "${SERVER_PID:-}" ] && kill "$SERVER_PID" 2>/dev/null || true; }
trap cleanup EXIT INT TERM

echo "[opencode-remote] starting opencode serve on $HOST:$PORT (password protected)"
OPENCODE_SERVER_PASSWORD="$PASSWORD" "$OPENCODE_BIN" serve --hostname "$HOST" --port "$PORT" &
SERVER_PID=$!

for _ in $(seq 1 20); do
  sleep 1
  kill -0 "$SERVER_PID" 2>/dev/null || { echo "[opencode-remote] server process died" >&2; exit 1; }
  if command -v curl >/dev/null 2>&1 && curl -sf -o /dev/null "http://127.0.0.1:$PORT/global/health"; then
    break
  fi
done
if ! kill -0 "$SERVER_PID" 2>/dev/null; then
  echo "[opencode-remote] server failed to start" >&2
  exit 1
fi

echo
echo "==================== OPENCODE REMOTE PAIRING ===================="
echo "  Scan this QR from the app:  Settings -> Scan QR"
echo "  (Backend switches to OpenCode automatically)"
echo
if command -v qrencode >/dev/null 2>&1; then
  qrencode -t ANSIUTF8 -m 2 "$URI"
else
  python3 - "$URI" <<'PY'
import sys
try:
    import qrcode
except ImportError:
    print("(install python3-qrcode or qrencode to render the QR here)")
    print("URI base:", sys.argv[1].split("?")[0] + "?p=***")
    sys.exit(0)
qr = qrcode.QRCode(border=2)
qr.add_data(sys.argv[1])
qr.print_ascii(invert=True)
PY
fi
echo "  Server : $QR_IP:$PORT"
echo "  Health : http://$QR_IP:$PORT/global/health"
if [ "${SHOW_URI:-0}" = "1" ]; then
  echo "  URI    : $URI   <-- contains the password, do not share"
fi
echo "================================================================="
echo

wait "$SERVER_PID"
