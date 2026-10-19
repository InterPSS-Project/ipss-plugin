# Ten recovered exciter profiles: fresh dense SMIB comparison

## Scope

This slice extends the explicit fresh-reference approach from
[AC11C](ac11c-fresh-cross-tool-2026-10-19.md) to AC7C, AC8C, AC9C, DC1C,
DC2C, AC8B, EXAC1A, ST2C, ST3C and ST5C. It changes no production model
equations, parameters, solver realization, state mapping or acceptance bounds.
Historical comparisons remain active; fresh comparisons are additional tests.
AC8B retains its declared equation-equivalent common profile, not exact-model
acceptance of every AC8B nonlinear branch.

The twenty completed reference runs from the
[input-recovery slice](exciter-input-recovery-2026-10-19.md) are reused rather
than described as newly generated. Each model has two completed cold runs,
no auto-correction or parameter overrides, and exact equality of every raw
sample/channel. All ten also match their historical full exports numerically.
The older AC11C discrepancy remains a separate unresolved diagnostic.

## Fixed reference and native input contracts

`fresh-exciter-contracts.json` pins each source-export hash, complete field
descriptors, numeric-body hash, row count and original reference input roles.
Its canonical SHA-256 is
`e2aa3a15008affc65337b19af96fec69d9a098a51e26bc8ca4d3c67b8b63352a`.
Each public gzip/Base64 numeric fixture retains all 2,003 rows, its 19–22
channels and both duplicate-time event samples. No numeric rounding,
resampling or coordinate transformation is used to build these projections.

The contract separately pins `running_inputs`: the exact native RAW/DYR
files used by the existing tests at baseline `4bd84c29e`. Five reference
manifests use different DYR filenames. AC8B and EXAC1A have identical
canonical bytes under different names; ST2C/ST3C/ST5C have distinct dialect
layouts. Those files are not interchanged. The existing native reader and
reference-tool mappings remain unchanged. Distinct filenames or layouts must
not be hidden by substituting a nearby file or a different model.

Before loading a fresh case, the helper hashes the actual native input bytes
and stages them in a per-test temporary directory. Every source hash and
destination collision is checked before writing either file; existing staged
files are never overwritten. Hash canonicalization changes CRLF/CR to LF only.
The case remains GENROU plus the selected exciter, modified Euler at 0.0005 s,
to 1 s, with a Bus 1 three-phase fault through j0.2 pu from 0.05 to 0.10 s.

The explicit private-export audit is:

```text
java -cp <test-runtime-classpath> org.interpss.core.dstab.reference.FreshExciterPowerWorldReference <model-lowercase> <raw-export.csv>
```

It uses the hash-pinned CSV importer and requires exact field descriptors,
timestamps, event occurrences and all channel values to match the public
projection. All twenty raw exports pass. The always-active public fixture
tests need no private export or installed simulator. Unknown model IDs fail;
there is no global reference-selection setting or historical-reference override.

## Coupled results

All 158 model-specific channel bounds pass at their original values. Each
model scores both nearby bus voltages, P/Q, relative angle/speed, four machine
electrical states and its existing exciter-state channels. Maximum Bus 1
voltage and relative-speed errors are:

- AC7C: 0.000263249235 pu voltage; 0.00000176310501 pu speed (16 metrics).
- AC8C: 0.000278792965 pu; 0.00000210351701 pu (15 metrics).
- AC9C: 0.000373606118 pu; 0.00000540187803 pu (17 metrics).
- DC1C: 0.000271738428 pu; 0.00000197563127 pu (15 metrics).
- DC2C: 0.000264300776 pu; 0.00000193491197 pu (15 metrics).
- AC8B common profile: 0.000397054125 pu; 0.00000281085820 pu (15 metrics).
- EXAC1A: 0.000253842564 pu; 0.00000181600034 pu (15 metrics).
- ST2C: 0.000249617111 pu; 0.00000178235740 pu (16 metrics).
- ST3C: 0.000263320630 pu; 0.00000187586832 pu (16 metrics).
- ST5C: 0.000258087471 pu; 0.00000182547148 pu (18 metrics).

The private audit retains every metric's maximum, its timestamp and original
bound, plus twenty 2,001-row native comparison traces and the shared ST5B
historical trace. All native trace values are finite, and each model's fresh
and historical native traces match exactly: reference selection changes only
the comparison data, not the simulated response. The source audit proves the original simulation body,
recorders, field mappings, interpolation, event exclusions and tolerance arrays
are unchanged. ST5C continues to use the existing ST5B shared harness.

The original comparison contract selects the final occurrence at a repeated
time and excludes timestamps within one integration step of fault application
or clearing. This is dense regular-time coupled acceptance, not proof of
instantaneous event-side algebraic equality. The raw projection audit retains
and verifies all event-side rows separately. Inactive auxiliary/derivative
states do not establish active-branch acceptance.

## Checklist and remaining work

- [x] Pin ten complete numeric fixtures and separate reference/native inputs.
- [x] Audit all twenty raw exports against the pinned projections, without
  dropping event rows, and recheck exact cold/historical equality.
- [x] Preserve historical tests and add ten always-active fresh comparisons.
- [x] Add guards for complete finite/event-aware fixtures, unknown IDs, mutated
  native inputs and staged-file collisions with no partial writes.
- [x] Pass 42 focused checks, including fresh/historical model comparisons,
  input contracts and seventeen CSV/fixture guards.
- [x] Pass 410 registered plugin-core tests (six existing optional skips) and
  1,609 scoped SMIB/model/solver tests (one existing diagnostic skip), with
  zero failures/errors and terminal exit zero. Six language-policy tests and
  the repository wording check also pass.
- [x] Retain source, input, reference, trace and report hashes for the scoped
  commit in private archive `2026-10-19-exciter-fresh-dense-cross-tool`.
- [ ] Complete independent post-initialization parameter readback, additional
  active-selector/limiter/zero-time/lifecycle profiles and instantaneous
  event-side coupled checks before claiming complete-model verification.
- [ ] Recover the six unavailable historical specifications and close the
  remaining approved-model coverage gaps recorded in the development plan.

The initial focused run rejected five wrong reference/native filename
assumptions. The final contract separates both roles explicitly; no reader
schema, input file or tolerance was changed to make the tests pass.
No full Texas2k dynamic simulation is run.

Final gates use baseline `91cc8b652`. During the first regression run, that
commit introduced a renewable-history correction and four unit tests. The
isolated snapshot was refreshed from its committed blobs, not from dirty
files, and all final gates were repeated. The final source audit matches
1,802 unchanged Java/POM files and thirteen owned Java files, with no
unexpected sources. No production source changes belong to this exciter
slice. Earlier failed and superseded test reports are retained separately.
