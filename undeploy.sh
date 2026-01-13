#!/bin/bash
set -e

# Chemin des fichiers YAML
DEPLOYMENT_DIR="./deployment"

kubectl delete -f "$DEPLOYMENT_DIR/servers"
kubectl delete -f "$DEPLOYMENT_DIR/gateways/intermediaire"
kubectl delete -f "$DEPLOYMENT_DIR/gateways/finales"
kubectl delete -f "$DEPLOYMENT_DIR/devices"
kubectl delete -f "$DEPLOYMENT_DIR/applications"

echo "🎉 Tous les pods sont déployés et prêts !"
