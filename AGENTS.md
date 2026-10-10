# CLAUDE.md

## Workflow

- Work in small, reviewable steps. Propose wide refactors before doing them.
- Minimal changes: touch only what the task needs; no drive-by refactoring.
- Fix root causes, not symptoms; guard related edge cases.
- The user often edits code between turns: treat files on disk as current, build on those edits, never revert them.
- When asked to advise, explain or check, answer only; do not modify code.
- Never commit: all commits are made by hand. Do not add tests unless asked; run existing tests to catch regressions.

## Specs

Project knowledge lives in `spec/`. Core specs are imported below; read other `spec/` files when a task touches their
area. Behavior requirements live in `openspec/specs/` and change through OpenSpec changes (`openspec/changes/`).
When a change makes a spec inaccurate, update it in the same change: architecture changes go in `spec/`; behavior
changes go in OpenSpec delta specs.

- @spec/overview.md
- @spec/coding-conventions.md
