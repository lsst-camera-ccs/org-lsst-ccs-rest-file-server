# LSSTCCS-3029 — One shared cache per JVM (ADR 0003)

- Branch: `LSSTCCS-3029`
- ADR: [0003 — One shared cache per JVM, global cache config, policy in the file system](../decisions/0003-shared-per-jvm-cache.md)
  (amends [0002](../decisions/0002-option-resolution-cascade.md))
- Line numbers anchored to commit `3d756fa`; use `git show 3d756fa:<path>` if they look off.
- Supersedes the [0001 workplan](LSSTCCS-3029-per-file-system-cache-isolation.md), which built the
  isolation this now unwinds.

## Goal

Collapse the per-file-system cache isolation from ADR 0001 back to **one shared cache per JVM**:
one JCS `default` region and one disk store, shared by every mount. Make the cache **location and
spill flag JVM-global** (resolved once, before any file system is created), keep **caching policy
per-mount**, and restore the lock to a **cross-JVM-only** guard where same-JVM mounts share instead
of colliding.

This is a **client-only** change. **No changes are required on the toolkit side**: the client stops
reading the per-FS `CacheLocation`/`ignoreLockedCache` values, so the toolkit's existing calls become
inert no-ops on upgrade — it keeps working unchanged. Removing those now-dead calls is optional
cleanup (step 6), not a prerequisite.

## Design summary (see ADR 0003 for the why)

- **One cache per JVM.** Region `default`, single disk store at the resolved location.
- **Global config.** `CacheLocation` + `CacheFallbackLocation` resolved once: test backdoor →
  `DEFAULT_ENV_PROPERTY` JSON → built-in default `~/ccs/cache/default`. Per-FS `env` for these two
  is ignored; builder setters deprecated + no-op.
- **Per-FS policy.** `CacheOptions` (default `NONE`) and `CacheFallback` still flow the 0002 cascade.
- **Lock.** Three `tryLock()` outcomes on `<loc>/lockFile`: lock → own it; overlap-exception →
  another mount in *this* JVM → share (no lock); `null` → another process → spill `<loc>-N` (if the
  global spill flag is set) or fail "in use". Flag defaults `false`.
- **Policy-free `Cache`.** Expiry decision moves to the per-mount `CacheRequestFilter`.

## Steps

### 1. Restore `.ccf` templates (revert 0001)

Undo the `%REGION%`/`%AUX%` tokenization; region `default`, auxiliary `DC`. Set `MaxObjects=2500`
in `memory.ccf`. Keep `disk.ccf`'s `DiskPath` unset (set programmatically in `Cache`).

- `client/src/main/resources/.../implementation/memory.ccf`
- `client/src/main/resources/.../implementation/disk.ccf`

### 2. Global cache-config resolution

`CacheLocation` and `CacheFallbackLocation` become JVM-global, resolved before the first FS:

- Add a global resolver (a static holder) that returns the location and spill flag from, in order:
  a package-private test backdoor → `DEFAULT_ENV_PROPERTY` JSON → built-in default
  `~/ccs/cache/default`. Move the leading-`~` + `Path.normalize()` handling
  (`RestFileSystemOptionsHelper.expandTilde`/`getDiskCacheLocation`, `:117-150`) into this resolver
  and apply it to the built-in default too.
- Stop reading these two keys from the per-FS merged env, so a per-FS `cacheLocation()` /
  `ignoreLockedCache()` value is inert (this is what makes the toolkit call a no-op without a rebuild).
- Deprecate `RestFileSystemOptions.Builder.cacheLocation()` and `ignoreLockedCache()`
  (`RestFileSystemOptions.java:172`, and the `cacheLocation` builder method) as no-ops; add the
  package-private backdoor setter(s) that relocate the globals for tests, resettable per test.

- `client/.../implementation/RestFileSystemOptionsHelper.java`
- `client/.../RestFileSystemOptions.java`

### 3. Collapse `Cache` to one shared cache + cross-JVM lock (revert 0001 + new share logic)

`Cache` constructor back to `Cache(RestFileSystemOptionsHelper)` — drop the `URI serverURI` param,
`regionKey`, `shortHash`, per-server subdir/`region` fields, and `getRegion()`. Region is `default`,
DiskPath is the resolved global location leaf.

Rewrite the lock loop for the three outcomes (ADR 0003 §3):
- `tryLock()` → non-null: keep the lock, `break`.
- `OverlappingFileLockException`: this JVM already owns the location → share (no lock; close the
  probe channel), record the location, `break`. Independent of the spill flag.
- `tryLock()` → `null`: another process → if the global spill flag set, walk to `<loc>-N`; else
  throw "in use".

