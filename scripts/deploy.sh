#!/bin/bash
#
# deploy.sh — развёртывание warchi-mcp в Kubernetes через Helm.
#
# Примеры:
#   SKIP_CONFIRM=true ./scripts/deploy.sh
#   BUILD_IMAGE=false IMAGE_TAG=0.1.0 ./scripts/deploy.sh
#

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]:-$0}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$REPO_ROOT"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

NAMESPACE="${NAMESPACE:-arch}"
RELEASE_NAME="${RELEASE_NAME:-warchi-mcp}"
CHART_PATH="${CHART_PATH:-charts/warchi-mcp}"
VALUES_FILE="${VALUES_FILE:-$CHART_PATH/values.yaml}"
BUILD_IMAGE="${BUILD_IMAGE:-true}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-180}"
IMAGE_TAG="${IMAGE_TAG:-0.1.1}"
AREPOS_BASE_URL="${AREPOS_BASE_URL:-http://arepos-server:8080}"

log_info() { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    log_error "$1 не установлен"
    exit 1
  fi
}

log_info "Проверка необходимых команд..."
check_command kubectl
check_command helm
check_command docker

log_info "Проверка подключения к Kubernetes..."
if ! kubectl cluster-info >/dev/null 2>&1; then
  log_error "Не удалось подключиться к Kubernetes кластеру"
  exit 1
fi

CURRENT_CONTEXT=$(kubectl config current-context)
log_warn "Текущий kubectl context: $CURRENT_CONTEXT"
if [ "${SKIP_CONFIRM:-false}" != "true" ]; then
  read -r -p "Деплоить в этот кластер? (y/N) " CONFIRM
  if [ "$CONFIRM" != "y" ] && [ "$CONFIRM" != "Y" ]; then
    log_info "Деплой отменён"
    exit 0
  fi
fi

if ! kubectl get namespace "$NAMESPACE" >/dev/null 2>&1; then
  log_info "Создание namespace '$NAMESPACE'..."
  kubectl create namespace "$NAMESPACE"
fi

if [ "$BUILD_IMAGE" = "true" ]; then
  log_info "Сборка Docker-образа..."
  IMAGE_TAG="$IMAGE_TAG" "$SCRIPT_DIR/buildImage.sh"
else
  log_warn "Сборка образа пропущена (BUILD_IMAGE=false)"
fi

log_info "Helm upgrade --install $RELEASE_NAME..."
HELM_ARGS=(
  upgrade --install "$RELEASE_NAME" "$CHART_PATH"
  -n "$NAMESPACE"
  --set "image.tag=$IMAGE_TAG"
  --set "arepos.baseUrl=$AREPOS_BASE_URL"
)
if [ -f "$VALUES_FILE" ]; then
  HELM_ARGS+=(-f "$VALUES_FILE")
fi

helm "${HELM_ARGS[@]}"

log_info "Ожидание готовности Deployment (таймаут: ${WAIT_TIMEOUT}с)..."
kubectl rollout status "deployment/$RELEASE_NAME" -n "$NAMESPACE" --timeout="${WAIT_TIMEOUT}s"

log_info "Поды:"
kubectl get pods -n "$NAMESPACE" -l "app.kubernetes.io/name=warchi-mcp"

log_info "Проверка health через port-forward..."
kubectl -n "$NAMESPACE" port-forward "svc/$RELEASE_NAME" 18090:8090 >/tmp/warchi-mcp-pf.log 2>&1 &
PF_PID=$!
cleanup() { kill "$PF_PID" >/dev/null 2>&1 || true; }
trap cleanup EXIT
sleep 2
if curl -fsS "http://127.0.0.1:18090/actuator/health" >/dev/null; then
  log_info "Health OK: http://127.0.0.1:18090/actuator/health"
else
  log_warn "Health check через port-forward не удался (см. /tmp/warchi-mcp-pf.log)"
fi

echo ""
log_info "Деплой warchi-mcp завершён"
echo "Namespace:       $NAMESPACE"
echo "Release:         $RELEASE_NAME"
echo "Image tag:       $IMAGE_TAG"
echo "AREPOS_BASE_URL: $AREPOS_BASE_URL"
echo "MCP (port-forward): kubectl -n $NAMESPACE port-forward svc/$RELEASE_NAME 8090:8090"
echo "MCP URL:            http://127.0.0.1:8090/mcp"
