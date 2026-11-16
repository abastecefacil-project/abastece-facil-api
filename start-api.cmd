@echo off
if not defined JWT_SECRET set JWT_SECRET=YWJhc3RlY2VmYWNpbC1kZXNlbnZvbHZpbWVudG8tbG9jYWwtbmFvLXVzYXItZW0tcHJvZHVjYW8=
call mvnw.cmd spring-boot:run
