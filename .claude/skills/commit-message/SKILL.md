---
name: commit-message
description: Generates a commit message in the project format. Use when a commit message is requested.
---

Generate a commit message for the changes to be committed. Do not commit, stage or modify anything.

What to describe:

- If a commit revision or range is provided, describe it (`git show`, `git log -p`).
- Otherwise, if there are staged changes, describe only them (`git diff --cached`).
- Otherwise describe all uncommitted changes (`git diff HEAD`) plus new untracked files (`git status --porcelain`,
  read them in full).

Follow the "Commit Messages" section of `spec/coding-conventions.md`; read it before writing the message. Use recent
history (`git log --format=%s -20`) as examples of the style.

Issue reference:

- Take the issue number from the leading digits of the current branch name (`1038-some-title` → `#1038`).
- Otherwise take it from the proposal of the OpenSpec change that the changes implement (`openspec/changes/`,
  including `archive/`).
- If the issue number is not clear from the context, ask for it before writing the message.
- If there is no related issue, omit the reference.

Describe what the change does for the user or the code, not how it was implemented or which files were touched. Name
the domain concept the change is about (GPR, CSV, grid, template, line, mark, ...).

Prefer a one-line message. Add a bullet list body only when the commit also introduces behavioral changes that are side
fixes beyond its main purpose; list those side fixes there.

Output only the message in a fenced code block, without attribution lines.
