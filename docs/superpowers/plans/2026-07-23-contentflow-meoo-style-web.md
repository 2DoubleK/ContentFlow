# ContentFlow Meoo-Style Web Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restyle the existing Vue frontend into a blue-white-cyan workspace inspired by `meoo.com`.

**Architecture:** Keep the current Vue routes, API clients, Pinia store, and Element Plus components. Centralize shared visual language in `web/src/styles.css`, then apply page-specific layout classes in the five existing views.

**Tech Stack:** Vue 3, Vite, TypeScript, Element Plus, lucide-vue-next, CSS.

## Global Constraints

- Use blue, white, cyan, and light neutral backgrounds.
- Keep high whitespace and low visual noise.
- Keep cards light with small radius and subtle borders.
- Make generation input the visual center of the app.
- Do not add marketing copy or unrelated content.
- Do not add new dependencies.
- Do not change backend, Agent, routes, API contracts, or business behavior.

---

## File Structure

- Modify `web/src/styles.css`: global shell, rail, notice band, panels, buttons, composer, responsive behavior.
- Modify `web/src/views/LoginView.vue`: login layout and blue/cyan brand presentation.
- Modify `web/src/views/ProjectsView.vue`: workspace shell, side rail, central hero, project cards.
- Modify `web/src/views/ProjectDetailView.vue`: fix visible labels, add detail panels and workspace shell.
- Modify `web/src/views/GenerateView.vue`: large prompt composer and action surface.
- Modify `web/src/views/ContentDetailView.vue`: fix visible labels and readable article surface.

## Task 1: Shared Visual System

**Files:**
- Modify: `web/src/styles.css`

**Interfaces:**
- Produces shared CSS classes used by all existing Vue pages.

- [x] Add CSS variables for blue, cyan, border, muted text, background, and shadow.
- [x] Replace the current generic page styling with a workspace shell, notice band, rail, topbar, hero, panel, card grid, and composer styles.
- [x] Add responsive rules so the rail becomes horizontal and text stays inside controls on narrow screens.
- [x] Verify there are no new dependencies or route changes.

## Task 2: Projects And Login Pages

**Files:**
- Modify: `web/src/views/LoginView.vue`
- Modify: `web/src/views/ProjectsView.vue`

**Interfaces:**
- Consumes existing auth store, project API, router, and lucide icons.
- Produces the same login/register, project list, project create, and logout behavior.

- [x] Update login markup to use the shared brand, notice, and panel styles.
- [x] Update projects markup to use the workspace shell, rail, hero, quick chips, and cards.
- [x] Keep `createProject`, `logout`, and routing logic unchanged except visible copy.
- [x] Verify the page templates compile with existing imports.

## Task 3: Detail, Generate, And Content Pages

**Files:**
- Modify: `web/src/views/ProjectDetailView.vue`
- Modify: `web/src/views/GenerateView.vue`
- Modify: `web/src/views/ContentDetailView.vue`

**Interfaces:**
- Consumes existing project/content API calls and router params.
- Produces the same document upload, content listing, generation, and content viewing behavior.

- [x] Replace garbled visible labels with readable Chinese labels.
- [x] Apply shared workspace shell and panel styles to project detail and content detail.
- [x] Apply the composer surface to the generation prompt page.
- [x] Keep API calls, upload handler, loading state, and navigation behavior unchanged.

## Task 4: Verification

**Files:**
- Test: `web/`

**Interfaces:**
- Produces a passing frontend build/test signal or a clear failure report.

- [x] Run `npm test` in `web/`.
- [x] Run `npm run build` in `web/`.
- [x] If a command fails from missing local dependencies, report the exact blocker.
- [x] Inspect `git diff -- web/src docs/superpowers` to ensure only requested frontend/design files changed.
