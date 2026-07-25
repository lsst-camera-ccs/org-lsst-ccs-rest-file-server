# 0004 — The per-JVM cache lock is silently released; acquire it once per JVM

- Status: accepted (amends [0003](0003-shared-per-jvm-cache.md) §3)
- Date: 2026-07-24
- Ticket: LSSTCCS-3029 (follow-up defect; new JIRA TBD)

## Context

[ADR 0003](0003-shared-per-jvm-cache.md) §3 claims the lock on `<loc>/lockFile` is a working
cross-JVM guard: the first JVM to mount a location holds an exclusive `fcntl` lock for the mount's
lifetime, and a *second* JVM resolving the same location gets `tryLock() == null` and spills to
`<loc>-N` (or fails if spill is off). Field testing on `lsstcam-mcm` (local **ext3**, where
`fcntl` locks *are* enforced between processes) shows that guard does not hold.

## The defect

Under 0003 the lock describes JVM-global state but is acquired **per `Cache` instance**, and a shell
(like the toolkit's three services) mounts **more than one** `ccs://` file system in one JVM. That
combination trips the classic POSIX advisory-lock misfeature — *closing any file descriptor to a
file releases every `fcntl` lock the process holds on that file*:

1. **Mount A** opens `lockFile` → FD_A; `tryLock()` succeeds → real kernel lock placed; FD_A kept open.
2. **Mount B** (same JVM, same global location) opens `lockFile` again → FD_B; Java's own overlap
   check throws `OverlappingFileLockException` *before* reaching the kernel; the catch does
   `lockFileChannel.close()` on FD_B.
3. That `close()` on a **second** FD to the same inode **drops Mount A's kernel lock**, even though
   Java still believes `lock_A` is valid and FD_A stays open.

End state: the JVM holds `lockFile` **open** (via FD_A) but holds **no kernel lock** on it. The next
JVM's Mount-A `tryLock()` therefore succeeds, and it drops its own lock the same way. Every JVM ends
up an open-FD-without-lock, all sharing one JCS disk store (`default.data` + `default.key`), with no
exclusion.

### Field evidence (2026-07-24, `lsstcam-mcm`, ext3)

Same host, same build, two launch styles — opposite symptoms, one cause:

| Launch style | JVMs alive concurrently | Locks on lockFile (`/proc/locks`) | Result |
|---|---|---|---|
| **Staggered** (min apart) | 3, all at a prompt | **0** | silent shared store, no spill |
| **Burst** (`for … &`) | 5, all at a prompt | 0 (after startup) | spill `ccs-shell-1 … -4` |

- `lsof` showed all three staggered shells holding an open **write** FD on the *same* `lockFile`
  inode, while `/proc/locks` held **no** lock on that inode — the open-FD-without-lock state above,
  observed live with every shell alive.
- The burst run spilled only because it is a **race**: a starting JVM's Mount-A `tryLock()` landed
  inside an earlier JVM's brief window **[Mount A acquires → Mount B closes]**, before that JVM
  dropped its own lock. Wide window (network I/O between a shell's mounts) makes the race easy to
  win under a burst — which is why 0003's live "spill verified" (and the `-1-2-3` compounding that
  led to the naming fix) was observed at all. It is timing luck, not a working guard.
- The spill dirs are not a stable partition: they have no owners and no locks, so a later staggered
  shell reuses `ccs-shell` (or any unlocked sibling) and the "exclusion" decays back to sharing.

## Impact

- **Shells / consoles** (`ccs-shell`, `ccs-console`) — the case 0003 §3 was written *for* — get **no
  exclusion**. Multiple JVMs concurrently read/write one `IndexedDiskCache`, which is not built for
  multi-process access: interleaved appends plus last-writer-wins on the `.key` index can serve
  wrong bytes or throw on read. Bounded for dictionaries (checksum-in-URL, refetchable), **not**
  obviously bounded for config/persistence URLs. Integrity risk, not cosmetic.
- **Role agents** (`focal-plane`, `mcm`, …) are incidentally safe: uniqueness comes from their
  per-agent app-name location, not the lock, so no two of them target the same directory in normal
  operation. The dropped lock only bites when JVMs *share* a location — exactly the shell case.

## Why the tests missed it

- `CacheLockCrossJvmTest` spawns `CacheLockHolder`, which opens the cache **exactly once**. A
  single-mount holder never opens a second FD, so it never trips close-drops-lock; its lock stays
  genuinely held and the spill assertions pass. The blind spot is precisely a **multi-mount** holder.
- `CachingTest.sameJvmCacheShareTest` does create two mounts in one JVM, but asserts only that they
  resolve to the same **directory** — never that the kernel lock survived the second mount's
  `close()`. The silent drop walks straight through it.

## Decision (proposed remedy)

Acquire the lock **once per JVM** and hold it for the JVM lifetime, instead of per `Cache`:

- Hoist lock acquisition and the spill walk into the JVM-global config resolution
  (`RestFileSystemOptionsHelper`): resolve location *and* acquire the lock together, once, memoized
  in a static holding the locked channel. The result is the *final* (post-spill) location.
- Per-mount `Cache` instances read that resolved location and **never open `lockFile` again** — so
  there is no second `close()` to drop the lock. The window closes deterministically, independent of
  launch timing.
- This also fixes 0003 §3's stated known-limitation ("the lock is released when the *first* mount
  closes, not the last sharer") — one fix, both bugs: with a single JVM-lifetime lock there is no
  per-mount release at all.
- Add a **multi-mount** cross-JVM regression test (a holder that opens ≥2 caches, then asserts the
  lock is still enforced — a second JVM must still see `null`/spill). This is the gap that let the
  defect ship.

**Implemented.** `RestFileSystemOptionsHelper.lockCacheLocation()` now acquires the location + lock
once per JVM (memoized static holding the channel + lock); `Cache` reads that and never opens the
`lockFile`. The per-`Cache` `OverlappingFileLockException` share path is gone. Regression coverage:
`CacheLockHolder` is now **multi-mount** (opens two caches), so all three `CacheLockCrossJvmTest`
cases exercise the production path and would fail against the old code;
`CachingTest.sameJvmCacheShareTest` now asserts the lock survives a second mount and a mount close.
Client suite green (43 tests).

**Field-verified (2026-07-24, `lsstcam-mcm`, ext3).** Two `ccs-shell` JVMs launched **staggered**
(~3 min apart — the case that previously shared one store silently). Result: two directories
(`ccs-shell`, `ccs-shell-1`) and, in `/proc/locks`, **one real POSIX WRITE lock per JVM on its own
`lockFile` inode** (distinct inodes on the ext3 device). Contrast the pre-fix run in the evidence
table above: three live JVMs, one shared store, **zero** locks. The 0003 §3 guarantee now holds, and
holds independent of launch timing (no longer a startup race).

## Consequences

- 0003 §3's three-outcome model stays correct in intent; only *where/when* the lock is acquired
  changes. The `OverlappingFileLockException` "share" path disappears from `Cache` — same-JVM mounts
  simply reuse the already-resolved location and never probe the lock.
- The lock is held until JVM exit (released by the OS on process death, as today). A mount `close()`
  no longer releases the shared lock. **Verified compatible with the toolkit (2026-07-24):** it
  creates/closes `ccs://` mounts only through `RemoteFileServer` (one `newFileSystem` site, one
  `close()` site), and every `close()` is terminal shutdown or immediately precedes `System.exit` —
  no close-then-reopen of the same location within a live JVM. Nothing references the cache lock. A
  hypothetical reopen is in fact *safer* now: the memoized location is returned without re-locking,
  rather than re-running the old per-`Cache` spill/lock path.
- No on-disk format change; existing caches are unaffected.
