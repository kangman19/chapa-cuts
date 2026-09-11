#!/usr/bin/env bash
set -a; source .env; set +a
cd backend
if command -v mvn >/dev/null 2>&1; then
  mvn spring-boot:run
else
  ./mvnw spring-boot:run
fi
