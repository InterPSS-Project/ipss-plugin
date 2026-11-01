# Compressor electrical inputs and event derivatives

Status: accepted electrical inputs, voltage/temperature replay and expanded
coupled SMIB controls pass under modified Euler and staggered integration.
Thermal protection, restoration and complete composite-load qualification remain
open; the catalog status remains `LOADABLE`.

## Measurement boundary

The standalone compressor uses the voltage and heating-current inputs sampled
at the accepted endpoint. Previously its next derivative evaluation used the
voltage after the next network solve. A motor restart changes that network
voltage before the following integration interval, so the voltage filter and
the non-restarting group's temperature moved one interval early.

`LD1PACImpl.ElectricalInputMode` declares the contract before initialization:

- `ACCEPTED_ENDPOINT` stores voltage magnitude and each motor group's squared
  heating current in reusable primitive fields. Native standalone imports
  select this mode automatically.
- `LIVE_NETWORK` preserves the existing typed/composite input convention.
  Composite measurement and restoration qualification remains a separate task.

The accepted callback updates protection/run histories, refreshes motor power,
then samples each group's electrical inputs at that same voltage. Its new
run/stall state therefore determines its next heating input. Derivative
evaluation reads those inputs without recomputing current or committing history.
Modified Euler's predictor and corrector share the declared held inputs;
staggered integration reads the same numerical equations and storage.

Norton-current requests continue to refresh injections at every trial network
voltage. Holding the measurement inputs does not cache a voltage-dependent
network injection. Trial evaluations and zero-duration accepted callbacks do
not advance or reset the sampled history. Reinitialization prepares fresh
inputs, and changing the mode after initialization is rejected.

## Scheduled network events

The existing `NetworkEventInput` callback refreshes accepted electrical inputs
after a scheduled event has converged, without advancing time. A bypassed
voltage coordinate projects to the new input; dropped timer pickups apply their
defined resets without a time advance. Other differential coordinates remain
unchanged. Repeated notification at the same physical time is idempotent.

The staggered participant contract now optionally exposes model-prepared
pre-event endpoint derivatives through `providesNetworkEventLeftRates()` and
`readNetworkEventLeftRates(...)`. The compressor prepares those derivatives
before replacing its electrical inputs. The shared solver uses them only for
the event averaging selected by the coordinate's policy.

These endpoint derivatives remain separate from the preceding interval's
two-step integration history. In particular, the negative history term in the
two-step update is preserved. The distinction becomes visible when a voltage
filter has not settled before fault clearing. Other participants retain their
existing default, and the previously qualified external-input-jump policy is
unchanged. Modified Euler retains its own integration rule and does not consume
the staggered event derivatives.

The prepared buffer is allocated once. Copies are read-only, repeated reads do
not consume it, and the next accepted callback invalidates it. The existing
state fields and derivative buffers remain the sole integration storage; the
[legacy nine-state contract](acmtblu1-protection.md) includes all five timers.
No wrapper model or second numerical realization is introduced.

## Independent checks

Cold-repeat controls include running/stalled groups, repeated restart,
frequency-sensitive inputs, a bypassed voltage filter, and altered voltage and
thermal time constants at two integration intervals. All source channels,
startup observations and event-side duplicates are retained.

Recorded-input replay independently integrates sensed voltage and both
temperatures while feeding frequency. The staggered limits are 1e-6 pu for
voltage and 2.5e-6 pu for each temperature. The retained source uses finite
state-storage precision; the unmasked temperature difference is approximately
1.82e-6 pu in the slow-heating control.

Modified Euler is compared with the exact held-input exponential response.
Its explicit bound includes initial/input observation differences and the Heun
truncation term. Its raw differences from the source integration remain in the
complete reports. The bypassed voltage coordinate also has a separate direct
source-observation gate.

Coupled controls keep their existing voltage, P/Q, speed and rotor-angle
limits. They now also gate staggered sensed voltage at 2.5e-5 pu maximum error
and each temperature at 2.5e-6 pu. The complete accepted-input and repolished
recordings include sampled voltage and both heating currents. They use their
actual endpoint/event-side observations without sample shifts or omissions.

In the repeated-restart control, the former 0.005676 pu sensed-voltage error is
reduced below 1.7e-5 pu. Independent altered-filter replay also distinguishes
the accepted pre-event derivative from the preceding interval's history; the
same strict replay gate applies after that correction.

These checks qualify input sampling and the declared integration policies.
Separate [UV/contactor qualification](acmtblu1-protection.md) now covers those
active branches and all five timer coordinates. These electrical-input checks
do not establish thermal actions, warm restoration,
production-step/long-duration behavior or complete composite trajectory parity.
No whole-simulation timing claim follows from removing repeated current work
inside the derivative callback.
