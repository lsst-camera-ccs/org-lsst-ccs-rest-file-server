# HANDOFF

- Anchor: branch `LSSTCCS-3029`, as of the commit that carries this file.
- On resume: `git log --oneline main..HEAD` to see what moved on the branch.

## State

**ADR 0003 is implemented across all three repos** (one shared cache per JVM; cache location + spill
flag JVM-global; caching policy per-mount; the per-FS `cacheLocation()`/`ignoreLockedCache()` builder
methods removed and replaced by a set-once `setCacheLocation(Path)` that also backs the CLI's
`--cacheDir`).

- **rest-file-server (this repo):** 0003 implemented + the API cleanup, `mvn install` green
  (client 42 tests, war 9, cli builds). **Working tree is uncommitted** — the docs are committed
  through `ae22a5c` (that commit is also unpushed); the 0003 client/cli implementation + this doc
  refresh are not yet committed. No PR opened yet.
- **bootstrap:** `<app|default>` token + shipped `defaultEnvironment` line — committed, pushed,
  PR #20.
- **toolkit:** client bumped to 1.1.11 + `RemoteFileServer` cleanup — committed, pushed,
  PR #316.

The three are coupled only at deployment (no build dependency on each other's SNAPSHOTs beyond the
toolkit's declared versions); roll out together.

## Next up

- **Commit + push this repo and open its PR** (client 0003 implementation + API cleanup + doc
  refresh). This is the linchpin the toolkit/bootstrap PRs pair with.
- Optional: full toolkit reactor build (`mvn install`) to confirm nothing downstream of
  `core/configuration` breaks — only that module + its deps were built so far.
- Then the deferred deployment-config items below.

Design detail lives in [ADR 0003](../decisions/0003-shared-per-jvm-cache.md); the build steps (now
executed) are in the [workplan](LSSTCCS-3029-shared-per-jvm-cache.md).

## Deferred (deployment-config, not code)

- Per-agent-category default policy: role agents (unique, reproducible names) reattach with a
  persistent disk cache; `ccs-shell`/`ccs-console` (unique, non-reproducible names) don't
  reattach — decide `OFFLINE` vs `WHEN_POSSIBLE` and memory vs disk per category.
- Bootstrap default `defaultEnvironment` values/location for base and summit.
