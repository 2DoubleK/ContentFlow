# Stage 13 Agent Conversations Design

## Goal

Add persistent, project-scoped Agent conversations so a user can create a conversation, send multiple messages, reload message history, and never access another user's conversation.

## Chosen Architecture

Spring Boot owns conversation identity, authorization, and message persistence. The Agent remains a stateless generation service and receives the trusted `userId`, `projectId`, and `conversationId` from Spring Boot. Vue reads and writes conversations only through authenticated Spring Boot APIs.

Alternatives rejected:

- Frontend-only history cannot survive another browser or device and cannot enforce server ownership.
- Agent-owned thread persistence duplicates PostgreSQL business data and bypasses Spring Boot authorization.

## Backend Model

Use the existing tables:

- `cf_agent_conversation`: one user/project conversation with UUID `thread_id`, title, status, and timestamps.
- `cf_agent_message`: ordered USER and ASSISTANT messages with optional JSONB metadata.

Create a focused `com.contentflow.agent` module with controller, service, DTO, entity, and mapper classes. `AgentConversationService.requireOwned` is the single ownership gate for conversation detail and message operations.

## HTTP Interfaces

- `POST /api/agent/conversations`: create an ACTIVE conversation after project ownership validation.
- `GET /api/agent/conversations?projectId={id}`: list the current user's project conversations by `updated_at DESC`.
- `GET /api/agent/conversations/{id}/messages`: list owned conversation messages by `created_at ASC, id ASC`.
- `POST /api/agent/conversations/{id}/messages`: validate ownership and nonblank content, insert USER, call Agent with the conversation ID, insert ASSISTANT, touch the conversation timestamp, and return both persisted messages plus generation metadata.

The send operation deliberately does not hold one database transaction open around the network call. If generation fails, the USER message remains in history and no fake ASSISTANT message is inserted.

## Agent Integration

Extend `AgentGenerationClient.generate` with a `conversationId` argument while preserving the existing no-conversation overload. The Agent already validates a non-null conversation through Spring Boot before saving a draft.

Assistant metadata stores title, summary, tags, references, and `savedDraftId`. Message content stores the generated Markdown.

## Frontend Experience

Replace the single prompt page with a three-column workspace:

- Existing narrow application rail.
- Conversation sidebar with inline create control and project conversation list.
- Main chat surface with ordered history, assistant output, saved-draft action, and a bottom composer.

The page reuses the current blue, white, and cyan visual system. Conversation creation is inline rather than modal. Stage 13 uses normal request/response; SSE remains Stage 14.

## Validation And Errors

- Missing project or blank title/content returns 400.
- Missing conversation returns 404.
- Cross-user or cross-project access returns 403.
- Agent failure leaves the USER message stored and returns the existing global error response.
- Message roles written by this module are restricted to USER and ASSISTANT.

## Verification

- Focused backend tests cover create/list/history/send/ownership/failure persistence.
- Frontend API and view tests cover conversation loading, creation, selection, sending, and rendering.
- Full Agent, backend, and frontend suites pass.
- Real acceptance creates one conversation, sends two messages, reloads four ordered messages, and verifies cross-user denial.
