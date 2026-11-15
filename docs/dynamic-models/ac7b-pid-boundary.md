# AC7B PID/PI boundaries and zero-integral-gain initialization

## Scope and dialect selection

`Ac7bExciter` retains the imported model name as a behavioral selector:

| Record | Layout | Behavior covered by this correction |
|---|---|---|
| `AC7B` | 27 constants | Directional outer-PID and inner-PI nonwindup, plus `KIA=0` inverse initialization |
| `ESAC7B` | 28 constants; reordered field/feedback parameters and trailing `SPDMLT` | Existing tracking and initialization retained |

The reader preserves that distinction through attachment. Sharing a class does
not establish that independently observed boundary behavior applies to both
dialects. This correction changes no record layout or entered parameter.

## Outer PID limit

Let `e` be the complete outer error, including compensated voltage sensing,
reference, stabilizer/limiter input and rate feedback. With outer integral `IR`:

```text
uR = KPR*e + IR + derivativeOutput
vR = clamp(uR, VRMIN, VRMAX)
r  = KIR*e

dIR/dt = 0   if uR >= VRMAX and r > 0
        0   if uR <= VRMIN and r < 0
        r   otherwise
```

The signed integral rate determines outward motion, including with negative
integral gain. Equality at a limit is included. Inward motion remains enabled.
Clipping only the downstream amplifier does not freeze the outer integral.

The previous outer equation included a tracking term proportional to
`uR-vR`. Independent outward-limit captures held `IR` constant while that
tracking equation generated a large spurious rate. The resulting integral
displacement could persist after the disturbance. The directional gate removes
that artificial displacement on the `AC7B` path.

## Why `KIA=0` needs a different initial allocation

Define inner feedback `FB = KF1*EFD + KF2*VFE` and the amplifier equations:

```text
eA = vR - FB
uA = KPA*eA + IA
vA = clamp(uA, VAMIN, VAMAX)
```

For `AC7B` with `KIA=0`, independent initialization captures have `IA0=0`.
Obtain the outer integral by inverting the proportional amplifier at zero
outer error:

```text
VB0 = 1 if KP=0, otherwise KP*physicalTerminalVoltage0
VA0 = VFE0 / VB0
IR0 = KF1*EFD0 + KF2*VFE0 + VA0/KPA
IA0 = 0
```

The rotating-field/rectifier initialization supplies `EFD0` and `VFE0`.
Potential-source voltage remains physical terminal voltage; compensation belongs
to sensing. Previously, initialization stored `VA0` as a frozen inner-integral
bias. That allocation can agree in the interior but changes the amplifier
response when the outer PID clips. When both inner gains are zero, a required
nonzero `VA0` is unrepresentable and initialization fails explicitly.

## Inner PI limit with nonzero integral gain

The native 27-constant `AC7B` record uses the same directional nonwindup rule at
its inner amplifier. With `eA`, `uA` and `vA` defined above:

```text
rA = KIA*eA

dIA/dt = 0    if uA >= VAMAX and rA > 0
         0    if uA <= VAMIN and rA < 0
         rA   otherwise
```

Equality at the amplifier limits is included. The signed rate permits inward
recovery while the amplifier still clips; a negative integral gain must not be
treated as though its rate had the sign of the error. Inner clipping does not
freeze the separate outer integral. A zero integral gain remains zero-rate.
Inside the amplifier limits, the ordinary PI equation and local Jacobian are
unchanged because the former clipping residual was also zero.

The previous implementation used `KIA*(eA - 2*(uA-vA))` for this native branch.
Independent nonzero-gain captures show a held inner integral during the stable
outward-saturation interval. The tracking term instead drove the integral far
from its initial value even while the clipped amplifier output still matched.
After clearing the disturbance, that hidden state displacement suppressed field
voltage and enlarged the machine's first rotor-angle swing. Restoring nonwindup
removes the artificial displacement without changing a gain, limiter, reference,
initial operating point, integration step or damping coefficient.

Nonzero-`KIA` initialization is unchanged: `IA0=VA0` and `IR0=FB0`. The inner
integral is not itself clipped to the amplifier's output bounds; the gate acts
on its derivative. Existing predictor/corrector states and saved slopes retain
their coordinates. Focused regressions cover both limits, equality, signed gains,
inward release and accepted-step history; they fail with the old equation.

## Preserved behavior

The reordered `ESAC7B` dialect retains its earlier tracking equation for both
integrators and its frozen-bias initial allocation. Independent captures in this
slice qualify the 27-constant record only. Field limits, rectifier/saturation,
time-constant preparation and integration scheduling remain unchanged. Runtime
range admission of the solved initial point remains a legacy engine policy;
synthetic persistence tests do not establish reference limit expansion.

## Qualification limits

The source-specific coupled component has nonzero `KIA`, `KDR=0`, both amplifier
limits active, and its actual machine/exciter/stabilizer stack. Cold reference
RAW, DYR, primary boundary CSV and complete controller CSV repeat byte for byte.
The corrected ten-second component substantially reduces angle and field-voltage
errors with unchanged source constants; quantitative trajectories remain in
private QA. All 27 selected parser/equation/initialization/limiter tests pass; the
four new native
inner-boundary discriminators fail against the retained old implementation.

This is not complete state-trajectory qualification. Internal-state residuals
are retained against the strict paired state gate, including failures. Halving
the component step does not uniformly improve its comparison with the unchanged
reference step, so it is not used as a fitted correction or as evidence that
remaining integration/event-side differences vanish. The original boundary rows,
saturation transitions and both event sides remain in primary errors. No
complete derivative-kick or reordered-dialect boundary proof is added here.

A separate full-network first-swing diagnostic and supplied-horizon rerun retain
the original solver/case configuration with only the corrected class overriding
the old runtime. Their actual terminal manifests determine completion; a live
run or successful small component is not a full-system parity acceptance.
Source-specific data, exact simulator/manual provenance, original trajectories
and comparison artifacts stay in private QA. Catalog support status is unchanged.

Implementation: [`Ac7bExciter`](../../ipss.plugin.core/src/main/java/org/interpss/dstab/control/exc/psse/ac7b/Ac7bExciter.java).
Focused tests: [`Ac7bPidBoundaryTest`](../../ipss.test.plugin.core/src/test/java/org/interpss/core/adapter/builder/dstab/Ac7bPidBoundaryTest.java).

## Negative regulator limit with zero KL

The reference AC7B implementation treats zero KL as a bypass of the negative
regulator limit. The PI amplifier's signed output then feeds the
regulator through the existing potential factor. Applying
`max(VB * VA, -KL * VFE)` at zero would force the regulator output
nonnegative and slow field-voltage recovery after a fault.

The native component test reconstructs the regulator input from
the recorded internal field-voltage rate and field feedback. For
zero KL, the signed amplifier gives the measured negative
regulator trajectory. Changing only KL to 0.2 activates the lower
bound at `-0.2 * VFE`. The shared algebraic calculation bypasses
the lower gate only for native AC7B with exact zero KL. The PI
amplifier bounds, upper regulator cap, dynamic field limits and
reordered ESAC7B path retain their original behavior.
