#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")"

if [[ ! -f .env.local ]]; then
  echo "Missing .env.local — run: cp .env.example .env.local"
  exit 1
fi

set -a
# shellcheck disable=SC1091
source .env.local
set +a

if docker ps --format '{{.Names}}' | grep -q '^mergefruit-adminer$'; then
  echo "Adminer already running → http://localhost:8081"
  exit 0
fi

if docker ps -a --format '{{.Names}}' | grep -q '^mergefruit-adminer$'; then
  docker start mergefruit-adminer >/dev/null
  echo "Started existing Adminer → http://localhost:8081"
  exit 0
fi

# Connect Adminer to the same network as Postgres (works with docker run setup)
docker network create mergefruit-net 2>/dev/null || true
docker network connect mergefruit-net mergefruit-db 2>/dev/null || true

docker run -d --name mergefruit-adminer \
  --network mergefruit-net \
  -p 8081:8080 \
  -e ADMINER_DEFAULT_SERVER=mergefruit-db \
  adminer:4

echo ""
echo "Database UI ready: http://localhost:8081"
echo ""
echo "Login with values from .env.local:"
echo "  System:   PostgreSQL"
echo "  Server:   mergefruit-db   (or host.docker.internal if connection fails)"
echo "  Username: $DB_USER"
echo "  Password: (your DB_PASSWORD from .env.local)"
echo "  Database: $DB_NAME"
