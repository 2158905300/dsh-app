#!/data/data/com.termux/files/usr/bin/bash
# Only an explicit confirmation in the native App authorizes fallback.
harness_select_sandbox() {
  local runner=$1 probe code
  if probe=$("$runner" --probe 2>&1); then
    printf '%s\n' "$probe"
    node ./patch-android-sandbox.cjs || return $?
    HARNESS_SELECTED_MODE=workspace-write
    rm -f "$HOME/dsh-setup/sandbox-unavailable"
    return 0
  else
    code=$?
  fi
  printf '%s\n' "$probe"
  # Do not disguise a broken executable or an unrelated probe failure.
  if [[ "$probe" != *'landlock is not enforced by this kernel'* ]]; then
    printf '沙箱探测异常（退出码 %s），停止安装，请查看日志。\n' "$code"
    return 1
  fi
  printf 'kernel-landlock-unavailable\n' > "$HOME/dsh-setup/sandbox-unavailable"
  if [ "${HARNESS_ALLOW_UNSANDBOXED:-0}" != 1 ]; then
    printf '此手机内核不支持 Landlock。请返回安装页，自行选择是否以无沙箱模式继续；当前未降低权限限制。\n'
    return 1
  fi
  HARNESS_SELECTED_MODE=danger-full-access
  printf '已按本次明确确认使用无沙箱模式：工具可访问本 App 有权访问的文件；没有系统 root 权限。\n'
}
