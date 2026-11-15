# Machine-only fault-boundary discriminator

This synthetic two-bus system has one GENROU machine and one infinite-bus
machine. Neither machine has an exciter, stabilizer, or governor. A three-phase
fault is applied at 0.05 s and cleared at 0.10 s. Complete reference captures
include both sides of each event and all machine states. Cold repetitions at
each integration interval produced identical CSV files. The capture script is
Core `tools/genrou_boundary_reference_probe.py`; exact source provenance and full
outputs remain in the ignored private evidence archive.

| Network interval | Integration path | First fault-on voltage error (pu) | Reactive-power error (pu) | GENROU damper-flux error (pu) |
| --- | --- | ---: | ---: | ---: |
| 0.5 ms | Modified Euler | 0.0001490 | 0.001719 | 0.0005483 |
| 0.5 ms | Staggered Euler | 0.0000000181 | 0.000000462 | 0.000000138 |
| Quarter cycle | Modified Euler | 0.0009698 | 0.01107 | 0.003366 |
| Quarter cycle | Staggered Euler | 0.0000000353 | 0.000000705 | 0.000000152 |

Mechanical-power error is below 1.4e-8 pu in all four runs. Re-evaluating the
machine Norton current after every converged network solve left the modified
Euler figures unchanged. This rules out missing controller models and a stale
Norton-current cache as explanations for this particular first-step gap.

The two paths use different machine integration and event-history rules. The
modified-Euler path uses its predictor and corrector stages; the staggered
path carries accepted machine derivatives and separate multistep history
across the event. An existing independent machine-state test checks the
reference event-history recurrence. This experiment localizes the difference;
it does not justify changing the modified-Euler equations or asserting that
every longer trajectory has the same error. The focused test
`MachineOnlyBoundaryDiscriminatorTest` checks the high-accuracy staggered
boundary and reports both paths when the private capture is available.
