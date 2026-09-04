#!/usr/bin/env sh
set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
repository_root=$(CDPATH= cd -- "$script_dir/.." && pwd)

"$script_dir/check-build-environment.sh"

git -C "$repository_root" config core.hooksPath .githooks

echo "Kairo Git hooks installed from .githooks."
echo "Use './gradlew qualityCheck' during development and './gradlew qualityGate' before merge."
