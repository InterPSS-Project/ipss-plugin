# OEL2C rated-field-current input

For native input selector 2, OEL2C normalizes the machine's exciter-base
field current by the entered rated field current before applying the
entered input gain. The measured input is therefore
`Kscale * XADIFD / IFDrated`. The conversion belongs ahead of the
transducer, so the initialized and dynamic paths use the same scale.
The entered gain and rating remain available unchanged. The Efd and
prescribed-input paths keep their existing units and behavior.

The implementation previously omitted the rated-current denominator.
With a rating greater than unity, the measured input was inflated and
the limiter could reduce excitation while the physical field current
was below its configured pickup. Terminal voltage and reactive power
could then settle on an incorrect plateau.

Independent native component probes varied the input gain and rated
current separately. For two unchanged source profiles, a rating-only
variation, and a gain-only variation, the measured initial transducer
value agrees with the normalized expression within 1.7e-7 pu.

In a ten-second high-field reference-step test with unchanged controller
data, the former implementation activated the OEL and the reference did
not. The fixed implementation follows the reference through the rising
field response. After 1.02 s, peak field-voltage, terminal-voltage, and
reactive-power errors fall from 3.01890, 0.11471, and 0.99594 pu to
0.01564, 0.000253, and 0.00181 pu respectively. A component-only variant
with explicitly lower limiter bounds exercises active control. The
normalized model reproduces the limiting field trajectory with peak
error 0.03104 pu after 1.02 s.

All twelve selected OEL tests pass, including the existing Efd-input
native trajectories. The two new DYR-to-runtime cases fail before the
change. Large-network comparison remains a separate gate.
