#!/usr/bin/env bash
# First-time Git setup. Usage: scripts/git-init.sh https://github.com/<you>/<repo>.git
set -euo pipefail
cd "$(dirname "$0")/.."
git init -b main
git add .
git status --short | grep -q '^A  .env$' && { echo "ERROR: .env is staged - aborting"; exit 1; } || true
git commit -m "Initial commit: PlaceBot (Spring Boot, Selenium, MySQL, Docker, Kubernetes)"
if [ "${1:-}" != "" ]; then git remote add origin "$1" && git push -u origin main; fi
