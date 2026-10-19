# PSS6C/PSS7C exact native schema separation

Status: native reader/schema defect corrected; scoped verification accepted.
This is an import-boundary slice, not exhaustive stabilizer verification.

## Source contract and defect

The installed reference model library, PDF pages 120-127, defines PSS6C as
four ICONs plus thirty CONs and PSS7C as six ICONs plus thirty-two CONs.
The final constants are Xcomp and Tcomp. The parameter tables on pages 121
and 125 were rendered and visually checked; page 126 confirms the latter
model's record syntax. The private evidence retains the source-document hash.

The [PowerWorld PSS6C reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS6C.htm)
and [PowerWorld PSS7C reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS7C.htm)
list an additional Tpgfilt ahead of Xcomp/Tcomp. These are distinct 35/39-value
layouts. They must not silently redefine the native 34/38-value contract.

The catalog and reader previously accepted both vendor widths, and two old
tests explicitly blessed that mixed import. A new three-test schema guard
fails twice before the correction: the catalog admitted the extra width and
lenient import attached the vendor record as native data. The correction
restricts native import to exact cardinality and retains PowerWorld builder
support. The old mapping tests now exercise that builder directly, with
distinct nonzero Tpgfilt/Xcomp/Tcomp checks. No controller equation, state
coordinate, initialization rule or comparison tolerance is changed.

## Cold reference evidence

`tools/pss_canonical_schema_probe.py` runs each unchanged SMIB fixture twice
in fresh simulator processes. It stages relative RAW/DYR inputs, captures
the actual allocated ICON/CON/STATE/VAR counts, entered stored parameters,
all published stabilizer states, network boundary signals and completed logs.
The fault is three-phase j0.2 pu at Bus 1, from 0.05 to 0.10 seconds, with
0.5 ms steps through one second. The existing core GENROU is reused.

PSS6C allocates 4/30/10/5 and exports twenty channels. PSS7C allocates
6/32/19/5 and exports twenty-nine. All four runs pass native initialization
and convergence checks. Each has 2,005 raw rows, including negative history
and duplicate event-side samples. The raw accumulated clock ends at
1.00001871586 seconds; it is preserved, not relabeled to an exact endpoint.

Every numeric value, channel and timestamp matches both its cold repeat and
the existing complete private native reference. That proves fresh repeatability
of these two profiles, not independent acceptance of every selector/limit.
`tools/audit_pss_canonical_schema.py` checks all input/output/source digests,
exact source-library identity, storage-precision parameter equality, event
times, finite full histories and initialization quality. Six offline tests
exercise tampering, path escape, extra fields, fractional selectors, nonfinite
data and preservation of duplicate event rows.

## Validation and remaining work

- [x] Retain the two failing-before-fix schema assertions.
- [x] Enforce native widths in catalog and direct parser, in both strict and
  lenient modes, without partial controller attachment.
- [x] Verify native Xcomp/Tcomp ordering and zero implicit Tpgfilt.
- [x] Retain typed PowerWorld extension and existing filter/hysteresis tests.
- [x] Cold-repeat four native runs; audit all raw rows against old references.
- [x] Pass 100 focused checks, 406 plugin-core tests (six existing optional
  skips) and 1,572 scoped SMIB/model/solver checks (one existing diagnostic
  skip), with zero failures/errors. Six artifact-auditor and six language
  policy checks also pass. Preserve source/report/reference hashes for the
  scoped implementation commit under the private archive
  `2026-10-19-pss-canonical-native-schema`.
- [ ] Expand selector, active-limit, lifecycle and matched/half-step coupled
  profiles; exact instantaneous event-side parity remains unproved.

The existing PSS6C/PSS7C trajectory tests retain their bounded checkpoint
comparisons and exclusions near event callbacks. Reproducing their raw native
histories does not remove those limitations or establish complete-model parity.
No full Texas2k dynamic simulation is run for this slice.

The final verification baseline is `93cb13b54`. The snapshot audit checks
1,801 unchanged Java/POM baseline files and seven owned Java sources. The
PSS6C/PSS7C runtime classes remain byte-identical to the preceding accepted
gate. An early focused run retained the old catalog-width assertion and
failed; its report is preserved, and the source-backed expectation is corrected
before all final gates. Unrelated shared-checkout changes remain excluded.
