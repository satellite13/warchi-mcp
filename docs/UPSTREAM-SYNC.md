# Upstream sync (GitVerse)

Product code syncs from GitVerse via:

```bash
git fetch upstream
git merge --no-ff upstream/master
# npm run format:check   # обязательно до push (warchi / papirus)
```

После merge повесить последний релизный тег upstream на **этот merge-коммит** с постфиксом `-upstream` (`v0.21.2-upstream` → merge, не сырой GitVerse `Release`). Этот тег собирает LMRU CI. Не force-push теги без постфикса.

LMRU CI/CD and Polaris deploy config live in **lmru-warchi-deploy** — do not reintroduce
`art.lmru.tech` URLs, Jenkins stage scripts, or env-specific Helm values into this repo.

See `lmru-warchi-deploy/docs/SYNC.md`.
