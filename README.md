# ragify-api-java

Java port of the Ragify HTTP backend ([api-python](../api-python/README.md)). Same endpoints, same data model, same service boundary: it owns auth, workspaces, uploads and chat sessions in PostgreSQL and delegates all AI work (ingestion, retrieval, generation) to the `rag` gRPC server. There is no AI/ML code in this service.

## Stack

- Java 21, Gradle wrapper
- Spring Boot 4.1, Spring Web MVC
- Spring Data JPA + Hibernate 7, PostgreSQL (JSONB via Jackson 3 format mapper)
- gRPC client (`grpc-java`) via `ragify.ragify_client`
- JWT auth (jjwt) + bcrypt (spring-security-crypto)

## Run

```bash
./gradlew bootRun            # compiles + runs on :8000
PORT=8001 ./gradlew bootRun  # custom port
```

Requires PostgreSQL (repo root `make infra-up`) and the rag gRPC server on `RAGIFY_GRPC_ENDPOINT` (default `localhost:50051`) for workspace/upload/query features.

The schema is created automatically at startup from `src/main/resources/schema.sql` (`spring.sql.init.mode=always`) and mirrors api-python's Alembic migration.

## Build / test

```bash
./gradlew test             # unit tests (JUnit 5 + Mockito)
./gradlew integrationTest  # H2-backed repository and HTTP integration tests
./gradlew check            # runs both suites and generates coverage
```

Integration tests use an in-memory H2 database in PostgreSQL compatibility mode, so they do not
require the Docker infrastructure or a running gRPC server. JaCoCo writes HTML and XML coverage
reports to `build/reports/jacoco/test/`.

## Endpoints

All routes are prefixed with `/api/v1`; `auth/register` and `auth/login` are public, everything else needs `Authorization: Bearer <jwt>`.

| Method | Path | Description |
| --- | --- | --- |
| `POST` | `/auth/register` | Register a user |
| `POST` | `/auth/login` | Login, returns `access_token` |
| `GET` | `/auth/session` | Current user |
| `GET` / `POST` | `/workspaces/` | List / create workspaces |
| `GET` / `PATCH` / `DELETE` | `/workspaces/{id}` | Get / update / delete a workspace |
| `POST` | `/workspaces/{id}/upload` | Upload files (multipart) |
| `GET` | `/workspaces/{id}/uploads/{status_id}` | Poll upload status |
| `POST` | `/workspaces/{id}/query` | Chat query (proxies `RagService.Query`) |
| `GET` | `/workspaces/{id}/sessions` | List sessions |
| `GET` / `PATCH` / `DELETE` | `/workspaces/{id}/sessions/{session_id}` | Session messages / rename / delete |
| `DELETE` | `/workspaces/{id}/sessions` | Delete all sessions |

## Structure

```text
src/main/java/ragify/
  controllers/   HomeController, AuthController, WorkspaceController
  schemas/       request/response records (AuthSchemas, WorkspaceSchemas)
  models/        JPA entities: User, Workspace, Session, UploadStatus
  repository/    Spring Data repositories
  services/      AuthService, WorkspaceService, SessionService, UploadService, UploadProcessor
  ragify_client/ gRPC transport: GrpcClient, VectorStoreClient, IngestionClient, RagClient
  common/        ApiException, GlobalExceptionHandler, AuthenticatedUser, AuthFilter
  config/        JwtService, WebConfig, PasswordConfig, Jackson3JsonFormatMapper
  utils/         JsonNodes, MessageDicts, WorkspaceUtils
  v1/            committed gRPC stubs (generated from src/main/proto/ragify.proto)
```

## gRPC contract

`src/main/proto/ragify.proto` mirrors `rag/src/grpc/ragify.proto`. The Java stubs are checked into `src/main/java/ragify/v1` so the module is always present without a build step; regenerate them with:

```bash
scripts/gen_grpc.sh
```

## Configuration

Read from the environment / `application.properties`: `PORT`, `DB_URL`, `DB_USER`, `DB_PASSWORD`, `JWT_SECRET`, `JWT_EXPIRES_MINUTES`, `CORS_ORIGINS`, `STORAGE_PATH`, `RAGIFY_GRPC_ENDPOINT`.

## Docker

```bash
docker build -t ragify-api-java .
docker run --rm -p 8000:8000 ragify-api-java
```
