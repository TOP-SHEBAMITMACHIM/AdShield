#!/usr/bin/env bash
# Rebuilds AdShield with the toolchain that lives in .toolchain/ inside this workspace.
#
# Why the drive letter: this project sits under a path with Hebrew characters and spaces.
# cmd.exe (used by gradle.bat, sdkmanager.bat and some SDK tools) encodes arguments with the
# Windows ANSI code page, which mangles those characters, so the tools are reached through a
# short ASCII SUBST mapping of the very same folder. Nothing is copied, renamed or moved.
#
# Usage:
#   tools/build-windows.sh                          # debug APK
#   tools/build-windows.sh :app:assembleRelease     # release APK
#   tools/build-windows.sh :app:testDebugUnitTest :app:lintDebug
set -euo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DRIVE="${ADSHIELD_DRIVE:-S:}"

if [ ! -d "$PROJECT_DIR/.toolchain/jdk" ] || [ ! -d "$PROJECT_DIR/.toolchain/android-sdk" ]; then
    echo "Toolchain not found under $PROJECT_DIR/.toolchain" >&2
    echo "Expected .toolchain/jdk and .toolchain/android-sdk (see README)." >&2
    exit 1
fi

case "$(uname -s)" in
    MINGW* | MSYS* | CYGWIN*) ;;
    *)
        echo "This helper is for Windows (Git Bash). On other systems just run ./gradlew." >&2
        exit 1
        ;;
esac

WIN_PATH="$(cd "$PROJECT_DIR" && pwd -W)"
case "$WIN_PATH" in
    [A-Za-z]:/*) ;;
    *)
        # pwd -W only degrades to a bare drive root when we are already on the mapping, and
        # in that case the mapping is by definition correct.
        [ "$WIN_PATH" = "${DRIVE}/" ] || { echo "Cannot resolve a Windows path for $PROJECT_DIR" >&2; exit 1; }
        ;;
esac

# The mapping is only (re)created when this project is not already reachable through it.
if [ ! -f "${DRIVE}/settings.gradle.kts" ]; then
    echo "Mapping $DRIVE -> $WIN_PATH"
    powershell.exe -NoProfile -Command "if (Test-Path ${DRIVE}\) { subst ${DRIVE} /D }; subst ${DRIVE} '$WIN_PATH'" >/dev/null
    if [ ! -f "${DRIVE}/settings.gradle.kts" ]; then
        echo "Failed to map $DRIVE to $WIN_PATH" >&2
        exit 1
    fi
fi

TASKS=("$@")
if [ ${#TASKS[@]} -eq 0 ]; then
    TASKS=(":app:assembleDebug")
fi

# Run from the mapped drive so the Gradle client and daemon see ASCII paths too.
cd "/$(printf '%s' "${DRIVE:0:1}" | tr '[:upper:]' '[:lower:]')"

env \
    JAVA_HOME="${DRIVE}\\.toolchain\\jdk" \
    ANDROID_HOME="${DRIVE}\\.toolchain\\android-sdk" \
    ANDROID_SDK_ROOT="${DRIVE}\\.toolchain\\android-sdk" \
    GRADLE_USER_HOME="${DRIVE}\\.toolchain\\gradle-home" \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8" \
    "${DRIVE}\\.toolchain\\gradle-8.9\\bin\\gradle.bat" --console=plain "${TASKS[@]}"
