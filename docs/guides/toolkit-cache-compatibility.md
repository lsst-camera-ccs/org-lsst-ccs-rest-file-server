# Toolkit remote-file-server cache behavior under the client changes

This guide records how the toolkit's three remote file servers (configuration, persistence,
dictionaries) behave with the LSSTCCS-3029 client changes — per-file-system cache isolation
(ADR 0001), the option-resolution cascade (ADR 0002), and `~` expansion — and, specifically,
whether the `DEFAULT_ENV_PROPERTY` system property alters them.

## Scenario verified

JVM launched with:

```
-Dorg.lsst.ccs.rest.file.client.defaultEnvironment='{"CacheOptions":"MEMORY_AND_DISK","CacheLocation":"~/ccs/cache/remoteFileSystem"}'
```

The `~/ccs/cache/remoteFileSystem` value was also checked against the earlier
`/var/lib/ccs/rest-cache` variant; the conclusion is the same for both.

## Toolkit classes examined

All three toolkit services create their `ccs://` file system through a single factory,
`RemoteFileServer.createFileSystem` (`core/configuration/.../RemoteFileServer.java`):

- `RestFileServerRemoteDAO` — mount point `config`
- `PersistencyService.RemotePersistencyDAO` — mount point `persistence`
- `RemoteDictionaryDAO` — caller-supplied mount point; sets `cacheFallback=when_possible`

The factory builds an **explicit** environment map with the `RestFileSystemOptions.builder()`,
always setting `MountPoint`, `CacheLocation` (`~/ccs/cache/<cacheName>` as a `File`),
`CacheOptions.MEMORY_AND_DISK`, `ignoreLockedCache(true)`, a `CacheFallback`, and an `SSLOptions`.

## Result: the property does not change these three services

The option cascade (ADR 0002) resolves each key with the explicit `env` at the **highest**
precedence. Because the factory sets `CacheOptions` and `CacheLocation` explicitly, the
system property's values for those keys are overridden. The property only reaches a mount for
keys the builder leaves unset — and the builder leaves neither of the two keys in this
property unset. So config, persistence, and dictionaries keep caching under
`~/ccs/cache/<cacheName>/...`, not under the property's path.

The `~` expansion added for string `CacheLocation` values also does not apply here: the
factory passes `CacheLocation` as a `File` (`cacheDir.toFile()`), which resolves `user.home`
itself and skips the string branch of `getDiskCacheLocation()` entirely.

### Effective options for the three services in this scenario

Assumes no `org.lsst.ccs.config.remote.cacheOnly`, and a non-dev server URI.

| Option | config | persistence | dictionaries | Source (all three) |
|--------|--------|-------------|--------------|--------------------|
| `CacheOptions` | `MEMORY_AND_DISK` | `MEMORY_AND_DISK` | `MEMORY_AND_DISK` | explicit env (property matches, so redundant) |
| `CacheLocation` (base) | `~/ccs/cache/<desc>/config/` | `~/ccs/cache/<desc>/persistence/` | `~/ccs/cache/<desc>/<mount>/` | explicit env — **property path ignored** |
| `MountPoint` | `config/` | `persistence/` | `<mount>/` | explicit env |
| `CacheFallback` | `OFFLINE` | `OFFLINE` | `WHEN_POSSIBLE` | default / default / explicit (`RemoteDictionaryDAO`) |
| `CacheFallbackLocation` | `true` | `true` | `true` | explicit env (`ignoreLockedCache`) |
| `UseSSL` | `AUTO`¹ | `AUTO`¹ | `AUTO`¹ | explicit env |
| `CacheLogging` | `false` | `false` | `false` | hardcoded fallback |
| `JWTToken` | none | none | none | hardcoded fallback |

¹ `TRUE` if the server URI contains `lsst-camera-dev.slac.stanford.edu` (dev workaround);
`AUTO` otherwise. If `org.lsst.ccs.config.remote.cacheOnly=true`, every `CacheFallback`
becomes `ALWAYS`.

## Two effects that *do* reach the services when the toolkit upgrades

These follow from the client changes themselves, independent of the property:

1. **Per-server disk subdirectory.** `CacheLocation` is now a base directory, so the disk
   cache moves from `~/ccs/cache/<desc>/config/` to `~/ccs/cache/<desc>/config/<key>/`
   (`<key>` = sanitized `getFullURI()` + short hash). Not breaking, but any existing on-disk
   cache at the old path is orphaned — a one-time cold cache after the upgrade.
2. **Region isolation.** Previously every mount shared the single JCS `"default"` region;
   each now gets its own region keyed by `getFullURI()`. The three services already use
   distinct mount points, so they become genuinely isolated, and their `ignoreLockedCache`
   workaround is moot for distinct servers.

## How this was verified, and its limits

This is **source-level analysis** of the toolkit at
`/home/turri/Code/LSST/ccs/org-lsst-ccs-toolkit` against the client on branch
`LSSTCCS-3029` — not a runtime JVM launch. No public client API changed, so the toolkit
compiles and links unchanged; the toolkit only uses the public builder/enum surface.

Version note: the toolkit currently depends on `org-lsst-ccs-rest-file-server-client`
**1.1.8** (`core/configuration/pom.xml`); the changes described here are on
**1.1.10-SNAPSHOT**. The analysis describes behavior once the toolkit upgrades to a build
containing them. A runtime cross-build (install the SNAPSHOT client locally, build the
toolkit against it) has not been done.

## Takeaway

The property is safe to set: it does not change config/persistence/dictionary caching. If the
intent is for those services to honor a central cache location, that requires a change in
`RemoteFileServer.createFileSystem` (stop hardcoding `cacheLocation`), not in the client.
