# Security Policy

## Supported Versions

Security fixes are currently provided on a best-effort basis for the latest `master` state.

## Reporting a Vulnerability

Please do **not** open public issues for security vulnerabilities.

Instead:

1. Prepare a private report including:
   - vulnerability description
   - impact
   - reproduction steps
   - possible mitigation
2. Send it to project maintainers through a private communication channel:
   - private vulnerability/advisory report in the Git hosting platform (preferred), or
   - direct private message/email to maintainers (`nikolay@groznykh.ru`).

If no private channel is available yet, create one before public release and update this file.

## Security Best Practices for Deployments

- Treat API keys as secrets: show once at creation, store only in MCP client config / secret store
- Prefer HTTPS termination in front of `warchi-mcp`; never expose plain HTTP on the public internet
- Ensure the reverse proxy forwards `Authorization` / `X-Api-Key` and does not log their values
- Do not log API keys, JWTs, or full `Authorization` headers in application logs
- Point `AREPOS_BASE_URL` at a trusted arepos instance (prefer private network / mTLS where possible)
- Revoke compromised keys immediately in wArchi Profile
- Use least privilege: `mode=all` with `models:read` when write is not needed, or `mode=grants` with read-only on most models and write only where required
- One `warchi_ak_…` secret per agent; prefer per-model grants over issuing multiple keys
- Deactivating a user soft-blocks exchange via `isActive` (keys are not auto-revoked — revoke explicitly if needed)
- Keep dependencies and base images updated
- Restrict network access so only intended clients reach the MCP endpoint
