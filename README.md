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
- **Porta:** **5433** no host (dev) / 5432 dentro da rede do compose (docker)
- **Database:** abastecefacil
- **Usuário:** abastecefacil_user
- **Senha:** abastecefacil_password

> A porta publicada é 5433, e não 5432, porque uma instalação nativa do PostgreSQL na
> máquina costuma já ocupar a 5432 — nesse caso o Docker publica só em IPv6 e uma
> conexão para `localhost:5432` cai no banco errado, falhando com "autenticação do
> tipo senha falhou". Só quem chega de fora usa a 5433: o container da API e o pgAdmin
> falam `postgres:5432` pelo DNS interno da rede.

### Arquivos de configuração

- `application.yml` — base e defaults de desenvolvimento local (`./mvnw spring-boot:run`).
- `application-docker.yml` — sobrescreve só o datasource quando o perfil `docker`
  está ativo, o que o `docker-compose.yml` faz via `SPRING_PROFILES_ACTIVE`.

Variável de ambiente tem precedência sobre os dois. A tabela completa de propriedades
está no `CLAUDE.md`, seção 3.

### Administrador inicial

O primeiro administrador é criado na subida da aplicação, por configuração — não existe
endpoint de bootstrap e não há como promover alguém pela interface ainda. Três variáveis:

| Variável | Obrigatória | Default |
|---|---|---|
| `ABASTECEFACIL_ADMIN_EMAIL` | sim | — |
| `ABASTECEFACIL_ADMIN_SENHA_HASH` | sim | — |
| `ABASTECEFACIL_ADMIN_NOME` | não | `Administrador` |

Sem e-mail e hash, a criação é pulada e a aplicação sobe normalmente — é o esperado em
desenvolvimento. Subir várias vezes cria no máximo um usuário: se o e-mail já existe,
nada é alterado.

> **Não use `admin@abastecefacil.com`.** Esse endereço já vem no `init-scripts/dump.sql`
> com perfil `COLABORADOR` e senha de origem desconhecida. Configurá-lo faz o
> inicializador encontrar o registro e não criar nada — você fica com um "administrador"
> que não é administrador e não loga. Escolha outro endereço.

#### Gerar o hash BCrypt

A senha nunca é passada para a aplicação em texto plano: o que se configura é o **hash
BCrypt**. Para gerá-lo, use o teste `GerarHashBCryptTest`, que existe para isso — o
projeto já tem Spring Security no classpath, então não é preciso instalar nada.

Em **PowerShell**, a partir de `abastece-facil-api/`:

```powershell
$env:BCRYPT_SENHA = 'a-senha-escolhida'
.\mvnw.cmd test -Dtest=GerarHashBCryptTest
Get-Content target/hash-bcrypt.txt
Remove-Item Env:\BCRYPT_SENHA
```

O hash sai em `target/hash-bcrypt.txt`, que o `.gitignore` já cobre. A senha entra por
variável de ambiente e nada é impresso no console, para que nem o hash nem a senha
sobrem no scroll do terminal ou em log de CI.

Confira o resultado antes de usar: deve ter **60 caracteres** e começar com `$2a$10$`.
Copie o valor para `ABASTECEFACIL_ADMIN_SENHA_HASH` no ambiente de execução —
**nunca no `application.yml`**, que é versionado.

Sem `BCRYPT_SENHA` definida o teste não escreve nada e passa em silêncio, então
`./mvnw clean test` continua verde normalmente.

Se o valor configurado não tiver formato de hash BCrypt, a aplicação sobe, **não** cria
o usuário e registra dois `ERROR` no log — um no ponto da falha e um resumo ao final. O
valor recebido não é registrado em lugar nenhum.

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
