#!/usr/bin/env sh
set -eu

die() {
    printf 'ERROR: %s\n' "$*" >&2
    exit 1
}

read_java_property() {
    printf '%s\n' "$java_properties" | awk -v requested_key="$1" '
        {
            key = $0
            sub(/^[[:space:]]*/, "", key)
            sub(/[[:space:]]*=.*/, "", key)

            if (key == requested_key) {
                value = $0
                sub(/^[^=]*=[[:space:]]*/, "", value)
                sub(/[[:space:]]*$/, "", value)
                print value
                exit
            }
        }
    '
}

if [ -n "${JAVA_HOME:-}" ]; then
    java_command="$JAVA_HOME/bin/java"
    [ -x "$java_command" ] || die "JAVA_HOME does not contain an executable bin/java: $JAVA_HOME"
else
    java_command=$(command -v java 2>/dev/null) ||
        die "JAVA_HOME is not set and no java executable was found on PATH. Install JDK 17 and set JAVA_HOME."
fi

if ! java_properties=$("$java_command" -XshowSettings:properties -version 2>&1); then
    printf 'ERROR: Unable to run the Java executable Gradle would use: %s\n' "$java_command" >&2
    printf '%s\n' "$java_properties" >&2
    exit 1
fi

java_home=$(read_java_property java.home)
java_vendor=$(read_java_property java.vendor)
java_version=$(read_java_property java.version)
java_specification_version=$(read_java_property java.specification.version)
java_arch=$(read_java_property os.arch)

[ -n "$java_home" ] || die "The selected Java runtime did not report java.home: $java_command"
[ -n "$java_vendor" ] || die "The selected Java runtime did not report java.vendor: $java_command"
[ -n "$java_version" ] || die "The selected Java runtime did not report java.version: $java_command"
[ -n "$java_specification_version" ] ||
    die "The selected Java runtime did not report java.specification.version: $java_command"
[ -n "$java_arch" ] || die "The selected Java runtime did not report os.arch: $java_command"

javac_command="$java_home/bin/javac"
[ -x "$javac_command" ] || {
    printf 'ERROR: The selected Java home is not a complete JDK; bin/javac is missing or not executable: %s\n' \
        "$java_home" >&2
    printf 'Install a full JDK 17, set JAVA_HOME to it, and configure Android Studio to use the same Gradle JDK.\n' >&2
    exit 1
}

java_major=${java_specification_version%%.*}
if [ "$java_major" = "1" ]; then
    legacy_remainder=${java_specification_version#*.}
    java_major=${legacy_remainder%%.*}
fi

[ "$java_major" = "17" ] || {
    printf 'ERROR: Kairo requires JDK 17, but the selected Java runtime reports specification version %s.\n' \
        "$java_specification_version" >&2
    printf 'Selected Java home: %s\n' "$java_home" >&2
    printf 'Set JAVA_HOME to a JDK 17 installation and configure Android Studio to use the same Gradle JDK.\n' >&2
    exit 1
}

kernel_name=$(uname -s 2>/dev/null || printf 'unknown\n')
machine_arch=$(uname -m 2>/dev/null || printf 'unknown\n')
mac_hardware=not-darwin

if [ "$kernel_name" = "Darwin" ]; then
    mac_hardware=unknown

    case "$machine_arch" in
        arm64|aarch64)
            mac_hardware=apple-silicon
            ;;
    esac

    if [ "$mac_hardware" = "unknown" ] && command -v sysctl >/dev/null 2>&1; then
        if arm64_capable=$(sysctl -n hw.optional.arm64 2>/dev/null); then
            case "$arm64_capable" in
                1)
                    mac_hardware=apple-silicon
                    ;;
                0)
                    mac_hardware=intel
                    ;;
            esac
        fi
    fi

    if [ "$mac_hardware" = "unknown" ] &&
        [ -x /usr/bin/arch ] && [ -x /usr/bin/true ]; then
        if /usr/bin/arch -arm64 /usr/bin/true >/dev/null 2>&1; then
            mac_hardware=apple-silicon
        fi
    fi
fi

case "$java_arch" in
    x86_64|amd64|X86_64|AMD64|x86|i386|i686)
        case "$mac_hardware" in
            apple-silicon)
                printf 'ERROR: The selected JDK is %s, but this Mac has Apple silicon. Gradle would run through Rosetta.\n' \
                    "$java_arch" >&2
                printf 'Install a native ARM64 JDK 17, set JAVA_HOME to it, and select the same Gradle JDK in Android Studio.\n' >&2
                printf 'For example: brew install --cask temurin@17\n' >&2
                printf 'Then stop old daemons once with: ./gradlew --stop\n' >&2
                exit 1
                ;;
            unknown)
                printf 'ERROR: The selected JDK is %s, but this script could not determine whether this Mac is Intel or Apple silicon.\n' \
                    "$java_arch" >&2
                printf 'Refusing to approve an x86 JDK because it may run through Rosetta.\n' >&2
                printf 'Run /usr/sbin/sysctl -n hw.optional.arm64 in an unrestricted terminal (1 means Apple silicon; 0 means Intel), then rerun this check.\n' >&2
                printf 'On Apple silicon, install a native ARM64 JDK 17 and set JAVA_HOME to it.\n' >&2
                exit 1
                ;;
        esac
        ;;
esac

printf 'Build JVM is compatible.\n'
printf '  Java home: %s\n' "$java_home"
printf '  Vendor:    %s\n' "$java_vendor"
printf '  Version:   %s\n' "$java_version"
printf '  os.arch:   %s\n' "$java_arch"
