# AC7B PID boundary and zero-integral-gain initialization

## Scope and dialect selection

`Ac7bExciter` retains the imported model name as a behavioral selector:

| Record | Layout | Behavior covered by this correction |
|---|---|---|
| `AC7B` | 27 constants | Directional outer-PID nonwindup and `KIA=0` inverse initialization |
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

## Preserved behavior

For nonzero `KIA`, including `KIA>0`, inner initialization remains `IA0=VA0`
and `IR0=FB0`. Its existing tracking equation is unchanged:

```text
dIA/dt = KIA*(eA - 2*(uA-vA))
```

The outer directional rule still selects by the `AC7B` name; this does not
qualify its interaction with a nonzero inner integral. `ESAC7B` retains both
its prior frozen-bias initialization and outer tracking equation. Existing
field limits, rectifier/saturation paths, time-constant policy and integration
stages remain unchanged. Zero-duration steps preserve stored stages and rates.
Runtime range admission of the solved initial point remains a legacy engine
policy, separate from the independently established correction; its persistence
in a synthetic test is not evidence of reference limit expansion.

## Qualification limits

Focused synthetic tests cover both outward and inward directions, exact
boundaries, signed gains, amplifier-only clipping, inverse initialization,
retained nonzero-`KIA`/`ESAC7B` behavior and integration history. Retained private
small-network captures independently establish outward hold at both PID limits
and zero-`KIA` initialization; the qualified profile has `KDR=0` and `KIA=0`.

Independent inward release remains open: an attempted injected-state probe did
not preserve the requested state through the reference integration history.
There is also no exact independent nonzero-`KIA` boundary proof or derivative-kick
proof from those captures. Synthetic coverage is not a substitute for either.
Full corrected-controller trajectory parity and full-network event stability
remain separate qualification gates. Source-specific vectors, simulator
provenance and complete captures stay in private QA evidence.

Implementation: [`Ac7bExciter`](../../ipss.plugin.core/src/main/java/org/interpss/dstab/control/exc/psse/ac7b/Ac7bExciter.java).
Focused tests: [`Ac7bPidBoundaryTest`](../../ipss.test.plugin.core/src/test/java/org/interpss/core/adapter/builder/dstab/Ac7bPidBoundaryTest.java).
