#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:/system/bin"
export TMPDIR="$PREFIX/tmp"
export SHELL="$PREFIX/bin/bash"
export TZ=Asia/Shanghai
export DSH_HOME="$HOME/.dsh"
export DSH_PERMISSION_MODE=workspace-write
if [ -f "$HOME/dsh-setup/permission-mode" ] && [ -f "$HOME/dsh-setup/unsandboxed-consent" ] && [ "$(cat "$HOME/dsh-setup/permission-mode")" = danger-full-access ]; then
    export DSH_PERMISSION_MODE=danger-full-access
fi
unset LD_PRELOAD
unset LD_LIBRARY_PATH NODE_OPTIONS NODE_PATH
if [ -f "$HOME/dsh-setup/model-env.sh" ]; then source "$HOME/dsh-setup/model-env.sh"; fi
cd "$HOME/projects/harness"
printf '%s\n' "$$" > "$HOME/dsh-setup/server.pid"
exec node --expose-internals "$PREFIX/lib/node_modules/@deepseek-ai/dsh/lib/bin.js" --profile mobile-share --port 3080 --no-open
