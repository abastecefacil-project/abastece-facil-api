@echo off
docker compose -f docker-compose.dev.yml up -d
if errorlevel 1 exit /b %errorlevel%
echo PostgreSQL disponivel em localhost:5432
