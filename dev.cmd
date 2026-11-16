@echo off
setlocal

docker compose -f docker-compose.dev.yml up -d
if errorlevel 1 exit /b %errorlevel%

if not defined JWT_SECRET set JWT_SECRET=YWJhc3RlY2VmYWNpbC1kZXNlbnZvbHZpbWVudG8tbG9jYWwtbmFvLXVzYXItZW0tcHJvZHVjYW8=
call mvnw.cmd spring-boot:run
