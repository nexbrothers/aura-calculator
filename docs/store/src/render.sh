#!/usr/bin/env bash
# Renders the Play Store graphics from the HTML sources in this folder with headless Edge.
# Usage: render.sh <folder with raw 1080x2424 screenshots named s_*.png>
set -e
SRC="$(cd "$(dirname "$0")" && pwd -W)"
OUT="$(cd "$(dirname "$0")/.." && pwd -W)"
RAW="$1"
EDGE="/c/Program Files (x86)/Microsoft/Edge/Application/msedge.exe"
PROFILE="${TEMP:-/tmp}/waura-edge-profile"

render() { # <html file + query> <width> <height> <output png>
  # A fresh profile per render: a second headless Edge on a busy profile hands off and draws nothing.
  local profile="$PROFILE-$RANDOM$RANDOM"
  rm -f "$4"
  "$EDGE" --headless=new --user-data-dir="$profile" --no-first-run --disable-gpu --hide-scrollbars \
    --force-device-scale-factor=1 --allow-file-access-from-files --virtual-time-budget=4000 \
    --window-size="$2,$3" --screenshot="$4" "file:///$SRC/$1"
  for _ in $(seq 1 40); do [ -s "$4" ] && break; sleep 0.5; done
  [ -s "$4" ] || echo "FAILED: $4"
}

render "icon.html" 512 512 "$OUT/waura-icon-512.png"
render "feature.html" 1024 500 "$OUT/waura-feature-1024x500.png"

# name | caption | two aura tones
while IFS='|' read -r name cap tones; do
  [ -z "$name" ] && continue
  enc=$(printf '%s' "$cap" | sed 's/ /%20/g')
  render "frame.html?img=file:///$RAW/$name.png&cap=$enc&tones=$tones" 1080 1920 "$OUT/screenshot-$name.png"
done <<'LIST'
s_home|Your phone time has an aura.|FFB27A,FF9EC7
s_running|Focus sessions add aura.|FFB27A,FF8A7A
s_done|Finish a session, watch it glow.|FF9EC7,FFE27A
s_limits|Keep your limits. Take the W.|FFE27A,9FF0CF
s_insights|See what changed, line by line.|9FF0CF,FFE27A
s_levelup|Level up from Static to Halo.|FF9EC7,FFB27A
s_privacy|Everything stays on your phone.|9FF0CF,B8C7FF
LIST
sleep 5
ls -la "$OUT"/*.png
