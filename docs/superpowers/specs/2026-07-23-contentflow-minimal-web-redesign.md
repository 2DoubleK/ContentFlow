# ContentFlow Minimal Web Redesign

## Goal

Update only the frontend layout and visual style to a minimal blue-white-cyan workspace inspired by `meoo.com`.

## Scope

Included:

- Login page visual simplification.
- Projects page top navigation and lightweight project cards.
- Project detail page cleaner document and content sections.
- Generation page centered large prompt surface.
- Content detail page readable minimal article layout.

Excluded:

- Backend changes.
- Agent changes.
- API contract changes.
- New business features.

## Visual Rules

- Use blue, white, cyan, and light neutral backgrounds.
- Keep high whitespace and low visual noise.
- Keep cards light with small radius and subtle borders.
- Make generation input the visual center of the app.
- Do not add marketing copy or unrelated content.

## Approved Approach

Use the medium redesign option. Keep the existing Vue routes and API behavior, but reshape the pages into a lightweight AI workspace inspired by `meoo.com`: a slim app rail on desktop, a soft notice band, pill-shaped action groups, a centered creation surface, and restrained project/content panels.

Translate the reference site's purple accents into ContentFlow's blue-white-cyan palette. Preserve Element Plus and lucide-vue-next; do not add new dependencies.

## Page Requirements

- Login: brand-forward, centered, calm blue/cyan treatment.
- Projects: workspace shell with rail navigation, central hero, quick action bar, and project cards.
- Project detail: project header, document upload/list, generated content list, and fixed readable labels.
- Generate: large central prompt composer similar to Meoo's input surface.
- Content detail: readable article panel with clean navigation and fixed readable labels.
