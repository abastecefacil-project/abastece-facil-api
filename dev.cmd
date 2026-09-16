@echo off
setlocal

docker compose -f docker-compose.dev.yml up -d
if errorlevel 1 exit /b %errorlevel%

call mvnw.cmd spring-boot:run
