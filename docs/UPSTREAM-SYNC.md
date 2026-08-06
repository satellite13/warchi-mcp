# Upstream sync (GitVerse)

Product code syncs from GitVerse via:

```bash
git fetch upstream
git merge upstream/master
```

LMRU CI/CD and Polaris deploy config live in **lmru-warchi-deploy** — do not reintroduce
`art.lmru.tech` URLs, Jenkins stage scripts, or env-specific Helm values into this repo.

See `lmru-warchi-deploy/docs/SYNC.md`.
