# AGENTS.md

This file provides guidance to AI coding agents when working with code in this repository. (`CLAUDE.md` is a symlink to this file.)

## What this is

A REST file server for the LSST Camera Control System (CCS). It exposes a directory tree on the server's filesystem over HTTP, with first-class support for **versioned files** (files that retain every prior version). The headline feature is a client that implements the Java NIO `FileSystemProvider` SPI under the `ccs://` URI scheme, so remote files can be read and written through standard `java.nio.file.Files`/`Path` APIs as if they were local.

## Build & test

This is a multi-module Maven build. There is no Maven wrapper; use a system `mvn`. The parent POM (`org-lsst-ccs-parent` 3.2.5) is resolved from the LSST Nexus repository, so a network connection to `repo-nexus.lsst.org` is needed on first build.

```bash
mvn install                              # build all modules (client tests depend on the war test-jar, so build the reactor together)
mvn -pl war test                         # test a single module
mvn -pl war,client -am install           # build a module and everything it depends on
mvn -Dtest=VersionedFileTest test -pl war        # single test class
mvn -Dtest=ClientTest#methodName test -pl client # single test method
```

Build order is `common → client → war → cli` (declared in the root `pom.xml`). Note the dependency inversion in tests: the **client** module depends on the **war** module's `test-jar` and `classes` classifier artifacts, because client integration tests spin up a real server in-process. This means you cannot test `client` against an unbuilt `war`.

Java 21 toolchain; source level follows the parent POM.

### Native CLI image

The `cli` module can be compiled to a GraalVM native binary named `cfs`:

```bash
mvn -pl cli -Pnative-image package
```

**Reflection config must cover every wire POJO.** GraalVM strips reflection metadata for any class not listed in `cli/src/main/resources/META-INF/native-image/jersey/reflect-config.json`, so Jackson cannot deserialize an unlisted `common` POJO in the native image (symptom: `InvalidDefinitionException: Cannot construct instance of X (no Creators … exist)` — works under plain `java`, fails as `cfs`). When you add or rename a `data` class, add it there with `allDeclaredFields`/`allDeclaredMethods`/`allDeclaredConstructors`. **This includes v2-only and nested types** — the field `VersionInfoV2.defaultHistory` is a `List<DefaultChangeRecord>`, and nested classes like `VersionInfoV2$Version` need their own entries; these never appear as top-level responses, so they are the easiest to forget (see Protocol versioning).

## Module architecture

- **common** — POJOs shared between client and server, serialized as JSON over the wire (`RestFileInfo`, `VersionInfo`/`VersionInfoV2`, `ServerInfo`, `VersionOptions`, `DefaultChangeRecord`, `IOExceptionResponse`). Package: `org.lsst.ccs.web.rest.file.server.data`. Changing these changes the wire protocol — keep them backward compatible (see Protocol versioning).
- **war** — the server. JAX-RS (Jersey) resources. Package: `org.lsst.ccs.web.rest.file.server`.
- **client** — the NIO `FileSystemProvider`. Package: `org.lsst.ccs.rest.file.server.client`.
- **cli** — a picocli command-line tool (`cfs`) built on top of the client. Package: `org.lsst.ccs.rest.file.server.cli`.

## Server (war)

Two JAX-RS resource classes, both registered in `MyConfiguration` (the Jersey `ResourceConfig`), both resolving paths against an injected `baseDir`:

- `FileServer` (`@Path("/")`) — non-versioned operations: `list`, `info`, `download`, `upload`, `createDirectory`, `createFile`, `move`, `deleteFile`, `setOptions`, plus `serverInfo`.
- `VersionedFileServer` (`@Path("version")`) — version-specific operations: per-version info, download, diff, set default version, hide/unhide versions.

`baseDir` is injected via HK2: in production from the servlet init parameter `org.lsst.ccs.web.rest.file.server.baseDir`; in tests/standalone by binding a `Path` in an `AbstractBinder`. **Both resource classes hardcode a fallback of `/home/tonyj/ConfigTest/` when no base dir is configured** — watch for this if a server appears to serve the wrong tree.

The application is mounted at the `rest` path (`@ApplicationPath("rest")`). The bundled web UI (a file browser + Ace editor under `war/src/main/webapp`) is a single-page app; `WEB-INF/rewrite.config` rewrites non-asset, non-`rest`, non-`ace` requests to `index.jsp`.

### Versioned files

A versioned file is **not a file — it's a directory** (see `VersionedFile`). Layout inside that directory:
- numbered plain files `1`, `2`, `3`, … — one per version, each set read-only (`r--r--r--`) once written.
- symlinks `latest` and `default` pointing at version files.
- `version-meta.properties` — the marker that makes a directory a versioned file (`isVersionedFile` checks for this file plus the two symlinks). Holds `hidden-versions`, and per-version `comment.N` / `creator.N` entries.
- `default-history.json` — append-only log of default-version changes (version, timestamp, changedBy).

`addVersion(content, onlyIfChanged)` skips writing when content is byte-identical to `latest`. `convert()` turns an existing plain file into a versioned directory in place (via a `$$TMP$$` staging dir + atomic move). The latest and default versions cannot be hidden.

### Hidden (non-versioned) entries

