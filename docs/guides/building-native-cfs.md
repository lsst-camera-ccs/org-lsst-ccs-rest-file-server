# Building the `cfs` native executable

The `cli` module can be compiled either as an ordinary JVM jar (runs on any Java 21) or as a
standalone **GraalVM native image** named `cfs`. This guide covers the native build: the toolchain
you need, how to invoke it, and how to regenerate the reflection config when it falls out of date.

## You must build with GraalVM

The native build is driven by `org.graalvm.buildtools:native-maven-plugin`, which shells out to the
`native-image` tool. That tool ships **only** with GraalVM — a stock OpenJDK / Oracle JDK cannot
produce a native image. So the JVM running Maven must be a GraalVM distribution.

Verified toolchain:

```
Java(TM) SE Runtime Environment Oracle GraalVM 21.0.12+7.1 (build 21.0.12+7-LTS-jvmci-23.1-b96)
```

Point `JAVA_HOME` at that GraalVM install before building (adjust the path to your install):

```bash
export JAVA_HOME=/path/to/graalvm-jdk-21.0.12+7.1
export PATH="$JAVA_HOME/bin:$PATH"
java -version        # should report "Oracle GraalVM 21.0.12"
native-image --version
```

In recent GraalVM (21+) `native-image` is included in the base distribution; if `native-image` is
missing, install it with `gu install native-image` on distributions that still ship `gu`.

> The plain JVM build (`mvn install`, or running the CLI with `java`) has no GraalVM requirement — it
> is only the `native-image` profile that does.

## Building

The native build is gated behind the **`native-image` Maven profile** (defined in `cli/pom.xml`,
`activeByDefault=false`), so a normal build never pays its cost. Activate it with `-P`:

```bash
mvn -pl cli -Pnative-image package
```

Because the native build consumes the `client` (and transitively `common`) artifacts, those must be
installed first. From a clean checkout, build the reactor once, then the native image:

```bash
mvn install                              # build + install all modules
mvn -pl cli -Pnative-image package       # produce the native binary
```

The binary is written to `cli/target/cfs`. Run it directly:

```bash
./cli/target/cfs ls config/Chiller/General
```

What the profile configures (see `cli/pom.xml`):
- `imageName` = `cfs`, `mainClass` = `org.lsst.ccs.rest.file.server.cli.TopLevelCommand`.
- `--initialize-at-build-time=…client.implementation.RestFileSystemProvider`.
- `--enable-url-protocols=http,https` (the client talks to the server over both).

The plugin automatically reads any reachability metadata under
`cli/src/main/resources/META-INF/native-image/**`, including `jersey/reflect-config.json`.

## Reflection config and the tracing agent

GraalVM's closed-world analysis strips reflection metadata for any class it cannot prove is used.
Jersey and Jackson both drive deserialization reflectively, so the wire POJOs in `common` must be
declared in `cli/src/main/resources/META-INF/native-image/jersey/reflect-config.json`. A class that
is missing there compiles fine and works under plain `java`, but fails in the native binary with:

```
javax.ws.rs.ProcessingException: Error reading entity from input stream.
  Caused by: com.fasterxml.jackson.databind.exc.InvalidDefinitionException:
    Cannot construct instance of `…data.DefaultChangeRecord` (no Creators, like default constructor, exist)
```

(This is exactly how a missing `DefaultChangeRecord` — reachable only via the protocol-v2
`VersionInfoV2.defaultHistory` field — first surfaced.)

### Option A — edit `reflect-config.json` by hand

For a single new POJO this is the quickest fix. Add an entry alongside the others:

```json
{
  "name":"org.lsst.ccs.web.rest.file.server.data.DefaultChangeRecord",
  "allDeclaredFields":true,
  "allDeclaredMethods":true,
  "allDeclaredConstructors":true
}
```

Remember nested and v2-only types need their own entries (e.g. `VersionInfoV2$Version`), since they
never appear as a top-level response and are easy to miss.

### Option B — regenerate with the tracing agent (preferred when several classes drift)

The GraalVM **tracing agent** records every reflective access made during a real run and writes the
corresponding config. The idea: exercise the CLI on the ordinary JVM with the agent attached, pointed
at an output directory, then merge what it captured back into the source tree.

The agent needs the full application classpath. The built CLI jar carries a `Class-Path` manifest
listing its dependency jars (all resolved next to it under `cli/target/`), so running that jar is the
simplest way to get a complete classpath:

```bash
# 1. Build the JVM artifacts so the jar + its dependency jars are in cli/target/
mvn install

# 2. Run the CLI under the agent, writing config to a scratch dir. Use a GraalVM JDK so the
#    native-image-agent library is present. Exercise the code paths you need covered — in
#    particular a versioned-file listing, which pulls the full VersionInfoV2 incl. defaultHistory.
AGENT_OUT=/tmp/cfs-agent-config
java -agentlib:native-image-agent=config-output-dir=$AGENT_OUT \
     -cp cli/target/org-lsst-ccs-rest-file-server-cli-*.jar \
     org.lsst.ccs.rest.file.server.cli.TopLevelCommand \
     ls config/Chiller/General

# ...repeat the java invocation for other subcommands (cat, diff, edit, set, mkdir, move) to widen
#    coverage. Use config-merge-dir instead of config-output-dir to accumulate across runs:
java -agentlib:native-image-agent=config-merge-dir=$AGENT_OUT \
     -cp cli/target/org-lsst-ccs-rest-file-server-cli-*.jar \
     org.lsst.ccs.rest.file.server.cli.TopLevelCommand \
     cat config/Chiller/General/camera.properties
```

The agent produces `reflect-config.json`, `resource-config.json`, `proxy-config.json`,
`serialization-config.json`, and `jni-config.json` in `$AGENT_OUT` — the same set already checked in
under `META-INF/native-image/jersey/`.

**Do not blindly overwrite the committed files.** The agent captures only what the specific run
touched, so a single run will typically produce a *narrower* set than what is committed. Diff the
generated output against the tree and copy over only the genuinely new entries:

```bash
diff $AGENT_OUT/reflect-config.json \
     cli/src/main/resources/META-INF/native-image/jersey/reflect-config.json
```

Then rebuild the native image (Option A/B both end here) and re-test the failing command:

```bash
mvn -pl cli -Pnative-image package
./cli/target/cfs ls config/Chiller/General
```

## See also

- `AGENTS.md` → *Native CLI image* and *Protocol versioning* — why v2-only / nested POJOs are the
  ones that get forgotten in `reflect-config.json`.
- GraalVM docs: [Collecting Metadata with the Tracing Agent](https://www.graalvm.org/latest/reference-manual/native-image/metadata/AutomaticMetadataCollection/).
