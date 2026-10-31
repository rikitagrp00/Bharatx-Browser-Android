#!/usr/bin/env bash
# SPDX-License-Identifier: MPL-2.0
set -euo pipefail
sdk_root="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
if [[ -z "$sdk_root" ]]; then
  echo 'Set ANDROID_HOME or ANDROID_SDK_ROOT to an Android SDK with command-line tools.' >&2
  exit 1
fi
sdkmanager="$sdk_root/cmdline-tools/latest/bin/sdkmanager"
if [[ ! -x "$sdkmanager" ]]; then
  sdkmanager="$(command -v sdkmanager || true)"
fi
if [[ ! -x "$sdkmanager" ]]; then
  echo 'Android SDK command-line tools are missing.' >&2
  exit 1
fi
packages_file="$(mktemp)"
trap 'rm -f "$packages_file"' EXIT
python3 automation/bharatx/sdk_packages.py > "$packages_file"
mapfile -t packages < "$packages_file"
[[ "${#packages[@]}" -eq 4 ]]
set +e
yes | "$sdkmanager" --sdk_root="$sdk_root" --licenses >/dev/null
license_status=${PIPESTATUS[1]}
set -e
[[ "$license_status" -eq 0 ]]
"$sdkmanager" --sdk_root="$sdk_root" "${packages[@]}"
printf 'sdk.dir=%s\n' "$sdk_root" > local.properties
