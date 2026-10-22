# ST1 single-step accuracy experiment

Removing the ST1 local substeps is **not qualified for production**. Cases 1 and
2 complete, but the single-step trajectories differ materially from the retained
ten-substep implementation and still fail the supplied-reference accuracy gates.
The production default remains ten local steps.

## Controlled change

An isolated executable overlay starts from plugin commit `8b9c61728`. It changes
only the ST1 local integration count from ten to one, in both the direct and
fallback paths. It retains the same two-step state recurrence, event-history
handling, limits, input holding and network/controller sequencing. This tests
removing subdivisions, not replacing the integration method.

The network step is 1/240 second, dynamic voltage-correction tolerance is 1e-5 pu,
and load-flow tolerance is 1e-3 pu. Each run uses one logical CPU and records all
channels. The local ST1 interval therefore increases from 0.4167 ms to 4.1667 ms.
Pending observation-cache and compact-state-update changes are excluded.

The ten-substep control reproduces the retained Case 1 and Case 2 binary,
channel metadata and CSV outputs byte for byte. Each variant is one cold
correctness run; these runs do not establish a warmed performance difference.

## Ten-second results

Errors below are worst-channel RMSE over all nonnegative recorded samples,
including the separate pre-event and post-event observations. Different columns
can have different worst channels.

| Quantity | Case 1: ten steps vs reference | Case 1: one step vs reference | Case 2: ten steps vs reference | Case 2: one step vs reference |
| --- | ---: | ---: | ---: | ---: |
| Rotor angle, degrees | 42.3457 | 40.7661 | 102.7470 | 108.1341 |
| Bus voltage, pu | 0.0915854 | 0.0915852 | 0.0462767 | 0.0466229 |
| Generator active power, MW | 84.4109 | 84.3501 | 130.7477 | 131.0833 |
| Generator reactive power, Mvar | 48.8002 | 48.8048 | 158.5232 | 138.6538 |

The baseline already has a substantial independent-reference error. A smaller
aggregate error in some single-step channels does not establish correct local
integration or satisfy the rotor-angle target of 0.01 degree RMSE.

| Difference caused by one step instead of ten | Case 1 | Case 2 |
| --- | ---: | ---: |
| Worst rotor-angle RMSE | 5.63295 degrees | 5.88287 degrees |
| Largest absolute rotor-angle difference | 9.55632 degrees | 9.78722 degrees |
| Worst bus-voltage RMSE | 0.00157958 pu | 0.00728416 pu |
| Largest absolute bus-voltage difference | 0.00980177 pu | 0.05916673 pu |

Cases 3 and 4 fail at 0.0458333 seconds with either count. Their short prefixes
are retained for diagnosis, but cannot qualify ten-second accuracy or timing.
The experiment does not show that every ST1 instance requires ten steps, or that
ten is the minimum acceptable count. It shows that changing all of them to one
is not an acceptable accuracy-preserving optimization in these coupled runs.

## Comparison and evidence

Case 1 compares 5,191 channels and Case 2 compares 4,501: all recorded bus
voltages and angles, generator P/Q, and synchronous rotor angles. Bus angles are
reported in the evidence without an acceptance gate; no angular offset or
wrapping correction is fitted. Supplied reference files are unchanged.

Ordinary samples use linear interpolation within their own event segment.
Duplicated fault-application and clearing samples are paired by event identity
and side; interpolation never crosses an event jump. Actual sample times remain
unchanged. The reference clearing timestamp differs slightly from the actual
quarter-cycle boundary, so this is an event-aware comparison, not an assertion
of identical time grids. Range normalization uses the full reference interval
from zero through ten seconds. Raw-unit rotor-angle errors need no range
normalization.

The [evidence index](checkpoints/st1-single-step-experiment-2026-10-21.json)
identifies the private archive, source overlays, run commands, statuses, input
and recording hashes, full channel-error summaries and comparison script. Raw
recordings remain local dependencies rather than being duplicated in the archive.
An initial bus-only scan is superseded by the complete channel-name mapping in
the retained all-channel comparison.

