# Stage 13 Agent Conversations Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build persistent, authorized project conversations with multi-message Agent generation and a usable Vue chat workspace.

**Architecture:** Spring Boot owns conversations and messages in PostgreSQL, validates every user/project boundary, and calls the stateless Agent with a trusted conversation ID. Vue consumes only authenticated Spring Boot APIs; SSE is deferred to Stage 14.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis-Plus, PostgreSQL JSONB, Python 3.12 Agent API, Vue 3, TypeScript, Element Plus, Vitest.

## Global Constraints

- Reuse `cf_agent_conversation` and `cf_agent_message`; do not create duplicate tables.
- Keep runtime user/project/conversation IDs controlled by Spring Boot.
- Do not hold a database transaction open during the Agent HTTP call.
- Persist USER before generation and ASSISTANT only after generation succeeds.
- Keep Stage 14 SSE out of scope.

---

### Task 1: Conversation Domain And CRUD APIs

**Files:**
- Create: `backend/src/main/java/com/contentflow/agent/entity/AgentConversationEntity.java`
- Create: `backend/src/main/java/com/contentflow/agent/entity/AgentMessageEntity.java`
- Modify: `backend/src/main/java/com/contentflow/agent/mapper/AgentConversationMapper.java`
- Create: `backend/src/main/java/com/contentflow/agent/mapper/AgentMessageMapper.java`
- Create: `backend/src/main/java/com/contentflow/agent/dto/AgentConversationDtos.java`
- Create: `backend/src/main/java/com/contentflow/agent/service/AgentConversationService.java`
- Create: `backend/src/main/java/com/contentflow/agent/controller/AgentConversationController.java`
- Test: `backend/src/test/java/com/contentflow/agent/service/AgentConversationServiceTest.java`
- Test: `backend/src/test/java/com/contentflow/agent/controller/AgentConversationControllerTest.java`

**Interfaces:**
- Produces: `create(userId, request)`, `list(userId, projectId)`, `messages(userId, conversationId)`, and `requireOwned(userId, conversationId)`.
- Consumes: `ProjectService.requireOwned` and the existing PostgreSQL tables.

- [ ] **Step 1: Write failing service tests** for ACTIVE creation with UUID thread ID, project-scoped listing, chronological messages, 404 missing conversation, and 403 cross-user access.
- [ ] **Step 2: Run** `mvn '-Dtest=AgentConversationServiceTest' test` and verify missing classes fail.
- [ ] **Step 3: Implement entities, mappers, DTOs, ownership checks, CRUD service, and controller endpoints.**
- [ ] **Step 4: Run focused service/controller tests and verify PASS.**

### Task 2: Send Message And Persist Agent Response

**Files:**
- Modify: `backend/src/main/java/com/contentflow/agent/client/AgentGenerationClient.java`
- Modify: `backend/src/main/java/com/contentflow/agent/service/AgentConversationService.java`
- Modify: `backend/src/main/java/com/contentflow/agent/dto/AgentConversationDtos.java`
- Test: `backend/src/test/java/com/contentflow/agent/client/AgentGenerationClientTest.java`
- Test: `backend/src/test/java/com/contentflow/agent/service/AgentConversationServiceTest.java`

**Interfaces:**
- Produces: `sendMessage(userId, conversationId, SendMessageRequest)` returning persisted USER and ASSISTANT messages.
- Consumes: `AgentGenerationClient.generate(userId, projectId, conversationId, prompt)`.

- [ ] **Step 1: Write failing tests** that assert USER is inserted before Agent invocation, conversation ID is forwarded, ASSISTANT Markdown and metadata are persisted, blank content is rejected, and Agent failure leaves only USER.
- [ ] **Step 2: Run focused tests and verify expected failures.**
- [ ] **Step 3: Add the conversation-aware Agent client overload and message orchestration without a network-spanning transaction.**
- [ ] **Step 4: Run focused tests and verify PASS.**

### Task 3: Vue Conversation API And Workspace

**Files:**
- Create: `web/src/api/conversations.ts`
- Create: `web/src/api/conversations.test.ts`
- Modify: `web/src/views/GenerateView.vue`
- Create: `web/src/views/GenerateView.test.ts`

**Interfaces:**
- Produces: typed `Conversation`, `AgentMessage`, and `SendMessageResponse` APIs.
- Consumes: the four Stage 13 Spring Boot endpoints.

- [ ] **Step 1: Write failing API tests** for create/list/messages/send URL and payload contracts.
- [ ] **Step 2: Write failing view tests** for automatic first conversation selection, inline creation, history rendering, sending, and saved-draft navigation.
- [ ] **Step 3: Run** `npm test -- conversations.test.ts GenerateView.test.ts` and verify failures.
- [ ] **Step 4: Implement the typed API and chat workspace with stable sidebar, message list, composer, loading, empty, and error states.**
- [ ] **Step 5: Run focused frontend tests and `npm run build`.**

### Task 4: Migration, Acceptance, And Documentation

**Files:**
- Modify: `backend/src/main/resources/db/schema.sql` only if an index or constraint required by implementation is missing.
- Modify: `开发步骤文档.md`
- Modify: `docs/superpowers/plans/2026-07-25-stage-13-agent-conversations.md`

**Interfaces:**
- Verifies the complete authenticated browser-to-Agent flow.

- [ ] **Step 1: Apply `schema.sql` and verify conversation/message indexes.**
- [ ] **Step 2: Run Agent, backend, and frontend full test suites.**
- [ ] **Step 3: Restart services and verify health.**
- [ ] **Step 4: Create a real conversation, send two messages, reload ordered history, and verify PostgreSQL USER/ASSISTANT rows.**
- [ ] **Step 5: Call list/messages as another user and verify access is denied.**
- [ ] **Step 6: Record evidence in `开发步骤文档.md`, check all plan boxes, and commit Stage 13.**
