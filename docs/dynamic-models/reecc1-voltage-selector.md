# Direct voltage-control selector

The QFLAG=1/VFLAG=0 branch uses the selected external reference as an absolute
voltage reference. The diagram takes this wire before the reactive-power limits
and applies the voltage limits after the selector. The printed zero labels the
selector position; it is not a zero-valued input.

The implementation previously supplied a literal zero to this branch and
initialized its external reference as reactive power. It now initializes the
external reference to terminal voltage and routes the selected reference through
the existing voltage limits. The inactive PF=0 real-power-filter state is initialized and held at zero, as
confirmed by the independent state channels. The PF branch still uses its existing initialized
power-factor ratio; no artificial voltage offset is added.

Two cold no-event voltage-control captures agree byte for byte, report clean
initialization, and preserve parameter readback. The reference initializes QEXT
to 1.01 pu and retains all six electrical control states and current commands at
equilibrium. An analytic test also confirms the direct reference bypasses a
reactive-power upper bound below the initialized voltage.

A separate paired PF/voltage-selector profile reports suspect initial conditions
in the independent simulator. Both failed-quality captures are preserved for
diagnosis; they are not counted as accepted trajectory evidence, and the model
has not been biased to make this incompatible operating point appear flat.

Private retained evidence:
`verification-artifacts/reecc1-voltage-selector-20261104`.
The extended recorded-input test compares every row for six electrical states
and both current commands, including startup samples. Battery energy/SOC has its
separate paired-limit qualification. Staggered/shared-state support remains open.

Validation: 13 tests pass with no failures/errors/skips. Across all samples,
the six control states and both commands differ by at most 6.903e-9 pu from the
clean reference, below the 1e-8 pu regression ceiling. Existing fault trajectory
ceilings remain unchanged.
