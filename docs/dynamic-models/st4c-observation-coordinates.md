# ST4C state observation coordinates

A zero-time lag has no differential integration history. Its physical output
still follows its current algebraic input, including a changed terminal voltage
at a switching boundary. Reporting the unused integrator slot as that output
creates an apparent state mismatch even when the controller uses the correct
algebraic signal.

`getStateSnapshot()` continues to return actual integrator storage. The separate
`getNativeStateSnapshot()` maps bypassed transducer, feedback and amplifier lag
coordinates to their evaluated outputs. PI integral coordinates remain actual
integrator states. Neither getter advances equations or changes stored history.
This leaves rollback/debug storage, controller limits and integration unchanged.
Existing named-state keys and their semantic meanings are preserved.

A regression changes terminal voltage with all three lags bypassed, verifies the
reported coordinates against their algebraic outputs, and checks bitwise-unchanged
integrator storage. The existing equation, limiter, initialization and independent
full-solver ST4C checks remain applicable. Observation equivalence alone is not
an independent complete-trajectory verification or justification to retune a
source time constant.
