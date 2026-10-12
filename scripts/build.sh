#!/usr/bin/env bash
#
# WebIDE - A powerful IDE for Android web development.
# Copyright (C) 2025  如日中天  <3382198490@qq.com>
#
# This program is free software: you can redistribute it and/or modify
# it under the terms of the GNU General Public License as published by
# the Free Software Foundation, either version 3 of the License, or
# (at your option) any later version.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with this program.  If not, see <https://www.gnu.org/licenses/>.
#
# WebIDE 一键构建脚本（Git Bash / WSL / Linux / macOS）
#
# 用法：
#   scripts/build.sh                        # debug 两个架构全构建
#   scripts/build.sh release                # release 两个架构
#   scripts/build.sh release arm64-v8a      # release 仅 arm64
#   scripts/build.sh debug all --open       # 构建完成后打开产物目录
#   scripts/build.sh --clean                # 先 clean 再构建
#
# 产物位置：
#   app/build/outputs/apk/arm64-v8a/{debug,release}/
#   app/build/outputs/apk/armeabi-v7a/{debug,release}/

set -euo pipefail

# --- 定位项目根目录（脚本在 scripts/ 下） ---
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$PROJECT_DIR"

# --- 参数解析 ---
BUILD_TYPE="debug"
FLAVOR="all"
OPEN_DIR=false
CLEAN=false

print_usage() {
    sed -n '2,20p' "$0" | sed 's/^# \{0,1\}//'
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        debug|release)
            BUILD_TYPE="$1"
            ;;
        arm64-v8a|armeabi-v7a|all)
            FLAVOR="$1"
            ;;
        --open)
            OPEN_DIR=true
            ;;
        --clean)
            CLEAN=true
            ;;
        -h|--help)
            print_usage
            exit 0
            ;;
        *)
            echo "未知参数: $1" >&2
            print_usage
            exit 1
            ;;
    esac
    shift
done

# flavor -> gradle 任务名后缀。注意任务名必须带 flavor，
# app 模块不存在 assembleDebug / compileDebugKotlin 这类无 flavor 任务。
case "$BUILD_TYPE" in
    debug)   TYPE_CAP="Debug" ;;
    release) TYPE_CAP="Release" ;;
esac

if [[ "$FLAVOR" == "all" ]]; then
    FLAVORS=("arm64-v8a" "armeabi-v7a")
else
    FLAVORS=("$FLAVOR")
fi

TASKS=()
for f in "${FLAVORS[@]}"; do
    TASKS+=(":app:assemble${f}${TYPE_CAP}")
done

# --- 环境检查 ---
if [[ ! -x "./gradlew" ]]; then
    echo "错误: 未找到可执行的 gradlew（应在项目根目录运行）" >&2
    exit 1
fi

if [[ -z "${JAVA_HOME:-}" ]] && ! command -v java >/dev/null 2>&1; then
    echo "错误: 未检测到 Java，请先安装 JDK 17 或设置 JAVA_HOME" >&2
    exit 1
fi

# --- 开始构建 ---
echo "=========================================="
echo " WebIDE 构建"
echo "   类型: $BUILD_TYPE"
echo "   架构: ${FLAVORS[*]}"
echo "   任务: ${TASKS[*]}"
echo "=========================================="

if [[ "$CLEAN" == true ]]; then
    echo "[1/2] clean ..."
    ./gradlew clean --console=plain --quiet
fi

START_TS=$(date +%s)
./gradlew "${TASKS[@]}" --console=plain
END_TS=$(date +%s)

# --- 汇总产物 ---
echo ""
echo "=========================================="
echo " 构建完成，耗时 $((END_TS - START_TS)) 秒"
echo "=========================================="

APKS=()
for f in "${FLAVORS[@]}"; do
    OUT_DIR="app/build/outputs/apk/${f}/${BUILD_TYPE}"
    found=0
    for apk in "$OUT_DIR"/*.apk; do
        [[ -e "$apk" ]] || continue
        found=1
        SIZE=$(du -h "$apk" | cut -f1)
        printf "  %-60s %s\n" "$apk" "$SIZE"
        APKS+=("$apk")
    done
    if [[ $found -eq 0 ]]; then
        echo "  警告: 未在 $OUT_DIR 找到 APK" >&2
    fi
done

# --- 可选：打开产物目录 ---
if [[ "$OPEN_DIR" == true && ${#APKS[@]} -gt 0 ]]; then
    LAST_DIR="$(dirname "${APKS[-1]}")"
    echo ""
    echo "打开产物目录: $LAST_DIR"
    case "$(uname -s)" in
        MINGW*|MSYS*|CYGWIN*) start "" "$LAST_DIR" ;;
        Darwin)               open "$LAST_DIR" ;;
        *)                    xdg-open "$LAST_DIR" >/dev/null 2>&1 || true ;;
    esac
fi
