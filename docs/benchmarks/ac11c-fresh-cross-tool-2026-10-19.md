# AC11C fresh SMIB cross-tool acceptance

## Scope and immutable inputs

This slice adds an always-active comparison against the complete numerical
projection of the recovered AC11C cold-run pair. It changes no production
model equations, parameters, integration method or acceptance tolerance.
The existing historical checkpoint comparison remains active and unchanged.

The source is the pair documented in the
[input-recovery report](exciter-input-recovery-2026-10-19.md): two completed
PowerWorld processes, no parameter overrides and no auto-correction, with
exact equality of every numerical row/channel. They are reused, not claimed
as newly regenerated in this slice. The canonical source-export SHA-256 is
`9cbc6dfe6f43c8dbb18a2288e631afde9fc51deef432437ce934130e7cb23fa1`.

The public numeric fixture retains all 2,003 rows, 23 channels and both
duplicate-time event samples. It is gzip/Base64 encoded without resampling,
rounding or numerical transforms. The decoded numeric body is pinned to
`e67e27eaa5db7a84c2a12cdf4d376cec5f52fd2fa17633fd38f7c49ef2a46360`.
Raw tool exports, executable identities and precise native provenance remain
in the private evidence archive, not in public prose.

Before the fresh simulation loads the inputs, the test verifies their
canonical hashes and stages the exact bytes in its own temporary directory:

- RAW: `986c6b3c8201ff0c4581a60e48af3438a14c692c12e4f8b966f4f2c710daa02b`.
- DYR: `0489565825a23cd8c768fbdbbe6216684c636b89deeab3119e737b68129db047`.

The reference tool uses the already archived alternate DYR dialect, pinned to
`3df21e8bcf21330dffd0132f34656737611690d93edae345ac0f07e70e275165`.
This slice neither substitutes that dialect into the native reader nor changes
either file's parameter mapping. Both dialects and the exact runnable spec
remain in the cold-run evidence.

Canonical hashes normalize CRLF/CR to LF only. The profile remains GENROU
plus AC11C, modified Euler at 0.0005 s, simulated to 1 s, with a Bus 1
three-phase fault through j0.2 pu from 0.05 to 0.10 s (three 60 Hz cycles).

## Reusable pinned export import

`PowerWorldCsvRepeatability.trajectory(path, expectedCanonicalSha256)` checks
and parses the same in-memory UTF-8 text. It requires a fixed SHA-256,
complete field metadata, unique object/key/variable identities, finite
numbers, exact widths, nondecreasing times and a complete results terminator.
Quoted commas and doubled quotes in metadata are decoded explicitly.
The resulting reference is immutable and preserves each event occurrence.
It never replaces an embedded historical checkpoint or selects a reference
through a JVM-wide setting.

The private raw export can be audited explicitly against the public fixture:

```text
java -cp <test-classes> org.interpss.core.dstab.reference.Ac11cFreshPowerWorldReference <raw-export.csv>
```

This command checks all metadata, timestamps, event occurrences and all 23
values in every row for exact equality after verifying the source hash.
The public SMIB test always runs against the pinned numerical fixture; it
does not skip when the private export or a simulator is unavailable.

## Coupled comparison contract

The existing comparison maps 19 boundary, machine and exciter channels.
Relative angle is referenced to the infinite-bus machine and its initial
offset; relative speed uses the same reference machine. Existing linear
interpolation and bounds remain unchanged. The comparison deliberately
retains its original event contract: request the last occurrence at repeated
times, and exclude timestamps within one integration step of fault application
or clearing. Therefore this is dense regular-time coupled acceptance, not
proof of matching every instantaneous event-side algebraic value. The raw
import and projection audit separately retain and check all event-side rows.

Final focused results at unchanged bounds include maximum voltage errors
of 0.000266596294 pu (Bus 1) and 0.0000873433857 pu (Bus 2), relative-speed
error 0.00000186195206 pu, relative-angle error 0.00454355250 degrees,
active-power error 0.0384722900 MW and reactive-power error 0.177898497 Mvar.
The maximum exciter internal-field error is 0.000289719365. Each of the nine
exciter states is scored separately. PSS auxiliary states remain inactive
and zero; this profile does not verify their active branches.
Complete per-channel maxima/times and both 2,001-row InterPSS traces are
retained with the test reports.

## Checklist and remaining work

- [x] Preserve the old checkpoint, field mappings, tolerances and event contract.
- [x] Add a pinned complete numerical fixture and explicit raw-export audit.
- [x] Add importer guards for bad hashes, malformed metadata/numbers, missing
  termination, widths, duplicate identities and event-row immutability.
- [x] Pass the focused AC11C historical/fresh and importer/input-contract checks.
- [x] Pass 18 focused checks, 406 registered plugin-core tests (six existing
  optional skips) and 1,596 scoped SMIB/model/solver tests (one existing
  diagnostic skip), all with zero failures/errors and terminal exit zero.
- [x] Retain source, input, reference, trace and report hashes for the scoped
  commit in private archive `2026-10-19-ac11c-fresh-cross-tool`.
- [ ] Extend fresh dense coupled comparisons to the other recovered exciters.
- [ ] Diagnose the older-versus-fresh AC11C export drift; this passing fresh
  comparison does not establish its cause.
- [ ] Complete independent post-initialization parameter readback and further
  active-selector, limiter, zero-time and lifecycle profiles before claiming
  complete-model verification.

No full Texas2k dynamic simulation is run. This slice is profile acceptance
and reusable verification infrastructure, not a claim that all models are
fully verified.

The first broad gate correctly rejected a global reference-selection setting.
It was removed, not exempted from the repository guard. The final focused and
both broad gates were rerun on the corrected sources. Six language-policy
tests and the repository wording check also pass. The isolated snapshot audit
against baseline `850de1e3f` matches 1,809 unchanged Java/POM files and four
owned Java files, with no unexpected sources; unrelated working changes are
excluded. Earlier failed-run reports are retained alongside the final reports.
