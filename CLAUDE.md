# Abastece Fácil — Contexto do sistema

Documento de contexto para assistentes de IA. Descreve arquitetura, contratos,
regras de negócio e armadilhas conhecidas do projeto.

Projeto de TCC. Sistema de consulta e gestão de postos conveniados da
**FIESC / UNISENAI Joinville**.

---

## 1. Visão geral do domínio

O sistema atende dois públicos, com dois layouts e dois conjuntos de rotas:

- **Usuário final** — consulta os postos conveniados numa lista ou num mapa, vê a
  rota até o posto, e registra ocorrências sobre veículos da frota.
- **Administrador** — gerencia postos, veículos, usuários e analisa as ocorrências
  registradas, com um dashboard de totais.

Quatro entidades: **posto de combustível**, **veículo**, **usuário** e **ocorrência**.

Uma ocorrência é um relato sobre um **veículo da frota** (ex.: "devolvido com menos
de meio tanque", "sujo internamente"). Não é um relato sobre o posto — este é um
ponto que costuma ser mal interpretado.

---

## 2. Arquitetura

Dois repositórios independentes, lado a lado:

```
abastece_ai_tcc/
├── abastece-facil-api/                                    ← backend
└── front-abastece-facil-main/front-abastece-facil-main/   ← frontend
```

O backend é um clone e o código fica direto em `abastece-facil-api/`. O frontend
ainda tem **um nível de pasta duplicado**, herdado de download de ZIP: o caminho
real é `front-abastece-facil-main/front-abastece-facil-main/`.

Versionamento: backend em `abastecefacil-project/abastece-facil-api`, frontend em
`abastecefacil-project/abastece-facil-web`. Ambos privados.

### Backend

| Item | Valor |
|---|---|
| Stack | Java 21, Spring Boot 3.5.4, Spring Security, Spring Data JPA |
| Banco | PostgreSQL 15 |
| Autenticação | JWT (biblioteca jjwt), stateless |
| Porta | 8081 |
| Build | Maven (`./mvnw`), wrapper incluso |
| Execução | Docker Compose (API + Postgres + pgAdmin) |
| Pacote raiz | `com.github.api_abastecefacil` |

Integrações externas: **ViaCEP** (consulta de endereço por CEP) e
**Nominatim/OpenStreetMap** (geocodificação de endereço para coordenadas).

### Frontend

| Item | Valor |
|---|---|
| Stack | Vue 3.5 (Composition API, `<script setup>`), Vite 7 |
| UI | Vuetify 3.9 |
| Ícones | Material Design Icons (`@mdi/font`) |
| Estado | Pinia 3 |
| HTTP | Axios |
| Mapa | Leaflet + leaflet.markercluster + OpenStreetMap |
| Fonte | Manrope |
| Porta (dev) | 5173 |
| Deploy | Render, via GitHub Actions em push na `main` |

---

## 3. Como executar

O backend sobe por Docker; o frontend roda local.

```bash
# 1. Backend (API + banco + pgAdmin)
cd abastece-facil-api
docker compose up -d

# 2. Frontend
cd front-abastece-facil-main/front-abastece-facil-main
npm run dev
```

| Serviço | URL |
|---|---|
| Aplicação | http://localhost:5173 |
| API | http://localhost:8081 |
| pgAdmin | http://localhost:5050 — `admin@abastecefacil.com` / `admin` |
| PostgreSQL | localhost:5432 — `abastecefacil_user` / `abastecefacil_password` / db `abastecefacil` |

Observações operacionais:

- O **Vite leva ~20 segundos** para ficar pronto. Só abra o navegador depois que a
  linha `➜ Local: http://localhost:5173/` aparecer.
- `http://localhost:8081` sozinho retorna erro: a raiz é bloqueada pelo Spring
  Security. Endpoints válidos começam em `/api/`.
- **Nunca use `docker compose down -v`** em desenvolvimento: o `-v` apaga o volume
  e o banco volta ao dump inicial, perdendo os cadastros feitos pela interface.
- O `docker-compose.yml` monta `./init-scripts`, executado apenas na **primeira**
  inicialização (volume vazio). É onde fica a carga inicial derivada de `dump.sql`.
- Rodar o backend fora do Docker exige **JDK 21**. Um terminal com ambiente conda
  ativo, ou aberto antes da instalação do JDK, pode resolver um Java diferente do
  configurado em `JAVA_HOME`. Confirme com `./mvnw -version` antes de investigar
  erro de compilação, e observe também o `platform encoding`, que precisa ser UTF-8
  por causa dos acentos nas mensagens em português.
- Os containers têm nome fixo (`abastecefacil-api`, `abastecefacil-db`,
  `abastecefacil-pgadmin`). `docker compose down` só remove os containers do projeto
  onde é executado, então containers antigos de outra pasta causam conflito de nome.
  Nesse caso, `docker rm -f <nome>` antes de subir.

---

## 4. Modelo de dados

Quatro tabelas. O schema é versionado por **Flyway**, com as migrations em
`src/main/resources/db/migration/`. `ddl-auto: validate`: o Hibernate apenas confere
o schema contra as entidades e recusa subir se houver divergência, sem nunca
alterá-lo.

**Toda alteração de schema passa por migration.** A `V1__baseline.sql` reproduz o
schema original, derivada de `pg_dump --schema-only`, preservando os nomes de
constraint gerados pelo Hibernate (`uk6dotkott2kjsp8vw4d0m25fb7` em `users.email`,
`uk8atkmpnk417qqgkf1r1gw7ujk` em `gas_stations.cnpj`, `ukdbc9idlyetvssufb2vxicvb87`
em `cars.license_plate`). A partir da V2, usar nomes legíveis.

**Pendência conhecida: duas fontes de verdade para o schema.**
`init-scripts/01-dump.sql` também cria as tabelas, e roda na inicialização do
Postgres, antes de a aplicação subir. Em volume novo, o Flyway encontra o banco já
populado, faz baseline e a V1 nunca executa. As duas fontes são idênticas hoje,
verificado por diff, mas vão divergir a partir da V2.

A correção é mover o seed para dentro do Flyway e esvaziar o `init-scripts`.
Reduzir o init-script a apenas `INSERT` **não funciona**: as tabelas ainda não
existiriam no momento em que ele roda. Resolver antes do deploy em produção, onde
não haverá init-script e a V1 vai precisar rodar de verdade pela primeira vez.

### `users`
`id`, `name`, `email` (único), `password` (BCrypt), `is_active`, `created_at`, `updated_at`

### `gas_stations`
`id`, `name`, `fantasy_name`, `cnpj` (único), `latitude` (10,8), `longitude` (11,8),
`cep`, `district`, `address`, `state`, `city`, `phone`, `business_hours`, `is_active`,
`created_at`, `updated_at`

### `cars`
`id`, `license_plate` (único), `model`, `active`, `created_at`, `updated_at`

### `incidents`
`id`, `car_plate`, `user_name`, `occurrence_date` (date), `title`, `description`,
`created_at`

Não há relacionamento JPA entre `incidents` e `cars`: a ligação é pela **string da
placa**, validada em tempo de criação.

---

## 5. API

Prefixo geral: `/api`.

### Regra de autorização (`SecurityConfig`)

```
/api/auth/**    → público
/api/public/**  → público
qualquer outro  → exige JWT válido
```

Sessão **stateless**, CSRF desabilitado. O token vai no header
`Authorization: Bearer <token>` e expira em **86400000 ms (24 horas)**.

### Endpoints

| Método | Rota | Acesso |
|---|---|---|
| POST | `/api/auth/register` | público |
| POST | `/api/auth/login` | público |
| GET | `/api/public/gas-stations/filter` | público |
| GET | `/api/public/gas-stations/{id}` | público |
| POST | `/api/public/incident` | público |
| POST | `/api/gas-stations` | autenticado |
| PUT | `/api/gas-stations/{id}` | autenticado |
| DELETE | `/api/gas-stations/{id}` | autenticado |
| GET | `/api/cars/filter` | autenticado |
| GET | `/api/cars/{carId}` | autenticado |
| POST | `/api/cars` | autenticado |
| PATCH | `/api/cars/{carId}` | autenticado |
| DELETE | `/api/cars/{carId}` | autenticado |
| GET | `/api/incidents` | autenticado |
| GET | `/api/incidents/{id}` | autenticado |
| GET | `/api/incidents/dashboard` | autenticado |
| PATCH | `/api/incidents/{id}` | autenticado |
| GET | `/api/users` | autenticado |
| GET | `/api/users/{userId}` | autenticado |
| GET | `/api/users/dashboard` | autenticado |
| POST | `/api/users` | autenticado |
| PATCH | `/api/users/{userId}` | autenticado |
| DELETE | `/api/users/{userId}` | autenticado |
| GET | `/api/cep/info?cep=` | autenticado |

**Note a assimetria:** consultar postos e criar ocorrência são públicos; todo o
resto exige token. É proposital — o usuário final não faz login.

### Paginação

Endpoints de filtro retornam `Page<T>` do Spring Data, com `size = 10` por padrão.
O corpo traz `content`, `number`, `totalPages`, `totalElements`.

Parâmetros de filtro:

- `gas-stations/filter` → `search`, `active`
- `cars/filter` → `search`, `active`
- `incidents` → `carPlate`, `title`, `userName`, `occurrenceDate` (ISO)
- `users` → `active` (obrigatório), `name`

### Formatos relevantes

`GasStationResponse` devolve **latitude e longitude como String**, não número.

`CreateIncidentRequest`:
```json
{
  "licensePlate": "ABC1234",
  "userName": "Fulano",
  "occurrenceDate": "2026-08-18",
  "title": "Combustível Abaixo de Meio Tanque",
  "description": "texto livre"
}
```

`AuthResponse`: `{ token, type, message }`.

---

## 6. Regras de negócio que não são óbvias pelo código

1. **Criar ocorrência exige que o veículo exista.** `IncidentService` busca o carro
   por placa e lança `NotFoundException` se não achar. Uma placa não cadastrada
   resulta em erro, mesmo o endpoint sendo público.
2. **Criar posto dispara geocodificação.** `GasStationService` monta o endereço e
   consulta o Nominatim para obter latitude/longitude. Se não encontrar, lança
   `CoordinatesNotFoundException`. Isso significa que **cadastrar posto depende de
   internet** e está sujeito ao rate limit do Nominatim.
3. **CNPJ é único** e verificado tanto na criação quanto na atualização.
4. **Login rejeita usuário inativo** com mensagem específica, distinta de
   credencial inválida.
5. Exclusões são **lógicas** (`is_active` / `active` em `false`), não físicas. Há
   exceções dedicadas para "já excluído" (`UserAlreadyDeletedException`,
   `CarAlreadyDeletedException`).

---

## 7. Frontend — estrutura

```
src/
├── layouts/     AdminLayout.vue, DefaultLayout.vue
├── views/       admin/ (7)  user/ (4)  public/Login.vue
├── components/  admin/ (10)  user/ (8)  app/ (7 compartilhados)
├── services/    apiClient.js + auth/occurrence/station/user/vehicle
├── stores/      auth.js (Pinia)
├── router/      index.js
└── assets/      main.css, base.css, logos
```

### Rotas

| Grupo | Layout | Rotas |
|---|---|---|
| público | nenhum | `/login` |
| usuário | `DefaultLayout` | `/user/dashboardUser`, `/user/postos`, `/user/mapUser`, `/user/occurrencesUser`, `/user/HelpCenter` |
| admin | `AdminLayout` | `/admin/dashboard`, `/admin/station`, `/admin/map`, `/admin/vehicle`, `/admin/occurrences`, `/admin/user` |

A raiz `/` redireciona para `/user/dashboardUser`.

O guard em `router/index.js` protege apenas `/admin/*`, e a checagem é
`!!store.token` — ou seja, **qualquer string em `localStorage.token` passa pelo
guard**. A proteção real vem do backend, que rejeita o token inválido com 403.

`MapUser` e `MapAdmin` apontam para **a mesma view**, `views/admin/StationMap.vue`.

### Camada HTTP

`services/apiClient.js` exporta duas instâncias Axios:

- `apiPublic` — sem token
- `apiPrivate` — injeta `Authorization: Bearer` a partir de `localStorage.token`

Ambas usam `baseURL = import.meta.env.VITE_API_BASE_URL`.

### Variáveis de ambiente

Este ponto é fonte recorrente de erro. O `.env.example` e o `README` documentam
apenas `VITE_API_PROXY_TARGET`, mas o código lê **`VITE_API_BASE_URL`**.

```env
VITE_API_PROXY_TARGET="http://localhost:8081"
VITE_API_BASE_URL=
```

`VITE_API_BASE_URL` deve ficar **vazia** em desenvolvimento. Como todos os serviços
chamam caminhos iniciados por `/api/...`, as requisições saem relativas e são
capturadas pelo proxy do Vite (`vite.config.js`), que as encaminha ao backend.
Preenchê-la com `http://localhost:8081` faz as chamadas contornarem o proxy e
resultarem em erro de CORS.

### Mapa

`components/app/Map.vue` inicializa o Leaflet, busca os postos com
`apiPublic.get('/api/public/gas-stations/filter?page=0&size=100&active=true')` e
monta cada popup criando **uma aplicação Vue por marcador**
(`createApp(PopupStation)`).

`views/admin/StationMap.vue` envolve o mapa e cuida da geolocalização do navegador.
O componente `Map` só é montado quando a geolocalização retorna com sucesso
(`v-if="loadedMarker"`) — **negar a permissão de localização impede o mapa de
renderizar**, exibindo um aviso no lugar.

---

## 8. Design system do frontend

Definido em `src/assets/main.css` (tokens CSS) e `src/plugins/vuetify.js`
(tema e defaults de componentes).

| Token | Valor | Uso |
|---|---|---|
| `--color-primary` | `#16496E` | marca e ações principais |
| `--color-primary-soft` | `#E8EFF5` | fundo de ícones |
| `--color-background` | `#F7F8FA` | fundo de página |
| `--color-surface` | `#FFFFFF` | cards |
| `--color-border` | `#E3E7EC` | bordas de card |
| `--color-border-strong` | `#CFD6DE` | bordas de campo |
| `--color-text` | `#1F2933` | texto principal |
| `--color-text-muted` | `#6B7785` | texto secundário |
| `--color-warning` | `#ED6C02` | alerta |
| `--radius-sm/md/lg` | `6 / 10 / 14px` | raios |
| `--shadow-sm/md` | sombras discretas | elevação |
| `--transition` | `180ms ease` | microinterações |

`success` e `error` vêm do tema do Vuetify, não têm token CSS próprio.

Diretrizes visuais em vigor: identidade azul, fundo claro, cards brancos com borda
sutil, vermelho reservado a erro e alerta, sem gradiente/neon/glassmorphism,
estética de sistema corporativo. Hover destaca por borda, não por deslocamento.

Detalhe da barra lateral: item ativo recebe fundo levemente mais claro **e uma
barra branca de 3px à esquerda**.

---

## 9. Armadilhas conhecidas

Problemas reais já encontrados. Consultar antes de investigar comportamento estranho.

### CSS e Vuetify

1. **Ordem de injeção de CSS no dev server.** O CSS do Vuetify é injetado *depois*
   do `main.css`. Regras globais de mesma especificidade perdem a cascata. Por isso
   as regras globais do projeto são prefixadas com `.v-application`.
2. **Nunca definir `elevation` nos defaults do `VCard`** em `vuetify.js`. A classe
   `.elevation-N` do Vuetify usa `!important` e anula o `box-shadow` do CSS.
3. **Classes utilitárias de cor do Vuetify** (`.bg-blue`, `.bg-red`…) são
   `!important` e vencem estilos escopados de mesmo nome.
4. **Ao estilizar bordas de campo, preservar o estado de erro** com
   `:not(.v-field--error)`, senão o vermelho de validação é sobrescrito.
5. Vuetify 3 usa `.v-pagination__item--is-active`, não `--active`.

### Estrutura

6. **`src/assets/base.css` nunca é importado.** Resíduo do scaffold do Vue; editá-lo
   não tem efeito. O arquivo carregado é `main.css`.
7. **`AdminLayout.vue` e `DefaultLayout.vue` são ~95% duplicados**, divergindo apenas
   no array `menuItems` e no botão superior direito. Alterações no shell precisam ser
   aplicadas nos dois.
8. O botão "Admin" do `DefaultLayout` chama `handleLogout` — o rótulo não descreve a
   ação. Comportamento existente, preservado deliberadamente.

### Ambiente

9. **Docker Desktop no Windows** pode falhar ao iniciar por sockets Unix órfãos em
   `%LOCALAPPDATA%\Docker\run\` e `%LOCALAPPDATA%\docker-secrets-engine\`. O Windows
   não consegue apagá-los (`Não é possível o acesso ao arquivo pelo sistema`);
   remover via WSL: `wsl -d Ubuntu -e rm -f /mnt/c/Users/<user>/AppData/Local/...`.
10. Os containers usam `restart: unless-stopped` e voltam sozinhos após reinício.
    O servidor Vite **não** — precisa ser reiniciado manualmente.
11. Erro 500 no proxy do Vite com `ECONNREFUSED` significa backend fora do ar ou
    ainda inicializando (Spring Boot leva ~25s).

---

## 10. Estado do projeto e convenções

### Qualidade

- **49 testes unitários no backend, todos passando.** Cobrem `AuthService`,
  `UserService`, `JwtService`, `CarService`, `GasStationService`, `IncidentService`,
  `OpenStreetMapService`, `ViaCepService` e o handler global de exceções. São testes
  com mock, não sobem banco nem contexto Spring completo. Rodar `./mvnw clean test`
  ao final de qualquer alteração no backend: a contagem tem que continuar 49. O
  frontend não tem testes.
- `npx eslint src` reporta **15 erros pré-existentes**: nomes de componente de
  palavra única (`Map`, `Login`, `Reports`, `Occurrences`) e variáveis não usadas
  (`unwatchMobile`, `response`, `error`, `deletarPosto`, `apiPublic`). Não são
  regressões; usar essa contagem como linha de base.
- CI do frontend: build em PR e deploy no Render em push na `main`.

### Ao trabalhar neste projeto

- **Não alterar contratos da API** sem necessidade explícita: endpoints, métodos,
  payloads e formatos de resposta são consumidos pelo frontend.
- Preferir alterações de CSS e template a mudanças de lógica quando o objetivo for
  visual.
- Preservar as convenções existentes: nomes em português no domínio, `<script setup>`,
  organização de pastas atual.
- Não adicionar dependências sem justificativa — a stack é intencionalmente enxuta.
- O idioma do código e das mensagens de interface é **português do Brasil**.

### Credenciais de teste

O `init-scripts/01-dump.sql` popula a tabela `users` na primeira subida. Pelo menos
dois desses registros (`pedro@email.com`, `rafaela.mendes@email.com`) têm **hashes
BCrypt cuja origem não está documentada**, então não é possível logar com eles.

Para acessar a área administrativa hoje, crie um usuário novo via
`POST /api/auth/register`, que ainda é público, ou gere um hash e atualize o banco
diretamente. Esse endpoint será removido junto com a onda de controle de acesso, e
o administrador inicial passará a ser criado por variável de ambiente.

> **A verificar:** o banco subiu com 3 usuários, não 2. Rodar
> `SELECT id, name, email, is_active FROM users ORDER BY id;` e documentar quem é o
> terceiro. Se a senha for conhecida por alguém da equipe, resolve o acesso
> administrativo no curto prazo.

---

## 11. Documentos relacionados no repositório

| Arquivo | Conteúdo |
|---|---|
| `front-.../REFATORACAO-VISUAL.md` | Detalhamento da refatoração visual do frontend |
| `abastece-facil-api/README.md` | Comandos de Docker e configuração do banco |
| `front-.../README.md` | Instalação e scripts do frontend |

---

## 12. Manutenção deste documento

Ao alterar modelo de dados, contrato de API ou configuração, atualize as seções
correspondentes **no mesmo commit**. Um contexto desatualizado é pior que nenhum: o
assistente tenta reconciliar código novo com documentação velha.