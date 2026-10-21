#!/usr/bin/env bash
set -euo pipefail
project=$(cd "$(dirname "$0")/.." && pwd)
root="$project/build/npm-repository-test-$$"
mkdir -p "$root/mock-bin" "$root/home/dsh-setup"
export HOME="$root/home" MOCK_TRACE="$root/trace" PATH="$root/mock-bin:$PATH"
printf 'RECIPIENT_NPM_CONFIG\n' > "$HOME/.npmrc"
cat > "$root/mock-bin/timeout" <<'SH'
#!/usr/bin/env bash
shift 4
exec "$@"
SH
cat > "$root/mock-bin/npm" <<'SH'
#!/usr/bin/env bash
echo "${npm_config_registry:-current} $*" >> "$MOCK_TRACE"
if [ "$1" = view ]; then
 if [ "${MOCK_VIEW_FAIL_MIRROR:-0}" = 1 ] && [[ "${npm_config_registry:-}" == *npmmirror* ]]; then echo 'npm error ETIMEDOUT' >&2;exit 1;fi
 echo '0.2.0-rc.2';exit 0
fi
if [ "${MOCK_TOTAL_TIMEOUT:-0}" = 1 ]; then echo 'old cached network text ETIMEDOUT';exit 124;fi
if [ "${MOCK_COMPILE_ERROR:-0}" = 1 ]; then echo 'gyp ERR! missing compiler header';exit 1;fi
if [ "${MOCK_INSTALL_TIMEOUT:-0}" = 1 ] && [[ "${npm_config_registry:-}" == *npmmirror* ]]; then echo 'npm error code ETIMEDOUT';exit 1;fi
echo 'package installed';exit 0
SH
chmod +x "$root/mock-bin/timeout" "$root/mock-bin/npm"
source "$project/assets/harness/npm-repository.sh"
export HARNESS_NPM_REPOSITORY=auto MOCK_VIEW_FAIL_MIRROR=1
harness_npm_install > "$root/view-fallback.log" 2>&1
test "$npm_config_registry" = https://registry.npmjs.org/
test "$(cat "$HOME/.npmrc")" = RECIPIENT_NPM_CONFIG
unset MOCK_VIEW_FAIL_MIRROR
export MOCK_INSTALL_TIMEOUT=1
harness_npm_install > "$root/install-fallback.log" 2>&1
test "$npm_config_registry" = https://registry.npmjs.org/
grep -q '检测到 npm 网络错误' "$root/install-fallback.log"
unset MOCK_INSTALL_TIMEOUT
export MOCK_COMPILE_ERROR=1
: > "$MOCK_TRACE"
if harness_npm_install > "$root/compile-failure.log" 2>&1; then echo 'Compile failure incorrectly accepted' >&2;exit 1;fi
if grep -q 'registry.npmjs.org' "$MOCK_TRACE"; then echo 'Compile failure must not trigger registry fallback' >&2;exit 1;fi
unset MOCK_COMPILE_ERROR
export MOCK_TOTAL_TIMEOUT=1
: > "$MOCK_TRACE"
code=0
harness_npm_install > "$root/total-timeout.log" 2>&1 || code=$?
test "$code" = 124
grep -q '60 分钟总时限' "$root/total-timeout.log"
if grep -q 'registry.npmjs.org' "$MOCK_TRACE"; then echo 'Total compilation timeout must not trigger another compilation' >&2;exit 1;fi
printf 'npm registry checks passed: package preflight, network timeout fallback, compile error stop, fixed DSH version and original npm config preserved.\n'
