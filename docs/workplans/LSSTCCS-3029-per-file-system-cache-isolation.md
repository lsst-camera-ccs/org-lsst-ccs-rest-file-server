# LSSTCCS-3029 — Per-file-system cache isolation

- Branch: `LSSTCCS-3029`
- ADR: [0001 — Per-file-system cache isolation](../decisions/0001-per-file-system-cache-isolation.md)
- Line numbers anchored to commit `058fe1a`; use `git show 058fe1a:<path>` if they look off.

## Goal

Each `ccs://` remote file system gets its own JCS region and its own disk directory,
auto-derived from a stable per-server key, so multiple bootstrap mounts can all use
`MEMORY_AND_DISK` without lock collisions or a shared region.

## Steps

### 1. Per-server key

Add a helper that turns `RestFileSystem.getFullURI().toString()` into a filesystem- and
JCS-safe token: sanitize host/port/path to `[A-Za-z0-9_]` and append a short stable hash
(first 8 hex of SHA-256 of the full URI) to disambiguate sanitized-equal URIs. Must be
deterministic across restarts. Keep it inside `Cache` unless it needs reuse.

- `client/.../implementation/Cache.java`

### 2. Pass the key into the cache

`RestFileSystem` constructs `new Cache(options)` at `RestFileSystem.java:69`. Change to
`new Cache(options, getFullURI())` and thread the URI through the `Cache` constructor
(currently `Cache(RestFileSystemOptionsHelper)`, `Cache.java:45`). `getFullURI()` is
available at that point (uri/mountPoint set at `RestFileSystem.java:64-65`).

- `client/.../implementation/RestFileSystem.java`
- `client/.../implementation/Cache.java`

### 3. Per-server disk directory (layer 1)

Reinterpret `CACHE_LOCATION` as a base directory. In `Cache`, join `<base>/<key>` to form
the per-server disk dir before the lock/`DiskPath` logic (`Cache.java:62-93`).
`getDiskCacheLocation()` in `RestFileSystemOptionsHelper` (`:90`) still returns the base
unchanged; the subdir join happens in `Cache`. The lock then guards `<base>/<key>/lockFile`,
distinct per server. `ALLOW_ALTERNATE_CACHE_LOCATION` behavior is unchanged.

- `client/.../implementation/Cache.java`

### 4. Per-server JCS region + auxiliary (layer 2)

Tokenize the two resource files so the region name, auxiliary name, and disk path are
placeholders (e.g. `%REGION%`, `%AUX%`, `%DISKPATH%`) instead of the hardcoded
`default` / `DC` / `${user.home}/jcs_swap`:

- `client/src/main/resources/org/lsst/ccs/rest/file/server/client/implementation/memory.ccf`
- `client/src/main/resources/org/lsst/ccs/rest/file/server/client/implementation/disk.ccf`

In `Cache`, load the template, substitute the per-server key into the tokens, then
`ccm.configure(props)` and `map = JCS.getInstance("<region-key>")` (replacing the
hardcoded `"default"` at `Cache.java:97`). The `CompositeCacheManager` stays a singleton
hosting N regions.

- `client/.../implementation/Cache.java`

## Tests

- **Rewrite `cacheLockTest`** (`CachingTest.java:172-209`). Current premise (two servers,
  same `CACHE_LOCATION` → second fails "in use") is reversed by this change: with
  per-server keying both should now succeed against a shared base. Assert that, and add a
  case that still proves the lock fires for genuine same-directory contention (two `Cache`
  instances on the identical resolved dir).
- **New isolation test** (`CachingTest` or new `CacheIsolationTest`): two `TestServer`s,
  `MEMORY_AND_DISK` + shared base `CACHE_LOCATION`. Assert both construct, `getCache()` on
  each resolves a different region/disk dir, and an entry cached for one server is absent
  from the other's cache (extend the `cache.getEntry(...)` pattern at `CachingTest.java:68`).
- **`ccm.configure` accumulation check**: the isolation test doubles as the guard for the
  main risk — confirm the second region does not reset the first. If it does, switch to
  programmatic region creation per the ADR.

## Verify

- `mvn -pl war,client -am install` (client tests need the war test-jar), or
  `mvn -Dtest=CachingTest test -pl client` for the focused loop.

## Risks

- `ccm.configure(props)` repeated on the singleton — see accumulation check above.
- `CACHE_LOCATION` leaf → base semantic change; review deployed config before release.
