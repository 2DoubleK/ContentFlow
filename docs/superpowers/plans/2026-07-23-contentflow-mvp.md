# ContentFlow MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the smallest usable ContentFlow loop: login, project management, reference upload, LangGraph RAG generation, PostgreSQL content save, and Vue viewing.

**Architecture:** The repository contains three independent applications: `backend/`, `agent/`, and `web/`. The frontend calls only the backend; the backend owns PostgreSQL and authorization; the Agent owns LangGraph, Chroma, and model orchestration and never connects to PostgreSQL.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, PostgreSQL, JWT, Python 3.12, FastAPI, LangGraph, Chroma, Pydantic, Vue 3, Vite, TypeScript, Pinia, Element Plus, Axios.

## Global Constraints

- Keep first implementation minimal.
- Use PostgreSQL as the business database.
- Do not commit real credentials from the local credential TXT file.
- Keep backend, Agent, and frontend responsibilities separate.
- Agent never connects directly to PostgreSQL.
- Frontend never calls Agent directly.
- Support `.txt` and `.md` reference uploads only.
- Defer Redis, MinIO, admin pages, PDF parsing, publishing, analytics, and complex streaming.

---

## File Structure

Create:

- `backend/pom.xml`: Maven dependencies and Java 17 build config.
- `backend/src/main/java/com/contentflow/ContentFlowApplication.java`: Spring Boot entry point.
- `backend/src/main/java/com/contentflow/common/*`: API response and error handling.
- `backend/src/main/java/com/contentflow/config/*`: security, JWT, CORS, MyBatis config.
- `backend/src/main/java/com/contentflow/auth/*`: register/login controller, service, DTOs.
- `backend/src/main/java/com/contentflow/project/*`: project entity, mapper, service, controller.
- `backend/src/main/java/com/contentflow/document/*`: document metadata, upload controller, Agent indexing client.
- `backend/src/main/java/com/contentflow/content/*`: generation controller, content entity, Agent generation client.
- `backend/src/main/java/com/contentflow/internal/*`: internal APIs consumed by Agent.
- `backend/src/main/resources/application.yml`: environment-driven config.
- `backend/src/main/resources/db/schema.sql`: PostgreSQL schema.
- `backend/src/test/java/com/contentflow/*`: focused backend tests.

- `agent/pyproject.toml`: Python dependencies and pytest config.
- `agent/app/main.py`: FastAPI app.
- `agent/app/config.py`: environment config.
- `agent/app/schemas.py`: Pydantic request/response models.
- `agent/app/graph.py`: LangGraph workflow.
- `agent/app/retrieval.py`: Chroma indexing and retrieval.
- `agent/app/llm.py`: model adapter with deterministic fallback for missing API key.
- `agent/app/backend_client.py`: backend internal API client.
- `agent/tests/*`: graph and retrieval tests.

- `web/package.json`: frontend scripts and dependencies.
- `web/index.html`, `web/vite.config.ts`, `web/tsconfig*.json`: Vite config.
- `web/src/main.ts`, `web/src/App.vue`: Vue app shell.
- `web/src/router/index.ts`: routes.
- `web/src/stores/auth.ts`: auth store.
- `web/src/api/*`: backend API clients.
- `web/src/views/*`: login, projects, project detail, generation, content detail.
- `web/src/styles.css`: restrained app styling.
- `web/src/**/*.test.ts`: focused frontend tests.

Modify:

- `.gitignore`: exclude credentials, local env files, and build outputs.
- `README.md`: local startup and verification commands.

## Task 1: Backend Foundation

**Files:**
- Create backend Maven project, config, common response, schema, and tests.

**Interfaces:**
- Produces `POST /api/auth/register`, `POST /api/auth/login`, JWT validation, and PostgreSQL schema.

- [x] Write failing tests for register/login and JWT rejection.
- [x] Run `mvn test` in `backend/` and confirm tests fail because implementation is missing.
- [x] Implement Spring Boot app, security config, JWT service, auth DTOs, user mapper, and auth service.
- [x] Add `schema.sql` for `sys_user`, `cf_project`, `cf_document`, and `cf_content`.
- [x] Run `mvn test` in `backend/` and confirm auth tests pass.
- [x] Commit backend foundation.

