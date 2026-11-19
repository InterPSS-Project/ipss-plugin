# Renewable startup state and local stability diagnostic

Read-only state traces reproduce the Case 3 and Case 4 early failures exactly. A separate held-voltage linearization identifies discrete-update instability in 35 of 117 eligible zero-Q solar converter bundles per case at the quarter-cycle step. Increasing local renewable updates avoids immediate failure but does not restore supplied-reference trajectories. No production default or performance claim follows from this diagnostic; the eight-second complete-run goal remains open.

## Stored-state evidence

The observation helper reads named states, current integration coordinates, previously stored derivatives, controller memory and initial parameters. It does not explicitly refresh currents, outputs or network equations. All binary-network and selected-CSV bytes match the retained uninstrumented prefixes for Case 3 with one/four local steps and Case 4 with one. Channel metadata match except the expected sample-count/end-time fields when the four-step run is shortened to 0.1 s. Binary framing independently verifies those summary fields. Each snapshot covers all 290 active converter bundles.

Both one-step runs fail on the attempted network solve starting at 0.0458333 s, with a voltage magnitude above 10 pu at bus 1007. In Case 3, bus 3135 unit 2 reactive-current state changes from +0.031687 at 0.0208333 s to -0.079459 at 0.025 s and -0.213251 at 0.0291667 s. Bus 1007 unit 1 later changes from -0.491052 at the last accepted point to +0.632839 in the failed attempt; the stored last derivative is +165.240 per second. Case 4 shows the same leading devices and alternating growth. These are stored coordinates/derivatives, not reevaluated right-hand sides or proof that a leading device has incorrect data.

## Held-voltage integration discriminator

The isolated model uses the captured physical converter/electrical parameters and initial terminal voltage/power, holds terminal voltage and plant reference fixed, and omits wind controllers. Its scope is the 117 exact-zero-initial-Q, local Q/voltage-control bundles whose initial voltage is inside the dip and voltage limits. One-sided nonzero-Q recovery limits, active constraints, plant/network modes and general fleet stability are outside this linearization.

The seven coordinates are converter reactive current, the two PI integral perturbations, the retained published current command, and the three preceding derivatives. The derived update follows the runtime command publication order; it does not substitute an immediately refreshed current command. The continuous three-state closed loop has negative-real-part poles for all 117 eligible devices. Binary64 central-difference checks against the actual Java model at four leading devices, five local-step counts and two perturbation sizes check 560 matrix columns across Cases 3/4; the largest element error is below 4.6e-11. Binary32 rounding is deliberately excluded from this smooth local derivative calculation, while the coupled runs retain their original binary32 state storage.

| Local renewable updates per network step | Unstable isolated bundles, Case 3 | Case 4 |
| --- | ---: | ---: |
| 1 | 35 | 35 |
| 2 | 12 | 12 |
| 4 | 1 | 1 |
| 8 | 0 | 0 |
| 16 | 0 | 0 |

A spectral radius above one means a small perturbation grows per local update in this held-voltage model. Case 3 bus 3135 unit 2 has radii 2.215579, 1.525726, 1.020577 and 0.999576 for one, two, four and eight local updates. Its fastest continuous pole is about -910.37 per second. Four updates are therefore still insufficient for this isolated discrete loop. These results justify a discriminator, not an automatic substep policy or a full-network stability guarantee.

## Coupled comparison through 1.2 seconds

Four new runs retain one logical CPU, the Java sparse solver, a 1/240-second network step, 1e-5 complex-voltage correction tolerance, one ST1 update, unchanged physical parameters and full recording. Each uses eight or sixteen local updates of the complete renewable bundle against held network voltage, with internal power observations refreshed. All four complete 1.2 seconds, including bus-6307 fault application at 1 s and clearing after five cycles. They are cold diagnostics, not warmed timing measurements.

All 5,579 supported channels in Case 3 and 4,919 in Case 4 are compared, including voltage, bus angle, generator P/Q and synchronous rotor angle. The retained four-step runs are evaluated over the same 1.2-second window. Every nonnegative actual sample and both event sides are included; reference interpolation stays inside event segments, and no time/angle offset is fitted. Range normalization uses the full ten-second reference channel. Four unrecorded P/Q channels for the previously netted units remain listed explicitly; no channels are synthesized. Negative startup rows remain in raw files.

| Case | Local updates | Max voltage error through 0.1 s, pu | Prefault max voltage error, pu | Worst rotor RMSE through 1.2 s, degrees | Prefault max rotor error, degrees |
| --- | ---: | ---: | ---: | ---: | ---: |
| 3 | 4 | 0.028609 | 0.263015 | 18.265288 | 33.709128 |
| 3 | 8 | 0.028357 | 0.251480 | 19.046646 | 35.120426 |
| 3 | 16 | 0.028221 | 0.252663 | 16.380430 | 31.335749 |
| 4 | 4 | 0.030998 | 0.283789 | 25.436054 | 37.293250 |
| 4 | 8 | 0.030906 | 0.278469 | 29.819786 | 50.981550 |
| 4 | 16 | 0.030863 | 0.262997 | 23.923176 | 33.144268 |

The large remaining errors reject substep count alone as the accuracy fix. The early local numerical instability and the later reference mismatch must be investigated separately. Finer local integration is not a reason to relax accuracy limits, change physical parameters, select an arbitrary initial-Q sign, or claim the performance target is met. The next useful accuracy discriminator is recorded-boundary playback of the upstream controllers; another full timing sweep of these inaccurate trajectories is not justified.

## Reproduction and retention

The first trace runner stopped on the intentionally reproduced simulation failure. Its resume path accepts only the exact expected failed step and nonzero exit status. A later metadata comparison incorrectly required a shortened run to have the original ten-second sample count/end time; this was corrected by validating binary framing and allowing only those two fields to differ. Completed runs were reused, not repeated. Original runner, corrected runner, logs, sources/classes, state snapshots, matrix results, all-channel errors, commands, affinities and runtime/input identities are retained. Full binary/CSV recordings remain external dependencies identified by content hashes. No supplied reference or production source is changed.
