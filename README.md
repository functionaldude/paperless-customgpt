# paperless-customGPT

Spring Boot and Kotlin service that synchronizes the Paperless database into a pgvector-backed RAG and exposes tools
through a streamable HTTP MCP endpoint.

It exposes a streamable HTTP MCP endpoint at `/mcp`. ChatGPT connects with a user-defined OAuth client registered in an
OIDC-compatible identity provider; this service only acts as an OAuth2 resource server and validates JWT bearer tokens.

## Runtime configuration

The database variables are required at runtime. All others have local-development defaults.

| Variable                                 | Default                                             | Purpose                                                                                                |
|------------------------------------------|-----------------------------------------------------|--------------------------------------------------------------------------------------------------------|
| `PAPERLESS_DB_URL`                       | Required                                            | JDBC URL for the shared Paperless/Postgres database.                                                   |
| `PAPERLESS_DB_USER`                      | Required                                            | Database username.                                                                                     |
| `PAPERLESS_DB_PASSWORD`                  | Required                                            | Database password.                                                                                     |
| `PAPERLESS_BASE_URL`                     | `http://localhost:8000`                             | Public Paperless UI URL used for document source links.                                                |
| `PAPERLESS_MEDIA_ROOT`                   | `/usr/src/paperless/media`                          | Paperless media directory, mounted read-only, used by the raw-document MCP tool.                       |
| `APP_PUBLIC_URL`                         | `http://localhost:8080`                             | Externally reachable service URL used in MCP protected-resource metadata.                              |
| `OIDC_ISSUER_URI`                        | `http://localhost:9000/application/o/paperless/`    | Issuer that signs ChatGPT access tokens. The JWKS URL is derived as `<issuer>/jwks/`.                  |
| `OIDC_AUDIENCE`                          | `<APP_PUBLIC_URL>/mcp`                              | Required JWT audience. Override when the provider emits another audience, such as the OAuth client ID. |
| `OIDC_SCOPES`                            | `openid,profile,email,paperless_gpt,offline_access` | Comma-separated scopes advertised in protected-resource and MCP tool metadata.                         |
| `OIDC_REQUIRED_SCOPE`                    | `paperless_gpt`                                     | Scope required on every MCP request; it must be included in `OIDC_SCOPES`.                             |
| `MCP_TOOL_LOGGING`                       | `OFF`                                               | MCP tool call logging: `OFF`, `INFO` for tool names, or `DEBUG` for tool names and input parameters.   |
| `OPENAI_BASE_URL`                        | `http://localhost:1234/v1`                          | OpenAI-compatible embedding API base URL.                                                              |
| `OPENAI_MODEL_NAME`                      | `text-embedding-multilingual-e5-base`               | Embedding model name.                                                                                  |
| `OPENAI_API_KEY`                         | `lm-studio`                                         | Embedding API key; the default is a placeholder for LM Studio.                                         |
| `OPENAI_FORCE_HTTP1`                     | `false`                                             | Set to `true` for providers that require HTTP/1.1.                                                     |
| `RAG_EMBEDDING_DIMENSIONS`               | `1536`                                              | Vector dimensions stored in pgvector; model output is truncated or padded to this size.                |
| `RAG_HNSW_EF_SEARCH`                     | `400`                                               | HNSW query-time recall/speed setting.                                                                  |
| `RAG_QWEN3_QUERY_INSTRUCTION`            | Retrieve relevant passages from personal documents. | Task instruction added only to Qwen3 Embedding search queries; overrides the built-in English default. |
| `RAG_SPLITTER_CHUNK_TOKENS`              | `512`                                               | Token-based document chunk size.                                                                       |
| `RAG_SPLITTER_OVERLAP_TOKENS`            | `128`                                               | Token overlap between chunks.                                                                          |
| `RAG_SPLITTER_ESTIMATED_CHARS_PER_TOKEN` | `4.0`                                               | Character-to-token estimation factor used while splitting documents.                                   |

Database connections use `search_path=paperless_rag,public`, making both Flyway-managed RAG tables and existing
Paperless tables available.

### ChatGPT connector URL

- MCP endpoint: `https://<your-host>/mcp`
- Transport: streamable HTTP
- Auth: OAuth 2.1 authorization code with PKCE, using a user-defined OAuth client registered in your OIDC provider

