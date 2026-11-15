# Signed field-compensation input and runtime magnitude

## Reason for the correction

The generator's supplied `Kw` constant can be negative even when the runtime
field-compensation strength is its magnitude. Rejecting or rewriting that input
loses source fidelity; multiplying d-axis current by its signed value changes
the machine's required field voltage and disagrees with independent execution.

A matched-P/Q/V component discriminator isolated this discrepancy. Rotor angle,
the four electrical states and source resistance already agreed, while required
field voltage did not. Changing the exciter could not resolve that difference:
it was present in the bare machine before attaching any exciter.

Independent probes of both the standard model identifier and its supported user
wrapper gave the same field voltage for opposite signs of Kw. Reversing d-axis
current reversed the compensation direction. Larger diagnostic magnitudes
activated each product limit separately. The source constants were unchanged
after initialization. This distinguishes magnitude-of-Kw from the incorrect
magnitude-of-product interpretation.

## Runtime equation and preserved input

For the existing field numerator `N` and d-axis current `Id`, use:

```text
kId = clamp(abs(Kw) * Id, -0.4, +0.4)
LadIfd = N / (1 - kId)
```

The sign of Id remains physical. Do not use `abs(Kw * Id)`, which removes the
negative-current region, and do not clamp Kw itself. Both signed product bounds
and their release remain part of the original model.

`GenqecData` retains the entered finite signed Kw for inspection and export.
`GenqecMachine.limitedFieldCompensationProduct()` centralizes the runtime product.
Both the electrical derivative and the algebraic field-current evaluation call
it. Initialization therefore solves for the same field input that leaves the
electrical differential equations stationary, and the exciter receives the
same compensation interpretation during stepping. No parser cardinality,
integration coordinates, saturation equation, source impedance or controller
parameter changes are involved.

## Verification and scope

The synthetic regression `signedKwRetainsInputButUsesMagnitudeAcrossBothCurrentDirections`
checks retained opposite input signs, equal initial field voltages, and field
current over positive and negative d-axis currents. Its sweep explicitly counts
interior, positive-limit and negative-limit coverage. It fails against the
retained signed-product implementation. The test compares the actual model
objects; it does not replace them with a substitute solver.

Private component probes include 18 actual native/InterPSS runs: matched source
and solved operating points, a bare machine and machine-plus-exciter, standard
and wrapper schemas, zero compensation, both current directions and both signed
limits. At the source-reference operating point the bare-machine initial field
difference falls from approximately 0.0851 to 8.3e-7 pu. Opposite-sign and
zero-compensation controls preserve the intended product behavior. Two original
native fixtures regenerated cold give byte-identical trajectory CSVs.

The fresh private full-system initialization also reduces the target field
difference from approximately 0.0837 to 0.00116 pu. The remaining difference is
consistent with that system's different terminal power-flow voltage; using that
same P/Q/V in the native component reproduces the changed field requirement.

These are initialization, algebraic-limit and focused regression results.
They do not declare complete independent full-network transient trajectories
accepted. The existing positive-Kw electrical replay and fault tests remain
applicable because their runtime equation is unchanged. Long-horizon fault
comparisons must use the corrected runtime before their results can be claimed.

## Historical evidence

The earlier [field-compensation report](../benchmarks/genqec-field-compensation-2026-10-19.md)
tested zero and positive multipliers. It could establish the product limits but
could not distinguish signed Kw from its magnitude. Its historical guard and
bytecode statements describe that earlier verification snapshot; this correction
extends the input contract and changes the runtime accordingly.
