#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

IMAGE_NAME="${IMAGE_NAME:-arch/warchi-mcp}"
IMAGE_TAG="${IMAGE_TAG:-0.1.0}"

echo "Building Docker image ${IMAGE_NAME}:${IMAGE_TAG}..."
docker build -t "${IMAGE_NAME}:${IMAGE_TAG}" -t "${IMAGE_NAME}:latest" .
echo "Built ${IMAGE_NAME}:${IMAGE_TAG}"
