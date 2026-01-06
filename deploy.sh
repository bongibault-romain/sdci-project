#!/bin/bash
set -e

# Chemin des fichiers YAML
DEPLOYMENT_DIR="./deployment"

echo "=== Déploiement du serveur ==="
kubectl apply -f "$DEPLOYMENT_DIR/srv_cluster_ip.yaml"
kubectl apply -f "$DEPLOYMENT_DIR/srv_deployment.yaml"
kubectl rollout status deployment/sdci-server
echo "✅ Serveur prêt"

echo "=== Déploiement du gateway ==="
kubectl apply -f "$DEPLOYMENT_DIR/gw_cluster_ip.yaml"
kubectl apply -f "$DEPLOYMENT_DIR/gw_deployment.yaml"
kubectl rollout status deployment/sdci-gateway
echo "✅ Gateway prêt"

echo "=== Déploiement du device ==="
kubectl apply -f "$DEPLOYMENT_DIR/device_cluster_ip.yaml"
kubectl apply -f "$DEPLOYMENT_DIR/device_deployment.yaml"
kubectl rollout status deployment/sdci-device
echo "✅ Device prêt"

echo "=== Déploiement de l'application ==="
kubectl apply -f "$DEPLOYMENT_DIR/application_cluster_ip.yaml"
kubectl apply -f "$DEPLOYMENT_DIR/application_deployment.yaml"
kubectl rollout status deployment/sdci-application
echo "✅ Application prête"

echo "🎉 Tous les pods sont déployés et prêts !"