Document the JVM-scoped-`FileLock` asymmetry and the release-on-acquiring-mount wart inline
(concise). Keep a resolved-disk-location accessor for tests; drop `getRegion()`.

- `client/.../implementation/Cache.java` (`:58-179`)
- `client/.../implementation/RestFileSystem.java` (`new Cache(options, getFullURI())` at `:69`)

### 4. Move caching policy out of `Cache` into the filter (ADR 0003 §4)

- `Cache.getEntry(uri)` → `return map.get(uri)` (no expiry stamping). Delete the `cacheFallback`
  field, `setCacheFallbackOption`, `doEntriesExpire` (`Cache.java:40,125,181-195`).
- Delete `CacheEntry.isExpired`/`setIsExpired` and the `isExpired` field (`Cache.java:229,267-273`).
  Removing the serialized field is read-compatible with old disk entries; no `serialVersionUID` bump.
- `CacheRequestFilter` gains a per-mount `doEntriesExpire` flag; `filter()`'s `!entry.isExpired()`
  becomes `!doEntriesExpire` (`CacheRequestFilter.java:44-52`).
- `RestFileSystem` computes both flags next to the existing `cacheOnly` and passes them
  (`RestFileSystem.java:70`): `cacheOnly = offline || fallback==ALWAYS`,
  `doEntriesExpire = fallback != WHEN_POSSIBLE`.
- `SpeedTest`'s two `getCache().setCacheFallbackOption(...)` calls (`SpeedTest.java:38,52`) move to a
  file-system-level hook that re-registers/updates the filter's policy.

- `client/.../implementation/Cache.java`, `CacheRequestFilter.java`, `RestFileSystem.java`
- `client/src/test/.../implementation/SpeedTest.java`

### 5. Tests

- **Share test** (replaces `multiServerSharedBaseTest`, `CachingTest.java:174-228`): two `TestServer`s
  in one JVM on the global location (set via backdoor). Assert both construct, resolve the **same**
  disk dir, and an entry cached via one mount is visible through the other. (Inverts 0001's isolation
  assertion.)
- **Cross-JVM guard** (surefire subprocess): a helper `main` in test sources reuses the real `Cache`
  to grab the lock on a location, prints a readiness marker, blocks. Parent (globals via backdoor)
  asserts: spill flag on → second `Cache` resolves `<loc>-1`; flag off → "in use". Knobs: readiness
  handshake + timeout, forced teardown (`destroyForcibly` in `finally`), helper self-terminate
  safety timeout. Linux-only; revisit if CI flakes.
- **Per-FS location inert:** assert a per-FS `cacheLocation()` does **not** change the resolved
  location (proves the toolkit call is dead).
- **Migrate** `CachingTest`/`SpeedTest`/`VersionedFileTest` off per-FS `cacheLocation(tempDir)` onto
  the backdoor; ensure the backdoor is reset after each test (static-leak caveat, cf.
  `DefaultEnvTest.clearDefaults`).
- Rewrite `cacheLockTest` (`CachingTest.java:236-268`) — its in-JVM "in use" premise is now the
  *share* path; the cross-JVM assertion moves to the subprocess test.

- `client/src/test/.../implementation/CachingTest.java`, `SpeedTest.java`, `VersionedFileTest.java`

### 6. Toolkit — no changes needed (optional cleanup)

**Nothing must change in the toolkit.** On client upgrade, `RemoteFileServer.createFileSystem`'s
`.cacheLocation(...)` and `.ignoreLockedCache(true)` (`core/configuration/.../RemoteFileServer.java:157,159`)
become inert no-ops — the toolkit keeps working unchanged. Removing those dead calls is optional
cleanup, done after the client work. Toolkit is version-coupled (client 1.1.8; this is 1.1.10-SNAPSHOT).

## Verify

- `mvn -pl war,client -am install` (client tests need the war test-jar), or
  `mvn -Dtest=CachingTest test -pl client` for the focused loop.
- Update the [toolkit cache-compatibility guide](../guides/toolkit-cache-compatibility.md) to the
  0003 model in the same PR (it currently describes the 0001 build).

## Risks

- **Shared-region behavior:** confirm all mounts genuinely resolve the same `default` region and disk
  store and that entries are cross-visible (share test covers this).
- **Subprocess test flakiness** in CI — mitigated by the knobs; fall back to the share-path-only
  coverage if it proves unstable.
- **Backdoor leak:** a test that forgets to reset the global would pollute others — enforce reset.

## Out of scope (deferred, deployment-config — see HANDOFF)

Per-agent-category default policy (`OFFLINE` vs `WHEN_POSSIBLE`, memory vs disk); bootstrap
`defaultEnvironment` values/location for base and summit.
