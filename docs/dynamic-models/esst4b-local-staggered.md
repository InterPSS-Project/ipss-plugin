# Local staggered integration for ESST4B

A reference-simulator component replay identified the rate-history update for the
ESST4B path with an algebraic transducer, a dynamic amplifier, zero inner
integral gain, zero field-voltage feedback gain, and inactive external
limiters. At 3.3333 ms and 1.66665 ms, the native field-voltage sample closes
against the recorded amplifier state and electrical inputs within 1e-6
field pu. The staggered replay needs the average of the converged
pre-switch and post-switch rates at each event. Using only the post-switch
rate amplifies the disturbance.

Set `setEsst4bLocalStaggeredMachineIds` on the dynamic solver before
initialization to select qualified machine IDs when using modified Euler
network coupling. The solver checks the initialized controller topology,
active host, and resolved parameters. Each selected exciter integrates its
outer regulator and amplifier from the left boundary and the preceding
accepted rate. Its corrector refreshes the algebraic output. The machine
publishes the field voltage through the ordinary exciter path. Source
parameters and integration choices on the other controllers are preserved.

The pre-event rate becomes eligible only after the corresponding post-event
network solve converges. A first post-event fragment below 99% of nominal
uses modified Euler for that event segment. An interval ratio greater than
two suppresses extrapolation. The previous rate changes on accepted steps.
The solver's physical-step transaction restores the controller block
inputs, states and derivatives, and the machine field voltage after a
rejected trial, including in the modified Euler fallback.

The exact-source fixed-input replay reaches about 3.5e-5 field pu peak error
with event averaging. The coupled Colorado prototype changed Bus24312/3
time-weighted field-voltage MSE from 0.01909 to 0.01929 field pu squared
through two seconds. The option is explicit because fixed-input agreement
and coupled accuracy are separate checks.



The solver-level SMIB tests cover scheduled fault application and
clearing, a deliberately rejected corrector, retry with the same
rate history, and the short-interval fallback. The matched Colorado
production pair passed its two-second event checks. At the selected
unit field-voltage MSE changed from 0.0190859 to 0.0196029 pu squared.
The 35-second paired verification uses the same implementation.

An independent 10-second reference-simulator component run also exercised nonzero
inner integral gain and saturation of both regulator outputs. With native
input replay, averaging the two switching-side rates for all three states
reduced fault and early-recovery field-voltage RMSE from 0.0237 pu to 9.57e-6
pu. The coupled component reduced full-horizon field-voltage RMSE from
0.00668 to 0.00499 pu. This extends the explicit option to an active inner PI
using the native integral-state limits. The inner state is advanced from the
accepted amplifier output and participates in the same trial rollback and
accepted-rate history as the outer loop.
