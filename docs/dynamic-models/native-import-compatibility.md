# Native Import Compatibility

## Startup boundary follow-up

Import disposition does not establish dynamic initialization or trajectory
conformance. The following changes address independent startup boundaries:

- Legacy ST1 zero-lag blocks already bypass the filter in the runtime. Startup
  screening now accepts their retained lead constants instead of rejecting a
  branch the runtime never divides by. Nonfinite data and negative pole times
  remain invalid.
- GENQEC startup screening recognizes only the exact reactances produced by its
  validated constructor normalization. This avoids contradicting that model's
  existing subtransient floor; arbitrary inconsistent post-construction edits
  remain errors.
- Area-wide IEEL characteristics are defaults. Explicit composite or other
  specific load models retain their load IDs. Parse these defaults after
  specific models, attach them only to unclaimed loads and report the physical,
  attached and retained counts. A wholly shadowed default has intentional
  `SKIPPED_LOAD_PRIORITY` disposition. Do not retain an IEEL pointer to a load
  that composite initialization later moves into its expanded network. A small
  reference allocation probe confirms that the explicit composite keeps its
  load while IEEL attaches to another load in the same area.
- A composite initialized below 0.95 pu with an omitted transformer no longer
  aborts solely because feeder scaling cannot raise its source voltage. Apply
  the explicit compatibility floor `1e-7 + j1e-5` on the system base to an
  existing feeder, then recalculate voltage and power from the same incoming
  current. Entered feeder constants remain unchanged. An omitted feeder needs
  no scaling. This is the published distribution-equivalent fallback; the
  reference simulator accepts this operating point but produces a different
  small feeder impedance. Exact reference feeder-preparation parity remains
  open. The regression checks KCL-consistent equilibrium and retained constants,
  not an asserted exact reference impedance match.
- CIMTR4's circuit supports supersynchronous generation as well as motoring.
  Its former negative-generator-P guard rejected valid positive-P records.
  Search from zero slip in the direction required by the requested motor P,
  bracket the first crossing and bisect that branch. Evaluate the zero-slip
  rotor admittances by their zero limit to avoid division by zero. Preserve
  entered PG/QG; do not change their signs. A reference positive-P startup probe
  retains the source constants and reports valid initial conditions. Public
  tests check negative, zero and positive PG, terminal injection and short flat
  equilibrium. General disturbed-trajectory qualification is separate.
- AC7B zero KP bypasses the terminal-voltage multiplier. Use a unit potential
  factor in both inverse initialization and runtime; do not reject the record
  or multiply its regulator output by zero. The manual and an unchanged native
  startup probe establish this selector, and the public test checks flat field
  voltage with zero retained in the input object.
- AC8B native CON positions after VPIDMIN are `KA TA VRMAX VRMIN KC KD KE TE
  VFEMAX VEMIN E1 SE1 E2 SE2`. The old parser incorrectly read an alternate
  export layout. Correcting the mapping removes artificial extreme PID demands
  without relaxing limits. All 21 positions have distinct-token coverage.
  Immutable historical trajectory inputs retain their hashes; the tests now
  explicitly stage their alternate ordering into a native record with identical
  typed parameters. Reference trajectories and comparison tolerances are
  unchanged. No implicit production format guessing is introduced.
- Staged load-relay ownership now uses the shared zone-composite contract,
  rather than restricting moved-load resolution to its bus-specific subclass.
  Both use the existing source-ID validation and whole-owner capacity equations.
  Singleton source targets and whole-owner wildcards remain unambiguous;
  partial selection from a multi-source aggregate remains rejected. The test
  initializes a zone composite, verifies its original load is detached, sheds
  25 percent and confirms that a second accepted step does not shed again.

The zone parser also preserves **one composite per matched source load ID**.
The reference allocation probe assigns separate 27-state ranges to two loads
under one zone record; sharing constants does not mean sharing their states or
feeder. The first existing model supplies the engine's preparation entry point
for its source companions. Each source keeps its P/Q, leaves, capacity and
collision-free internal bus IDs. IEEL defaults inspect every source owner.
Specific relays resolve the matching model; wildcards select every owner once.
No proportional split of an aggregated injection is used. Public two-source
tests verify separate buses/states and independent versus wildcard shedding.
Several fully collapsed owners still require explicit parent-allocation support
and are rejected rather than misattributing shared shunts.

Validated LDS/LVS records whose explicit physical load is out of service are
reported as `SKIPPED_INACTIVE_TARGET`, separately from absent hosts. No active
relay is constructed for that inapplicable input. Stage and transfer parameters
are validated first, so invalid data still fail. The report retains the source
identity and reason; strict import accepts this intentional disposition.

