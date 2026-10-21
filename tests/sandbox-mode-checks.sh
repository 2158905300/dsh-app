#!/usr/bin/env bash
set -euo pipefail
project=$(cd "$(dirname "$0")/.." && pwd)
root="$project/build/sandbox-mode-test-$$"
mkdir -p "$root/bin" "$root/home/dsh-setup"
export HOME="$root/home" PATH="$root/bin:$PATH" MOCK_TRACE="$root/trace"
cat > "$root/bin/runner" <<'SH'
#!/usr/bin/env bash
case "${MOCK_PROBE:-supported}" in
 supported) echo 'landlock supported';exit 0;;
 unsupported) echo 'landlock-run: landlock is not enforced by this kernel (ABI unsupported or disabled)' >&2;exit 1;;
 *) echo 'unrelated executable failure' >&2;exit 127;;
esac
SH
cat > "$root/bin/node" <<'SH'
#!/usr/bin/env bash
echo "$*" >> "$MOCK_TRACE"
exit "${MOCK_FUNCTIONAL_FAILURE:-0}"
SH
chmod +x "$root/bin/runner" "$root/bin/node"
source "$project/assets/harness/sandbox-mode.sh"
export MOCK_PROBE=unsupported HARNESS_ALLOW_UNSANDBOXED=0
if harness_select_sandbox runner > "$root/no-consent.log" 2>&1;then echo 'Must not downgrade without consent';exit 1;fi
test -f "$HOME/dsh-setup/sandbox-unavailable"
test ! -e "$MOCK_TRACE"
export HARNESS_ALLOW_UNSANDBOXED=1
harness_select_sandbox runner > "$root/consent.log" 2>&1
test "$HARNESS_SELECTED_MODE" = danger-full-access
test ! -e "$MOCK_TRACE"
export MOCK_PROBE=supported
harness_select_sandbox runner > "$root/supported.log" 2>&1
test "$HARNESS_SELECTED_MODE" = workspace-write
grep -q patch-android-sandbox.cjs "$MOCK_TRACE"
test ! -e "$HOME/dsh-setup/sandbox-unavailable"
export MOCK_PROBE=broken
if harness_select_sandbox runner > "$root/broken.log" 2>&1;then echo 'Unexpected probe failure must not downgrade';exit 1;fi
export MOCK_PROBE=supported MOCK_FUNCTIONAL_FAILURE=1
# Called in a subshell with errexit enabled: actual supported-kernel policy failures stop installation.
if bash -c 'set -e;source "$1";harness_select_sandbox runner' -- "$project/assets/harness/sandbox-mode.sh" > "$root/functional.log" 2>&1;then echo 'Functional sandbox failure incorrectly accepted';exit 1;fi
printf 'Sandbox checks passed: no implicit downgrade, explicit fallback, supported kernel stays sandboxed, unexpected/functional failures stop.\n'
