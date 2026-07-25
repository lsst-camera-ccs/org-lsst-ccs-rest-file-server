# HANDOFF

- Anchor: branch `main`, at commit `53cb5c3` (PR #13 merged; working tree has the 0004 docs below, uncommitted).
- On resume: `git log --oneline` for history; `git status` for the pending 0004 doc changes.

## State

ADR 0003 (one shared cache per JVM) is **merged** (PR #13 → `53cb5c3`), along with the paired
bootstrap (#20) and toolkit (#316) PRs. Field testing of the merged code then surfaced a **defect in
the cross-JVM lock** — the 0003 §3 guard did not actually hold. Written up, fixed, and field-verified
as **[ADR 0004](../decisions/0004-per-jvm-cache-lock-defect.md) (accepted)**. Fix is in the working
tree, **uncommitted**.

**The 0004 defect in one line:** the lockFile lock was acquired per `Cache`, but a JVM mounts several
file systems, so the second mount's `close()` silently dropped the first's `fcntl` lock (POSIX
close-releases-all-locks). Result: JVMs sharing a location got **no** exclusion and silently shared
one disk store. Fix: acquire the lock once per JVM, held for JVM lifetime; `Cache` never reopens the
lockFile. See 0004 for the full mechanism, field evidence, and the remedy.

## This session (2026-07-24)

- **Reproduced the 0004 lock defect live** on `lsstcam-mcm` (local ext3). Staggered `ccs-shell`
  launches → 3 live shells holding `lockFile` open with **zero** locks in `/proc/locks`, all sharing
  one `default.data`; burst launch (`for … &`) → spill `ccs-shell-1 … -4` by winning the startup
  race. Same build, opposite symptoms, timing-dependent — confirming the guard is a race, not a lock.
  This also reframes 0003's earlier "spill verified" as timing luck, not a working guard.
- **Wrote up ADR 0004**; superseded-banner on 0003 §3; correction note on the toolkit guide's
  Verification section; README index row.
- **Implemented the 0004 remedy** (uncommitted, working tree):
  - `RestFileSystemOptionsHelper.lockCacheLocation()` — acquires location + lock once per JVM,
    memoized static holds the channel + lock for the JVM lifetime; spill walk moved here. Added
    `isCacheLockHeldForTest()`; `reset`/`setGlobalCacheConfigForTest` now release the lock.
  - `Cache` — reads the resolved location, no longer opens `lockFile`; `close()` no longer releases
    the lock; dropped the `FileChannel`/`FileLock`/`OverlappingFileLockException` machinery.
  - Tests — `CacheLockHolder` is now **multi-mount** (2 caches), so all 3 `CacheLockCrossJvmTest`
    cases regression-guard the defect; `sameJvmCacheShareTest` asserts the lock survives a second
    mount + a mount close. **Client suite green (43); war+client `install` green.**
- **Version bump:** all 5 poms `1.1.10-SNAPSHOT → 1.1.11-SNAPSHOT` (the branch was cut pre-release
  and never bumped; `main` is 1.1.11-SNAPSHOT via the release-plugin commit `564a544`). The deployed
  client jar must be `…-client-1.1.11-SNAPSHOT.jar` to match the toolkit.
- **Field-verified the fix** on `lsstcam-mcm` (ext3): two **staggered** shells → two dirs
  (`ccs-shell`, `ccs-shell-1`) with **one real POSIX WRITE lock per JVM** on its own lockFile inode
  in `/proc/locks` — vs. the pre-fix run's 3 live shells / 1 store / **0 locks**. 0004 → *accepted*.
- **Toolkit-compatibility verified** (audit of `org-lsst-ccs-toolkit`): all `ccs://` mounts go
  through `RemoteFileServer` (one `newFileSystem` site, one `close()` site); every `close()` is
  terminal shutdown or precedes `System.exit`, so there is **no** close-then-reopen-same-location in a
  live JVM — the only pattern the JVM-lifetime lock could affect. Nothing references the cache lock or
  the removed builder methods. Recorded in [ADR 0004](../decisions/0004-per-jvm-cache-lock-defect.md)
  Consequences. Toolkit pom still pins client `1.1.10-SNAPSHOT` (`core/configuration/pom.xml`) — bump
  to 1.1.11 at release to consume the fix; usage needs no change.

## Next up

- **Commit + PR** (client code + version bump + 0004 docs). Client-only change; no bootstrap/toolkit
  code coupling this time. **Branch topology:** `LSSTCCS-3029` trails `main` by the PR-#13 merge +
  release commits; the manual 1.1.11 bump here duplicates what a rebase onto `main` would bring.
  Decide rebase-onto-main vs. new branch off `main` before opening the PR, to avoid a
  redundant-looking version diff.
- At toolkit release: bump its client dependency `1.1.10-SNAPSHOT → 1.1.11` to pick up the fix.
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
