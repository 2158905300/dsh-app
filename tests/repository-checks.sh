#!/usr/bin/env bash
set -euo pipefail
project=$(cd "$(dirname "$0")/.." && pwd)
root="$project/build/repository-test-$$"
mkdir -p "$root/mock-bin" "$root/home/dsh-setup" "$root/usr/etc/apt"
export HOME="$root/home" PREFIX="$root/usr" MOCK_TRACE="$root/trace"
export PATH="$root/mock-bin:$PATH"
printf 'RECIPIENT_EXISTING_SOURCE\n' > "$PREFIX/etc/apt/sources.list"
cat > "$root/mock-bin/timeout" <<'SH'
#!/usr/bin/env bash
set -e
test "$1" = --foreground
shift
test "$1" = -k
shift 3
exec "$@"
SH
cat > "$root/mock-bin/apt-get" <<'SH'
#!/usr/bin/env bash
printf '%s\n' "$*" >> "$MOCK_TRACE"
if [ "${MOCK_FAIL_ALL:-0}" = 1 ]; then echo 'E: Some index files failed to download.' >&2;exit 100;fi
if [[ "$*" == *'--fix-broken --reinstall install -y openssl'* ]]; then touch "$HOME/dsh-setup/mock-openssl-repaired";fi
if [ "${MOCK_FAIL_TUNA:-0}" = 1 ] && grep -q 'tuna.tsinghua' "$HOME/dsh-setup/repository.list"; then
  echo 'E: Some index files failed to download. They have been ignored, or old ones used instead' >&2
  exit 100
fi
exit 0
SH
cat > "$root/mock-bin/dpkg" <<'SH'
#!/usr/bin/env bash
echo "dpkg $*" >> "$MOCK_TRACE"
if [ "${MOCK_DPKG_BROKEN:-0}" = 1 ]; then echo 'node post-install: cannot locate symbol OSSL_PROVIDER_add_conf_parameter' >&2;exit 1;fi
SH
cat > "$root/mock-bin/node" <<'SH'
#!/usr/bin/env bash
echo 'node validation' >> "$MOCK_TRACE"
if [ ! -f "$HOME/dsh-setup/mock-openssl-repaired" ]; then echo 'CANNOT LINK EXECUTABLE node: cannot locate symbol OSSL_PROVIDER_add_conf_parameter' >&2;exit 127;fi
echo '{"node":"v26.4.0","openssl":"matching-library"}'
SH
chmod +x "$root/mock-bin/timeout" "$root/mock-bin/apt-get" "$root/mock-bin/dpkg"
chmod +x "$root/mock-bin/node"
source "$project/assets/harness/repository.sh"
export HARNESS_REPOSITORY=auto MOCK_FAIL_TUNA=1
harness_refresh > "$root/fallback.log" 2>&1
test "$(cat "$HOME/dsh-setup/selected-repository")" = official
grep -q 'packages.termux.dev' "$HOME/dsh-setup/repository.list"
grep -q 'APT::Update::Error-Mode=any' "$MOCK_TRACE"
grep -q 'Acquire::https::Timeout=15' "$MOCK_TRACE"
test "$(cat "$PREFIX/etc/apt/sources.list")" = RECIPIENT_EXISTING_SOURCE
export MOCK_FAIL_ALL=1
if harness_refresh > "$root/failure.log" 2>&1; then echo 'All-source failure incorrectly counted as success' >&2;exit 1;fi
grep -q '所有选择的软件源均未刷新成功' "$root/failure.log"
unset MOCK_FAIL_ALL MOCK_FAIL_TUNA
export HARNESS_REPOSITORY=current
harness_refresh > "$root/current.log" 2>&1
test "$(cat "$HOME/dsh-setup/selected-repository")" = current
harness_apt_install nodejs npm
tail -n 1 "$MOCK_TRACE" | grep -q 'install -y nodejs npm'
if tail -n 1 "$MOCK_TRACE" | grep -q 'Dir::Etc::sourcelist='; then echo 'Current source must not be overridden' >&2;exit 1;fi
if harness_verify_node > "$root/node-before-repair.log" 2>&1; then echo 'Broken Node incorrectly accepted' >&2;exit 1;fi
export MOCK_DPKG_BROKEN=1
harness_prepare_base > "$root/base-repair.log" 2>&1
unset MOCK_DPKG_BROKEN
test -f "$HOME/dsh-setup/mock-openssl-repaired"
grep -q 'dist-upgrade -y' "$MOCK_TRACE"
harness_verify_node > "$root/node-after-repair.log" 2>&1
printf 'Repository checks passed: index-failure fallback, all-failure stop, preserved source config, timeouts and noninteractive package install.\n'
printf 'Runtime repair checks passed: broken Node rejected; failed prior package configuration does not prevent OpenSSL repair and full base upgrade.\n'
