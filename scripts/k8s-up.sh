#!/usr/bin/env bash
# Local kind cluster for the journal service. Not a production cluster.
# Usage: scripts/k8s-up.sh   (compose stack must be down)
set -euo pipefail
cd "$(dirname "$0")/.."

KIND="${KIND:-.tools/kind.exe}"
[ -x "$KIND" ] || KIND="$(command -v kind)"
CLUSTER=journal
NS=journal

if [ -n "$(docker compose ps -q 2>/dev/null)" ]; then
  echo "Compose stack is running. Run 'docker compose down' first (memory)." >&2
  exit 1
fi

"$KIND" get clusters | grep -qx "$CLUSTER" || "$KIND" create cluster --name "$CLUSTER" --config k8s/kind-config.yaml --wait 120s

docker build -t journal-app:k8s .
"$KIND" load docker-image journal-app:k8s --name "$CLUSTER"

kubectl config use-context "kind-$CLUSTER" >/dev/null
kubectl apply -f k8s/namespace.yaml
# Real secret generated here; nothing secret is stored in the repository.
kubectl -n "$NS" create secret generic journal-secret \
  --from-literal=JWT_SECRET="$(head -c 48 /dev/urandom | base64 | tr -d '\n=+/')" \
  --dry-run=client -o yaml | kubectl apply -f -
kubectl apply -k k8s

kubectl -n "$NS" rollout status statefulset/mongo --timeout=300s
kubectl -n "$NS" rollout status statefulset/kafka --timeout=300s
kubectl -n "$NS" rollout status deployment/redis --timeout=300s
kubectl -n "$NS" wait --for=condition=complete job/mongo-init --timeout=300s
kubectl -n "$NS" rollout status deployment/journal-app --timeout=420s

scripts/smoke.sh http://localhost:8080
kubectl get pods,svc,hpa -n "$NS"
