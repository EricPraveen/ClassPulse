# Contributing – ClassPulse

## Before coding

1. Read `PROJECT_CONTEXT.md`, `ARCHITECTURE.md`, and `docs/communication-protocol.md`.
2. Check `TASKS.md` and coordinate with the member responsible for the relevant area.
3. Pull the latest shared development branch.
4. Do not change shared message types or method contracts without notifying the group.

## Suggested Git workflow

- `main`: stable submission-ready version.
- `develop`: integration branch (optional if the group prefers a simpler workflow).
- `feature/<short-task-name>`: individual work.

Typical commands:

```bash
git switch develop
git pull origin develop
git switch -c feature/short-task-name
# implement and test
git add .
git commit -m "Implement short task description"
git push -u origin feature/short-task-name
```

Open a pull request into `develop` and request a teammate review. Merge into `main` only when the integrated project is stable.

If the team chooses not to use a `develop` branch, use feature branches and pull requests directly into `main`.

## Code conventions

- Use descriptive Java class and method names.
- Keep classes focused on one responsibility.
- Use packages already allocated in `ARCHITECTURE.md`.
- Avoid hard-coded local paths, credentials, and machine-specific settings.
- Close channels, sockets, selectors, and streams reliably (prefer try-with-resources where suitable).
- Handle partial TCP reads/writes; do not assume one read equals one complete message.
- Keep the selector event loop non-blocking.
- Document public protocol changes and update the protocol examples.

## Before requesting review

From the project root, run:

```bash
mvn clean install
```

Also run the tests relevant to your change. Describe what you tested and any known limitations in the pull request.

## Scope control

This is an assignment project. Implement the required networking concepts and core classroom-polling workflow first. Avoid adding a database, GUI, deployment platform, or unrelated features unless the assignment requires them.
