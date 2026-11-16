#!/usr/bin/env bash
set -euo pipefail

docker compose -f docker-compose.dev.yml up -d
export JWT_SECRET="${JWT_SECRET:-YWJhc3RlY2VmYWNpbC1kZXNlbnZvbHZpbWVudG8tbG9jYWwtbmFvLXVzYXItZW0tcHJvZHVjYW8=}"
./mvnw spring-boot:run
