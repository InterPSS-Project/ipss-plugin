# ST2CUT bypassed lead-lag state observations

The canonical CML state and the reference simulator's allocated state slot have
different meanings when a lead-lag is bypassed. This is an observation correction;
the controller equations, integration memories and output trajectory are unchanged.

For each of the three lead-lags, the existing nonzero-denominator conversion is
`nativeState = Tden * canonicalState`. When both time constants are zero, the CML
block is an algebraic bypass and its integrator memory remains dormant. The
allocated reference slot follows the bypass output. Multiplying the dormant
memory by zero incorrectly reported a constant zero during a moving signal.

`St2cutStabilizer.getPsseStateCoordinates()` now uses the accepted block output
for a zero denominator and retains the existing scaling for a positive
denominator. The private helper reads the initialized runtime block, without
refreshing its input or writing any state, derivative or predictor history.
`getNamedStates()` continues to expose canonical CML memories, including the
zero dormant memory. No model input or public state name changes.

Independent component probes exercise each bypass position. For the first
bypass, the allocated slot agrees with the preceding washout output reconstructed
from reference states within 2.9e-10. A second-bypass discriminator reports the
same moving signal as its preceding bypass slot exactly. A third-bypass
discriminator agrees with the preceding active lead-lag's algebraic output
within 1.8e-9. The original source fixture repeats cold with identical input,
primary-channel and controller-channel CSV bytes. Source data and exact
simulator provenance remain in private QA evidence.

The synthetic regression checks positive and negative inputs at all three zero
denominators. It confirms that the observed reference slot follows the input
and that observing it leaves the canonical memories unchanged. The new test
fails on the former implementation. All four tests in the existing stabilizer
conformance class pass after the correction, including its original active
lead-lag trajectory and algebraic limit checks.

In the private source stack, the previously false zero-slot error decreases
from approximately 0.00395 to 0.000727. The remaining input and integration
differences are retained. Exact primary-trajectory equivalence and read-only
guards demonstrate that this correction cannot be claimed to improve the
physical rotor, field or network response. Full-system reference parity remains
an independent open investigation.
