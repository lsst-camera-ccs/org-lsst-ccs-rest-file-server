# 0002 — Option resolution cascade for the ccs:// client

- Status: accepted (amended by [0003](0003-shared-per-jvm-cache.md) for two keys — see note below)
- Date: 2026-07-20
- Ticket: LSSTCCS-3029

> **Amended by [ADR 0003](0003-shared-per-jvm-cache.md).** The cascade below still governs
> per-file-system options (`CacheOptions`, `CacheFallback` policy, mount point, SSL, auth). But
> `CacheLocation` and `CacheFallbackLocation` describe the single per-JVM cache, so 0003 pulls them
> *out* of the per-key cascade and resolves them once, globally (test backdoor → `DEFAULT_ENV_PROPERTY`
> → built-in default `~/ccs/cache/default`); per-FS `env` no longer supplies them. The tilde/normalize
> handling below moves with `CacheLocation` to that global resolver.

## Context

The CCS bootstrap adds `ccs://` file systems at JVM start, before the toolkit's
configuration layer exists, by calling `newFileSystem(uri, null)`. The only configuration
mechanism available that early is a system property — `DEFAULT_ENV_PROPERTY`
(`org.lsst.ccs.rest.file.client.defaultEnvironment`), a JSON map — which is exactly what
it was designed for.

That property was effectively dead on the normal path. `RestFileSystemProvider.newFileSystem`
substituted a non-null empty map (`NO_ENV`) when the caller passed `null`, and
`RestFileSystemOptionsHelper` only consulted the property when its `env` was `null`. So the
non-null empty map shadowed the property, and every bootstrap mount silently fell through to
the hardcoded `CacheOptions.NONE` / `CacheFallback.OFFLINE`. Setting the property via `-D`
had no effect.

Separately, an explicit `env` completely *replaced* defaults rather than merging with them,
so a caller passing a partial map lost all defaults for the keys it omitted.

## Decision

Resolve each option key independently through a cascade, highest precedence first:

1. the explicitly supplied `env`,
2. the programmatic default (`RestFileSystemOptions.setDefaultFileSystemEnvironment`),
3. the `DEFAULT_ENV_PROPERTY` system-property JSON map,
4. the hardcoded fallback in `RestFileSystemOptionsHelper.getOption`.

A key present at a higher level overrides the same key lower down; keys absent higher up
fall through. The merge happens once in the `RestFileSystemOptionsHelper` constructor, which
builds a single merged map from all sources. This localizes resolution to the one class that
already owned defaults and makes the system property live regardless of whether `env` is
null or an empty map.

The JWT auth token, previously read straight from the raw `env` in `RestFileSystem`, is now
read through the helper (`getAuthToken()`) so it honors the cascade too and cannot NPE on a
null `env`.

## Tilde and path normalization

A `CacheLocation` supplied as a **string** (the JSON-property path — the case that never
passes through a shell) now has a leading `~` expanded to `user.home` and is run through
`Path.normalize()` to collapse `.`/`..`. Java has no built-in `~` expansion (it is a shell
convention), so without this a property value like `~/ccs/cache` would create a literal `~`
directory under the working directory. Only a leading `~` or `~/` is expanded; `~user` and
non-leading tildes are left untouched. Values supplied as `Path` or `File` (e.g. the toolkit's
`RemoteFileServer`, which resolves `user.home` itself) are unaffected — only the string branch
of `getDiskCacheLocation()` changed.

## Consequences

- The bootstrap can set a single system-property default (e.g.
  `{"CacheOptions":"MEMORY_AND_DISK","CacheLocation":"..."}`) and every mount picks it up,
  with per-server isolation from ADR 0001 keeping the mounts from colliding.
- Callers passing a partial `env` now keep defaults for the keys they omit — a behavior
  change from the previous all-or-nothing replacement, and the intended one.
- Both default sources are static/global. Tests that set them must clear them afterward
  (`DefaultEnvTest.clearDefaults`) to avoid leaking into other tests.
- `RestFileSystemProvider` still substitutes the programmatic default (or an empty map) when
  `env` is null; this is now redundant with the helper's own resolution but harmless, and
  left in place to keep the change focused.
