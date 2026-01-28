#!/bin/bash

DEPLOYMENT="sdci-gwf1-dev1"
NAMESPACE="default"
MIN_PODS=1
MAX_PODS=8
INTERVAL=3

echo "Starting scenario..."

# Initial scale
kubectl scale deployment "$DEPLOYMENT" --replicas=$MIN_PODS -n "$NAMESPACE"

sleep 10

echo "Scaling to $MAX_PODS device(s)"

kubectl scale deployment "$DEPLOYMENT" --replicas=$MAX_PODS -n "$NAMESPACE"

sleep 60

echo "Scaling to $MIN_PODS device(s)"

kubectl scale deployment "$DEPLOYMENT" --replicas=$MIN_PODS -n "$NAMESPACE"

sleep 60

echo "Scenario ended."
