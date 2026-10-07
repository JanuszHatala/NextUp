# Git & Branching Strategy

## Feature Branching & Pull Requests
For all code modifications, enhancements, bugfixes, and CI/tooling updates:

1. **No Direct Commits to `main`**:
   - Never commit or push directly to the `main` branch.
   - All work must be conducted on a dedicated feature/fix branch (e.g., `feat/<feature-name>`, `fix/<issue-name>`, `chore/<task-name>`).

2. **Automated Verification Before Merge**:
   - Ensure local unit tests (`./gradlew testDebugUnitTest`) and build validation (`./gradlew assembleDebug`) pass before pushing.
   - Pull requests trigger GitHub Actions CI workflows to validate changes on GitHub runners before merging.

3. **Pull Request Protocol**:
   - Push the branch to GitHub (`git push -u origin <branch-name>`).
   - Create a Pull Request against `main` using `gh pr create --title "..." --body "..."`.
   - Wait for CI checks or verify workflow trigger status (`gh pr checks` / `gh run list`).
   - Merge the Pull Request into `main` (using squash or merge commit as appropriate: `gh pr merge --merge` or `gh pr merge --squash`).
   - Pull the updated `main` branch locally (`git checkout main && git pull`).
