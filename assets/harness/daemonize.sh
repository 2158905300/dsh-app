#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:/system/bin"
unset LD_PRELOAD
cd "$HOME"
if node "$HOME/dsh-setup/is-running.cjs"; then exit 0; fi
nohup bash "$HOME/dsh-setup/start.sh" >"$HOME/dsh-setup/server.log" 2>&1 </dev/null &
printf 'DSH launched.\n'
