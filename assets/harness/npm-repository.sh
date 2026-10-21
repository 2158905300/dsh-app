#!/data/data/com.termux/files/usr/bin/bash
# Per-install configuration; never rewrite the recipient's ~/.npmrc or proxy.
HARNESS_ORIGINAL_NPM_REGISTRY_SET=${npm_config_registry+x}
HARNESS_ORIGINAL_NPM_REGISTRY=${npm_config_registry-}
HARNESS_ORIGINAL_NPM_USERCONFIG_SET=${npm_config_userconfig+x}
HARNESS_ORIGINAL_NPM_USERCONFIG=${npm_config_userconfig-}
harness_npm_source() {
  local choice=$1 registry label
  case "$choice" in
    mirror) registry=https://registry.npmmirror.com/;label=npmmirror镜像 ;;
    official) registry=https://registry.npmjs.org/;label=npm官方源 ;;
    current)
      if [ "$HARNESS_ORIGINAL_NPM_REGISTRY_SET" = x ]; then export npm_config_registry="$HARNESS_ORIGINAL_NPM_REGISTRY";else unset npm_config_registry;fi
      if [ "$HARNESS_ORIGINAL_NPM_USERCONFIG_SET" = x ]; then export npm_config_userconfig="$HARNESS_ORIGINAL_NPM_USERCONFIG";else unset npm_config_userconfig;fi
      label=现有npm配置;registry= ;;
    *) return 2 ;;
  esac
  if [ "$choice" != current ]; then
    export npm_config_registry="$registry"
    export npm_config_userconfig="$HOME/dsh-setup/npm-install.npmrc"
    printf 'registry=%s\n@deepseek-ai:registry=%s\nstrict-ssl=true\n' "$registry" "$registry" > "$npm_config_userconfig"
    chmod 600 "$npm_config_userconfig"
  fi
  printf '正在检查 npm 下载源：%s（固定 DSH 版本，最多约 50 秒）\n' "$label"
  local version
  if version=$(timeout --foreground -k 5s 50s npm view '@deepseek-ai/dsh@0.2.0-rc.2' version --prefer-online --fetch-timeout=20000 --fetch-retries=0 </dev/null); then
    version=$(printf '%s' "$version" | tr -d '\r\n')
    if [ "$version" = '0.2.0-rc.2' ]; then printf 'npm 下载源检查通过：%s\n' "$label";return 0;fi
    printf '源返回的版本与固定 DSH 版本不一致，不继续使用该源。\n'
  fi
  printf 'npm 下载源检查失败：%s。\n' "$label"
  return 1
}
harness_npm_install() {
  local choice=${HARNESS_NPM_REPOSITORY:-auto} source code
  local sources=()
  case "$choice" in auto) sources=(mirror official);;mirror|official|current) sources=("$choice");;*)return 2;;esac
  for source in "${sources[@]}"; do
    if ! harness_npm_source "$source"; then continue;fi
    printf '正在下载并安装固定版本 DSH；编译脚本输出会显示在下方。此阶段最多 60 分钟，超时会明确停止。\n'
    if timeout --foreground -k 15s 3600s npm install -g '@deepseek-ai/dsh@0.2.0-rc.2' --no-audit --no-fund --foreground-scripts </dev/null 2>&1 | tee "$HOME/dsh-setup/npm-attempt.log"; then
      return 0
    else
      code=$?
    fi
    if [ "$code" = 124 ] || [ "$code" = 137 ]; then
      printf '安装阶段达到 60 分钟总时限，已停止。可能仍在编译；请保留日志，不会因总时限自动换源并重新编译。\n'
      return "$code"
    fi
    if [ "$choice" = auto ] && grep -Eq 'ETIMEDOUT|ESOCKETTIMEDOUT|ENOTFOUND|EAI_AGAIN|ECONNRESET|ECONNREFUSED|EHOSTUNREACH|ENETUNREACH' "$HOME/dsh-setup/npm-attempt.log"; then
      printf '检测到 npm 网络错误，尝试下一个下载源；已缓存的内容会保留。\n'
    else
      printf 'npm 安装失败（退出码 %s）；请查看 npm 详细日志。不是网络错误时不反复换源编译。\n' "$code"
      return "${code:-1}"
    fi
  done
  printf 'npm 下载源不可用或下载均失败，请切换 npm 下载源/网络后重试；不需要填写 npm 账号或模型 Key 来下载公开包。\n'
  return 1
}
