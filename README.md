# API AbasteceFacil

## Configuração do Banco de Dados

Este projeto utiliza PostgreSQL rodando em Docker para desenvolvimento.

### Pré-requisitos

- Docker
- Docker Compose
- Java 21 (apenas para desenvolvimento local)
- Maven (apenas para desenvolvimento local)

### Como executar

#### **Opção 1: Docker Compose (Recomendado)**

```bash
# Buildar a imagem da aplicação
docker compose build

# Executar tudo (API + Banco) - Com logs
docker compose up 

# Ou executar em background
docker compose up -d

# Executar apenas Banco de dados
docker compose up -d postgres
```

#### **Opção 2: Desenvolvimento Local**

1. **Iniciar o banco de dados:**
   ```bash
   docker-compose up -d postgres
   ```

2. **Executar a aplicação:**
   ```bash
   ./mvnw spring-boot:run
   ```

### Configurações do Banco

- **Host:** localhost (dev) / postgres (docker)
- **Porta:** 5432
- **Database:** abastecefacil
- **Usuário:** abastecefacil_user
- **Senha:** abastecefacil_password

### Endpoints da API

- **URL Base:** http://localhost:8081
- **Health Check:** http://localhost:8081/actuator/health

### Comandos úteis

```bash
# Executar tudo
docker-compose up -d

# Parar os containers
docker-compose down

# Parar e remover volumes (cuidado: apaga os dados)
docker-compose down -v

# Ver logs da API
docker-compose logs api

# Ver logs do PostgreSQL
docker-compose logs postgres

# Rebuild da API
docker-compose up -d --build api

# Acessar o PostgreSQL via CLI
docker-compose exec postgres psql -U abastecefacil_user -d abastecefacil
```

### Estrutura do Docker Compose

- **postgres:** Banco de dados PostgreSQL 15
- **api:** Aplicação Spring Boot
- **Volumes:** Dados persistentes do PostgreSQL
- **Network:** Rede isolada para comunicação entre serviços
