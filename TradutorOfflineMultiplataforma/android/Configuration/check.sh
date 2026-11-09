#!/bin/sh
# Checks an already installed toolchain; never installs SDK packages or accepts licenses.
set -eu
cd "$(dirname "$0")/.."

if [ -n "${JAVA_HOME:-}" ]; then
    if [ ! -x "$JAVA_HOME/bin/javac" ]; then
        printf '%s\n' "JAVA_HOME precisa apontar para um JDK completo (bin/javac ausente): $JAVA_HOME" >&2
        exit 2
    fi
elif ! command -v javac >/dev/null 2>&1; then
    printf '%s\n' 'JDK completo ausente. Instale JDK 17 e configure JAVA_HOME com bin/javac.' >&2
    exit 2
fi

TASK_ANDROID_SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [ -z "$TASK_ANDROID_SDK" ] || [ ! -d "$TASK_ANDROID_SDK" ]; then
    printf '%s\n' 'SDK Android ausente. Configure ANDROID_HOME com um SDK instalado e licenciado por você.' >&2
    exit 2
fi
if [ ! -f "$TASK_ANDROID_SDK/platforms/android-35/android.jar" ] ||
   [ ! -x "$TASK_ANDROID_SDK/build-tools/35.0.0/aapt2" ]; then
    printf '%s\n' 'SDK incompleto. Instale platforms;android-35 e build-tools;35.0.0 pelo Android Studio/SDK Manager.' >&2
    exit 2
fi

export ANDROID_HOME="$TASK_ANDROID_SDK"
export GRADLE_USER_HOME="$PWD/work/gradle-home"
./Configuration/verificar-wrapper.sh
exec ./gradlew --no-daemon assembleDebug testDebugUnitTest lintDebug --console=plain "$@"
