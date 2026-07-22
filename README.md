# CCS REST File Server

A REST file server for the LSST Camera Control System (CCS). It exposes a directory tree on
the server's filesystem over HTTP, with first-class support for **versioned files** (files
that retain every prior version). Its headline feature is a client that implements the Java
NIO `FileSystemProvider` SPI under the `ccs://` URI scheme, so remote files can be read and
written through standard `java.nio.file.Files`/`Path` APIs as if they were local.

The build has four modules: `common` (wire POJOs), `war` (the JAX-RS server), `client` (the
NIO provider), and `cli` (the `cfs` command-line tool).

## Using it

- **Client (`ccs://` NIO provider)** — mount a server with `FileSystems.newFileSystem(URI, env)`
  and use ordinary `Files`/`Path` calls. Options (caching, cache location, SSL, auth token,
  mount point) are passed in the `env` map, via `RestFileSystemOptions.builder()`, or through
  the `org.lsst.ccs.rest.file.client.defaultEnvironment` system property.
- **CLI (`cfs`)** — `cat`, `edit`, `list`, `diff`, `move`, `mkdir`, `set`; defaults to the dev
  server, override with `-r`.
- **Toolkit cache behavior** — how the toolkit's configuration/persistence/dictionary remote
  file servers cache, and how the client system property interacts with them:
  [docs/guides/toolkit-cache-compatibility.md](docs/guides/toolkit-cache-compatibility.md).

## Working on it

Documentation is organized by lifecycle under [`docs/`](docs/):

- **Decisions (ADRs — the *why*)**
  - [0001 — Per-file-system cache isolation](docs/decisions/0001-per-file-system-cache-isolation.md) (largely superseded by 0003)
  - [0002 — Option resolution cascade for the ccs:// client](docs/decisions/0002-option-resolution-cascade.md) (amended by 0003)
  - [0003 — One shared cache per JVM, global cache config, policy in the file system](docs/decisions/0003-shared-per-jvm-cache.md)
- **Guides (the *how it behaves*)**
  - [Toolkit remote-file-server cache compatibility](docs/guides/toolkit-cache-compatibility.md)
- **Workplans (the *how we'll build it*)**
  - [LSSTCCS-3029 — One shared cache per JVM](docs/workplans/LSSTCCS-3029-shared-per-jvm-cache.md) (active — ADR 0003)
  - [LSSTCCS-3029 — Per-file-system cache isolation](docs/workplans/LSSTCCS-3029-per-file-system-cache-isolation.md) (superseded — ADR 0001)
  - [HANDOFF — current state + backlog](docs/workplans/HANDOFF.md)

## Build & test

Multi-module Maven build (no wrapper; use a system `mvn`). Java 21.

```
mvn install                              # build all modules
mvn -pl war,client -am install           # a module and its dependencies (client tests need the war test-jar)
mvn -Dtest=CachingTest test -pl client   # a single test class
```
