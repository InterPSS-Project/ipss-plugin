# AC/DC Import, Configuration, And Solved-Export Implementation Notes

## Purpose

This note documents the production changes on the
`ac_dc-power-flow-enhancement` branch of `ipss-plugin`. The plugin is the
boundary between PSS/E/EPC source data and the core ACLF model, so it must
preserve enough source intent for the solver while keeping explicit JSON run
configuration authoritative.

The branch also adds solved RAW/RAWX export. A solved export is not merely a
copy of source records: bus voltages, controller active sets, converter
injections, and the limited set of generator outputs that can move during the
solve must describe the accepted operating point when the file is imported by
a new process.

## Configuration Precedence

The intended run sequence is:

1. Parse the case and attach source solution settings to network extra info.
2. Apply supported RAW settings when saved-setting replay is requested.
3. Apply explicit ACLF JSON fields last, so a field present in JSON overrides
   the imported value.
4. Initialize devices and calculate active HVDC injections before evaluating
   iteration-zero mismatch.

Parser selection is intentionally absent from `AclfRunConfigRec`; it is an
import concern, not a power-flow algorithm setting.

`schemaVersion` is emitted by the Java model and currently equals `1`. For
compatibility with existing files, an absent value defaults to version 1;
deserialization rejects any explicitly unsupported version instead of silently
applying newer or incompatible field meanings. Nullable advanced fields mean
"leave the current algorithm or replayed value unchanged"; primitive fields
carry the schema default.

Numerical NR configuration is independent of adjustment inclusion. In
particular, normal NR (`nonDivergent=false`) may still use PSS/E DVLIM through
`variableUpdateLimit`. Step-size optimization fields affect only the
non-divergent solver path.

## PSS/E Solution Settings

`PsseLoadflowSolutionSettings` retains the original system-wide lines and maps
only fields with a verified InterPSS equivalent. The relevant mappings are:

- `THRSHZ` to the zero-impedance threshold;
- `PQBRAK` to the constant-power low-voltage breakpoint;
- `ITMXN` to the existing RAW Newton iteration limit;
- `TOLN`, converted from MVA to per unit on the case base;
- `DVLIM` to a global relative-voltage correction limit;
- `ADJTHR` and `MXTPSS` to adjustment admission/batching;
- binary `ACTAPS`, `AREAIN`, `PHSHFT`, `DCTAPS`, and `SWSHNT` activity flags
  when saved-solution replay is selected.

Unknown, non-binary, or unsupported values remain in the application report and
are not guessed. `NONDIV` is not treated as a RAW solver field because normal
versus non-divergent NR is an explicit run-policy choice in JSON.

RAWX stores the original solution-setting lines in the
`general.ipss_loadflow_solution_settings` extension. The parser restores that
extension to network extra info so RAW -> RAWX -> ACLF retains source settings
without making the extension part of the PSS/E standard schema.

## EPC Parser

`GEPslfDirectParser` was renamed to `EpcDirectParser` because the implementation
parses EPC records directly. Adapter entry points still use the existing
`GE_PSLF` file-format selection for API compatibility.

The parser pre-scans SVD records before building buses. EPC places SVD data
after bus/generator data, but bus type must be known while generators are
classified. The pre-scan records only voltage-controlling SVD buses and avoids
creating duplicate devices during the real section pass.

Transformer parsing now distinguishes explicit star-point records from ordinary
two-winding records, preserves impedance-correction table references, and
handles continuations by section grammar. Generator reactive data is finalized
through the same public normalization helper used by PSS/E parsing so fixed-Q,
usable-Q-range, and PV/PQ classification are consistent across direct adapters.

SVD records create switched-shunt/SVC controls with their limits, setpoints,
status, and remote association. Exact values close to a physical limit are
snapped within a small tolerance to avoid inventing control headroom from text
rounding.

## RAW And RAWX Import Fidelity

Both PSS/E direct parsers preserve switched-shunt `RMPCT`. Controls that share a
remote pilot therefore retain their source participation instead of reverting
to equal 100-percent participation.

RAW transformer phase-shifter limits are normalized as max/min before they are
given to the builder. RAWX VSC loss coefficients, minimum loss, and AC-current
rating are imported in addition to existing power and Q limits. These fields
affect converter injections and must survive round trip.

The builder distinguishes `BINIT`, the imported/saved setting, from `BActual`,
the active injection. An inactive shunt may preserve source block data and
`BINIT`, but contributes zero `BActual` until activated.

## Solved-State Export

`PSSEJsonExporter(network, true)` creates the canonical solved RAWX model;
`PSSERawExporter` serializes that model using the requested RAW version. Keeping
one canonical model avoids RAW and RAWX making different control-state
decisions.

