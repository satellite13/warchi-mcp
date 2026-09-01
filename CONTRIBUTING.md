# Contributing to warchi-mcp

Thanks for your interest in contributing.

## Development Prerequisites

- JDK 24/25
- A running arepos-server (for integration smoke tests against a real API)
- Optional: Docker

## Local Setup

```bash
export AREPOS_BASE_URL=http://localhost:8080
./gradlew build
./gradlew test
```

Run the application:

```bash
./gradlew bootRun
```

## Branching and PR Workflow

1. Fork or create a feature branch from `master`
2. Keep commits focused and atomic
3. Add/update tests for behavior changes
4. Open a pull request with context and test notes

## Commit Guidelines

- Use clear, imperative commit titles
- Mention *why* the change is needed, not only *what* changed
- Keep refactoring separate from functional changes when possible

## Testing Expectations

Before creating a PR, run:

```bash
./gradlew build
./gradlew test
```

GitHub Actions runs the same `./gradlew build` (plus Helm chart lint) on every push and pull request to `master`.

If you change MCP tool contracts, update `docs/tools.md` and `docs/tools.ru.md`.

If you change auth behavior, update `docs/auth.md` and `docs/auth.ru.md`.

## Pull Request Checklist

- [ ] Code builds locally
- [ ] Tests pass locally
- [ ] Tests cover new behavior
- [ ] Documentation is updated if needed
- [ ] No secrets, API keys, or private data were added

## Reporting Issues

Please include:

- expected behavior
- actual behavior
- reproduction steps
- MCP client / arepos versions if relevant
- logs or stack trace (redact API keys and JWTs)
