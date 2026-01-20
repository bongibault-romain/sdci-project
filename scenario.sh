#!/bin/bash

DEPLOYMENT="sdci-gwf1-dev1"
NAMESPACE="default"
MIN_PODS=1
MAX_PODS=8
INTERVAL=3

echo "Starting autoscale random scenario..."

# Initial scale
kubectl scale deployment "$DEPLOYMENT" --replicas=$MIN_PODS -n "$NAMESPACE"

while true; do
  # Get current number of replicas
  CURRENT=$(kubectl get deployment "$DEPLOYMENT" -n "$NAMESPACE" -o jsonpath='{.spec.replicas}')

  # Random choice: 0 = scale down, 1 = scale up
  ACTION=$((RANDOM % 2))

  if [ "$ACTION" -eq 1 ] && [ "$CURRENT" -lt "$MAX_PODS" ]; then
    NEW=$((CURRENT + 1))
    echo "Scaling UP from $CURRENT to $NEW pods"
    kubectl scale deployment "$DEPLOYMENT" --replicas=$NEW -n "$NAMESPACE"

  elif [ "$ACTION" -eq 0 ] && [ "$CURRENT" -gt "$MIN_PODS" ]; then
    NEW=$((CURRENT - 1))
    echo "Scaling DOWN from $CURRENT to $NEW pods"
    kubectl scale deployment "$DEPLOYMENT" --replicas=$NEW -n "$NAMESPACE"

  else
    echo "No scaling action (current: $CURRENT pods)"
  fi

  sleep $INTERVAL
done
