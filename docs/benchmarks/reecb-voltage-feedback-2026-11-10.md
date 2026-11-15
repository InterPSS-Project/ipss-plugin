# Electrical-controller absolute voltage reference and feedback

REECB1's coordinated reactive/voltage path must pass an absolute voltage
reference from the outer reactive-power PI to the inner voltage PI. The inner
summing junction subtracts measured voltage. The former implementation stored
the outer state as zero and fed its clipped output directly to the inner PI.
It also extended a positive minimum voltage to zero. Together those choices
removed the normal negative voltage-error path: after a disturbance moved
reactive current in one direction, the controller could fail to restore its
reactive absorption as network voltage recovered.

## State coordinates and equations

For `QFLAG=1, VFLAG=1`, with the current/reference sign expressed as positive
reactive injection:

```text
eQ = selectedQ - measuredQ
outerVoltage = clip(Kqp * eQ + xQ, VMIN, VMAX)
eV = outerVoltage - measuredV
innerCurrent = clip(Kvp * eV + xV, IqMin, IqMax)
dxQ/dt = Kqi * eQ       subject to its existing freeze/anti-windup
dxV/dt = Kvi * eV       subject to its existing freeze/anti-windup

xQ(initial) = measuredV(initial)
xV(initial) = initialized reactive current
```

`xQ` is an absolute voltage-coordinate integral. It is not an incremental
voltage bias and cannot initialize at zero for an ordinary energized bus.
For example, initializing at 1.03 pu gives `outerVoltage=1.03` and `eV=0`, while
the inner integral retains the solved reactive current. When measured voltage
rises or falls, `eV` can have either sign even if the reactive-power target
remains unchanged. The implementation's emitted `Iqcmd` uses the converter
host's opposite reactive-current sign; that sign conversion is preserved.

`QFLAG=1, VFLAG=0` bypasses the outer Q PI and routes the initialized absolute
voltage reference through the voltage limits before the same measured-voltage
subtraction. This direct voltage branch bypasses the reactive-power limiter;
a voltage reference must not be clipped as though it were Mvar per unit.
The stored reference therefore uses Q units in Q-control modes and voltage
units in the direct voltage mode. Plant initial physical P/Q measurements are
unchanged; its reference offset follows the selected reference coordinate.

Voltage limits keep their supplied values. Effective initialization bounds
include the actual initial voltage, consistent with the existing companion
controller's initialization accommodation, rather than including zero. The
normal valid-limit case does not widen either bound. The original source data
object is unchanged.

## Changes and preserved behavior

`Reecb1Model` now initializes the outer Q integral in its absolute voltage
coordinate, retains the measured-voltage feedback in both voltage-selector
branches, and applies the physical voltage limits in that coordinate. The
existing direct-Q branch, current-priority selection, transient-current
injection, integration/substep scheduling and voltage-dip freeze rules remain
in their established paths. No supplied gain, time constant, physical limit,
power-flow setting or model selector is changed to reduce a comparison error.
The implementation reuses its existing model/data objects and scalar reference
storage; it introduces no new domain object or case-dependent operating mode.

The `getQControlIntegral()` observation now reports the corrected absolute
voltage-coordinate state when the outer Q PI is active. The direct-voltage
branch leaves that bypassed state at zero. This is an equation/state-coordinate
correction, not merely a display adjustment.

## Validation and scope

Four public synthetic regressions independently distinguish absolute-reference
initialization, bidirectional voltage feedback at unchanged Q target, the
direct voltage branch and a positive physical minimum voltage. They all fail
against the retained old implementation. They pass with the correction, along
with the 40 existing selected electrical/plant parser, control and independent
reference checks: 44 tests, no skips or failures.

An older plant-controller flag test incorrectly rejected an already-supported
negative reference selector. That test now preserves the existing accepted
selector and rejects an unsupported negative value; no plant-controller
production behavior is changed by that test correction.

Private exact-source component evidence includes recorded-input replay and
coupled converter/network disturbances, with all native rows and event sides
retained. It confirms the outer state initializes at sensed voltage and shows
a much smaller reactive-current error after restoring the missing feedback.
Cold reference inputs and complete primary CSV repeat byte for byte. Residual
integration/event-side differences remain quantified separately; this slice
does not claim complete electrical-model state-trajectory verification.

A closed short full-network comparison isolates the resulting plant reactive
power and local voltage improvement using identical case inputs and solver
settings. A fresh full supplied-horizon run is also retained as separate
verification; its completion/parity status must be read from its actual run
manifest. Component completion or a short diagnostic window does not certify
that full-horizon result.

The registered repository suite was attempted, but compilation stops at
pre-existing core API mismatches in other modules, including missing
`getPlantMvarControlPFactor()` methods in the resolved core dependency. That
build failure is not a passing broad regression gate. The focused selection
uses the retained qualified runtime and current controller/test sources with
explicit source/classpath provenance. Catalog support status is unchanged.
