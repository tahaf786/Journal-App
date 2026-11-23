# Kubernetes on a local kind cluster

The service runs on a one-node [kind](https://kind.sigs.k8s.io/) cluster on a laptop. This is a local
learning and demo setup, not a production cluster: one node, single-instance data services, no auth or TLS
on MongoDB, Redis or Kafka.

## What runs where (namespace `journal`)

| Object | Kind | Notes |
|---|---|---|
| `journal-app` | Deployment, 2 replicas | rolling update (maxUnavailable 0, maxSurge 1); readiness `/actuator/health/readiness`, liveness and startup `/actuator/health/liveness`; requests 200m CPU / 512Mi, limits 1 CPU / 640Mi |
| `journal-app` | Service (ClusterIP) | in-cluster address |
| `journal-app-nodeport` | Service (NodePort 30080) | kind maps it to `localhost:8080` for the smoke test |
| `journal-app` | HorizontalPodAutoscaler | CPU 70%, min 2, max 4 |
| `mongo` | StatefulSet + headless Service + 1Gi PVC | single-node replica set `rs0`; the `mongo-init` Job runs `rs.initiate` |
| `redis` | Deployment + Service | cache only, no persistence |
| `kafka` | StatefulSet + headless Service + 1Gi PVC | one KRaft broker |
| `journal-config` | ConfigMap | non-secret settings |
| `journal-secret` | Secret | `JWT_SECRET`, generated at run time (`k8s/secret.example.yaml` holds a placeholder only) |

Not in the cluster: Ollama and Qdrant. The manifests cover the base stack; the optional "ask my journal"
feature stays off (`APP_RAG_ENABLED=false`).

## Commands

Needs Docker, `kubectl` and the `kind` binary at `.tools/kind.exe` (or on `PATH`). The compose stack must
be down; the script refuses to run otherwise.

```bash
scripts/k8s-up.sh      # create cluster, build and load image, apply, wait, smoke test, kubectl get
scripts/k8s-down.sh    # delete the cluster
```

## Run recorded on this machine

Versions: kind v0.33.0 (release checksum verified), Kubernetes v1.37.0 (server), kubectl client v1.36.1.
Script time from empty cluster to smoke result: about 3m40s including the image build.

```
ok: unauthenticated request is rejected -> 401
ok: register alice-... -> 201
ok: create entry -> 201
ok: second user cannot read the first user's entry (404)
ok: health endpoint -> 200
SMOKE TEST PASSED

pod/journal-app-554697f4cc-2gtzv   1/1   Running     1 (87s ago)
pod/journal-app-554697f4cc-wx56c   1/1   Running     1 (88s ago)
pod/kafka-0                        1/1   Running     0
pod/mongo-0                        1/1   Running     0
pod/mongo-init-hp8l8               0/1   Completed   4
pod/redis-6844549f6f-tf5rk         1/1   Running     0
horizontalpodautoscaler/journal-app   cpu: <unknown>/70%   2  4  2
```

What the output shows:

- Both app pods restarted once. They started before the replica set had been initiated (the init Job also
  needed four attempts), so the first start failed and Kubernetes restarted them. A fresh run settles
  without intervention. An init container waiting for Mongo would avoid the restart; not added.
- The HPA target is `<unknown>` because metrics-server is not installed in kind by default and was not
  installed here. Autoscaling was not exercised. The HPA object exists and is valid, nothing more.

## Rolling update check

I changed `ROLLOUT_MARKER` in the ConfigMap from 1 to 2 and ran `kubectl rollout restart` on the
Deployment, while a loop requested `/actuator/health/readiness` through the NodePort every 0.2 s.

Result: rollout completed, 88 requests, 0 failed (all HTTP 200); the smoke test passed afterwards. This is
a small sample from one client on one machine. It shows the readiness gate and `maxUnavailable: 0` working
for a quick probe, not that every request type survives a rollout.

## Limits

- One laptop, one node; no node failure, no real load balancer, no ingress, no network policies.
- MongoDB, Redis and Kafka are single instances with no authentication or TLS. Data lives on a kind
  volume that disappears with the cluster.
- The image is built locally and loaded into the node (`kind load`); there is no registry.
- Secret values are base64 in etcd, not encrypted, and visible to anyone with cluster access.
- No metrics-server, so no autoscaling test; no resource tuning beyond the figures in the manifests.
