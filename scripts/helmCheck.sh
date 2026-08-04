#!/bin/bash
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"
helm lint ./charts/warchi-mcp
helm template warchi-mcp ./charts/warchi-mcp >/dev/null
echo "Helm chart OK"