### Precision and version

RAWX numbers are emitted as JSON doubles. RAW bus voltage magnitude and angle
use `Double.toString`, which provides round-trip precision and therefore more
than the requested eight meaningful digits for normal voltage values. The run
layer resolves an omitted output version to the input version before it creates
`PSSERawExporter`; this low-level exporter always receives the resolved version.
Explicit export supports v30-v36. The run layer is responsible for warning
before a lower-version conversion because older layouts cannot represent every
newer field.

Version-specific section ordering and field layouts are handled by
`PSSERawExporter`, including v30 embedded fixed shunts and the v34/v35+
two-terminal converter column differences. System-wide source lines are carried
forward where the target version supports them.

### Bus and generator state

Bus `VM` and `VA` always come from the accepted network state. Bus type export
reflects the active set but preserves `IDE=2` for an enabled generator remote-Q
controller; exporting that internal PQ representation as `IDE=1` would discard
`IREG` and prevent reconstruction of the controller.

Machine `PG` is normally preserved. It is reconciled to solved bus generation
only at the system swing or an area-interchange swing, because those are the
active-power balancing generators. Solved Q is allocated only across active
machines with usable Q range; fixed-Q machines remain fixed. Allocation is
weighted by machine MVA base and the final machine receives the arithmetic
remainder so the exported aggregate is exact.

### SVC and switched shunts

An active continuous SVC inside its B range remains an active FACTS voltage
controller and exports its solved B together with its physical limits. A
disabled or limit-bound SVC cannot reproduce voltage-control behavior on import,
so solved export writes an inactive FACTS metadata record plus an active fixed
shunt carrying the accepted susceptance.

Switched shunts export solved `BINIT`. If a control was disabled or the final
regulated-voltage error exceeds the universal solved-control tolerance, solved
export writes `MODSW=0`. This preserves its electrical state and prevents an
unsettled control from moving at iteration zero after re-import. The original
blocks, remote participation, and identifiers remain available as metadata.

### LCC and VSC HVDC

For an ordinary active LCC, solved export derives the tap needed by the accepted
DC voltage/current/firing-angle equation when that value is finite and inside
the physical tap range. It also exports the solved DC setpoint and scheduled
voltage.

When core has fixed an unstable LCC outer loop at its last AC-safe state, RAW
cannot encode the complete frozen active state. The exporter therefore blocks
the LCC record and adds fixed terminal-load equivalents equal to its accepted
P/Q injections. This is an electrical-equivalence fallback, used only for links
identified by core metadata. RAWX additionally appends firing/extinction angle,
tap position, and terminal P/Q information. RAW appends the same information in
a trailing comment so it is available for diagnostics without changing PSS/E
field parsing.

For VSC links, solved export retains terminal P/Q extension fields. A remote
voltage controller or a local controller at its Q bound is exported in fixed
power-factor mode using the accepted P/Q ratio; otherwise an interior local
controller remains AC-voltage mode. This prevents import initialization from
resetting a limit-bound bus to an unattainable voltage target.

## Production File Map

| File | Reason and logic change |
| --- | --- |
| `GEFormat`, `IpssAdapter` | Route existing GE/PSLF API selections to the accurately named direct EPC parser. |
| `AclfNetworkBuilder` | Preserve switched-shunt remote participation and separate saved B from active B. |
| `EpcDirectParser` | Parse EPC sections directly, pre-scan SVD controls, preserve transformer/star/table data, and normalize generator/shunt modeling. |
| `PSSEDirectParser` | Normalize phase-shifter ranges and retain version-specific switched-shunt participation and FACTS supervision. |
| `PSSEGeneratorReactivePower` | Share machine-Q normalization and bus classification with EPC import. |
| `PSSEJsonDirectParser` | Restore solution-setting extensions, switched-shunt participation, VSC losses/current rating, and solved metadata. |
| `PsseLoadflowSolutionSettings` | Parse, validate, report, and selectively apply supported RAW solution settings. |
| `PSSEJsonExporter` | Build canonical RAWX and solved-state records, including controller active sets and HVDC diagnostics/equivalents. |
| `PSSERawExporter` | Serialize canonical data with version-specific layouts, precision, comments, and section order. |
| `AclfRunConfigRec` | Version the JSON schema and map explicit normal-NR, DVLIM, adjustment, area, and reduced-LCC settings. |

The plugin tests cover schema rejection, source-setting mapping, RAW version
gates, RAW/RAWX exporter precision and round trip, SVC and shunt active-set
preservation, fixed-LCC equivalents, VSC limits, EPC section gates, a 2,000-bus
EPC comparison, and Kundur two-area LCC derivative/start parity.
