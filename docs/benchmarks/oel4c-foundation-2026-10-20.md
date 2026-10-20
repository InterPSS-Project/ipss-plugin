# OEL4C equation foundation and independent reference contract

## Scope and source contract

OEL4C has a standalone five-constant data/kernel foundation. It is not yet
attached to a generator and has no catalog/reader entry; the loadable approved
limiter count remains five. This slice is a prerequisite for the bounded runtime
and full coupled SMIB comparison, not completion of OEL4C support.

The visually inspected [PowerWorld OEL4C diagram and parameter table](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20OEL4C.htm)
define a delayed reactive-power error feeding a bounded PI controller. The
downloaded diagram SHA-256 is
`837bbee0fc5423b56a3e75b7ca58797d29a057c1fb37c462ff17ff97c9829db8`.
The installed model-library pages 172–173
independently confirm five entered constants and one published PI state; exact
source provenance and page renders are retained privately. The approved-list
mapping is the existing May 2026 catalog entry; no aliases are invented.

```
Qref, Tdelay, Kp, Ki, Vmin
Qerror = Qref_effective - Qterminal_machine_base
Verror = 0 during an unexpired negative-error delay; otherwise Qerror
PI = Kp * Verror + integral
output = clamp(PI, Vmin, 0)
```

PowerWorld documents Qref=0 as using the owner's power-flow reactive maximum.
The typed data API resolves that default only when supplied a finite,
nonnegative machine-base maximum; it does not silently select a network base.
The standalone validation domain permits proportional-only or integral-only PI
operation, but rejects both gains zero instead of applying PowerWorld's automatic
Kp correction. Negative delay/pickup/integral gain, positive Vmin and nonfinite
constants are rejected. No source parameter correction or curve fitting is used.

The kernel uses the existing core PI integrator in its unlimited realization,
with OEL4C-specific conditional integration at the output boundaries. It does
not reuse the core PI block's automatic non-windup state projection: projection
of a positive idle error would create a negative hidden integral and premature
output when the negative-error delay starts. A permanent transition test
prevents this. No shared CML behavior is changed.

Elapsed time resets after a short overload, but remains through an enforcing
integral-only PI's recovery. Independent references distinguish these branches:
the delayed fault activates at 0.06 seconds; the integral-only case retains
timer history until approximately 0.214/0.213 seconds at the two steps.
The typed foundation exposes one semantic integral state, with elapsed time and
gate status separately observable. Pure observations do not advance history;
predictor cancellation restores integral/timer state. Zero-time, stage ordering,
reinitialization and numerical-overflow checks are included.

## Independent artifacts and exported coordinates

Twenty-four PowerWorld runs cover six profiles at 0.5 and 0.25 ms, each cold-run
twice: flat, delayed fault, zero-delay fault, integral-only, lower saturation and
default-Qmax. The three-phase fault is applied at 0.05 seconds and cleared at
0.10 seconds, three 60-Hz cycles later; each run ends at 0.5 seconds. The flat
profile's required contingency is explicitly scheduled at 0.6 seconds, outside
the simulation horizon, so there is no in-horizon disturbance.

All twelve whole-CSV pairs repeat byte-for-byte. All 36,064 raw rows and 21
channels are retained, including both event occurrences. Exact limiter, AC1C,
GENROU and solver/load-option readbacks are checked before and after initialization;
automatic correction and model overrides are disabled. All profiles validate
with zero errors and warnings. Each run retains its actual probe/runner copies,
inputs, logs, readbacks, exported CSV and executable/input/output hashes.

The reference exports require these distinctions:

- `TSOELState:1` equals the limiter output, not the core integral coordinate.
- `TSOELOther:1` is an integer mode with observed values 0/1/2, not a boolean.
- `TSOELOther:2` is timer storage; printed single precision can straddle an exact delay boundary.
- `TSOELOther:3` is the controlled error after delay logic, not unconditional Qerror.
- `TSOELOther:4` is retained without assigning it an undocumented physical meaning.

The controlled-error channel identity uses measured reactive power and the
exported mode, preserving all rows. Its worst RMSE is approximately 3.59e-8 pu.
This checks the channel mapping only: it is not an independent timer, PI replay,
physical host-routing or coupled trajectory acceptance gate. No time offset or
observed-error-based tolerance is introduced.

An additional diagnostic replays measured terminal Q through the actual Java
kernel on all 18,032 representative rows, including duplicate event samples.
It independently compares output, controlled error and timer at RMSE < 0.001.
Only the flat and lower-saturation cases pass all three quantities at both
steps; eight of twelve cases fail at least one quantity. The controlled-error
RMSE remains below 3.59e-8 in every case. The largest output differences occur
at the fault-application/clearing export rows; integral-only timer differences
occur around recovery expiry. These are localization evidence, not permission
to discard event rows, shift clocks or alter model constants.

Representative all-row RMSE values (coarse / fine):

| Profile | Output pu | Timer seconds | All three quantities pass? |
|---|---:|---:|---|
| Flat | 0 / 0 | 0 / 0 | yes |
| Delayed fault | 0.00608 / 0.00430 | 2.34e-10 / 2.32e-10 | no |
| Zero-delay fault | 0.00969 / 0.00686 | 2.34e-10 / 2.32e-10 | no |
| Integral only | 5.05e-5 / 2.52e-5 | 0.00731 / 0.00515 | no |
| Lower saturation | 0.000632 / 0.000447 | 2.34e-10 / 2.32e-10 | yes |
| Default Qmax | 0.00279 / 0.00197 | 2.34e-10 / 2.32e-10 | no |

The complete diagnostic retains per-channel peaks and their timestamps. Its
source, output and reference dependencies are sealed; a portable permanent
recorded-input test is still TODO. Event-export phase and the PI recovery/expiry
transition must be resolved before claiming independent timer/output parity.

Final gates: 267 focused tests, 435 core unit tests and 2,219 registered scoped
SMIB/model tests, zero failures/errors and seven existing skips, plus 253 offline
checks. The final gates include the independently committed renewable-controller
fix already present in the shared repository. All 86 prior limiter exports remain
unchanged. The evidence seal is `2026-10-20-oel4c-foundation` in the private
verification-artifact directory.

## TODO and acceptance boundary

- [x] Establish exact five-constant data order and bounded equation foundation.
- [x] Add direct stage, delay/recovery, conditional-integration and lifecycle tests.
- [x] Produce twelve cold-repeatable PowerWorld pairs with exact readbacks.
- [x] Preserve raw coordinates and distinguish output, integral, mode and controlled error.
- [ ] Add portable permanent reference fixtures and full recorded-input PI/timer replay.
- [ ] Implement bounded exciter-host/measurement integration, including machine-MVA/Qmax conversion.
- [ ] Add strict record import, ownership, preprocessing and host rejection tests.
- [ ] Run imported and typed flat/fault SMIB profiles on core GENROU.
- [ ] Compare every coupled channel independently at RMSE < 0.001, retaining peaks and event-side errors.

No full Texas2k dynamics are run. OEL4C is not yet `LOADABLE` or `VERIFIED`.
