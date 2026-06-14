#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env ]]; then
  echo "Missing backend/.env — create it from the template:"
  echo "  cp .env.example .env"
  echo "Then set DB_PASSWORD, JWT_SECRET (32+ chars), and ANONYMOUS_USER_PASSWORD."
  exit 1
fi

# Export secrets into the environment for Maven/Spring Boot
set -a
# shellcheck disable=SC1091
source .env
set +a

export SPRING_PROFILES_ACTIVE=dev
exec mvn spring-boot:run
