# Compressor lag preparation at the network interval

Status: the legacy standalone and composite compressor now prepare the voltage,
frequency and thermal lag branches before initialization. Both modified Euler
and staggered integration use the same equations, allocated states, numerical
buffers and prepared decisions. This resolves the identified production-step
voltage-filter instability. Complete composite trajectories, restoration,
remaining parameter validation and longer-duration qualification remain open;
catalog status stays `LOADABLE`.

## Independently established boundary

Cold SMIB captures distinguish bypass from differential behavior for `Tv`, `Tf`
and `Tth`. With outer network interval `H`, the observed rule is algebraic bypass
when `T <= 2H`; the lag remains differential above that boundary. These are
observed exact-model preparation rules, not a general assertion that every
dynamic model uses this threshold.

Decimal input readers can differ by a binary32 rounding unit. To establish the
equality branch, the controlled reference probe reads the actual integration
interval and sets each disputed constant through the simulator API. Captures
cover the representable value immediately below `2H`, exactly `2H`, and the
value immediately above it. The raw entered and initialized constants agree:
the simulator selects a branch without rewriting the input constant. Separate
interval and parameter controls distinguish the rule from a fixed cutoff.

Voltage bypass exposes terminal-voltage magnitude. Thermal bypass exposes
`Rstall * I_rms²`, with the existing accepted-input rules for irreversible
thermal actions. Frequency bypass uses the existing sampled-phase input and
event/reset history. The [startup clock](acmtblu1-startup-clock.md),
[accepted electrical inputs](acmtblu1-electrical-inputs.md) and
[thermal-action contract](acmtblu1-thermal-bypass.md) still apply.

## Shared implementation

`LD1PACImpl.configureIntegrationStep(H)` prepares the branches using the source
family's binary32 comparison. Raw getters retain the supplied constants;
`getEffectiveTv()`, `getEffectiveTf()` and `getEffectiveTth()` expose the
prepared choices. Live parameter writes recompute those choices. Reinitializing
with a different network interval also recomputes them without changing accepted
states or histories during preparation. A local RHS or integration-stage
interval does not change the outer-interval decision.

The common solver's preinitialization hook now visits bus devices, dynamic-load
members and composite owners as well as generator controllers. Bus membership
is deduplicated by identity during preparation. `Cmldznu2Model` forwards the
interval to its compressor before composite expansion, so `Cmldblu1Model` uses
the same prepared leaf engine. No per-step membership search is added.

The legacy characteristic owns the new threshold policy. Historical typed and
zone-specific differential conventions remain separate. Direct low-level
clients must call `configureIntegrationStep(H)` before initialization to request
the interval-dependent preparation; an RHS's local duration is insufficient.

## Evidence and limits

`CompressorShortLagTest` retains immutable raw references, exact cold-repeat
comparisons, complete startup and event-side samples, input-consistency checks,
native boundary discriminators, raw-parameter preservation, live writes,
reinitialization, composite preparation and full imported SMIB faults under
both methods. Its positive-lag unit test independently checks predictor and
corrector equations. The warmed algebraic RHS allocation check exercises the
prepared path with reusable numerical storage.

In the repeated-fault control at `H = 1/240 s`, the staggered voltage-filter
maximum error falls from approximately **8.06845 pu** to **0.00007524 pu**.
Across the short-lag coupled controls, staggered maximum voltage-state error is
below `8e-5 pu`, frequency-state error below `7e-6 pu`, and temperature-state
error below `2.5e-5 pu`. Its worst terminal-voltage range-normalized RMSE is
approximately `1.78e-5`; worst rotor-angle RMSE is approximately `0.00163°`.
These bounds qualify the stated controls, not complete model parity.

Modified Euler retains its own integration equations. Both methods preserve
algebraic voltage projection and remain finite in these controls, but the
positive differential-lag states differ at finite intervals. Modified Euler's
worst rotor-angle RMSE is approximately `0.0443°`, with maximum differential
voltage-state error approximately `0.0903 pu`. These differences are retained
as diagnostics; they are not hidden by changing parameters, time shifts or
using reference states in the coupled simulation. Its existing neighboring
model gates are unchanged; this matrix records separate method-specific
boundary criteria and leaves strict differential-state parity open.

Some cold captures reported successful initialization while exposing stale
electrical voltage inputs. They are preserved separately as rejected evidence.
The maintained probe now rejects standalone compressor captures when either
raw voltage input differs from the recorded terminal voltage by more than
`2e-7 pu`. Only healthy, exactly repeated captures enter this test archive.

Reference archive SHA-256:
`effe34b21222ab10de1c3d05ed5fb7c88ca030a3dd45475388940168ff34d40f`.
Exact tool provenance, source hashes, executed API overrides, rejected captures,
before/after runtimes and complete comparison streams remain in private
verification artifacts. This change makes no complete-run speedup claim.
