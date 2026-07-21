# Toolkit remote-file-server cache behavior

How the toolkit's three remote file servers (configuration, persistence, dictionaries) behave
with the LSSTCCS-3029 client changes, and whether the `DEFAULT_ENV_PROPERTY` system property
affects them.

> **Currency:** describes the branch *as built* (ADR 0001 + 0002). [ADR 0003](../decisions/0003-shared-per-jvm-cache.md)
> (proposed) reverses the per-server isolation in "Effects on upgrade" below and adopts the
> toolkit change in "Takeaway". Revisit when 0003 lands.

## Setup

All three services create their `ccs://` file system through one factory,
`RemoteFileServer.createFileSystem`, which builds an **explicit** env map via
`RestFileSystemOptions.builder()` — always setting `MountPoint`, `CacheLocation`
(`~/ccs/cache/<name>` as a `File`), `CacheOptions.MEMORY_AND_DISK`, `ignoreLockedCache(true)`,
a `CacheFallback`, and `SSLOptions`. Mount points: `config`, `persistence`, and a
caller-supplied one for dictionaries (which also sets `cacheFallback=WHEN_POSSIBLE`).

Scenario checked:
`-Dorg.lsst.ccs.rest.file.client.defaultEnvironment='{"CacheOptions":"MEMORY_AND_DISK","CacheLocation":"~/ccs/cache/remoteFileSystem"}'`.

## The property does not change these three services

The cascade (ADR 0002) resolves each key with explicit `env` at highest precedence. The factory
sets `CacheOptions` and `CacheLocation` explicitly, so the property's values for those keys are
overridden — it only reaches keys the builder leaves unset, and it leaves neither unset. `~`
expansion also doesn't apply: `CacheLocation` is passed as a `File`, which skips the string
branch of `getDiskCacheLocation()`. So the three services keep caching under
`~/ccs/cache/<name>/...`, and the property is safe to set.

## Effects on upgrade (from the client changes, not the property)

1. **Per-server subdirectory.** `CacheLocation` is now a base dir, so the disk cache moves to
   `~/ccs/cache/<name>/<key>/`. Existing caches at the old path are orphaned — a one-time cold
   cache.
2. **Region isolation.** Each mount gets its own JCS region instead of the shared `default`.
   The three services use distinct mount points, so they become genuinely isolated.

## Verification

Source-level analysis of the toolkit at `/home/turri/Code/LSST/ccs/org-lsst-ccs-toolkit`
against the branch — not a runtime launch. No public client API changed, so the toolkit
compiles unchanged. The toolkit depends on client **1.1.8** (`core/configuration/pom.xml`);
these changes are on **1.1.10-SNAPSHOT**, so the behavior above applies once it upgrades. No
runtime cross-build done.

## Takeaway

The property is safe to set; it does not change config/persistence/dictionary caching. Making
those services honor a central cache location requires changing
`RemoteFileServer.createFileSystem` to stop hardcoding `CacheLocation` — which is exactly what
ADR 0003 proposes.