When creating the ChatGPT app/connector, use the MCP URL above and choose **User-Defined OAuth Client**. Paste the
client ID and optional client secret from a dedicated OAuth2/OIDC client. The ChatGPT callback URL shown in the
connector UI must be added to that client's allowed redirect URIs.

This service advertises the configured OIDC issuer through MCP protected-resource metadata. It does not expose dynamic
client
registration, client credentials, or token endpoint auth method metadata.

### ChatGPT deep research compatibility

The MCP endpoint exposes two read-only tools tailored to ChatGPT deep research:

- `search(query)` returns up to ten distinct Paperless documents as citation-ready `id`, `title`, and `url` results.
- `fetch(id)` returns one document's full extracted `text`, citation-ready `url`, and metadata.

Existing `searchRag`, `findDocumentsByIds`, and filtered lookup tools remain available for direct tool use.
`listDocuments`
is paginated: it defaults to 50 documents, accepts `limit` and `offset`, caps a page at 100 documents, and returns
`nextOffset` when another page exists.

Use `getRawDocuments(ids)` to retrieve one or more documents' original binary content through `tools/call` as
base64-encoded
embedded resource with its MIME type. All Paperless document formats are eligible; documents without a stored MIME type
are sent as `application/octet-stream`. Mount the Paperless media directory read-only at `PAPERLESS_MEDIA_ROOT`;
original files are preferred, with Paperless archive files used as a fallback.

For ChatGPT, deploy the endpoint at a remote HTTPS URL or connect a private deployment through OpenAI Secure MCP Tunnel.
Enable Developer mode, add the `/mcp` URL as an OAuth-protected app, and grant the `paperless_gpt` scope. Ensure the
identity provider supports authorization-code flow with PKCE and issues refresh tokens for `offline_access` so longer
research tasks retain access.

Spring Boot packages `src/main/resources/application.yaml` into the executable jar, so deployment-time configuration is
provided through the environment variables above.

## Container image via `bootBuildImage`

Use the Spring Boot Gradle plugin to build an OCI image with Cloud Native Buildpacks. The default image name is
`paperless-customgpt:<project-version>`; override it with the task's `--imageName` option.

```bash
./gradlew bootBuildImage --imageName ghcr.io/<owner>/<repo>:local
```

The image includes the Java 25 runtime required by the project and the packaged `application.yaml`. Run it locally with
the required environment variables:

```bash
docker run --rm -p 8080:8080 \
  --mount type=bind,source=/path/to/paperless/media,target=/usr/src/paperless/media,readonly \
  -e PAPERLESS_DB_URL=jdbc:postgresql://postgres/paperless \
  -e PAPERLESS_DB_USER=paperless \
  -e PAPERLESS_DB_PASSWORD=paperless \
  -e PAPERLESS_BASE_URL=https://paperless.example.com \
  -e PAPERLESS_MEDIA_ROOT=/usr/src/paperless/media \
  -e APP_PUBLIC_URL=https://paperless-gpt.example.com \
  -e OIDC_ISSUER_URI=https://idp.example.com/application/o/paperless/ \
  -e OIDC_AUDIENCE=https://paperless-gpt.example.com/mcp \
  ghcr.io/<owner>/<repo>:local
```

Push the resulting tag with the standard Docker CLI:

```bash
docker push ghcr.io/<owner>/<repo>:local
```

### Deploy to Docker Swarm

Once the `bootBuildImage` artefact is in a registry (see below), you can deploy it via `docker stack deploy` or `docker
service create`:

```bash
docker service create --name paperless-gpt \
  --with-registry-auth \
  --mount type=bind,source=/path/to/paperless/media,target=/usr/src/paperless/media,readonly \
  --env PAPERLESS_DB_URL=jdbc:postgresql://postgres/paperless \
  --env PAPERLESS_DB_USER=paperless \
  --env PAPERLESS_DB_PASSWORD=paperless \
  --env PAPERLESS_BASE_URL=https://paperless.example.com \
  --env APP_PUBLIC_URL=https://paperless-gpt.example.com \
  --env OIDC_ISSUER_URI=https://idp.example.com/application/o/paperless/ \
  --env OIDC_AUDIENCE=https://paperless-gpt.example.com/mcp \
  --publish published=8080,target=8080 \
  ghcr.io/<owner>/<repo>:<tag>
```

