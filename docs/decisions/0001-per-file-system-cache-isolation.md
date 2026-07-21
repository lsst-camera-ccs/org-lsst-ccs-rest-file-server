# 0001 — Per-file-system cache isolation

- Status: largely superseded by [0003](0003-shared-per-jvm-cache.md)
- Date: 2026-07-20
- Ticket: LSSTCCS-3029

## Context

The CCS bootstrap adds several `ccs://` remote file systems at JVM start, before the
toolkit's configuration layer is available. We want those mounts to default to
`MEMORY_AND_DISK` caching. The current cache implementation was written as if there is
one file system per JVM, so making several mounts use a disk cache fails.

Two layers share state across file systems, and both break under multiple mounts:

1. **Disk path + file lock.** `CACHE_LOCATION` is used as the literal leaf directory
   that gets locked (`Cache.java`) and written into JCS `DiskPath`. Two `MEMORY_AND_DISK`
   file systems pointed at the same location — the second throws `"Cache already in use"`
   (asserted today by `cacheLockTest` in `CachingTest`).
2. **JCS region (JVM-global singleton).** Every `Cache` calls
   `CompositeCacheManager.getUnconfiguredInstance()` and `JCS.getInstance("default")`
   with the region name hardcoded to `"default"` and the disk auxiliary hardcoded to
   `"DC"`. Even with distinct directories, all file systems collapse onto one shared
   region, one disk store, and one `MaxObjects=1000` budget; the last `ccm.configure(props)`
   wins.

The existing `ALLOW_ALTERNATE_CACHE_LOCATION` fallback (spilling to `<dir>-1`, `-2`
siblings) is not a solution: it only addresses layer 1, still shares the `"default"`
region (layer 2), and assigns suffixes by construction order — so a server does not
reliably reattach to its own cache after a restart.

## Decision

Give each remote file system its own JCS region *and* its own disk directory, auto-derived
from a stable per-server key computed from `RestFileSystem.getFullURI()` (server URI +
mount point).

- The key is sanitized to a filesystem- and JCS-safe token and made deterministic across
  restarts (sanitized host/port/path plus a short stable hash of the full URI), so a
  server reattaches to its own on-disk cache.
- `CACHE_LOCATION` is reinterpreted as a **base** directory; each file system uses
  `<base>/<key>` as its disk directory. The per-directory lock keeps its real purpose:
  guarding against a *different process* reusing the same cache.
- The JCS `CompositeCacheManager` remains a singleton, but hosts N independent regions —
  one per server — each with its own disk auxiliary and `DiskPath`. The `memory.ccf` /
  `disk.ccf` resources become templates whose region/auxiliary/disk-path tokens are
  substituted per instance.

The bootstrap then needs only a single default policy (one base `CACHE_LOCATION`, or none)
and every mount fans out to its own cache with no per-mount configuration.

## Consequences

- The premise of `cacheLockTest` is intentionally reversed: two servers sharing a base
  `CACHE_LOCATION` now both succeed. That test is rewritten to assert isolation, plus a
  case proving the lock still fires for genuine same-directory contention.
- `CACHE_LOCATION` changes meaning (leaf → base directory). Callers are internal, but any
  deployed config relying on the old leaf semantics must be reviewed.
- Repeated `ccm.configure(props)` on the singleton must accumulate regions rather than
  reset existing ones. If it turns out to be destructive, the implementation switches to
  programmatic region creation (`ccm.getCache(region, attrs)` plus a manually attached
  disk auxiliary). This is the main technical risk and is covered by a test.

## Out of scope

Making `MEMORY_AND_DISK` the *default* policy at bootstrap, and the related wiring gap
where a non-null empty `NO_ENV` map shadows the `DEFAULT_ENV_PROPERTY` system property in
`RestFileSystemProvider.newFileSystem`, are separate concerns tracked in HANDOFF. This ADR
covers only the isolation mechanism that must exist before that default is safe.
