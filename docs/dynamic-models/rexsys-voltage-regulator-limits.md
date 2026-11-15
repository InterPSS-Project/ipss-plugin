# REXSYS voltage input limit and proportional-only initialization

The voltage-input limiter belongs before the voltage PI regulator. Applying its
bound to the PI output clips away proportional gain and changes the transfer
from terminal-voltage error and stabilizer output to field voltage. A large KVP
makes that error especially consequential; it is an equation defect, not a
reason to retune the imported gain or disable the stabilizer.

The regulator uses

```
vi  = clamp(Vref + Vs - sensedVoltage - feedback, -VIMAX, VIMAX)
uV  = KVP * vi + xVI
dxVI/dt = KVI * vi
```

The following lead-lags and limited regulator lag retain their own output limits.
VIMAX is not a bound on uV or xVI. In particular, saturation of the input does
not introduce an undocumented non-windup bound on the voltage integrator.
The algebraic regulator-feedback loop uses the same limited input equation.

## Equilibrium with zero integral gain

A proportional-only PI block cannot acquire an arbitrary constant integral bias
merely to hold the initial output. Backsolve the actual proportional input.
For the required initial internal voltage VE0 and field feedback IFE0:

```
eI0 = KII == 0 ? IFE0 / KIP : 0
VR0 = KH * IFE0 + eI0
xII0 = KII == 0 ? 0 : IFE0
vi0 = KVI == 0 ? VR0 / KVP : 0
xVI0 = KVI == 0 ? 0 : VR0
Vref0 = sensedVoltage0 + vi0 - Vs0
```

Lead-lag states start at VR0, the current-regulator/bridge output starts at IFE0,
and the feedback washout starts at zero output. A zero-gain proportional-only
controller that cannot supply a required nonzero output fails initialization.
Any initial accommodation of VIMAX compares it with vi0, whose units are the
regulator input, rather than VR0. Entered data objects retain their source values.

Using frozen integral biases with zero integral gains can produce the same
initial field voltage, but places regulator signals on the wrong operating point
for subsequent input and output limiting. The corrected initialization matches
the documented block connections and the independently observed initialized states.

## Evidence and tests

The installed model-library section for REXSYS, including both regulator diagrams,
and the [published excitation-system diagram, page 95](https://www.powerworld.com/files/Block-Diagrams-17.pdf)
identify the input-limit position. Exact manual/library identities and private
source-record probes remain in the private QA repository.

`RexsysVoltageInputLimitTest` is registered in `CorePluginTestSuite`. It covers
high proportional gain, both input-limit directions, integral accumulation from
the limited error, proportional-only equilibrium, integrating-block bias, and
infeasible zero gain. The five new tests and the four existing parser/equation/
equilibrium tests pass, as does the existing independent full-solver SMIB
comparison across all twenty retained channels. These focused results do not
promote the entire model to a new support classification.

A private cold-paired component probe retains all entered constants, initialized
states and every sampled state through small and large reference steps. A
separate supplied-horizon network run checks the damping effect; component
conformance alone is not a system-stability acceptance.
