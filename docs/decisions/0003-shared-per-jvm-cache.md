# 0003 — One shared cache per JVM, global cache config, policy in the file system

- Status: proposed (supersedes much of [0001](0001-per-file-system-cache-isolation.md); amends [0002](0002-option-resolution-cascade.md))
- Date: 2026-07-20 (design refined 2026-07-21)
- Ticket: LSSTCCS-3029

## Context

ADR 0001 gave each `ccs://` file system its own JCS region and disk subdirectory to stop
multiple mounts in one JVM from colliding on the cache lock. Tony (the original author of the
lock) clarified its intent: the lock guards against a **different JVM on the host** reusing the
same cache directory. It was never meant to keep file systems *within one JVM* apart.

The requirement is the opposite of 0001: a JVM with several remote file systems should use
**one** cache location backed by **one** storage, shared by all its mounts. This is driven by
`ccs-shell` and `ccs-console`, which read dictionaries; the cache exists so camera subsystems
**start when the server is down**, from the freshest data they already hold.

Two facts make this safe. Dictionary URLs embed a checksum, so cached content is immutable per
URL — "never expire" is correct, not stale. Agent-name uniqueness is enforced by the messaging
system, so a role agent (`focal-plane`, `mcm`, …) is the only holder of its name and reattaches
to its own cache across restarts; shells/consoles get unique, non-reproducible names and simply
do not reattach, which is acceptable.

## Decision

### 1. One cache per JVM

A single location, JCS region (`default`), and disk store shared by every mount. This reverts
0001's per-file-system region and subdirectory and the `.ccf` tokenization. `MaxObjects` in
`memory.ccf` rises to **2500** since mounts now share one region's budget.

### 2. Cache location and spill flag are JVM-global

`CacheLocation` and `CacheFallbackLocation` (the spill flag) describe the single per-JVM cache, so
they are resolved **once, before the first file system is created**, and *not* per mount. Their
resolution order is: a package-private test backdoor → the `DEFAULT_ENV_PROPERTY` JSON map → a
built-in default. Per-file-system `env` no longer supplies these two keys; the builder methods
`cacheLocation()` and `ignoreLockedCache()` are `@Deprecated` and become **no-ops**. This
**amends [0002](0002-option-resolution-cascade.md)**, which resolved *every* key through the
per-key cascade — these two keys now leave it.

The built-in default location is `~/ccs/cache/default`. The resolver expands a leading `~`
(`~` or `~/…`; `~user` untouched) and applies lexical `Path.normalize()` (`./`, `../`), with no
symlink resolution or env-var expansion — the same scope as 0002, relocated to the global path
and applied to the built-in default too.

**How the properties are set.** The global config arrives through the `DEFAULT_ENV_PROPERTY` system
property (`org.lsst.ccs.rest.file.client.defaultEnvironment`), a JSON map carrying `CacheOptions`,
`CacheLocation`, and (if needed) `CacheFallbackLocation`, e.g.

```
-Dorg.lsst.ccs.rest.file.client.defaultEnvironment='{"CacheOptions":"MEMORY_AND_DISK","CacheLocation":"~/ccs/cache/focal-plane"}'
```

Because the location is resolved **once, before the first `ccs://` file system is created**, this
property must be in place at JVM startup — it cannot be set from application code that runs after a
mount already exists. Two ways to set it:

- **Bootstrap (preferred).** The CCS bootstrap sets the property as it launches the JVM, injecting a
  **unique per-host location** (e.g. the agent name) so a role agent reattaches to its own cache
  across restarts. This is the intended mechanism — the bootstrap already knows the agent identity
  and owns JVM launch.
- **App/launch files.** A `-D` in the app's launch configuration works for one-off or non-agent JVMs,
  but the operator is then responsible for location uniqueness.

A JVM that sets neither falls to the built-in default `~/ccs/cache/default` (shared, non-reattaching —
fine for anonymous shells).

### 3. Lock guards cross-JVM only; same-JVM mounts share

The lock on `<loc>/lockFile` has three outcomes, distinguished by JVM-scoped `FileLock` semantics:

- **`tryLock()` returns a lock** — nobody holds it. This mount owns the location; keep the lock.
- **`OverlappingFileLockException`** — another mount in *this* JVM already holds it. Share the
  location: take no lock, close the probe channel, stop. (JCS region and disk store are JVM-wide
  singletons, so sharing is automatic once the lock stops rejecting us.)
- **`tryLock()` returns `null`** — another *process* holds it. Spill to `<loc>-1`, `-2`, … if the
  global spill flag is set, else fail "in use".

A foreign process yields `null`; our own JVM *throws* — that asymmetry is what separates "share"
from "spill". The spill flag defaults to `false` (fail loud), so a non-unique location surfaces as
an error rather than silently degrading. Deployments that want anonymous shells to spill set
`CacheFallbackLocation:true` in the JSON. Spill acts on the *resolved* location regardless of
provenance: a non-unique *provided* location spills exactly as the default does.

Known limitation: the OS lock is held by whichever mount acquired it first and released when
*that* mount closes, not when the last sharer closes. Bootstrap mounts live for the JVM lifetime
and close together, so this is low risk.

### 4. Policy moves out of `Cache` into the file system

`Cache` becomes policy-free storage (`URI → CacheEntry` + disk/lock). The expiry decision — today
`Cache.doEntriesExpire()`, i.e. `fallback != WHEN_POSSIBLE` — moves into the per-mount
`CacheRequestFilter`, which already owns the `cacheOnly`/offline half. Without this, a shared
`Cache`'s single `cacheFallback` field would force one policy on all mounts — and the toolkit runs
config/persistence as `OFFLINE` alongside dictionaries as `WHEN_POSSIBLE` in one JVM. So
`CacheOptions` and the `CacheFallback` *policy* stay per-file-system (via the 0002 cascade);
only the two location keys go global. `CacheOptions` keeps its `NONE` default — caching is opt-in
via the JSON, so plain callers do not write a disk cache under the always-present default location.

## Consequences

- Reverts 0001: per-server region/subdir logic, `.ccf` tokenization, and `multiServerSharedBaseTest`
  are removed or rewritten.
- The client stops *reading* per-FS `CacheLocation`/`CacheFallbackLocation`, so the toolkit's
  `.cacheLocation()`/`.ignoreLockedCache()` calls (client 1.1.8) become inert without a rebuild.
  The toolkit change is therefore **cosmetic cleanup, not a required coordinated change**.
- Sharing one region means sharing its `MaxObjects` budget (now 2500); mounts can evict each other.
  Eviction is cheap — an evicted immutable entry is re-fetched, or fails per the rules below.
- Accepted failures, both allowed to be loud: no free cache slot after spill → fail at construction;
  offline with an uncached entry → fail at read. The cache is a best-effort warm-start reservoir.
- `CacheEntry.isExpired` (serialized but always overwritten) is removed; removing the field is
  read-compatible with existing on-disk entries (the field in the stream is ignored), so no
  `serialVersionUID` bump. `SpeedTest`'s `setCacheFallbackOption` hook moves to the file system.

## Out of scope

The per-agent-category default policy (`OFFLINE` vs `WHEN_POSSIBLE`, memory vs disk) and the
bootstrap `defaultEnvironment` values for base and summit are deployment-config choices, tracked
in HANDOFF.
