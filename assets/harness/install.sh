#!/data/data/com.termux/files/usr/bin/bash
set -euo pipefail
umask 077
export HOME=/data/data/com.termux/files/home
export PREFIX=/data/data/com.termux/files/usr
export PATH="$PREFIX/bin:/system/bin"
export TMPDIR="$PREFIX/tmp"
export SHELL="$PREFIX/bin/bash"
export TERM=xterm-256color
export DEBIAN_FRONTEND=noninteractive
export npm_config_script_shell="$SHELL"
export npm_config_fetch_retries=2
export npm_config_fetch_timeout=60000
export npm_config_fetch_retry_mintimeout=2000
export npm_config_fetch_retry_maxtimeout=10000
export UCF_FORCE_CONFFOLD=1
unset LD_PRELOAD
unset LD_LIBRARY_PATH NODE_OPTIONS NODE_PATH
cd "$HOME/dsh-setup"
source ./repository.sh
printf '%s\n' "$$" > "$HOME/dsh-setup/install.pid"
trap 'rm -f "$HOME/dsh-setup/install.pid"' EXIT
if [ "${HARNESS_RESUME_PATCHES:-0}" = 1 ] && [ -f "$PREFIX/lib/node_modules/@deepseek-ai/dsh/lib/bin.js" ]; then
  printf '继续之前已到达的安卓补丁阶段，保留已安装依赖。\n'
  harness_verify_node
else
  harness_refresh
  printf 'HARNESS_STAGE: 正在安装 Node、编译工具和 DSH（首次可能较久）\n'
  bash ./install-dsh-reviewed.sh
fi
printf 'HARNESS_STAGE: 正在应用安卓兼容补丁\n'
node ./fix-session-import.cjs
node --expose-internals ./patch-builtin-loader.cjs
mod="$PREFIX/lib/node_modules/@deepseek-ai/dsh/node_modules/@deepseek-ai/node-addon-system"
mkdir -p "$mod/bin/android"
clang -shared -fPIC -O2 -DNAPI_VERSION=8 -I"$PREFIX/include/node" "$mod/src/flock.c" -o "$mod/bin/android/system.node"
node ./patch-android-flock.cjs
clang -O2 "$mod/src/main.c" -o "$mod/bin/android/landlock-run"
mkdir -p "$HOME/projects/harness"
source ./sandbox-mode.sh
harness_select_sandbox "$mod/bin/android/landlock-run"
printf 'HARNESS_STAGE: 正在配置基础插件和启动入口\n'
node ./configure-runtime.cjs
printf 'HARNESS_STAGE: 正在验证运行环境\n'
node --expose-internals "$PREFIX/lib/node_modules/@deepseek-ai/dsh/lib/bin.js" --help >/dev/null
printf '%s\n' "$HARNESS_SELECTED_MODE" > "$HOME/dsh-setup/permission-mode.tmp"
mv "$HOME/dsh-setup/permission-mode.tmp" "$HOME/dsh-setup/permission-mode"
printf '0.4.7-alpha\n' > "$HOME/dsh-setup/share-ready.tmp"
mv "$HOME/dsh-setup/share-ready.tmp" "$HOME/dsh-setup/share-ready"
printf 'HARNESS_STAGE: 运行环境安装完成，请配置模型\n'
