#!/bin/zsh
# Smoke test on the Prism instance EzTest (never Ryan's instances): ./tools/smoke.sh <script file> [timeout s]
# Copies the built jar, writes config/ezgamble-debug, launches a singleplayer world offline, waits for the run to end,
# kills ONLY the EzTest java it started, prints the debug lines and the screenshots taken.
set -u
SCRIPT=$1; TIMEOUT=${2:-240}
I="$HOME/Library/Application Support/PrismLauncher/instances/EzTest/minecraft"
PRISM="/Users/ryaanbcq/Desktop/Jeux/Prism Launcher.app/Contents/MacOS/prismlauncher"
cd "$(dirname "$0")/.."
if ps -axo command | grep "[i]nstances/EzTest" | grep -q bin/java; then echo "EzTest already running, abort"; exit 1; fi
find "$I/mods" -name 'ezgamble-*.jar' -delete
cp build/libs/ezgamble-*.jar "$I/mods/"
find "$I/screenshots" -name 'ezgamble-*.png' -delete 2>/dev/null
cp "$SCRIPT" "$I/config/ezgamble-debug"
"$PRISM" --launch EzTest --world "Nouveau monde" --offline EzTest >/dev/null 2>&1 &
sleep 8
PID=""
for i in $(seq 1 $TIMEOUT); do
  [ -z "$PID" ] && PID=$(ps -axo pid,command | grep "[i]nstances/EzTest" | grep bin/java | awk '{print $1}' | head -1)
  if [ -n "$PID" ] && ! kill -0 $PID 2>/dev/null; then break; fi
  if grep -q "Stopping!" "$I/logs/latest.log" 2>/dev/null && grep -q "ezgamble\] debug" "$I/logs/latest.log"; then sleep 3; break; fi
  sleep 1
done
[ -n "$PID" ] && kill $PID 2>/dev/null; sleep 1; [ -n "$PID" ] && kill -9 $PID 2>/dev/null
rm -f "$I/config/ezgamble-debug"
grep -E "ezgamble\]|Exception|Error:|at dev.ryaan" "$I/logs/latest.log" | grep -v "existing team" | tail -60
ls "$I/screenshots" | grep ezgamble
