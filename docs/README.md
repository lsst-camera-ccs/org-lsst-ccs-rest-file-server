# Documentation

Organized by lifecycle — each category has its own maintenance rule.

| Category | Holds | Lifecycle |
|----------|-------|-----------|
| [decisions/](decisions/) | ADRs — the *why* | Long-lived, append-only. Reverse a decision with a new superseding ADR; don't rewrite the old one. |
| [guides/](guides/) | How the system behaves / how to use it | Living. Updated in the same PR that changes the behavior. |
| [workplans/](workplans/) | How we'll build a ticket's work | Disposable scaffolding, kept in-repo. |
| [workplans/HANDOFF.md](workplans/HANDOFF.md) | Where the last session stopped + backlog | Volatile. Refreshed when wrapping up. |

## Contents

**Decisions**
- [0001 — Per-file-system cache isolation](decisions/0001-per-file-system-cache-isolation.md) — largely superseded by 0003
- [0002 — Option resolution cascade](decisions/0002-option-resolution-cascade.md) — amended by 0003 (two keys go global)
- [0003 — One shared cache per JVM](decisions/0003-shared-per-jvm-cache.md) — proposed

**Guides**
- [Toolkit cache compatibility](guides/toolkit-cache-compatibility.md)

**Workplans**
- [LSSTCCS-3029 — One shared cache per JVM](workplans/LSSTCCS-3029-shared-per-jvm-cache.md) — active (ADR 0003)
- [LSSTCCS-3029 — Per-file-system cache isolation](workplans/LSSTCCS-3029-per-file-system-cache-isolation.md) — superseded (ADR 0001)
- [HANDOFF — current state + backlog](workplans/HANDOFF.md)
