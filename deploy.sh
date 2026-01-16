#!/bin/bash
set -e

# Chemin des fichiers YAML
DEPLOYMENT_DIR="./deployment"

kubectl apply -f "$DEPLOYMENT_DIR/servers"
sleep 10  # Attendre que les serveurs soient prêts
kubectl apply -f "$DEPLOYMENT_DIR/gateways/intermediaire"
sleep 10  # Attendre que les serveurs soient prêts
kubectl apply -f "$DEPLOYMENT_DIR/gateways/finales"
sleep 10  # Attendre que les serveurs soient prêts
kubectl apply -f "$DEPLOYMENT_DIR/devices"
sleep 10  # Attendre que les serveurs soient prêts
kubectl apply -f "$DEPLOYMENT_DIR/applications"
sleep 10  # Attendre que les serveurs soient prêts

echo "🎉 Tous les pods sont déployés et prêts !"
