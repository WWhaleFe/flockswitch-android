#!/bin/sh
# Pre-push guard: runs, as far as this Mac can, what CI runs (.github/workflows/ci.yml),
# so a failure never reaches GitHub (and the failure mail never reaches a phone).
# Installed as .git/hooks/pre-push (a one-line wrapper that calls this script).
#   - A push of tags only (releases) runs nothing slow.
#   - Emergency escape hatch: FLOCKSWITCH_SKIP_PREPUSH=1 git push ...
set -e
ROOT=$(git rev-parse --show-toplevel)
cd "$ROOT"
# Git hooks run with a minimal PATH: add the usual cargo locations.
PATH="$HOME/.cargo/bin:$HOME/.rustup/toolchains/stable-aarch64-apple-darwin/bin:$PATH"
export PATH
JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home}"
export JAVA_HOME

if [ "${FLOCKSWITCH_SKIP_PREPUSH:-}" = 1 ]; then
  echo "pre-push: skipped (FLOCKSWITCH_SKIP_PREPUSH=1)"
  exit 0
fi

# git gives one line per pushed ref on stdin: "<local ref> <local sha> <remote ref> <remote sha>".
tags_only=0
if [ ! -t 0 ]; then
  seen=0
  tags_only=1
  while read -r _ _ remote_ref _; do
    seen=1
    case "$remote_ref" in refs/tags/*) ;; *) tags_only=0 ;; esac
  done
  [ "$seen" = 1 ] || tags_only=0
fi

step() { echo "pre-push: $*"; }

if [ "$tags_only" = 1 ]; then
  step "tags only, nothing to check"
  exit 0
fi

# CI runs `./gradlew testDebugUnitTest assembleDebug` (assembleDebug also builds the
# network library for every ABI with cargo-ndk, which needs the NDK; not run here).
step "flocknet-jni cargo test"
cargo test --manifest-path flocknet-jni/Cargo.toml --locked
step "netcore tests"
cargo test --manifest-path netcore/Cargo.toml --locked
step "gradle testDebugUnitTest"
./gradlew --no-daemon testDebugUnitTest
step "all checks passed"