## Task 2: Backend Project, Document, And Content APIs

**Files:**
- Create project, document, content, internal, and Agent client modules under `backend/src/main/java/com/contentflow/`.

**Interfaces:**
- Consumes authenticated user id from JWT.
- Produces project CRUD, document upload/list, generation endpoint, content list/detail, internal Agent APIs.

- [x] Write failing tests for project owner isolation.
- [x] Write failing tests for unsupported document type returning `400`.
- [x] Write failing tests for successful generation saving `cf_content`.
- [x] Run `mvn test` in `backend/` and confirm expected failures.
- [x] Implement project mapper/service/controller.
- [x] Implement document mapper/service/controller and Agent indexing client.
- [x] Implement content mapper/service/controller and Agent generation client.
- [x] Implement internal project context and internal content save APIs.
- [x] Run `mvn test` in `backend/` and confirm all backend tests pass.
- [x] Commit backend business APIs.

## Task 3: Agent Foundation And Retrieval

**Files:**
- Create FastAPI app, config, schemas, Chroma retrieval service, backend client, and tests.

**Interfaces:**
- Produces `POST /documents/index`, `POST /generate`, and `GET /health`.
- Uses `projectId` and `documentId` metadata in Chroma.

- [x] Write failing pytest for indexing `.txt` text and retrieving only matching `projectId`.
- [x] Run `pytest` in `agent/` and confirm the retrieval test fails.
- [x] Implement Pydantic schemas and Chroma retrieval service.
- [x] Implement `/documents/index` endpoint.
- [x] Run `pytest` in `agent/` and confirm retrieval tests pass.
- [x] Commit Agent retrieval foundation.

## Task 4: Agent LangGraph Generation

**Files:**
- Create `agent/app/graph.py` and `agent/app/llm.py`.

**Interfaces:**
- Consumes `GenerateRequest(projectId, prompt)`.
- Produces `GenerateResponse(title, summary, markdown, references)`.

- [x] Write failing pytest for graph node order and structured output.
- [x] Run `pytest` in `agent/` and confirm graph test fails.
- [x] Implement LangGraph nodes: `parse_request`, `retrieve_context`, `generate_content`, `format_output`.
- [x] Implement LLM adapter using environment model config with deterministic fallback when no API key is present.
- [x] Implement `/generate` endpoint.
- [x] Run `pytest` in `agent/` and confirm all Agent tests pass.
- [x] Commit Agent LangGraph generation.

## Task 5: Frontend Foundation

**Files:**
- Create Vue Vite app, router, auth store, API client, base layout, and auth views.

**Interfaces:**
- Consumes backend `/api/auth/*`.
- Produces login state and authenticated router behavior.

- [x] Write failing Vitest tests for auth token persistence and logout.
- [x] Run `npm test` in `web/` and confirm expected failures.
- [x] Implement Vite, Vue, Pinia, Router, Axios client, auth store, and login/register view.
- [x] Run `npm test` in `web/` and confirm frontend auth tests pass.
- [x] Commit frontend foundation.

## Task 6: Frontend Project And Generation Views

**Files:**
- Create project list, project detail, generation, and content detail views plus API modules.

**Interfaces:**
- Consumes backend project, document, generation, and content APIs.
- Produces user-facing minimal workflow.

- [x] Write failing Vitest tests for project API client path construction and generation loading state.
- [x] Run `npm test` in `web/` and confirm expected failures.
- [x] Implement project list view.
- [x] Implement project detail view with document upload.
- [x] Implement generation view.
- [x] Implement content detail view.
- [x] Run `npm test` in `web/` and confirm all frontend tests pass.
- [x] Commit frontend workflow.

## Task 7: Integration Documentation And Verification

**Files:**
- Create `.env.example` files for backend, Agent, and frontend.
- Create `README.md`.

**Interfaces:**
- Produces local run instructions without real secrets.

- [x] Add placeholder-only env examples.
- [x] Add startup commands for PostgreSQL-backed backend, Agent, and web.
- [x] Add verification commands: `mvn test`, `pytest`, `npm test`, and health checks.
- [x] Run available verification commands locally.
- [x] Record any missing local runtime dependency clearly in final handoff.
- [x] Commit docs and verification updates.
