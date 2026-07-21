# 0003 — One shared cache per JVM, policy in the file system

- Status: proposed (supersedes much of [0001](0001-per-file-system-cache-isolation.md); keeps [0002](0002-option-resolution-cascade.md))
- Date: 2026-07-20
- Ticket: LSSTCCS-3029

## Context

ADR 0001 gave each `ccs://` file system its own JCS region and disk subdirectory to stop
multiple mounts in one JVM from colliding on the cache lock. Tony (the original author of the
lock) clarified its intent: the lock guards against a **different JVM on the host** reusing the
same cache directory. It was never meant to keep file systems *within one JVM* apart.

The requirement is now the opposite of 0001: a JVM with several remote file systems should use
**one** cache location backed by **one** storage, shared by all its mounts. This is driven by
`ccs-shell` and `ccs-console`, which read dictionaries. The cache exists so camera subsystems
**start when the server is down**, from the freshest data they already hold.

Two facts make this safe. Dictionary URLs embed a checksum, so cached content is immutable per
URL — "never expire" is correct, not stale. Agent-name uniqueness is enforced by the messaging
system, so a role agent (`focal-plane`, `mcm`, …) is the only holder of its name and reattaches
to its own cache across restarts; shells/consoles get unique, non-reproducible names and simply
do not reattach, which is acceptable.

## Decision

1. **One cache per JVM.** A single location, JCS region, and disk store shared by every mount.
   This reverts 0001's per-file-system region and subdirectory.

2. **Location from the bootstrap.** The bootstrap sets a unique cache name via the
   `DEFAULT_ENV_PROPERTY` system property, delivered by the 0002 cascade. This requires a
   coordinated toolkit change: `RemoteFileServer.createFileSystem` must stop setting
   `CacheLocation` so the property reaches every mount (explicit env wins the cascade).

3. **Lock returns to its original role.** It guards only against another JVM on the host using
   the same location. On collision the location spills to `<name>-1`, `-2`, … Because role names
   are unique, spill is an anomaly path for them; shells that collide spill and don't reattach.

4. **Policy moves out of `Cache` into the file system.** `Cache` becomes policy-free storage
   (`URI → CacheEntry` + disk/lock). The expiry decision — today `Cache.doEntriesExpire()`,
   i.e. `fallback != WHEN_POSSIBLE` — moves into the per-mount `CacheRequestFilter`, which
   already owns the `cacheOnly`/offline half. Without this, a shared `Cache`'s single
   `cacheFallback` field would force one policy on all mounts — and the toolkit already runs
   config/persistence as `OFFLINE` alongside dictionaries as `WHEN_POSSIBLE` in one JVM.

## Consequences

- Reverts 0001: the per-server region/subdir logic, the `.ccf` tokenization, and
  `multiServerSharedBaseTest` are removed or rewritten. `cacheLockTest`'s original premise
  (cross-process contention) is restored.
- Sharing a region means sharing its `MaxObjects` budget (1000 in the `.ccf`); mounts can evict
  each other. Likely needs raising. Eviction is cheap here — an evicted immutable entry is
  re-fetched, or fails per the rules below.
- Accepted failures: no free cache slot after spill → fail at construction (3a); offline with an
  uncached entry → fail at read (3b). The cache is a best-effort warm-start reservoir; both
  failures are allowed to be loud.
- `CacheEntry.isExpired` (serialized but always overwritten) is removed; `SpeedTest`'s
  `setCacheFallbackOption` hook moves to the file system.

## Out of scope

The `MaxObjects` value and the per-agent-category default policy (`OFFLINE` vs `WHEN_POSSIBLE`,
memory vs disk) are deployment-config choices, tracked in HANDOFF.
