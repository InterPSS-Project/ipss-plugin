# DC4B combined PID output limit

The voltage error drives the proportional, integral and filtered
derivative terms in parallel. The PID output limit applies after the
three terms are summed. The lower and upper limits are VRMIN/KA and
VRMAX/KA. The integral holds if the combined PID sum is on a limit and
the signed integral rate would drive it farther outward. Inward motion
is allowed. The limiter gates and terminal-scaled regulator bounds remain
downstream of the PID.

The previous implementation also limited the PI subtotal before adding
the derivative. During recovery from a fault, the PI and derivative
contributions can oppose one another. Clipping the subtotal prematurely
changed the magnitude of that cancellation and made the integral follow a
different limit from the published output. The PI observation now exposes
the actual subtotal entering the summation. The PID observation remains
the limited output.

An independent reference probe recorded the controller's internal states
and output during a source-stack disturbance. The native PID output was
reconstructed as clamp(KP times error plus integral plus filtered
derivative), with maximum residual 0.0001233 pu at the quarter interval.
On 3,286 shared intervals away from switching boundaries, the mean
squared error of the reconstructed integral rate was 0.26417 using
the old PI boundary and 0.000864 using the combined PID boundary.

With the reference terminal voltage and stabilizer signal played into the
controller, maximum field-voltage error fell from 0.32570 to 0.02288 pu
at the supplied interval and from 0.33007 to 0.00129 pu at a quarter
interval. In the complete two-bus source stack at the supplied interval,
field-voltage peak error fell from 0.31958 to 0.02314 pu and rotor-angle
peak error from 1.19221 to 0.06528 degrees.

The synthetic regression exercises derivative cancellation and
outward integral inhibition at both rails. Both tests fail on the
previous implementation. The focused run passed 77 of 78 tests.
The existing coupled reference test retains its pre-change first failure
at the remote bus voltage tolerance. Native evidence covers the DC4B
profile and common PID implementation. Full-network verification and
independent ESDC4B dialect verification remain separate.
