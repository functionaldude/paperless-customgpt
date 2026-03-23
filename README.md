# paperless-customGPT

Spring Boot+Kotlin service that synchronizes the Paperless database into a pgvector-backed RAG and exposes REST
endpoints for a custom GPT agent.

It now exposes a streamable HTTP MCP endpoint at `/mcp` and includes an embedded OAuth 2.1 authorization server so a
ChatGPT app can be connected by URL only.

## Runtime configuration

Set the following environment variables for both local runs and container deployments:

- `PAPERLESS_DB_URL`, `PAPERLESS_DB_USER`, `PAPERLESS_DB_PASSWORD` – connection details for the shared
  Paperless/Postgres instance.
- `PAPERLESS_BASE_URL` – public URL of the Paperless UI, used to expose document source links in API responses.
- `APP_PUBLIC_URL` – externally reachable HTTPS base URL for this service (used as OAuth issuer and metadata base).
- `APP_AUTH_LOGIN_MODE` – login mode for interactive authorization (`LOCAL` or `AUTHENTIK`).
- `APP_LOCAL_USERNAME`, `APP_LOCAL_PASSWORD` – local single-user login credentials used when
  `APP_AUTH_LOGIN_MODE=LOCAL`.
- `APP_OAUTH_KEY_ID`, `APP_OAUTH_PRIVATE_KEY_PEM`, `APP_OAUTH_PUBLIC_KEY_PEM` – signing key settings for issued JWTs.
  If no key pair is supplied, an ephemeral RSA key is generated on startup.
- `AUTHENTIK_CLIENT_ID`, `AUTHENTIK_CLIENT_SECRET`, `AUTHENTIK_ISSUER_URI` – only required when
  `APP_AUTH_LOGIN_MODE=AUTHENTIK` (interactive login via Authentik, tokens still issued by this app).
- `OPENAI_BASE_URL`, `OPENAI_MODEL_NAME`, `OPENAI_API_KEY` – overrides for the LangChain4j/OpenAI embedding client. By
  default the service points to `http://localhost:1234/v1`, uses the `text-embedding-multilingual-e5-base` model, and
  falls back to the dummy key `lm-studio` for LM Studio compatibility.
- `OPENAI_FORCE_HTTP1` – set to `true` (default) to force HTTP/1.1 for providers such as LM Studio; set to `false` to
  allow HTTP/2.
- `RAG_EMBEDDING_DIMENSIONS` – target dimension count stored in pgvector and used during similarity search. Defaults to
  `1536`; model outputs are truncated/padded to this size before insert/query.
- `RAG_HNSW_EF_SEARCH` – query-time HNSW recall/speed knob (higher = better recall, slower). Defaults to `400`.
- `RAG_SPLITTER_CHUNK_TOKENS` – token-based chunk size for document splitting. Defaults to `512`.
- `RAG_SPLITTER_OVERLAP_TOKENS` – token overlap between chunks. Defaults to `128`.
- `RAG_SPLITTER_ESTIMATED_CHARS_PER_TOKEN` – heuristic token estimation factor for splitting (lower = more estimated
  tokens per text). Defaults to `4.0`.
- Any additional secrets required by other LLM providers can be added to the environment; the application reads them
  through Spring configuration.

### ChatGPT connector URL

- MCP endpoint: `https://<your-host>/mcp`
- Transport: streamable HTTP
- Auth: OAuth 2.1 with dynamic client registration + PKCE S256

When creating the ChatGPT app/connector, use only the MCP URL above. OAuth discovery and registration metadata are
exposed automatically from this service.

Spring Boot packages `src/main/resources/application.yaml` into the executable jar, so the container image only relies
on
environment variables for deployment-time customization. Legacy stacks that still set `SPRING_CONFIG_IMPORT` with
semicolon-separated locations are automatically normalized during startup.

## Container image via `bootBuildImage`

Use the Spring Boot Gradle plugin to build an OCI image with Cloud Native Buildpacks. The default image name is
`paperless-customgpt:<project-version>`, but you can override it with `-PimageName=` or by exporting `IMAGE_NAME`.

```bash
./gradlew bootBuildImage -PimageName=ghcr.io/<owner>/<repo>:local
```

The image already includes Java 21 (via `BP_JVM_VERSION=21.*`) and the packaged `application.yaml`. Run it locally with
the required environment variables:

```bash
docker run --rm -p 8080:8080 \
  -e PAPERLESS_DB_URL=jdbc:postgresql://postgres/paperless \
  -e PAPERLESS_DB_USER=paperless \
  -e PAPERLESS_DB_PASSWORD=paperless \
  -e APP_PUBLIC_URL=https://paperless-gpt.example.com \
  -e APP_AUTH_LOGIN_MODE=LOCAL \
  -e APP_LOCAL_USERNAME=paperless \
  -e APP_LOCAL_PASSWORD=change-me \
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
  --env PAPERLESS_DB_URL=jdbc:postgresql://postgres/paperless \
  --env PAPERLESS_DB_USER=paperless \
  --env PAPERLESS_DB_PASSWORD=paperless \
  --env APP_PUBLIC_URL=https://paperless-gpt.example.com \
  --env APP_AUTH_LOGIN_MODE=LOCAL \
  --env APP_LOCAL_USERNAME=paperless \
  --env APP_LOCAL_PASSWORD=change-me \
  --publish published=8080,target=8080 \
  ghcr.io/<owner>/<repo>:<tag>
```

Replace `<owner>/<repo>` and `<tag>` with the coordinates reported by the GitHub Actions workflow (see below).

## GitHub Actions: build and publish to GHCR

The workflow defined in `.github/workflows/docker-image.yml` compiles the application, runs `bootBuildImage`, and pushes
the resulting image to the GitHub Container Registry (GHCR).

- **Triggers:** every push to `main` and manual `workflow_dispatch`.
- **Image name:** `ghcr.io/${{ github.repository }}`.
- **Tags:** managed automatically by `docker/metadata-action` (branch names, SHA, semver tags when applicable).
- **Runner:** executes on the repository's self-hosted runner (update the `runs-on` stanza if you need extra labels).

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

You can still run the service directly with Gradle after exporting the required environment variables:

```bash
./gradlew bootRun
```

This uses the same configuration as the container build and is helpful when iterating on endpoints or RAG logic.
