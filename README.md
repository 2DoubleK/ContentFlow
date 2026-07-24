# ContentFlow

ContentFlow is a minimal AI content generation system with three separate applications:

- `backend/`: Spring Boot API, PostgreSQL data ownership, JWT auth.
- `agent/`: FastAPI, LangGraph workflow, Chroma retrieval.
- `web/`: Vue 3 frontend.

## Requirements

- Java 17 and Maven.
- Python 3.12.
- Node.js 20 or newer.
- PostgreSQL.
- Ollama when local-model fallback is required.

## Environment

Copy each example file and fill local values:

- `backend/.env.example`
- `agent/.env.example`
- `web/.env.example`

Do not commit real credential files or real `.env` files.

## Database

Create a PostgreSQL database, then apply:

```bash
psql -d contentflow -f backend/src/main/resources/db/schema.sql
```

## Local Infrastructure

Copy `.env.example` to `.env`, then start PostgreSQL, Redis, MinIO, and Chroma:

```powershell
docker compose up -d postgres redis minio chroma minio-init
```

MinIO console: `http://localhost:9001`.

## Run

All services in separate PowerShell windows:

```powershell
.\start-dev.ps1
```

Use `.\start-dev.ps1 -SkipInstall` to skip dependency installation checks.

Backend:

```bash
cd backend
mvn spring-boot:run
```

Agent:

```bash
cd agent
py -3.12 -m venv .venv
.venv\Scripts\activate
pip install -e ".[test]"
uvicorn app.main:app --reload --port 8000
```

Agent can use a local persistent Chroma directory (`CHROMA_PATH`) or a Chroma server (`CHROMA_HOST` and `CHROMA_PORT`).
Copy `agent/.env.example` before configuring an LLM or embedding provider. The Agent uses `X-Internal-Token` for every call to Spring Boot internal endpoints.

For the default local fallback model:

```bash
ollama pull qwen3:1.7b
ollama run qwen3:1.7b
```

Generation providers are attempted in this order: cloud API, Ollama, then deterministic fallback. Set the `OLLAMA_*` values in `agent/.env` to select another local model.

Frontend:

```bash
cd web
npm install
npm run dev
```

## Verify

Health checks:

```bash
curl http://localhost:8080/actuator/health
curl http://localhost:8000/health
```

```bash
cd backend
mvn test
```

```bash
cd agent
py -3.12 -m pytest
```

```bash
cd web
npm test
```
