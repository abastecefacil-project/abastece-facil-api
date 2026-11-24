# API AbasteceFacil

Repositórios: [backend](https://github.com/abastecefacil-project/abastece-facil-api) e
[frontend](https://github.com/abastecefacil-project/abastece-facil-web).

## Rodar localmente (recomendado)

Você precisa de Docker Desktop, Java 21 e Node.js 20. O Maven já vem pelo wrapper do
projeto. Clone o backend e o frontend lado a lado, com estes nomes de pasta:

```text
workspace/
├── api-abastecefacil/
└── front-abastecefacil/
```

**Terminal 1 — API e banco:**

```bash
cd api-abastecefacil
./dev.sh
```

No Windows, use `dev.cmd`. O script sobe o PostgreSQL em Docker e inicia a API na porta
8081. Na primeira execução, o Maven baixa as dependências.

**Terminal 2 — frontend:**

```bash
cd front-abastecefacil
cp .env.example .env
npm ci
npm run dev
```

No PowerShell, use `Copy-Item .env.example .env` no lugar de `cp`.
Abra [http://localhost:5173](http://localhost:5173). Para desenvolvimento, use:

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | `admin@abastecefacil.com` | `Admin@12345` |
| Gestor de frota | `gestor@abastecefacil.com` | `Gestor@12345` |
| Colaborador | `colaborador@abastecefacil.com` | `Colab@12345` |

O envio de e-mail fica simulado por padrão. Não use essas contas fora do ambiente local.

### Scripts auxiliares

Execute os scripts a partir da pasta `api-abastecefacil`:

| Ação | Linux/macOS/WSL | Windows |
|---|---|---|
| Iniciar banco e API | `./dev.sh` | `dev.cmd` |
| Iniciar somente o banco | `./start-db` | `start-db.cmd` |
| Iniciar somente a API, com o banco já ativo | `./start-api` | `start-api.cmd` |

## Rodar tudo em Docker (opcional)

O Compose completo precisa das pastas irmãs mostradas acima. No diretório do backend, copie
o exemplo e troque a senha de banco de exemplo:

```bash
cp .env.example .env
```

No PowerShell, use `Copy-Item .env.example .env`.

Gere um segredo com `openssl rand -base64 32` e cole em `JWT_SECRET` no `.env`. No
PowerShell, gere-o com:

```powershell
[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
```

Depois rode:

```bash
docker compose up -d --build
```

Abra [http://localhost](http://localhost). O banco fica somente na rede interna do
Compose; a API e o frontend comunicam-se pela rede Docker. O envio de e-mail começa no
modo simulado (`log`). Para entrar, configure também o administrador inicial conforme a
seção abaixo; as contas de desenvolvimento só existem no modo local recomendado.

### Configurações do Banco

- **Host:** localhost (dev) / postgres (docker)
- **Porta:** 5432 no host (Compose de desenvolvimento) / 5432 na rede do Compose completo
- **Database:** abastecefacil
- **Usuário:** abastecefacil_user
- **Senha:** abastecefacil_password

> Só o Compose de desenvolvimento publica a porta 5432 no host. No Compose completo, a
> API acessa o banco por `postgres:5432` na rede interna.

### Arquivos de configuração

- `application.yml` — base e defaults de desenvolvimento local (`./mvnw spring-boot:run`).
- `application-docker.yml` — sobrescreve só o datasource quando o perfil `docker`
  está ativo, o que o `docker-compose.yml` faz via `SPRING_PROFILES_ACTIVE`.

Variável de ambiente tem precedência sobre os dois. A tabela completa de propriedades
está no `CLAUDE.md`, seção 3.

### Segurança

- O login aceita até 10 tentativas por e-mail e 30 por IP a cada 15 minutos.
- A recuperação aceita 3 pedidos por e-mail em 15 minutos e 10 por IP em uma hora.
- O JWT exige `JWT_SECRET`; a aplicação não inicia sem essa variável.
- O CORS aceita somente as origens definidas em `CORS_ALLOWED_ORIGINS`. No exemplo local,
  `http://localhost` atende o frontend pelo Compose e `http://localhost:5173` atende o Vite.
- As consultas usam Spring Data com parâmetros nomeados. Dados recebidos não são concatenados em SQL.
- Senhas usam BCrypt e links de acesso são aleatórios, armazenados como hash e válidos uma única vez.
- O Nginx envia CSP, proteção contra frames, política de referência e bloqueio de MIME sniffing.

O limite é mantido em memória e atende ao Compose atual, com uma instância da API. Se a API for
replicada, o contador deve ser movido para um armazenamento compartilhado.

### Administrador inicial

O primeiro administrador é criado na subida da aplicação, por configuração — não existe
endpoint público de bootstrap. Após o primeiro acesso, administradores podem criar e
editar perfis pela tela de usuários. Três variáveis:

| Variável | Obrigatória | Default |
|---|---|---|
| `ABASTECEFACIL_ADMIN_EMAIL` | sim | — |
| `ABASTECEFACIL_ADMIN_SENHA_HASH` | sim | — |
| `ABASTECEFACIL_ADMIN_NOME` | não | `Administrador` |

Sem e-mail e hash, a criação é pulada e a aplicação sobe normalmente — é o esperado em
desenvolvimento. Subir várias vezes cria no máximo um usuário: se o e-mail já existe,
nada é alterado.

### Usuários de desenvolvimento

Ao executar a API localmente, a migration de desenvolvimento recria os usuários abaixo:

| Perfil | E-mail | Senha |
|---|---|---|
| Administrador | `admin@abastecefacil.com` | `Admin@12345` |
| Gestor de frota | `gestor@abastecefacil.com` | `Gestor@12345` |
| Colaborador | `colaborador@abastecefacil.com` | `Colab@12345` |

Essas credenciais são exclusivas para desenvolvimento. A migration não é executada pelo
perfil Docker de produção.

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

### Envio de e-mail

Os e-mails de acesso (convite de primeiro acesso e recuperação de senha) saem por um
adaptador escolhido na subida, em `abastecefacil.email.provedor`.

| Variável | Obrigatória em produção | Default |
|---|---|---|
| `ABASTECEFACIL_EMAIL_PROVEDOR` | sim (`resend`) | `log` |
| `ABASTECEFACIL_EMAIL_API_KEY` | sim | — |
| `ABASTECEFACIL_EMAIL_REMETENTE` | sim | `Abastece Fácil <onboarding@resend.dev>` |
| `ABASTECEFACIL_EMAIL_FRONTEND_URL` | sim | `http://localhost:5173` |
| `ABASTECEFACIL_EMAIL_API_URL` | não | `https://api.resend.com` |

**Em desenvolvimento não configure nada.** O default `log` não envia mensagem nenhuma:
escreve o link no console, prefixado por `[E-MAIL SIMULADO]`. Assim a aplicação sobe sem
chave, sem rede e sem consumir cota.

> **`log` não pode ir para produção.** O link contém o token de acesso em claro, então
> deixá-lo lá equivale a publicar tokens no log da aplicação — quem lê o log define a
> senha de qualquer usuário convidado.

Com `ABASTECEFACIL_EMAIL_PROVEDOR=resend`, a chave e o remetente passam a ser
obrigatórios e a aplicação **não sobe** sem eles, com uma mensagem dizendo qual variável
falta. A chave nunca é registrada em log, nem quando é rejeitada.

A chave se obtém no painel do Resend e **nunca entra no `application.yml`**, que é
versionado — mesma regra do hash do administrador inicial. O remetente precisa ser um
endereço verificado no Resend; o default é o domínio de teste deles, que só entrega para
a conta dona da chave.

O `docker-compose.yml` repassa essas variáveis ao container da API. Para ativar o envio,
defina no `.env` usado pelo Compose `ABASTECEFACIL_EMAIL_PROVEDOR=resend`, a chave da API,
um remetente verificado e a URL pública do frontend. O `.env.example` mantém o modo `log`
para desenvolvimento; copie-o para `.env` e não versione esse arquivo. Na VPS, não use o
remetente de teste `onboarding@resend.dev`: configure o domínio verificado da aplicação.

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
