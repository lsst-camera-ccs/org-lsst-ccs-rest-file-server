# HANDOFF

- Anchor: branch `LSSTCCS-3029`, as of the commit that carries this file.
- On resume: `git log --oneline main..HEAD` to see what moved on the branch.

## State

Branch `LSSTCCS-3029` has the ADR 0001 + 0002 work implemented, tested (35 client tests pass),
and committed (`666f59c` isolation, `7ecd9ea` cascade, `781dcb9` ~ expansion, `4a19526` docs).

A design session with Tony then **reversed direction on 0001**. See
[ADR 0003](../decisions/0003-shared-per-jvm-cache.md) (proposed): one shared cache per JVM
instead of per-file-system isolation. 0002 (the option cascade) stands and is depended on. No
0003 code written yet — the branch still contains the 0001 isolation code that 0003 unwinds.

## Next up

Implement ADR 0003 (client + a coordinated toolkit change):

1. **Client — collapse to one cache per JVM.** Undo 0001's per-server region + disk subdir and
   `.ccf` tokenization in `Cache`. Restore the lock as a cross-JVM-only guard with `<name>-N`
   spill on collision.
2. **Client — move policy out of `Cache`.** Make `Cache` policy-free storage; move the expiry
   decision (`doEntriesExpire`) into the per-mount `CacheRequestFilter`. Drop
   `CacheEntry.isExpired` and `setCacheFallbackOption`; adjust `SpeedTest`.
3. **Client — tests.** Remove/rewrite `multiServerSharedBaseTest`; restore `cacheLockTest`'s
   cross-process premise.
4. **Toolkit.** `RemoteFileServer.createFileSystem` must stop setting `CacheLocation` so the
   bootstrap system-property name reaches every mount. Toolkit is at
   `/home/turri/Code/LSST/ccs/org-lsst-ccs-toolkit` (still on client 1.1.8).

## Deferred (deployment-config, not code)

- Raise `MaxObjects` in the `.ccf` — mounts now share one region's budget.
- Per-agent-category default policy: role agents (unique, reproducible names) reattach with a
  persistent disk cache; `ccs-shell`/`ccs-console` (unique, non-reproducible names) don't
  reattach — decide `OFFLINE` vs `WHEN_POSSIBLE` and memory vs disk per category.
- Bootstrap default `defaultEnvironment` values/location for base and summit.
