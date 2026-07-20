# HANDOFF

- Anchor: branch `LSSTCCS-3029`, as of the commit that carries this file.
- On resume: `git log --oneline main..HEAD` to see what moved on the branch.

## State

Two changes on branch `LSSTCCS-3029`, **implemented and tested, not yet merged**. All 35
client tests pass.

1. **Per-file-system cache isolation** (ADR 0001, workplan LSSTCCS-3029). Each `ccs://` file
   system gets its own JCS region and disk directory, keyed off a stable hash of
   `getFullURI()`. `CACHE_LOCATION` is now a *base* directory; each server caches under
   `<base>/<key>`. Committed as `666f59c`. The ADR's main risk is resolved: repeated
   `ccm.configure(props)` on the singleton **accumulates** regions rather than resetting them.

2. **Option resolution cascade** (ADR 0002). Options now resolve per key: explicit env →
   programmatic default → `DEFAULT_ENV_PROPERTY` system property → hardcoded fallback, merged
   in the `RestFileSystemOptionsHelper` constructor. This makes the system property live on
   the bootstrap `newFileSystem(uri, null)` path (it was previously shadowed by the non-null
   empty `NO_ENV` map). Auth token now read through the helper too. Not yet committed.

## Next up

- Commit the cascade work (ADR 0002 + helper/provider/RestFileSystem/DefaultEnvTest changes).
- Review + merge branch `LSSTCCS-3029`.
- Decide and set the actual bootstrap default policy (e.g. system property
  `{"CacheOptions":"MEMORY_AND_DISK","CacheLocation":"..."}`) — the mechanism now works; the
  chosen values/location for base and summit are a deployment-config decision, not code.
