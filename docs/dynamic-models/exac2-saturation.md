# EXAC2 exciter saturation

EXAC2 fits its exciter saturation curve to the two DYR points
`(E1, SE1)` and `(E2, SE2)`. The fitted zero-saturation intercept
`A` can be negative even when both entered voltages are positive.
For a valid two-point fit, the helper uses the following terms.

```text
r = sqrt(E2 * SE2 / (E1 * SE1))
A = (E2 - r * E1) / (1 - r)
B = E1 * SE1 / (E1 - A)^2
SE(VE) = 0                         for VE <= abs(A)
SE(VE) = B * (VE - A)^2 / VE       for VE > abs(A)
```

Native EXAC2 recordings with a negative fitted intercept showed zero
saturation below its magnitude. Using the signed intercept as the
cutoff activates saturation at the initial field state in that range.
It raises the initial exciter feedback and changes both the reference
voltage and the recovery response. The sign of the intercept remains
part of the polynomial above the cutoff.

`Exac2Exciter.saturationAt` selects the absolute cutoff on the shared
two-point helper. Both the initial field-current feedback and the
dynamic feedback block call it. This keeps initialization and the
integrator on the same curve and leaves each other exciter's cutoff
selection with its own caller. The synthetic negative-intercept
regression checks initialization, zero saturation at the boundary,
nonzero saturation above it, and the original value at the upper fit
point.