Independently of version hiding, any file or directory can be hidden from listings. `DirectoryMetadata` manages a per-directory `.hidden` JSON sidecar listing hidden entry names. `GET list` filters them out unless `?showHidden=true`; the `.hidden` file itself is never listed. Listings carry a `hasHidden` flag so clients can offer a "reveal" affordance without a second request.

### Authentication

JWT via Firebase. `@JWTTokenNeeded` marks protected endpoints; `JWTTokenNeededFilter` verifies the `Authorization: Bearer <token>` header against Firebase and stashes the caller's identity (email → name → uid) as request property `jwt.uid`, which versioned-write endpoints record as the version `creator`. The filter can be bypassed by source IP via the `CCS_REST_ALLOWED_IPS` env var (a regex). `MyConfiguration(true)` constructs the app **without** auth — this is how `TestServer` runs, so tests never touch Firebase.

## Client (NIO provider)

`RestFileSystemProvider` registers the **`ccs://`** scheme. Use it through standard NIO:

```java
FileSystem fs = FileSystems.newFileSystem(URI.create("ccs://host/RestFileServer/"), env);
Files.readAllBytes(fs.getPath("some/file.txt"));
```

Key layering:
- `RestFileSystemProvider` — the SPI entry point; caches one `RestFileSystem` per resolved URI. `getPath(URI)` uses heuristics to split the server-root URI from the in-filesystem path (the boundary is genuinely ambiguous over HTTP — see the comment there).
- `RestFileSystem` / `RestPath` — a Unix-like path model built on the `implementation.unixlike` abstract base classes.
- `RestClient` — does the actual HTTP calls (JAX-RS client) and maps server `IOExceptionResponse` payloads back into thrown `IOException`s.
- Caching is pluggable via `RestFileSystemOptions` (`CacheOptions` NONE/MEMORY_ONLY/MEMORY_AND_DISK, disk cache via Apache JCS, plus `CacheFallback` for offline behavior). Options are passed in the `env` map to `newFileSystem`, or as a JSON map in the system property `org.lsst.ccs.rest.file.client.defaultEnvironment`.
- Versioned files surface through a custom `VersionedFileAttributes`/`VersionedFileAttributeView` and the `VersionOpenOption`/`VersionedOpenOption` open options, so callers can request a specific version or write a new version through ordinary `Files.newInputStream`/`newOutputStream`.

Request filters add cross-cutting headers: `AddJWTTokenRequestFilter` (auth) and `AddProtcolVersionRequestFilter` (protocol version).

## Protocol versioning

The client sends header `x-protocol-version: 2` (`Constants.PROTOCOL_VERSION_HEADER`, set in `AddProtcolVersionRequestFilter`). The server downgrades its response for older/absent clients: with protocol 2 it returns `VersionInfoV2` (adds `comment`, `creator`, top-level `defaultHistory`); without it, the original `VersionInfo`. **When adding fields to versioned-file responses, gate them behind the protocol-version header and add them to `VersionInfoV2`, not `VersionInfo`** — older clients must keep working. The server advertises optional features through `GET /rest/serverInfo` (`ServerInfo.capabilities`, e.g. `hideFiles`, `versionComments`, `versionCreator`, `defaultHistory`) so clients can feature-detect at runtime; add a capability string when adding a new optional feature.

## Running locally

- `org.lsst.ccs.web.rest.file.server.standalone.Main` (in war) starts a JDK-HTTP-server instance on port 8899 serving a `configFiles` dir. It uses the default `MyConfiguration` (auth **enabled**), so it expects Firebase application-default credentials. For a no-auth in-process server, the test-sources `TestServer` (port 9999, `MyConfiguration(true)`) is the path of least resistance.
- The CLI defaults to `ccs://lsst-camera-dev.slac.stanford.edu/RestFileServer/`; override with `-r`. Subcommands: `cat`, `edit`, `list`, `diff`, `move`, `mkdir`, `set`.

## Documentation (`docs/`)

Design rationale lives in `docs/`, organized by lifecycle, and is the authoritative record of *why* the caching layer is shaped the way it is — read these before touching cache/lock code:
- **`docs/decisions/`** — ADRs (the *why*). 0001 per-file-system cache isolation (largely superseded), 0002 option-resolution cascade, 0003 one shared cache per JVM, **0004 per-JVM cache lock defect** (the live design: lock acquired once per JVM, held for JVM lifetime, because POSIX `close()` releases all of a process's `fcntl` locks on a file).
- **`docs/guides/`** — behavioral guides, e.g. toolkit remote-file-server cache compatibility.
- **`docs/workplans/`** — build plans plus **`HANDOFF.md`**, the volatile running state (where work stopped, what's next, backlog). Read `HANDOFF.md` at the start of a session.

## Conventions

- Version is centralized in the root POM; the server also hardcodes `SERVER_VERSION` in `FileServer.java` — bump both together on release (they currently drift: POM is `1.1.13-SNAPSHOT`, `SERVER_VERSION` is `1.1.10`).
- Tests are JUnit 5 (Jupiter) and run against a real in-process server (`TestServer`), not mocks.
- Commit messages in this repo are detailed and feature-oriented (multi-line bodies enumerating endpoint/field changes and backward-compatibility notes); match that style.
