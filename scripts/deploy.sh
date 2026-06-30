#!/bin/bash
# @file deploy.sh
# @agent TAG-05 (Necessary Identity)
# @description Deployment script ensuring environment identity.

set -e

echo "Verifying environment prerequisites..."

# Check Docker
if ! command -v docker &> /dev/null; then
    echo "Error: docker is not installed."
    exit 1
fi

# Check Docker Compose
if ! docker compose version &> /dev/null; then
    echo "Error: docker compose is not installed."
    exit 1
fi

echo "Building and starting services..."
docker compose up --build -d

echo "Waiting for services to be healthy..."

check_service() {
    local url=$1
    local name=$2
    local retries=30
    local wait=2

    echo "Checking $name at $url..."
    for i in $(seq 1 $retries); do
        if curl -s -f "$url" > /dev/null; then
            echo "$name is UP!"
            return 0
        fi
        echo "Waiting for $name... ($i/$retries)"
        sleep $wait
    done
    echo "Error: $name failed to start."
    return 1
}

# Health checks
check_service "http://localhost:8000/health" "AI Prediction Service"
check_service "http://localhost:8080/api/v1/greetings/latest" "Backend API"
check_service "http://localhost:3000" "Frontend UI"

echo "=========================================="
echo "Deployment Successful!"
echo "AI Service: http://localhost:8000"
echo "Backend API: http://localhost:8080"
echo "Frontend UI: http://localhost:3000"
echo "=========================================="
