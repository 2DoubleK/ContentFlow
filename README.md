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

## Run

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

Frontend:

```bash
cd web
npm install
npm run dev
```

## Verify

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