Replace `<owner>/<repo>` and `<tag>` with the coordinates reported by the GitHub Actions workflow (see below).

## GitHub Actions: build and publish to GHCR

The workflow defined in `.github/workflows/docker-image.yml` compiles the application, runs `bootBuildImage`, and pushes
the resulting image to the GitHub Container Registry (GHCR).

- **Triggers:** every push to `master` and manual `workflow_dispatch`.
- **Image name:** `ghcr.io/${{ github.repository }}`.
- **Tags:** managed automatically by `docker/metadata-action` (branch names, SHA, semver tags when applicable).
- **Runner:** executes on the repository's self-hosted runner (update the `runs-on` stanza if you need extra labels).

The image workflow runs unit tests before registry login, image building, or publishing. If tests fail, these steps are
skipped and available test reports are uploaded. This applies to both pushes to `master` and manual runs.

During the build, Gradle automatically uses the same local placeholder connection details as local development, so no
additional environment variables are required for compilation. Provide real credentials only when executing jOOQ code
generation tasks or when running the application.

### Pulling the published image

1. Authenticate against GHCR (a personal access token with `read:packages` scope works best):

   ```bash
   echo <PAT> | docker login ghcr.io -u <github-username> --password-stdin
   ```

2. Pull and run the tag you need:

   ```bash
   docker pull ghcr.io/<owner>/<repo>:<tag>
   docker run --rm ghcr.io/<owner>/<repo>:<tag>
   ```

   Remember to pass the runtime environment variables described earlier; Swarm/stack deployments can inject them through
   secrets/configs as needed.

## Local development without Docker

Use a Java 25 JDK and the checked-in Gradle 9.2.1 wrapper. The default checks run isolated unit tests and package the
application without external services:

```bash
./gradlew --no-daemon test bootJar
```

For a clean build, add `clean`. After dependencies are cached, add `--offline`. Compilation uses the checked-in jOOQ
sources; it does not introspect the database or require Docker.

### Integration tests

Spring tests tagged `integration` run only through this explicit command:

```bash
./gradlew --no-daemon integrationTest
```

This task uses the same test sources as `test` but selects only integration tests. It is not part of `check` or `build`.
Only `integrationTest` loads `.env` values into its test JVM, and shell environment variables take precedence.
Gradle's jOOQ code generation configuration continues to support `.env` separately.

Configure the database variables and embedding provider described above before running integration tests. They expect
existing Paperless document 233 and its metadata; the RAG tests ingest it and write to the configured database. The Spring
contexts also enable scheduled ingestion and cleanup workers, so use a suitable test instance with the expected fixtures.

### Codex Cloud

Create an environment for this repository using the
[Codex Cloud environment workflow](https://learn.chatgpt.com/docs/environments/cloud-environments):

1. Ask the environment setup to install **Temurin Java 25** and configure `JAVA_HOME` or `PATH` for task shells.
2. Use `bash .codex/setup.sh` as the **Install script**. It checks Java 25, runs unit tests, and builds the executable jar,
   populating the Gradle dependency cache. It can also be rerun from a task.
3. Enable internet access with the **Package managers** preset, which includes Maven, Gradle, and GitHub download hosts.
   If a download redirects elsewhere, allow the hostname reported by setup and retry.
4. Review the successful setup, save, and **Publish** the environment. Start a new task to verify it.

No start skill, database credentials, embedding API key, or running services are needed for the default checks. Runtime
deployment and integration tests still require their existing configuration. Repository dependency changes may require
rerunning the install command or republishing the environment; a populated cache supports offline checks for the current
dependencies.

### Pull request validation

`.github/workflows/unit-tests.yml` runs `./gradlew --no-daemon test bootJar` on a self-hosted runner with Temurin 25 and Gradle
caching only for pull requests targeting `master`. Failed runs upload available test reports. Pushes to `master` run unit
tests in the image publishing workflow described above, so each push runs tests in only one workflow.

### Running the service

You can still run the service directly with Gradle after exporting the required environment variables:

```bash
./gradlew bootRun
```

This uses the same configuration as the container build and is helpful when iterating on MCP tools or RAG logic.
