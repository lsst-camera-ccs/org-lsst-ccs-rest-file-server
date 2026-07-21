# HANDOFF

- Anchor: branch `LSSTCCS-3029`, as of the commit that carries this file.
- On resume: `git log --oneline main..HEAD` to see what moved on the branch.

## State

Branch `LSSTCCS-3029` has the ADR 0001 + 0002 work implemented, tested (35 client tests pass),
and committed (`666f59c` isolation, `7ecd9ea` cascade, `781dcb9` ~ expansion, `4a19526` docs).

A design session with Tony reversed direction on 0001. The 2026-07-21 brainstorm settled the full
design: **one shared cache per JVM**, cache location + spill flag go **JVM-global**, caching policy
stays per-mount. Now recorded in [ADR 0003](../decisions/0003-shared-per-jvm-cache.md) (proposed,
amends [0002](../decisions/0002-option-resolution-cascade.md)). No 0003 code written yet — the branch
still contains the 0001 isolation code that 0003 unwinds.

The **bootstrap half is done**: the `<app|default>` token + `getJavaOpts` resolution and the shipped
`defaultEnvironment` line are implemented, tested, and in PR
(`org-lsst-ccs-bootstrap` PR #20, branch `LSSTCCS-3029`). It's runtime-coupled only — no build
dependency, does not block this client work (see the workplan's deployment section).

## Next up

**Build ADR 0003 per the workplan:**
[LSSTCCS-3029 — One shared cache per JVM](LSSTCCS-3029-shared-per-jvm-cache.md). It holds the full
step-by-step (client-only for correctness; toolkit is non-blocking cleanup). The design decisions
live in ADR 0003; this workplan is the how, and is the doc to share with Tony.

## Deferred (deployment-config, not code)

- Per-agent-category default policy: role agents (unique, reproducible names) reattach with a
  persistent disk cache; `ccs-shell`/`ccs-console` (unique, non-reproducible names) don't
  reattach — decide `OFFLINE` vs `WHEN_POSSIBLE` and memory vs disk per category.
- Bootstrap default `defaultEnvironment` values/location for base and summit.
