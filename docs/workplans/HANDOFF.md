# HANDOFF

- Anchor: branch `main`, at commit `2886b1e` (PR #14 merged; working tree clean).
- On resume: `git log --oneline` for history.

## State

The cache work is **merged and done**. ADR 0003 (one shared cache per JVM) landed via PR #13
(`53cb5c3`) with its paired bootstrap (#20) and toolkit (#316) PRs. Field testing then exposed a
cross-JVM lock defect (0003 §3 did not actually hold); fixed under
**[ADR 0004](../decisions/0004-per-jvm-cache-lock-defect.md) (accepted)** and merged via **PR #14**
(`2886b1e`).

**The 0004 defect in one line:** the lockFile lock was acquired per `Cache`, but a JVM mounts several
file systems, so the second mount's `close()` silently dropped the first's `fcntl` lock (POSIX
close-releases-all-locks) — JVMs sharing a location got **no** exclusion and silently shared one disk
store. Fix: acquire the lock once per JVM, held for JVM lifetime; `Cache` never reopens the lockFile.
Field-verified on ext3 (staggered shells now hold one real lock each and spill deterministically);
toolkit usage audited compatible. Poms are at `1.1.11-SNAPSHOT`.

## Next up

- **Nothing outstanding in this repo.** Only cross-repo/deployment follow-ups remain:
- At toolkit release: bump its client dependency `1.1.10-SNAPSHOT → 1.1.11`
  (`org-lsst-ccs-toolkit/core/configuration/pom.xml`) to pick up the 0004 fix. Usage needs no change.
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