REECC1 now provides the ordinary engine's explicit predictor/corrector entry
point for auxiliary control. Its local auxiliary receives each stage once;
the shared plant's existing stage guard deduplicates calls from other receivers.
The existing bounded electrical update runs only at the predictor, so the
corrector does not double-integrate battery energy. Direct single-stage calls
with an auxiliary still fail, and ordinary calls cannot mix with externally
owned two-step integration. Tests check analytic auxiliary lag updates, battery
energy accounting and the existing small-network flat/disturbed smoke under
both solver paths. Full independent trajectory qualification remains separate.

The companion engine change removes a stale round-rotor `Xq1 == Xq11` rejection:
the current flux formulation divides by `Xq1 - Xl`, while equality of transient
and subtransient q reactances simply cancels a coupling coefficient. The actual
leakage singularity remains rejected. A boundary/near-boundary voltage-step
comparison verifies finite currents and continuous power outputs.

The engine's lead-lag filter accepts finite signed gains. A negative gain
reverses polarity while retaining the same pole. At zero gain, output-driven
initialization admits only zero output and chooses zero input; input-driven
initialization preserves its supplied input and zero output. This avoids an
inverse division by zero and never substitutes a nonzero gain. Paired step
tests and zero-gain input/output tests exercise both paths. The network
initializer now returns its accumulated device acceptance flag and clears stale
success on a retry, with bus/device diagnostics for failures. It no longer
reports success when an earlier controller failure skipped governor startup.

