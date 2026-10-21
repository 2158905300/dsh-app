#!/data/data/com.termux/files/usr/bin/bash
# Command-scoped APT sources: do not overwrite a recipient's system repo files.
harness_apt_options() {
  HARNESS_APT_OPTIONS=(-o Acquire::http::Timeout=15 -o Acquire::https::Timeout=15
    -o Acquire::Retries=0 -o Acquire::Languages=none -o APT::Update::Error-Mode=any
    -o DPkg::Lock::Timeout=20 -o Dpkg::Use-Pty=0
    -o Dpkg::Options::=--force-confdef -o Dpkg::Options::=--force-confold)
  if [ -f "$HOME/dsh-setup/selected-repository" ] && [ "$(cat "$HOME/dsh-setup/selected-repository")" != current ]; then
    HARNESS_APT_OPTIONS+=(-o "Dir::Etc::sourcelist=$HOME/dsh-setup/repository.list" -o Dir::Etc::sourceparts=-)
  fi
}
harness_refresh_one() {
  local choice=$1 label url code
  case "$choice" in
    tuna) label=清华镜像;url=https://mirrors.tuna.tsinghua.edu.cn/termux/apt/termux-main ;;
    official) label=官方软件源;url=https://packages.termux.dev/apt/termux-main ;;
    current) label=现有软件源;url= ;;
    *) return 2 ;;
  esac
  printf '%s\n' "$choice" > "$HOME/dsh-setup/selected-repository"
  if [ "$choice" != current ]; then printf 'deb %s stable main\n' "$url" > "$HOME/dsh-setup/repository.list"; fi
  harness_apt_options
  printf 'HARNESS_STAGE: 正在刷新软件源（%s，单次最多约 2 分钟）\n' "$label"
  if timeout --foreground -k 10s 120s apt-get "${HARNESS_APT_OPTIONS[@]}" update </dev/null; then
    printf '软件源刷新成功：%s\n' "$label";return 0
  else
    code=$?;printf '软件源刷新未成功：%s（退出码 %s）；网络超时、签名或镜像同步错误请查看上方日志。\n' "$label" "$code"
    return "$code"
  fi
}
harness_refresh() {
  local choice=${HARNESS_REPOSITORY:-auto}
  case "$choice" in
    auto)
      if harness_refresh_one tuna; then return 0; fi
      printf '正在尝试下一个软件源，不需要重新安装 App。\n'
      if harness_refresh_one official; then return 0; fi ;;
    tuna|official|current) if harness_refresh_one "$choice"; then return 0; fi ;;
    *) printf '无效的软件源选项。\n';return 2 ;;
  esac
  printf '所有选择的软件源均未刷新成功。请检查网络、系统时间，或回安装页切换软件源重试。\n'
  return 1
}
harness_prepare_base() {
  harness_apt_options
  printf '正在检查中断的软件包配置；若旧 Node 暂时无法启动，会先修复 OpenSSL 后继续。\n'
  # A failed node/npm post-install can itself block configuration. Do not abort
  # before APT gets an opportunity to replace the incompatible OpenSSL library.
  if ! timeout --foreground -k 10s 180s dpkg --force-confdef --force-confold --configure -a </dev/null; then
    printf '存在未配置完成的软件包，正在修复基础库，不会忽略最终验证结果。\n'
  fi
  printf '正在重新安装与当前软件源匹配的 OpenSSL，并修复依赖。\n'
  timeout --foreground -k 15s 1800s apt-get "${HARNESS_APT_OPTIONS[@]}" --fix-broken --reinstall install -y openssl </dev/null || return $?
  printf '正在统一升级基础环境，避免旧库与新 Node 混用。\n'
  timeout --foreground -k 15s 1800s apt-get "${HARNESS_APT_OPTIONS[@]}" dist-upgrade -y </dev/null || return $?
}
harness_apt_install() {
  harness_apt_options
  timeout --foreground -k 15s 1800s apt-get "${HARNESS_APT_OPTIONS[@]}" install -y "$@" </dev/null
}
harness_verify_node() {
  printf '正在验证 Node 与 OpenSSL 的动态链接。\n'
  if node -p 'JSON.stringify({node:process.version,openssl:process.versions.openssl})'; then return 0; fi
  printf 'Node 仍无法启动：请保留上方链接错误和软件包版本日志，不能继续安装 npm/DSH。\n'
  dpkg-query -W -f='${Package} ${Version}\n' nodejs openssl 2>/dev/null || true
  return 1
}
