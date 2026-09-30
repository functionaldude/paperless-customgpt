## What this repo is

This app exposes some basic REST endpoints, so a custom ChatGPT agent can interact with it.
At the same time it generates and stores documents in a RAG (postgres + pgvector) setup, so that the agent can use them
as context for its answers.
The documents are coming from an existing paperless instance, it is not part of this project. This project is only a
complimentary service, but it is attached to the same DB as paperless.

## Setup

- Basic kotlin+gradle project
- Uses spring for REST endpoints
- Uses jOOQ for DB access
- Uses langchain4j for RAG functionality

## Build and validation

- Use a Java 25 JDK and the checked-in Gradle 9.2.1 wrapper (`./gradlew`).
- Run `./gradlew --no-daemon test bootJar` for unit tests and executable jar packaging.
- Run `./gradlew --no-daemon clean test bootJar` for a clean validation.
- Default `test`, `check`, and `build` run isolated unit tests. They require no Paperless database,
  embedding service, OIDC provider, media mount, or Docker.
- Unit tests do not load `.env`. After dependencies have been downloaded, add `--offline` to validate without network access.
- In Codex Cloud, prepare Temurin 25 and run `bash .codex/setup.sh` as the install command.
  See the README for environment configuration; no services need to be started for the default checks.

## Integration tests and generated sources

- Run `./gradlew --no-daemon integrationTest` explicitly when integration validation is requested and services are available.
  Tests tagged `integration` are excluded from the default checks.
- Integration tests load `.env` values only when the corresponding environment variable is unset. Configure
  `PAPERLESS_DB_URL`, `PAPERLESS_DB_USER`, and `PAPERLESS_DB_PASSWORD` and an OpenAI-compatible embedding service.
- These tests depend on existing Paperless document 233 and its metadata. RAG tests ingest that document and write to
  the configured database; Spring contexts also enable the ingestion and cleanup workers. Use a suitable test instance.
- Compile against the checked-in jOOQ sources in `src/main/jooq`. Do not hand-edit generated sources or regenerate
  them during ordinary builds. Run `./gradlew jooqCodegen` only for an intentional schema update with database access.
