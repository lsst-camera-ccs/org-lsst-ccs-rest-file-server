# HANDOFF

- Anchor: branch `LSSTCCS-3029`, at commit `7cb189f` (pushed) + an uncommitted working tree (below).
- On resume: `git log --oneline main..HEAD` for branch history; `git status` for the working tree.

## State

**ADR 0003 is implemented, committed, pushed, and now runtime-verified** across all three repos
(one shared cache per JVM; cache location + spill flag JVM-global; caching policy per-mount; the
per-FS `cacheLocation()`/`ignoreLockedCache()` builder methods removed and replaced by a set-once
`setCacheLocation(Path)` that also backs the CLI's `--cacheDir`).

- **rest-file-server (this repo):** 0003 implemented + API cleanup, committed through `7cb189f`
  (pushed; `origin/LSSTCCS-3029` is level with HEAD). `mvn -pl war,client -am install` green
  (client 43 tests, war 9). **PR not yet opened.**
- **bootstrap:** `<app|default>` token + shipped `defaultEnvironment` line — committed, pushed, PR #20.
- **toolkit:** client bumped to 1.1.11 + `RemoteFileServer` cleanup — committed, pushed, PR #316.

Coupled only at deployment (no build dependency beyond the toolkit's declared client version); roll
out together.

**Uncommitted working tree (this session):**
- `Cache.java` — **spill-naming bug fix** (see below).
- `CacheLockCrossJvmTest.java` — new two-level spill regression test + `@TempDir`.
- `CachingTest.java`, `VersionedFileTest.java`, `SpeedTest.java` — `@TempDir` cleanup so tests no
  longer leak `/tmp/rfs*` dirs (`SpeedTest` migrated JUnit 4 → Jupiter).

## This session (2026-07-21)

- **Test cleanup:** the cache tests leaked a `/tmp/rfs*` dir per run (JCS files + lockFile). Moved
  all four to JUnit 5 `@TempDir`; JUnit now deletes each tree. Verified zero leak after a full run.
- **Live runtime verification** against the dev server (`lsst-camera-dev`), the first non-source
  check of the 0003 model — see the [toolkit guide](../guides/toolkit-cache-compatibility.md)
  Verification section. Covered: warm-start offline + reattach; one shared region with mixed
  `OFFLINE`/`WHEN_POSSIBLE` mounts; spill; stale-lock reclaim after `kill -9`.
- **Spill-naming bug found & fixed.** `Cache.lockCacheLocation` suffixed the already-suffixed
  candidate, so successive spills compounded (`<base>-1-2-3`) instead of flat `<base>-2`, `-3`. Safe
  (distinct exclusively-locked dirs) but misnamed; fixed to suffix the captured base, plus a
  two-level cross-JVM regression test. The unit suite missed it (only spilled one level); the live
  four-shell test surfaced it.

## Next up

- **Commit this session's working tree** (spill fix + test cleanup) and **open this repo's PR** — the
  linchpin the toolkit/bootstrap PRs pair with.
- Optional: full toolkit reactor build (`mvn install`) to confirm nothing downstream of
  `core/configuration` breaks (only that module + deps built so far).
- Then the deferred deployment-config items below.

## Backlog

- **Cosmetic SEVERE on shutdown (has a JIRA).** When the bootstrap mounts an external `ccs://` FS
  that is never closed, no `Cache.close()` runs, so the global logger-off workaround
  (`Cache.close()` sets `IndexedDiskCache` logging to OFF) never fires and JCS's own shutdown hook
  logs `Region [default] : Not alive and dispose was called`. Data still spools; lock released on
  exit. Cosmetic. Real fix: silence at the source (constructor / logging config) rather than as a
  close-time side effect, or close bootstrap mounts on shutdown.

## Deferred (deployment-config, not code)

- Per-agent-category default policy: role agents (stable, reproducible app names) reattach with a
  persistent disk cache; `ccs-shell`/`ccs-console` (same app name across instances → collide + spill)
  don't reattach — decide `OFFLINE` vs `WHEN_POSSIBLE` and memory vs disk per category.
- Bootstrap default `defaultEnvironment` values/location for base and summit.
