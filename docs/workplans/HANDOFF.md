# HANDOFF

- Anchor: branch `LSSTCCS-3029`, as of the commit that carries this file.
- On resume: `git log --oneline main..HEAD` to see what moved on the branch.

## State

Per-file-system cache isolation (LSSTCCS-3029) is **implemented and tested** — not yet
merged. Each `ccs://` file system now gets its own JCS region and its own disk directory,
keyed off a stable hash of `getFullURI()`. `CACHE_LOCATION` is now a *base* directory; each
server caches under `<base>/<key>`. Changes: `Cache.java` (per-server key, region, disk
subdir, `getRegion()`/`getDiskCacheLocation()` getters), `RestFileSystem.java` (passes
`getFullURI()` to `Cache`), `disk.ccf` (tokenized `%REGION%`/`%AUX%`), and `CachingTest.java`
(rewrote `cacheLockTest` for same-server contention, added `multiServerSharedBaseTest`).

All 32 client tests pass. The ADR's main risk is resolved: repeated `ccm.configure(props)`
on the singleton **accumulates** regions rather than resetting them.

Background from the investigation: previously every `Cache` used the JVM-global
`CompositeCacheManager` singleton with the hardcoded region `"default"` / auxiliary `"DC"`,
so all file systems shared one region, disk store, and memory budget — and two
`MEMORY_AND_DISK` mounts sharing a `CACHE_LOCATION` collided on the disk lock. See ADR 0001
and workplan LSSTCCS-3029.

## Next up

- Review + merge LSSTCCS-3029.
- Then tackle the default-policy / wiring gap below (isolation is now safe under multiple
  mounts, so the sequencing precondition is met).

## Backlog / deferred

- **Default policy at bootstrap + system-property wiring gap.** We want bootstrap mounts to
  default to `MEMORY_AND_DISK`, but `RestFileSystemProvider.newFileSystem` (`:66-69`) sets
  `env = NO_ENV` (a non-null empty map) when the caller passes `null`, which shadows the
  `DEFAULT_ENV_PROPERTY` system property — `RestFileSystemOptionsHelper` only consults the
  property when `env` is null (`:31-37`), so the property is effectively dead on the normal
  path. Needs a decision on resolution order (per-key cascade vs. coarse first-non-empty)
  and where to resolve it. Graduate to its own ADR + workplan when it firms up. Sequencing:
  the isolation work (3029) should land first so the default is safe under multiple mounts.
