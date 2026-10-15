#!/usr/bin/env bash
# Build images and deploy PlaceBot to a local Kubernetes cluster (Docker Desktop / minikube / kind).
set -euo pipefail
cd "$(dirname "$0")/.."
[ -f .env ] || { echo ".env missing - copy .env.example to .env first"; exit 1; }
set -a; . ./.env; set +a

docker build -t placebot-backend:1.0.0 ./backend
docker build -t placebot-frontend:1.0.0 ./frontend

# minikube / kind need the images loaded into the cluster
if command -v minikube >/dev/null 2>&1 && minikube status >/dev/null 2>&1; then
  minikube image load placebot-backend:1.0.0; minikube image load placebot-frontend:1.0.0
elif command -v kind >/dev/null 2>&1 && kind get clusters 2>/dev/null | grep -q .; then
  kind load docker-image placebot-backend:1.0.0 placebot-frontend:1.0.0
fi

kubectl apply -f k8s/00-namespace.yaml
kubectl -n placebot create secret generic placebot-secret \
  --from-literal=DB_PASSWORD="${DB_PASSWORD}" \
  --from-literal=GEMINI_API_KEY="${GEMINI_API_KEY:-}" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k k8s/
kubectl -n placebot rollout status statefulset/mysql --timeout=300s
kubectl -n placebot rollout status deployment/backend --timeout=300s
kubectl -n placebot rollout status deployment/frontend --timeout=120s
echo "Done. Open http://localhost:30080 (minikube: run 'minikube service frontend -n placebot')"
