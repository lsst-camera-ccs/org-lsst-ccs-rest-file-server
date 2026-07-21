# Toolkit remote-file-server cache behavior

How the toolkit's three remote file servers (configuration, persistence, dictionaries) behave
with the LSSTCCS-3029 client changes, and how the `DEFAULT_ENV_PROPERTY` system property affects
them.

> **Currency:** describes the branch as built for [ADR 0003](../decisions/0003-shared-per-jvm-cache.md)
> (one shared cache per JVM). Supersedes the earlier ADR 0001 description.

## Setup

All three services create their `ccs://` file system through one factory,
`RemoteFileServer.createFileSystem`, which builds an env map via `RestFileSystemOptions.builder()` —
setting `MountPoint`, `CacheLocation` (`~/ccs/cache/<name>` as a `File`), `CacheOptions.MEMORY_AND_DISK`,
`ignoreLockedCache(true)`, a `CacheFallback`, and `SSLOptions`. Mount points: `config`, `persistence`,
and a caller-supplied one for dictionaries (which sets `cacheFallback=WHEN_POSSIBLE`).

## The toolkit must rebuild against the new client

Under 0003 the cache **location** and the **spill flag** are JVM-global: the client resolves them
once from `DEFAULT_ENV_PROPERTY` (or the built-in default `~/ccs/cache/default`) and no longer reads
the per-file-system `CacheLocation` / `CacheFallbackLocation` values. The per-FS builder methods
`cacheLocation()` and `ignoreLockedCache()` are **removed** from the client API, so the factory's
`.cacheLocation(...)` / `.ignoreLockedCache(true)` calls no longer compile — the toolkit must rebuild
against the new client and drop them. This is done in the toolkit's LSSTCCS-3029 PR (bump client to
1.1.11 + remove the calls); it is a required coordinated change, not optional cleanup.

`CacheOptions` and the `CacheFallback` policy are still per-mount, so the three services keep their
distinct caching strategies (e.g. dictionaries `WHEN_POSSIBLE`, config/persistence `OFFLINE`) — that
half is unaffected.

## Effects on upgrade

1. **One shared cache per JVM.** All three services share a single JCS `default` region and one disk
   store at the resolved global location. They no longer cache under per-service `~/ccs/cache/<name>`
   subdirectories; existing caches at the old paths are orphaned — a one-time cold cache.
2. **Location comes from the property, not the factory.** With the property unset, all three land on
   `~/ccs/cache/default`. To give a JVM its own reattaching cache, set the property (the CCS bootstrap
   does this with an `<app|default>` token — see
   [bootstrap ADR 0001](../../../org-lsst-ccs-bootstrap/docs/decisions/0001-substitution-tokens-in-java-opts.md)).
3. **Shared `MaxObjects` budget.** The three services share one region's budget (raised to 2500);
   they can evict each other. Eviction is cheap — an evicted entry is re-fetched.

## Verification

Source-level analysis of the toolkit at `/home/turri/Code/LSST/ccs/org-lsst-ccs-toolkit` against the
branch — not a runtime launch. The per-FS builder methods `cacheLocation()`/`ignoreLockedCache()` are
**removed**, so the toolkit does not compile against the new client until its LSSTCCS-3029 PR lands
(client bumped to **1.1.10-SNAPSHOT**, calls removed). The toolkit previously depended on client
**1.1.8**.

## Takeaway

The property is safe to set and now *does* drive the toolkit services' cache location (they share one
per-JVM cache). Adopting the new client requires the toolkit to rebuild and drop the removed
`.cacheLocation()` / `.ignoreLockedCache()` calls — done in its LSSTCCS-3029 PR.
