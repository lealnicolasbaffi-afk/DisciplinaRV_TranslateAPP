#!/bin/sh
set -eu
cd "$(dirname "$0")/.."
printf '%s  %s\n' '2db75c40782f5e8ba1fc278a5574bab070adccb2d21ca5a6e5ed840888448046' 'gradle/wrapper/gradle-wrapper.jar' | sha256sum --check
