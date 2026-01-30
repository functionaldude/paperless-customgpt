# paperless-customGPT

Spring Boot+Kotlin service that synchronizes the Paperless database into a pgvector-backed RAG and exposes REST
endpoints for a custom GPT agent.

## Runtime configuration

Set the following environment variables for both local runs and container deployments:

- `PAPERLESS_DB_URL`, `PAPERLESS_DB_USER`, `PAPERLESS_DB_PASSWORD` – connection details for the shared
  Paperless/Postgres instance.
- `AUTHENTIK_CLIENT_ID`, `AUTHENTIK_CLIENT_SECRET`, `AUTHENTIK_ISSUER_URI` – Authentik OAuth/OIDC values used by Spring
  Security.
- `OPENAI_BASE_URL`, `OPENAI_MODEL_NAME`, `OPENAI_API_KEY` – overrides for the LangChain4j/OpenAI embedding client. By
  default the service points to `http://localhost:1234/v1`, uses the `text-embedding-multilingual-e5-base` model, and
  falls back to the dummy key `lm-studio` for LM Studio compatibility.
- `OPENAI_FORCE_HTTP1` – set to `true` (default) to force HTTP/1.1 for providers such as LM Studio; set to `false` to
  allow HTTP/2.
- Any additional secrets required by other LLM providers can be added to the environment; the application reads them
  through Spring configuration.

## Docker image

The repository now contains a multi-stage `Dockerfile` that:

1. Builds the executable Spring Boot jar with Gradle/JDK 21.
2. Copies the jar into a small Temurin JRE 21 layer.

The Gradle build falls back to harmless local defaults for the Paperless/Postgres connection, so container builds do not
require real database credentials. Provide real values at runtime (containers or Swarm services) so the application can
talk to the live database.

### Build locally

```bash
docker build -t ghcr.io/<owner>/<repo>:local .

docker run --rm -p 8080:8080 \
  -e PAPERLESS_DB_URL=jdbc:postgresql://postgres/paperless \
  -e PAPERLESS_DB_USER=paperless \
  -e PAPERLESS_DB_PASSWORD=paperless \
  -e AUTHENTIK_CLIENT_ID=... \
  -e AUTHENTIK_CLIENT_SECRET=... \
  -e AUTHENTIK_ISSUER_URI=... \
  ghcr.io/<owner>/<repo>:local
```

### Deploy to Docker Swarm

Once the image is in a registry (see below), you can deploy it via `docker stack deploy` or `docker service create`:

```bash
docker service create --name paperless-gpt \
  --with-registry-auth \
  --env PAPERLESS_DB_URL=jdbc:postgresql://postgres/paperless \
  --env PAPERLESS_DB_USER=paperless \
  --env PAPERLESS_DB_PASSWORD=paperless \
  --env AUTHENTIK_CLIENT_ID=... \
  --env AUTHENTIK_CLIENT_SECRET=... \
  --env AUTHENTIK_ISSUER_URI=... \
  --publish published=8080,target=8080 \
  ghcr.io/<owner>/<repo>:<tag>
```

Replace `<owner>/<repo>` and `<tag>` with the coordinates reported by the GitHub Actions workflow (see below).

## GitHub Actions: build and publish to GHCR

The workflow defined in `.github/workflows/docker-image.yml` compiles the application, builds the Docker image, and
pushes it to the GitHub Container Registry (GHCR).

- **Triggers:** every push to `main` and manual `workflow_dispatch`.
- **Image name:** `ghcr.io/${{ github.repository }}`.
- **Tags:** managed automatically by `docker/metadata-action` (branch names, SHA, semver tags when applicable).
- **Runner:** executes on the repository's self-hosted runner (update the `runs-on` stanza if you need extra labels).

During the build, Gradle automatically uses the same local placeholder connection details as the Dockerfile, so no
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
