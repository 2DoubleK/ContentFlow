# ContentFlow MVP Design

## Goal

Build the smallest usable AI content generation loop from project setup to reference upload, retrieval-augmented generation, and saved article viewing.

## Scope

Included:

- User registration and JWT login.
- Project create, list, detail, update, and delete.
- Upload `.txt` and `.md` reference documents.
- Index uploaded references by `projectId`.
- Generate structured content through a LangGraph workflow.
- Save generated content in PostgreSQL.
- Vue pages for login, projects, project detail, content generation, and content detail.

Excluded:

- Admin console.
- PDF parsing.
- MinIO object storage.
- Redis caching.
- Complex SSE streaming.
- Multi-agent workflow.
- Review, publishing, analytics, OCR, Elasticsearch, message queue, and multi-tenant features.

## Project Layout

Three separate projects are required:

- `backend/`: Spring Boot 3, Java 21, PostgreSQL, Spring Security, JWT, MyBatis-Plus.
- `agent/`: Python 3.11, FastAPI, LangGraph, LangChain, Chroma, Pydantic.
- `web/`: Vue 3, TypeScript, Vite, Pinia, Vue Router, Element Plus, Axios.

No project may own another project's responsibility. Integration happens only over HTTP.

## Responsibility Boundaries

Backend responsibilities:

- Own users, projects, documents, generated content, and authorization.
- Read PostgreSQL credentials from environment variables.
- Expose authenticated public APIs to the frontend.
- Expose internal APIs for the Agent to read project context and save generated content.
- Forward uploaded documents to the Agent for indexing.

Agent responsibilities:

- Parse uploaded text or markdown documents.
- Split and embed reference content.
- Store and query vectors in Chroma with `projectId` and `documentId` metadata.
- Run the LangGraph flow: `parse_request -> retrieve_context -> generate_content -> format_output`.
- Return structured generation output.
- Never connect directly to PostgreSQL.

Frontend responsibilities:

- Manage user session state.
- Call backend APIs only.
- Render project, document, generation, and content views.
- Never call Agent APIs directly.

## Data Flow

1. User logs in through the frontend and receives a JWT from the backend.
2. User creates or opens a project.
3. User uploads a `.txt` or `.md` reference document to the backend.
4. Backend validates project ownership, creates a document record, and sends the file to Agent `/documents/index`.
5. Agent parses, chunks, embeds, and stores vectors in Chroma using `projectId` filtering metadata.
6. User sends a generation request to the backend.
7. Backend validates project ownership and calls Agent `/generate`.
8. Agent retrieves context only for the given `projectId`, generates structured content, and returns it.
9. Backend saves the content in PostgreSQL and returns the saved result to the frontend.
10. Frontend renders the generated article and content detail page.

## Database

Use PostgreSQL.

Minimum tables:

- `sys_user`: user identity and password hash.
- `cf_project`: project metadata and owner.
- `cf_document`: uploaded reference metadata and indexing status.
- `cf_content`: generated article result.

All real credentials stay outside source code. `.env.example` files contain placeholder values only.

## API Surface

Backend public APIs:

- `POST /api/auth/register`
- `POST /api/auth/login`
- `GET /api/projects`
- `POST /api/projects`
- `GET /api/projects/{id}`
- `PUT /api/projects/{id}`
- `DELETE /api/projects/{id}`
- `POST /api/projects/{id}/documents`
- `GET /api/projects/{id}/documents`
- `POST /api/projects/{id}/generate`
- `GET /api/projects/{id}/contents`
- `GET /api/contents/{id}`

Backend internal APIs for Agent:

- `GET /internal/projects/{id}/context`
- `POST /internal/projects/{id}/contents`

Agent APIs:

- `POST /documents/index`
- `POST /generate`
- `GET /health`

## Error Handling

- Invalid login returns `401`.
- Missing or invalid JWT returns `401`.
- Accessing another user's project returns `403`.
- Unsupported upload type returns `400`.
- Failed Agent indexing marks the document as `FAILED`.
- Failed generation returns an error response and does not save empty content.

## Testing

Backend tests cover:

- Password hashing and JWT login.
- Project owner access checks.
- Document status transitions for successful and failed indexing.
- Content save after generation.

Agent tests cover:

- LangGraph node flow.
- `projectId` retrieval filtering.
- Structured Pydantic output.

Frontend tests cover:

- Auth state persistence.
- Project API wiring.
- Generate page loading and error states.

## Constraints

- Keep the first implementation minimal.
- Do not add unrelated text-generation features.
- Do not store secrets from the local credential TXT file in source-controlled files.
- Keep backend, Agent, and frontend responsibilities separate.
- Use PostgreSQL as the business database.