The composite compatibility floor follows the published
[distribution-equivalent initialization procedure](https://www.powerworld.com/WebHelp/Content/MainDocumentation_HTML/Transient_Stability_Load_Model_Group_Distribution_Feeder_Equivalent.htm).

The latest local plugin already contained the H6E and OEL3C kernels and the
explicit CHVDC2 runtime. This change connects additional source identities to
those implementations; it does not duplicate their equations or qualify an
entire large-system trajectory.

## Source Layouts

- `H6E1`: exactly one selector and 62 constants. After 27 scalar constants,
  read the three ten-point curves, then water inertia, reference rate,
  speed-deadband bounds, and turbine rating. Water inertia is CON offset 57.
  This differs from `H6EU1`, which retains water inertia at offset 27 before
  the curves. The source model name selects the layout, regardless of RAW
  revision. The current H6E runtime supports native H6E1 with zero asymmetric
  speed-deadband bounds; nonzero native bounds are explicitly rejected rather
  than reinterpreted as the legacy hysteretic deadband.
- `OEL3CU`: require a `USRMDL` header and allocation `10 0 2 10 2 4`, followed
  by the two selectors and ten constants used by OEL3C. Validate all six
  allocation fields and reject shortened or oversized records. The wrapper
  changes parsing only; the existing limiter kernel and host requirements
  remain in force. Host expansion is deferred.
- `CHVDC2` and `CHVDC2U1`: retain distinct source identities and the existing
  exact 51-field direct or 57-field wrapped schemas. Register them as partial
  implementations because general trajectory qualification remains open.

The installed model-library sections for H6E1 and OEL3C were consulted; exact
source hash/version and private-case diagnostics are retained in `qa.largecase`.
The native H6E1 example includes a blade-curve dip. The runtime now requires
finite blade data and an invertible physical flow curve instead of requiring
blade stroke itself to be monotone. Gate and power/flow ordering remain checked.
Since flow is gate times a piecewise-linear blade factor, its derivative is
affine on each segment; checking both endpoint derivatives prevents hidden
interior reversals. No entered curve or time constant is changed.

## GENTPJ1 Zero Current Multiplier

`Gentpj1Data` previously required `0 < Kis < 1`, copying the open interval in
the model-library parameter description. That excluded the documented example
value zero and the current-independent boundary used in real source records.
The installed reference simulator imports zero, retains it at initialization,
and completes a short equilibrium run. PowerWorld also retains zero through
import, validation and initialization with automatic correction disabled, and
completes a short fault run. Its exported model parameters are identical at
those three stages. PowerWorld explicitly identifies zero as the GENTPJ default
in its [June 2012 release note](https://www.powerworld.com/patch/june-8-2012).

The runtime already evaluates saturation at `airGapFlux + Kis * abs(Iterminal)`.
At zero, only the current contribution disappears; quadratic saturation and all
other GENTPJ1 equations remain active. Therefore the data contract now accepts
`0 <= Kis < 1`. No positive epsilon, renamed machine, fitted parameter, or
source-record rewrite is introduced. Negative/nonfinite values and the existing
upper boundary remain rejected; this investigation does not qualify wider bounds.

Tests cover exact zero retention, unchanged positive-current contribution,
invalid boundaries, initialization and an unperturbed operating point. The
private unchanged-case regression verifies every attached machine's Kis against
its original source and confirms all 1,328 zero-value records now attach.
Simulator probes and input/output hashes remain in `qa.largecase`; this boundary
acceptance check does not qualify a complete large-system dynamic simulation or
claim cross-simulator disturbed-trajectory parity.

## Additional Import Boundaries

The same source-contract and independent-probe audit covers the remaining
constructor/parser failures in the private pair. Its inventory and readback
artifacts remain in `qa.largecase`, including unsuccessful discriminators.

- REXSYS FBF/FLIMF and SCRX CSWITCH occupy real CON slots. Accept exact
  integer-valued decimal/exponent spellings there with `BigDecimal.intValueExact`;
  reject fractions, nonfinite spellings and integer overflow. Keep the distinct
  ICON lexer strict. Native import and initialization retain the entered values.
- DERA1 Ddn/Dup are multiplicative gains, so zero is a finite disabled gain.
  Both the native reference and PowerWorld initialize the unchanged zero-gain
  profile. The runtime already multiplies these values and requires no new
  bypass, replacement or parameter correction.
- CSVGN5 and SVSMO1T2/T3 retain remote-bus ICON zero but bind their sensor to
  the host. Do not resolve `Bus0` or overwrite source data. The switched-shunt
  source table explicitly defines local sensing; the CSVGN5 native probe also
  initializes the zero-selector profile successfully.
- IEEL subsystem selection skips active zero-power placeholder records whose
  load code is NON_LOAD. Those buses intentionally have no dynamic-load list.
  This prevents an area record from failing after partially attaching its real
  loads; the static placeholders remain in the network. The public regression
  includes a real load and a zero placeholder in the same area.
- WT1G1 accepts `X'' == X'` while retaining entered reactances and time constants.
  At this boundary the second cage has zero admittance; the existing `twoCage()`
  selection takes the finite single-cage circuit. Native and PowerWorld retain
  the parameters through initialization. Reversed reactances remain invalid.
- GENQEC accepts finite signed Kw. Both independent tools retain the negative
  source multiplier. The existing runtime bounds the signed product Kw*Id to
  [-0.4, 0.4], so no change to that equation or its limit is needed. Nonfinite
  multipliers remain rejected; neither absolute-value conversion nor a positive
  replacement is applied.
- SVSMO3T2 accepts Kdbd=0 with an active main deadband. A native probe with a
  valid assigned MSS verifies the controller remains active and initializes;
  an earlier probe with a missing MSS was ignored and is not acceptance evidence.
  Positive ratios below one and other existing thermal constraints stay rejected.
- GEWTGDU1 equal ramp endpoints disable the ramp even with nonzero stored height.
  Native zero-duration and positive-duration timing discriminators distinguish
  this from a wind step. Accept the source data and return zero for that branch
  before applying endpoint logic or dividing by duration. Positive-duration
  ramp and gust equations retain their original behavior. The full native wind
  host has other suspect initial derivatives; this proves the active wind-source
  branch, not equilibrium or complete turbine trajectory conformance.

Three source issues remain explicit. REECA1 has inverted Pmax/Pmin; PowerWorld
rejects the unchanged profile and swaps the pair only under its explicit
auto-correction operation. A native probe also reports suspect initial states.
REAXB has a zero active gain, contradicting the documented nonzero requirement;
with a valid plant the native active state loses equilibrium, including with
the exact legacy wrapper. PowerWorld accepts that wrapper with zero active
gain through its REPC_B conversion. This dialect difference requires a verified
compatibility rule; changing the shared constructor alone would divide by zero.
PLNTBU1 has an
out-of-domain frequency selector and inconsistent frequency limits. The native
loader retains that selector, but startup alone does not establish its frequency
branch semantics. PowerWorld also rejects that legacy profile for its frequency
limits and small filter time. Do not guess a replacement selector or repair limits silently.
Diagnostics name the REECA1 pair and REAXB gains so source corrections are reviewable.
The explicit correction and auxiliary-port follow-up below supersedes these
remaining-import dispositions; the retained native probe observations still apply.

## Explicit corrections and auxiliary battery hosts

`PSSEMultiFileLoader.setDynamicModelParameterOverrides` and the corresponding
direct-parser API now accept an explicit function from the immutable
`PsseDyrRecord` to a map of zero-based parameter indices and replacement numeric
strings. No caller override is selected by default. The reader copies the fields,
retains source text/location and identity, validates the effective record through
the normal model constructor, and records every entered/effective value in the
import report. Invalid indices, nonfinite replacements and wrapper-allocation
changes fail explicitly. Deferred attachment keeps the same correction audit.
This keeps reviewed source-data fixes separate from native model equations and
avoids case-specific production modes or rewritten private fixtures.

REECA1 import now automatically swaps inverted finite Pmax/Pmin after caller
overrides, in both ordinary and strict import. The warning and report identify
the automatic correction separately and retain any preceding override audit.
Already ordered or equal bounds are unchanged; nonfinite bounds are rejected.
Original source files remain untouched. Existing initialization
then expands effective active-power limits to contain the solved power; the
corrected data limits remain distinct from those runtime limits. This follows
the documented optional correction and initialization behavior of the independent
[electrical-controller definition](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_A.htm).
The typed data constructor still requires ordered bounds; normalization belongs
to the source import boundary rather than the model equations.

REAXB now supports a zero participation weight without dividing by it. For a
nonzero weight its original reference and lag equations are unchanged. For a
zero weight the target is the initialized host output, independent of subsequent
plant commands; legacy incremental initialization therefore retains zero, while
absolute electrical-host initialization preserves nonzero solved P or V/Q. Both
modified-Euler stages, algebraic bypass, staggered rates, monitoring and
reinitialization use this same target. The entered gain remains zero. The
source-located attachment diagnostic identifies this incremental-participation
compatibility behavior. It is supported by the retained independent legacy
conversion and **does not claim native zero-gain trajectory equivalence**: the
native probe still produces a nonstationary active state with that input.

REECC1 battery hosts now expose the same absolute auxiliary P and V/Q port as
REECA1. Initialization converts upstream system-base commands to the host base;
the existing power-factor selection, current priority, energy and state-of-charge
limits remain authoritative. A second plant controller cannot occupy the same
reference port. The shared staggered solver binds the plant and auxiliary once
and grants ownership through the common electrical-controller interface instead
of assuming a REECA1 host. REECC1 with an auxiliary requires this owning group;
its previous standalone single-stage kernel does not independently advance
shared upstream states. Unsupported integration paths fail with a clear message.
A public full-solver flat/disturbed smoke exercises this connection, including a
zero active weight, nonzero reactive response and battery energy evolution.

PLNTBU1 now preserves the entered Fflag and follows the native selector branches.
Exact legacy-wrapper probes apply frequency-reference, active auxiliary and
active-reference steps: only Fflag=1 activates the entire P-control path.
Selectors 0, 71, 2 and -1 produce zero active output. This corrects the earlier
implementation, which gated frequency droop alone while advancing the P PI/lag.
Reactive regulation stays connected. Power-factor mode still measures real power
even when the active controller is off.

Initialization and advancement use distinct conditions. A nonzero selector
initializes the P measurement; an inactive, non-PF path then holds that state.
A downstream converter-reference step changes measured branch MW while the
inactive plant state stays fixed, proving this is not just a flat-run artifact.
The model preserves this distinction in modified Euler, algebraic refresh,
staggered state application and reinitialization.

Inactive P-path deadbands and limits are retained without ordering checks or
evaluation; enabled-path checks remain strict. All constants must still be
finite, times/base nonnegative, and reactive-path limits valid. The original
selector, tiny P filter and inconsistent dormant limits therefore import without
rewriting data, inventing a replacement selector or applying a case-specific
override. A source-located warning/report explains the inactive branch. The
unchanged private profile initializes natively; its repeated command-step traces
match exactly. Private QA now uses default import before and after preparation,
without the former plant correction callback.

Acceptance remains scoped to import compatibility, focused equations and the
retained affected-family reference regression. Full case dynamic readiness and
trajectory equivalence for the new compatibility profiles are not established.

Focused checks pass 30 core-module tests, 103 existing affected-family tests
(including retained independent references), and five private QA tests.
Full integration-source compilation encounters an unrelated OPF/core API mismatch;
the focused run used temporary source selection, removed afterward. Full system
and repository gates remain open. No support label is upgraded by import success.

## Deferred DC Initialization

`Chvdc2DyrModel` retains immutable source parameters and exact static-link
ownership before load flow. It contributes zero injection and zero differential
states. The ordinary and staggered solvers prepare the existing shared runtime
after load flow using the configured outer integration interval, then attach
the established rectifier/inverter ports. Only those ports supply dynamic
injections and advance states.

Preparation validates every candidate before replacing existing ports. Duplicate
owners, conflicting CDC6T ownership, changed terminals, and missing owners are
rejected. Repreparation replaces ports rather than adding duplicate injections;
failed preparation preserves the existing pair. Pre-initialization state copies
retain exact source dialect, constants, flags, and owner identity. Special-branch
resolution is deferred until network-copy restoration has completed. Running
state checkpointing is explicitly rejected.

## Verification Scope

Coverage includes exact field ordering, wrapper allocation rejection, compatible
limiter initialization, nonmonotone-blade equilibrium and reversed-flow rejection,
DC ownership, source copying, repeated initialization, and imported-versus-explicit
flat/fault parity under both solvers. Existing H6E and OEL3C independent reference
tests retain their original tolerances.

The private combined import and source-field discriminator remain in
`qa.largecase`; no private records or reports are added to the plugin. Successful
parsing is separate from host availability, complete model coverage, and general
trajectory acceptance. CHVDC2 independent capture archives were not provisioned
in this worktree, so the two archive-dependent reference classes produced zero
tests; they are not counted as passed trajectory evidence.
# Remaining host and parameter boundaries

The following corrections preserve source values and address independent import,
attachment and startup-screening defects:

- Current flat AC8C records have five selectors and 27 constants. Their exact
  32-parameter mapping shares the existing wrapper mapper; legacy supported
  cardinalities remain available.
- GGOV1 accepts zero droop feedback, a zero opening rate, and signed `Tc` with
  positive `Tb`. Droop is multiplied, zero opening rate blocks that direction,
  and `Tc` is a numerator coefficient. Both model validation and startup screening
  retain the proper-transfer-function check when `Tb` is zero.
- PSS2A selector zero supplies a disconnected zero signal. PSS2B accepts an
  explicit reference to its own bus for a local machine signal.
- UEL2C gain selector zero follows the fixed-gain branch; only selector two
  initializes and advances the adjustable-gain state. Fixed-branch state eight
  remains zero. Existing selector-two equations are unchanged.
- Existing limiter summation/takeover methods on ST4C, ST5B/ST5C, ST6C, DC4B/DC4C,
  AC8C, the legacy static exciter and ESAC2A expose the common limiter interfaces.
  Disconnect methods remove gate ownership rather than install a zero ceiling.
  AC8C and ST6C expose exclusive OEL3C summation ports and initialize them after
  their own physical states. ST6C rejects the exciter-field-circuit input because
  that circuit is absent from a static exciter; machine field inputs remain valid.
- WT2G1 accepts the existing WT12 shaft and aerodynamic models. Static slip sets
  the initial speed; the attached shaft owns subsequent speed, while the rotor
  resistance controller retains its existing electrical-torque feedback.
- HYG3 retains signed no-load flow and nonmonotone ordinates, ignores unused
  zero gate slots, and preserves entered curve order. WSIEG1 also retains sparse,
  repeated and descending segments and an error parameter larger than the speed
  deadband. WSHYGP/WSHYDD normalize unused table capacity without sorting entered
  points; prepared curve evaluation allocates no arrays. Rejected hydro candidates
  do not attach to the machine before validation succeeds.
- H6E accepts `Bgvmin=1`, the regular constant blade-area limit. No epsilon change
  is applied to that entered upper endpoint.
- PIDGOV accepts zero power at its first intermediate gate. Inverting the no-load
  plateau selects its upper gate without division by zero.
- REPCA1 preserves selector `-1` on its voltage branch. Disabled frequency-control
  limits are retained without ordering checks or evaluation. Active reactive
  limits remain checked; reported native voltage-state selection follows the
  same nonzero-selector condition as the equations.
- REECB and REECD accept absolute auxiliary P/Q references with explicit base
  conversion at initialization. Auxiliary integration receives both ordinary
  predictor/corrector stages. REECD wind controls receive their shaft/torque
  inputs independently of its separate additive inertia `Paux` input. Existing
  standalone electrical integration remains unchanged when those ports are absent.

Private retained reference probes establish acceptance and initialization for
the disputed parameter boundaries. Synthetic tests additionally check connected
ports, initialization, both stages, response direction and reinitialization.
These are loadability and focused equation claims; they do not upgrade general
trajectory or full-system acceptance. Optional reference archives remain separately
reported as skipped when unavailable. The private QA report records every absent
parent explicitly; missing-parent policy cannot hide a validation exception.

## Startup corrections after complete import

Import completeness is checked separately from dynamic initialization and the
no-event trajectory. The following startup defects were exposed by retaining all
active controls rather than silently deactivating failed initializations:

- Static generator source impedance is on its own MVA base. The machine
  builder first converts that impedance to system base before applying the
  inverse machine impedance factor. Dividing RAW resistance directly by the
  machine factor applies MVA conversion twice and inflates stator losses for
  large generators, creating an artificial turbine-capacity violation. Source
  data and physical limits remain unchanged. Synthetic initialization checks
  keep copper loss invariant across system MVA and rated-voltage bases, and
  the retained native power audit matches terminal power plus source-R loss.
- ST4 static feedback is an algebraic equation at each integration endpoint.
  A fixed-point pass can diverge when its loop gain exceeds one. The existing
  exciter solves the three clipped affine feedback segments with frozen PI
  storage, validates the resulting root against the complete clipped equation,
  and uses a bounded monotone scalar solve at segment boundaries. It accesses
  the initialized CML block instances and live gains/limits; refreshing endpoint
  inputs does not integrate state a second time. Direct evaluation uses the same
  algebraic boundary. Public equation, endpoint-reuse and existing independent
  trajectory tests retain their original numerical gates.
- An OEL attached to an out-of-service generator is dormant. It neither requires
  a running exciter kernel nor advances its states. Owner and integration-method
  checks still apply; an uninitialized limiter cannot become active merely by
  changing generator status. The normal exciter initialization callback remains
  responsible for initializing a reactivated host.
- GEWTGCU overvoltage correction is inactive below the entered VHVRCR2
  pickup. Starting correction at nominal voltage instead changes solved Q at
  healthy terminals and drives artificial WindVAR response. The retained
  matched-terminal native probe reports zero correction at 1.05046 pu.
  The ordinary source-impedance interface is retained; the trial balanced
  Norton split and its unused core API have been removed. Above-pickup native
  network-iteration behavior remains unqualified, and a deliberately stressed
  isolated probe is retained as failed evidence rather than a passed gate.
- GEWTECU stores shaft speed as deviation for its torque-regulator equation;
  its shaft input setter converts absolute pu speed explicitly. Shaft startup
  uses the same power-to-speed reference as the electrical controller. WindVAR
  stores its initial Q in the voltage integrator and initializes the separate
  proportional voltage-error lag to zero. The running sum no longer adds a
  second Q reference. A nonzero-Q frozen-terminal test checks all 18 states,
  physical P/Q and the absolute-speed input. Matched native source probes
  independently establish these initial state coordinates and equilibrium.
- GEWTECU high-wind protection accumulates positive excess wind speed above
  SPDWMAX and compares the retained exposure with WTTHRES. It does not count
  one second whenever wind exceeds the numerical value of WTTHRES. A native
  sustained-ramp discriminator establishes the excess-speed slope and threshold;
  a finite gust establishes that exposure is retained after wind subsides.
  Normal source wind remains untripped in a two-second native run. The converter
  supplies the live wind-chain speed to its electrical controller, and the low
  rotor-speed comparison uses the same deviation coordinate as that controller.
  Focused tests cover normal wind, accumulated exposure, a lull, threshold trip
  and reinitialization. No entered wind or controller limit is widened.
- SVC protection with zero delay operates instantly only while its voltage
  pickup condition is true. Comparing a reset zero timer to a zero delay without
  the pickup predicate incorrectly trips a healthy SVC. Both UV/OV trip and
  low-voltage current-limit timer checks now require the matching pickup.
  Reinitialization clears timers and restores the physical initial susceptance.
- GGOV1 positive `Dm` subtracts `Dm * (omega - 1)` from turbine power. Negative
  `Dm` contributes no mechanical damping; its speed exponent belongs solely to
  the fuel-limit measurement. Applying either branch as a constant nominal-speed
  power offset can force the inverse-initialized valve outside its travel range
  and excite the turbine lead term. Focused tests cover negative, zero and
  positive branches, an unequal machine/turbine base, equilibrium and a frozen
  off-nominal speed. A retained private native cold-repeat confirms the negative
  branch equilibrium with unchanged source controller constants.
- UEL2C initializes its real/reactive measurement filters on generator MVA base,
  matching its published curve coordinates and runtime measurements. Entered RAW
  system-base powers are converted before filter initialization; otherwise a
  stationary physical signal causes an artificial filter transient. The unequal
  base regression checks initial values and predictor/corrector equilibrium.
- H6E reports an overload bypass if the initial mechanical power has no segment
  on its entered turbine power curve. It retains initial mechanical power, as
  the native implementation does when it ignores that overloaded governor.
  Curve/rating data remain unchanged; other controllers remain active. Public
  accessors expose the reason and status, a warning names the machine and MW
  boundary, and the private run report records each bypass. This path applies
  only to the supported overload condition, not arbitrary validation failures.
  Reinitializing at a valid point clears it and restores ordinary control;
  the exact maximum remains a valid equilibrium.
- WT3G2 initializes its internal voltage using device-base reactance and
  device-base Q. Its network Norton boundary still uses system-base impedance.
  The initialization derives the voltage-base conversion directly from rated
  and bus voltage; it does not read the impedance-factor cache refreshed later
  during network matrix formation. First and repeated initialization therefore
  use the same state base.
  WT3E1 explicitly converts generator device-base P/Q to its online aggregate
  control base and converts active-current commands back. Previously the
  controller treated device-base measurements as system-base values, multiplying
  power twice when the machine and system MVA bases differed. The existing
  equal-base references did not reveal it. Synthetic 21/100 and 200/100 MVA
  boundaries check physical Norton power, initialized commands and equilibrium.
- WT2G1 solves its initial rotor resistance using the saturated circuit at the
  actual terminal voltage. Scaling a unit-voltage saturated admittance by V²
  does not preserve the nonlinear flux equation. Public tests at 0.9 and 1.1 pu
  check the initial terminal power and frozen-voltage state equilibrium.
  Existing unsaturated independent fault references retain their gates.
- WT2E1 effective output bounds include the machine's required initial rotor
  command. This reported initialization correction prevents an
  immediate clip at zero control error. The data object retains the entered
  limits; public accessors and the owner warning expose entered/effective bounds
  and the required command. Anti-windup uses the effective bounds. Reinitializing
  at an interior point restores the entered limits. Private reports retain each
  correction separately from missing-host skips and governor bypasses. The
  source-derived saturated native wind probe reports suspect initial conditions;
  it is diagnostic evidence, not a passed general saturated trajectory gate.
- ESAC2A uses exciter-base field current in both initialization and its
  running saturation/field-feedback equation. The generic `mach.ifd` CML symbol
  is machine-base current, so using that symbol only at runtime introduced an
  immediate field-voltage step. The local feedback function now obtains the
  same exciter-base signal as its initialization and rectifier equations; the
  shared symbol and other exciter families retain their contracts. A high-gain
  frozen-machine equilibrium test covers the first step and 100 steps, and
  existing independent native/secondary-reference trajectory gates are unchanged.
- LCFB1 adds its output bias to the host governor's initialized reference, not
  directly to electrical power. The initialized GGOV1 and digital-hydro droop references, HYG3 and
  WSIEG1 references, and annotated host references are retained separately from
  the measured-power setpoint. Because bus auxiliaries initialize before their
  governors, the first predictor captures the completed host reference once.
  Later integration stages retain that baseline rather than accumulating their
  own previous bias. GGOV1's external setter conversion is explicitly inverted
  for the bias on unequal machine/turbine bases. Existing TGOV1 speed-reference
  mapping and public power-reference reporting remain compatible. Synthetic
  tests check equilibrium, nonzero bias and both initialization orders; two
  retained source-derived native runs confirm the GGOV1/LCFB1 pair is stationary.
- Composite full-removal eligibility examines the internal bus's live incident
  branch list instead of the entire network branch list. Only incident active
  connections can prevent that bus from being disconnected. Endpoint and foreign
  ownership guards remain in place, and the result is not cached: a newly added
  branch or a status change is observed immediately. This removes the whole-case
  relay audit's measured quadratic traversal without changing pickup, timer,
  action or network-convergence semantics. A paired ordinary/shared-solver test
  adds, deactivates and reactivates a foreign connection before a load change.

The expanded cross-module gate executes 511 tests: 470 pass and 41
optional archive tests are skipped. Private whole-network initialization succeeds
with no active controls lost. The retained ten-second no-event gate also passes
at a 1 ms step, with all-bus voltage and machine-speed drift below 0.001 pu.
This establishes the scoped initialization/no-event gate; complete fault
trajectory qualification and catalog verification labels remain separate.

## Edited electrical data and fixed-Q initialization

After all local preparation files have applied in source order, the loader
rebuilds the edited generator/load aggregates using the existing power-flow
aggregation helper. This occurs before dynamic models attach and avoids running
unrelated short-circuit initialization. Plant voltage targets and aggregate
capability therefore reflect the final edited child records, including explicit
zero reactive limits.

A zero-width reactive interval supplies fixed Q and cannot supply a generator
voltage equation. The core re-establishes that boundary after cold-start reset
and contribution aggregation, before equation numbering and external converter
or SVC promotion. Ordinary PV iteration caps do not remove a physical fixed-Q
constraint. Disabled PV-limit enforcement and separate external voltage-control
ownership retain their normal semantics. The limit owner remains available for
result allocation and auditing. Thirty-five focused core tests cover every
initialization mode, zero/nonzero fixed Q, a zero ordinary-PV capacity allowance,
limit backoff and colocated SVC behavior.


### Type-1 generator saturation and initialization coordinates

WT1G1 now keeps the Norton reactance and algebraic stator circuit fixed at their
unsaturated values. The two-point saturation term belongs in the first rotor
state equation, driven by the weighted internal Norton-source voltage. Applying
saturation to the magnetizing admittance instead produced a consistent local
physical-circuit equilibrium that disagreed with the source model's voltage-state
realization. The corrected forcing is `-Se(|EN|)*psiN/(n*Tprime)`, where
`EN=j*psiN`, `n=Xm/(Xm+Xr1)` and `psiN` is the weighted rotor-flux Norton source.
The second rotor equation and entered parameters are unchanged.

Initialization solves those same zero-derivative equations as a local complex
linear system with a frozen saturation factor, updates the factor from EN, and
solves slip at the actual terminal voltage. Scaling a unit-voltage admittance by
V squared cannot preserve a nonlinear saturation operating point. No new model
state, gain, user option or parameter correction is introduced. Runtime current
evaluation no longer iterates a voltage-dependent saturation circuit.

Strict independent initialization checkpoints cover saturated/unsaturated single-
and double-cage cases with matching source reactance. The formerly failing
high-voltage speed checkpoint now passes. Fixed-voltage equilibrium checks retain
requested terminal power and zero state movement. The single-cage source X must
match Xprime; retaining the double-cage source X gives a different experiment.

Retained wind-reference electrical fluxes use the recorded INTERNAL angle frame.
Conformance tests rotate their comparison operands into the fixed network frame,
keeping the original samples unchanged. The old wind experiments retain constant
P/Q static demand, as independently reconstructed from their original network
voltage/angle channels. They explicitly select that experimental load policy;
production multi-file imports retain I-for-P/Z-for-Q defaults. The earlier Z/Z
assumption for these wind references was incorrect.

The remaining two legacy Q failures occurred on the edge of their existing
one-step reference event window. Reference timestamps accumulate in binary32;
centering a strict comparison on nominal decimal event times made that window
asymmetric. Where paired event-side samples exist, tests now center the same
one-step window on the recorded duplicate event timestamp, with a binary32 ULP
rounding allowance. Missing boundary evidence is rejected; the older WT12
streams without paired samples retain their original policy. Four dedicated
clock-window regressions cover both edges, missing evidence and the first sample
outside the window. Numerical channel limits and original samples are unchanged.
All 19 focused parser, equilibrium, native-trajectory and timing tests now pass.
Event-inclusive full-case raw error reports remain unchanged in scope; this does
not imply exhaustive model coverage or full-system stability acceptance.


### Sampled generator-protection delay stages

The shared FRQTPAT/VTGTPAT implementation now compares each positive delay at
the nearest accepted integration endpoint. A strict elapsed-time comparison
added a whole step when six 0.0033333 s intervals fell just short of an entered
20 ms pickup. Independent onset experiments distinguish the actual half-step
sampling rule from a larger floating-point epsilon: 21 ms also selects six
intervals, while 21.7 ms selects seven. Source thresholds and delays are retained.

Pickup and breaker delays have separate elapsed histories. Rounding their sum
is incorrect: at a 1 ms step, two 20.4 ms stages select 40 ms, and two 20.6 ms
stages select 42 ms. A positive breaker begins after the accepted pickup
endpoint; its first positive interval is required even for a sub-half-step
delay. Zero pickup starts the breaker with the first outside-band interval.
The public cumulative timer observation is retained. Recovery before pickup
resets the timer; recovery after pickup does not cancel the latched breaker.

Predictor/corrector stages and zero-time algebraic event solves consume no
relay time. Clipped physical intervals accumulate their actual durations; no
fixed step count or case-specific tolerance is introduced. Fifty-three focused
parser, timing and topology tests pass, including active-fault preservation.
The independent onset timing matrix is established for voltage protection;
initial outside-band frequency probes confirm the shared sampled family but
include the reference's negative-time warm-up. They do not certify an arbitrary
frequency-input initialization protocol. The zero/zero-delay native experiment
logs a timeout without a physical trip and remains unqualified; it does not
justify copying that behavior into the physical disconnection path.


### Type-1 drive-train algebraic input timing

WT1G1 refreshes stator terminal power from the present stage voltage and frozen
rotor fluxes before WT12T1 computes its mechanical derivative. The common
machine lifecycle advances mechanics before electrical fluxes. At a fault-side
voltage jump, cached Pe could therefore still belong to the preceding endpoint
when the first mechanical predictor consumed it. The algebraic refresh consumes
no integration time and changes no state equation, source setting or tolerance.

A regression independently reconstructs present terminal P from this exact
model's pure Norton current override and compares the first derivative against
an independently initialized drive train. It fails with the prior owner and
passes with the corrected input boundary. All 20 focused induction parser,
initialization, independent trajectory and event-clock tests pass with unchanged
channel limits. Existing coupled WT12A1/WT12T1 trajectory errors decrease. A
fresh full-network fault-window verification is retained separately; this is
not yet a complete supplied-horizon stability claim.


### Type-J data and simultaneous machine-stage inputs

Type-J native parameters no longer pass through the compound compatibility
constructor or builder view. Independent initialization retains nonsingular
source reactances, including a removed q transient winding and nonstandard
q-axis subtransient ordering. The latter is reported for review; parameters
are preserved. The builder shares only model-independent attachment/base
wiring, and native validation consumes the native data. Separate modified-Euler
stage-origin regressions also remove a first-order mechanics/electrical split
for Type-J and Type-1 induction generation. See the
[Type-J derivation](type-j-voltage-state-realization.md#native-parameter-preservation-and-coupled-stage-origins)
for the reason, input ownership and focused verification scope.
