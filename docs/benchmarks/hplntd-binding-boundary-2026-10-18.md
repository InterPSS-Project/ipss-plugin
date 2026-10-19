# Hybrid wrapper, ownership-plan and measurement foundation

## Implemented scope

This slice adds three production APIs needed to attach the hybrid plant safely:

- `HplntdNativeRecords`: exact HPLNTDU/REAXDU wrapper decoding into immutable
  typed data, retaining the source record and location.
- `HplntdBindingPlan`: order-independent two-pass resolution of downstream
  links, with one owner per plant bus and downstream device identity.
- `HplntdNetworkBoundary`: read-only physical measurements on live plant MVA
  base, with switching measurements kept in Mvar.

These APIs are **not connected to the main dynamic importer or a runtime
coordinator yet**. LINKABLE means structural compatibility, not attachment,
network eligibility, initialization or trajectory acceptance. No catalog
descriptor or inferred standard-name alias is added. Runtime presence stays
**26/27**, with HPLNTD missing a loadable path.

## Exact source contract

Model Library sections 36.4 and 27.2 define these exact wrappers:

```
IBUS 'USRBUS' 'HPLNTDU' 504 0 35 61 13 45 ICON(35) CON(61) /
IBUS 'USRMDL' ID 'REAXDU' 107 0 9 8 2 7 ICON(9) CON(8) /
```

The source SHA-256 remains
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The decoder checks every allocation field, cardinality, integer/real type,
required quoted character slot and typed-data validation. It preserves all
61/8 constants and 35/9 ICON meanings rather than splicing another dialect.
Unused shunt bus/ID zero is accepted unquoted, consistently with the established
input convention. Negative entered downstream lag times remain unchanged;
the existing effective-time rule is applied at evaluation, not import.

Decoded plant/unit objects can only be created through the decoder. Duplicate
plant buses or downstream identities reject plan construction before any
attachment. Missing upstream plants, unsupported upstream model spellings and
invalid relative VAR indexes remain explicit ignored-link statuses, with the
original values retained. The plan has no global registry or network mutations.
Only the exact upstream spelling HPLNTDU is currently linkable; other name
variants are not silently treated as aliases.

## Physical measurement rules

The controller is owned by a bus, not one generator. A missing monitored branch
must not fall back to a selected generator's local P/Q/current. The boundary
marks the model ignored unless all three conditions hold:

```
Fflag == 0 && VcompFlag == 1 && RefFlag == 1
```

In that allowed branchless path, branch power/current are zero. Runtime handling
of the ignore/hold path remains the coordinator's responsibility. Bus number
zero selects the plant bus for voltage, or the regulated bus for frequency.
Unresolved nonzero voltage/frequency buses are conservatively rejected; their
complete native fallback policy is not claimed as independently established.

For a valid branch, current is calculated from both terminal voltages and the
full four-port admittance coefficients. It is not reconstructed as power over
voltage, so faulted zero voltage is safe. A signed FROM bus reverses the flow
at the same terminal; selecting the other terminal uses the other Y-matrix port.
Open branches report zero power/current.

```
Pplant = Psystem * systemMVA / livePlantMVA
Qplant = Qsystem * systemMVA / livePlantMVA
Iplant = Isystem * systemMVA / livePlantMVA
Qswitch_Mvar = Qswitch_system * systemMVA
```

Changing live plant MVA changes normalized control measurements but never the
physical switching measurement. A system-base change requires reinitializing
the boundary rather than silently mixing admittance and power bases. Zero/no
eligible-device handling belongs to aggregation/coordinator logic; measuring
with a nonpositive plant base is explicitly rejected. Observation does not
change shunt status, Y, or its dirty flag.

## Evidence

Eight new decoder/plan tests cover all field positions, defensive storage,
strict malformed input and located diagnostics, character typing, record order,
duplicates, ignored links, independent plants and registry isolation.

Six new integration/physical-boundary tests compare all seventeen reviewed
hybrid input datasets against the independent existing reference decoder,
including both downstream devices and every data field. Two existing test
fixture catalogs expose immutable reviewed-profile lists for reuse; the old
reference decoder itself is unchanged.

The physical branch test reuses six immutable native direct-API profiles, not
controller plot VARs. Those profiles were generated for the shared network
physics in the preceding REPCC boundary work; this is not independent HPLNTDU
controller/lifecycle or switching acceptance. Both branch ends, signed direction
and plant bases 50, 150 and 200 MVA are checked at every sample. Six twelve-column
paired traces retain 16,218 comparison rows, including history and event sides.

On a common 50-MVA normalization, maximum P23/Q23/P32/Q32/switching-Q errors are
`[2.393e-7, 3.388e-6, 2.393e-7, 3.289e-6, 3.388e-6]`.
The switching quantity is divided by 50 MVA only for this reported comparison
metric; the production API returns physical Mvar. Existing independently
established channel bounds are retained without relaxation.

Synthetic checks activate a non-unit transformer tap and a real phase-shifting
branch type, validate both measurement ports against the core branch API, and
exercise zero fault voltage, open branches, the complete missing-branch truth
table, bus fallbacks, invalid bases and no-network-mutation behavior. These
synthetic transformer checks are not a new native transformer benchmark.

No new simulator runs are claimed for this foundation; existing immutable
input/branch references and their hashes are reused.

## Next runtime slice

- Give bus plants their correct main-importer target scope; do not require a
  generator with ID `*` at the plant bus or apply generator-removal rules to it.
- Respect generator preprocessing, including the case's `_gnet.idv`; never
  recreate removed detailed generator/controller stacks.
- Resolve actual network/device eligibility and unused electrical command
  ports before atomically attaching anything. No legacy controller overwrite.
- Create one shared coordinator per plant, gather all downstream initial
  commands, then initialize aggregation, plant and lag states independently.
- Advance each shared plant once per integration stage from a coherent network
  snapshot. Compute all downstream command updates before units consume them,
  independently of bus/source ordering; clear inactive-unit history correctly.
- Route increments through the actual specified relative VAR links and expose
  plant/unit named states. Keep accepted endpoint switching outside trial stages.
- Verify coupled two-unit SMIB flat/reference/fault, trip/return, base changes,
  pause/resume and ordering invariance at two steps before adding loadability.
- Close the kernel/downstream selector, limiter, initialization and lifecycle
  gaps already listed in their evidence reports.

No full Texas2k dynamic simulation is required or run. Other dynamic file
formats are not mixed into the native record reader.

## Validation and archive

Final gates on Java baseline `6339a8f2e`: **65 focused**, **379 plugin-core**
(six optional skips), and **1,469 selected SMIB/model/solver** tests (one existing
diagnostic skip), all with zero failures/errors. The subsequent `e83cb20b6`
commit changes only unrelated documentation; the tested Java/POM baseline is
unchanged. A strengthened phase-shifter test initially distinguished positive
and negative zero; its numerical-zero assertion was corrected without changing
production equations or channel bounds. Those diagnostic logs are retained.

The generated evidence inventory matches its compiled generator. The neutral
wording guard checks 215 documents/new Java files with zero violations, and all
six policy tests pass. All classpaths retain frozen companion core `38e269ecf`,
SHA-256 `fbd9f015887b51ba438a538120cff00d0a724e76fd017674ec28fb0a924ab125`.

Private archive `2026-10-18-hplntd-binding-boundary` seals the scoped source,
six paired traces, final reports/logs, diagnostics and verification scripts.
The previous hybrid-kernel and native physical-branch archives are hash-checked
in place, not overwritten. A source audit compares owned files with the tested
snapshot and every other baseline Java/POM with Git. Unrelated working-tree
changes are excluded from the commit.
