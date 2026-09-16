#!/usr/bin/env bash
set -euo pipefail

docker compose -f docker-compose.dev.yml up -d
./mvnw spring-boot:run
