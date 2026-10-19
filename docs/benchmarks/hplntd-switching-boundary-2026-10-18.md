# Hybrid plant physical switched-shunt boundary

This follow-up to the [kernel slice](hplntd-switching-kernel-2026-10-18.md)
implements physical switching and main-import support on the four-bus/two-unit
testbed. It is bounded model evidence, not complete HPLNTDU verification or a
Texas2k dynamic-simulation result.

## Implementation

`PlantShuntNetworkBoundary` extracts the existing REPCC physical binding for
reuse by both plant models. Each named single-step device must have exact
individual system-base G+jB, including initially open devices. The importer uses
object-owned original-admittance metadata and fails before attaching any hybrid
port when metadata is missing. It never estimates device G from aggregate bus G.
Disabled/invalid-threshold/missing-branch switching needs no physical binding.

An accepted action updates device status, bus conductance, positive-sequence
shunt caches and the dynamic Y diagonal. Changes survive a later matrix rebuild.
No initial-voltage-squared divisor is used. HPLNTDU measurements remain physical
Mvar; REPCC measurements remain machine-base pu. The switching engine's timer,
selection and discharge-lockout algorithm is unchanged.

`HplntdCoordinator` dispatches only after all participating unit correctors are
complete. Converter after-step callbacks share the same owner and time, so a
second owner callback is idempotent. Origin dispatch occurs after solved-point
compensation has been captured; zero-delay actions are not absorbed into that
compensation. Supplied hybrid currents are preserved. Inactive groups cannot
dispatch. Reinitialization reseeds timers from actual physical device statuses.
Enabled switching exposes 32 additional semantic states, retaining the existing
13 plant and two-per-unit state names. Disabled paths retain their previous map.

## Exact references and comparison scope

The existing sixteen twice-cold references are unchanged. Both exact native RAW
networks are now embedded with fixed SHA-256 guards; each imported comparison
uses the corresponding exact RAW/DYR rather than rebuilding an approximate
network. The audit checks all 32 source runs and both embedded network variants.

`HplntdSwitchingNativeSmibTest` retains every native history/duplicate row and
reports per-channel maximum error and its actual native time. Its 26 channels
are V1–V4, both units' system-base P/Q, thirteen plant states, all four downstream
states and bus-2 frequency deviation. Physical-status traces preserve every
query and all ten devices. The separate recorded-input kernel comparison still
checks all twelve threshold/breaker timers.

Eight coupled profiles have exact status agreement and tight per-channel bounds:
positive/negative fast-band operation at 0.5 ms, plus disabled, invalid-threshold
and missing-branch cases at 0.5/0.25 ms. Bounds are approximately 12% above each
observed maximum, with a 1e-12 floor, after byte-exact cold native repeatability.
They are regression tolerances, not adjusted model parameters.

The largest accepted voltage error is about 6.35e-6 pu, active/reactive output
error 4.75e-6 pu, plant-state error 8.60e-6 and downstream-state error 1.28e-5.
Accepted frequency-deviation error is below 6.64e-8 pu. Complete vectors and
times remain in the test reports and paired traces.

## Explicit clock/origin diagnostics

The other eight coupled profiles are not accepted as exact trajectory parity:
fine fast-band profiles have five slot/tick mismatches per direction; slow-band
profiles have six at the coarse step and twenty at the fine step. Identical raw
reactive-power input replayed with only the clock changed reproduces **every**
positive-time native status on the native clock and every InterPSS status on the
double solver clock. Thus the discriminator isolates clock precision rather than
base conversion, threshold selection or physical measurement feedback. No clock
quantization, time offset, epsilon or parameter fitting is applied to production.

For example, a fine-step breaker request at native time 0.00425000023097 s has
not reached its entered 0.004 s delay at 0.0082499999553 s; the double-clock
endpoint reaches its corresponding boundary earlier. Slow-band native float
accumulation likewise changes the first threshold-crossing endpoint.

Both zero-delay profiles retain one origin-status mismatch against the negative
prehistory device query. Positive-time statuses match. Both raw time-zero output
rows are retained, not merged into a fabricated instantaneous observation.
Unused native threshold/discharge VAR storage is still not treated as functional
parity with published timers. Native discharge/reclose tests, exact callback-side
matching, alternate hosts and exhaustive plant/converter lifecycle remain open.

## Additional guards

Focused guards cover full G+jB removal and Y rebuilds, missing nominal metadata
before any port mutation, incomplete shared-stage rejection, repeated accepted
callbacks, zero-delay startup after compensation, preservation of supplied
currents and inactive-owner suppression. Existing REPCC boundary and trajectory
tests remain part of the focused and broad regressions.

## Final gates and evidence

38 focused Java checks, all 401 plugin-core tests (six existing optional skips),
and 1,536 selected SMIB/model/solver tests (one existing diagnostic skip) pass
with zero failures/errors. Forty offline probe/audit/language tests pass.
No full Texas2k dynamic simulations were run. The companion-core dependency
and previous sealed hybrid reference artifacts remain unchanged.

Private archive `2026-10-18-hplntd-switching-boundary-v2` retains all 32 original
native runs, exact inputs, sources, 32 paired/status traces, complete reports,
channel maxima/times, acceptance-versus-diagnostic labels and SHA inventory.