Retain the ten-step default while resolving the independent-reference mismatch.
Any subsequent smaller-count or alternative-integrator candidate needs the same
coupled reference checks before a warmed performance qualification.

## Trajectory plots

The [four-page PDF](figures/st1-single-step-2026-10-21/comparison.pdf) contains
full-run overlays and early-time enlargements for Cases 1 and 2. Blue is ten
local steps, orange is one, and the lower panels show one minus ten. The shaded
interval marks fault application through clearing.

- [Case 1, full run](figures/st1-single-step-2026-10-21/case1-full-run.png)
- [Case 1, first 1.3 seconds](figures/st1-single-step-2026-10-21/case1-early-time.png)
- [Case 2, full run](figures/st1-single-step-2026-10-21/case2-full-run.png)
- [Case 2, first 1.3 seconds](figures/st1-single-step-2026-10-21/case2-early-time.png)

Each family uses its largest absolute difference channel: Case 1 voltage bus
1082 and rotor bus 8155/unit 2; Case 2 voltage bus 3110 and rotor bus 5263/unit 1.
Case 1's voltage channel differs from the worst-RMSE voltage channel, bus 1050.
All 2,403 nonnegative samples, including both event sides, are retained without
resampling. Full-run metrics printed on the enlargements still cover ten seconds.
Reproduce with `python tools/plot_st1_substep_comparison.py`; its optional local
plotting dependencies are in `target/plot-dependencies`. Per-channel metrics and
peak times are saved in `figures/st1-single-step-2026-10-21/plotted-channels.json`.

## Why ten, and the four-step experiment

Ten was selected in commit `639e50c90` to reproduce independently observed ST1
state-history and field-publication behavior. The controlled EXST1 reference
step and ESST1A zero-regulator-time tests discriminate that update sequence.
It is a reference-fidelity choice, not a universal numerical stability rule or
proof that ten is the smallest usable count. See the earlier
[local-integration evidence](texas2k-native-reference-comparison-2026-10-17.md#st1-local-integration-and-field-publication).

Four steps were subsequently tested with the same isolated executable overlay,
input cases, 1/240-second network step, 1e-5 pu network tolerance, and full
recording as the one-step experiment. The local step is 1.04167 ms, compared
with 0.416667 ms for ten. Both Cases 1 and 2 complete ten seconds.

| Worst-channel quantity | Case 1 | Case 2 |
| --- | ---: | ---: |
| Rotor RMSE, four minus ten | 1.98233 degrees | 1.19633 degrees |
| Largest absolute rotor difference, four minus ten | 6.71355 degrees | 2.31567 degrees |
| Voltage RMSE, four minus ten | 0.00202360 pu | 0.00299588 pu |
| Largest absolute voltage difference, four minus ten | 0.0166873 pu | 0.0119014 pu |
| Rotor RMSE against reference, ten steps | 42.3457 degrees | 102.7470 degrees |
| Rotor RMSE against reference, four steps | 44.1859 degrees | 102.3923 degrees |

Four is closer to ten in rotor-angle RMSE than one, but coupled voltage errors
are not uniformly monotonic with step count. Reference rotor error improves
slightly in Case 2 and worsens in Case 1; both variants remain far outside the
0.01-degree target. These results neither qualify four nor prove ten optimal.

The two focused reference-response tests pass with ten steps and fail with four.
At a 0.005-second network step, the controlled EXST1 field increment with four
is 0.00185840528 pu versus reference 0.00185465813 pu: error 3.74715e-6 pu,
exceeding the existing 4e-7 pu tolerance. The zero-regulator-time ESST1A field
increment differs by 1.33937e-6 pu, exceeding its 1e-6 pu tolerance. These are
small local errors; the coupled trajectory comparison measures their broader
effect. The third lifecycle test fails in both configurations because of the
existing load-flow network cast on reinitialization, so this is not a passing
three-test suite.

Ten remains the default. No warmed speedup is claimed for four: these were
correctness runs. Cases 3 and 4 were not rerun with four; their existing early
failure cannot qualify full-run behavior. The
[four-step evidence index](checkpoints/st1-four-step-experiment-2026-10-21.json)
retains the complete channel comparisons, test reports, commands, source
references and recording hashes.
