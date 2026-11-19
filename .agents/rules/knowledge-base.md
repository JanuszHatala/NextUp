# Project Knowledge Base Enforcement Rule

`docs/kb/` is the canonical engineering memory of this repository. Keep it accurate on every task.

## Mandatory Lifecycle

1. **Before Planning or Designing:**
   - Read `docs/kb/INDEX.md` and relevant sections of `FEATURES.md`, `DECISIONS.md`, and `BACKLOG.md`.
   - Do not re-propose rejected approaches or duplicate already-built groundwork (e.g., existing DB tables or backlog items).

2. **During Implementation (Every Feature, Fix, or Release Branch):**
   - Update `docs/kb/FEATURES.md` in place when user-facing behavior, UI flows, or data contracts change.
   - Record non-obvious architectural, schema, or dependency choices as a new `ADR-NNN` entry in `docs/kb/DECISIONS.md`.
   - When the user defers a feature, marks scope out of bounds, or postpones work for later, add or update an entry in `docs/kb/BACKLOG.md` immediately.
   - Record user-visible changes under `[Unreleased]` or the bumped release version in `docs/kb/CHANGELOG.md`, and keep `docs/kb/INDEX.md` version metadata current.

3. **Before Committing / Opening a PR:**
   - Verify all `docs/kb/` updates are included in the same branch/PR as the code changes.
   - Verify file paths, version numbers, and symbol names cited in `docs/kb/` match the repository state.
   - Do not use em-dashes in KB files; use hyphens (`-`) or colons (`:`).
