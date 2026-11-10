#!/bin/sh
# commit-msg hook (dx.5): the first line must be a Conventional Commit. Merge, revert and fixup/squash lines pass.
# Usage: scripts/check-commit-msg.sh <file with the commit message>
first=$(grep -v '^#' "$1" | sed -n '1p')
case "$first" in
  "Merge "*|"Revert \""*|"fixup! "*|"squash! "*) exit 0 ;;
esac
if printf '%s\n' "$first" | grep -Eq '^(feat|fix|docs|chore|refactor|test|perf|ci|build|style|revert)(\(.+\))?!?: .+'; then
  exit 0
fi
echo "commit message rejected: the first line must look like 'type(scope): subject'" >&2
echo "types: feat fix docs chore refactor test perf ci build style revert" >&2
echo "got: $first" >&2
exit 1
