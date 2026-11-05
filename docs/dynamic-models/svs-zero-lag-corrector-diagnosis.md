# Zero-lag pulse: first modified-Euler divergence

This diagnostic uses the complete retained pulse comparison and hash-pinned independent cold reference. It does not change production equations, integration methods, thresholds, or full-trajectory acceptance gates.

At the right side of the 0.05 s reference pulse, the algebraic command changes from about 0.304501 to 0.444500 pu while physical current remains unchanged. At 0.0505 s both implementations publish that command and solve essentially the same terminal voltage, about 1.028774 pu. Their sensor coordinates already differ:

| Quantity at 0.0505 s | Modified Euler | Reference |
| --- | ---: | ---: |
| Terminal voltage | 1.028773663 | 1.028773546 |
| Sensor state | 1.000143868 | 1.000000000 |
| PI state | 0.304518513 | 0.304514170 |
| Algebraic command | 0.441641143 | 0.444513559 |
| Lead-lag state | 0.000341250 | 0.000262499 |

For sensor storage `s`, input voltage `v`, time constant `T`, and interval `h`, the modified-Euler sequence is `f0=(v_left-s0)/T`, `s_predict=s0+h*f0`, followed by `s_end=s0+h/2*(f0+(v_right-s_predict)/T)`. The new network voltage enters the corrector, so sensor movement during this interval is required by that method. The reference event-averaged two-step recurrence uses a zero pre-event rate and half the right-side rate in the AB2 update; the initial PI and lead-lag increments are therefore `0.75*h*f_right`. Its first sensor increment remains zero. The diagnostic verifies these recurrences separately; it does not substitute the reference method for modified Euler.

At 0.051 s physical current consequently differs: 0.441641143 versus 0.444513559 pu. Thus the first mismatch follows sensor/corrector stage semantics rather than another physical-publication defect.

Small state differences later change exact deadband crossings. At 0.107 s the reference sensor is 1.004969478, inside the entered 0.005 band; modified Euler is 1.005053317, outside it. The reference copies the accepted command into the PI state then, while modified Euler does so at 0.1075 s with a different command. Opposite-side release timing subsequently differs around 0.131 s. The largest retained voltage difference is -0.020569919 pu at 0.132 s.

This localizes the first divergence and a later amplification mechanism. It does not prove the entire 0.0189993 NRMSE is unavoidable under every correct implementation. No additional source-supported model defect is established by these observations. The full modified-Euler voltage gate remains failed; the staggered result remains 0.0000167073 against the unchanged 0.001 gate. Suppressing the corrector's new-voltage input, adding a fitted delay, or changing the deadband comparison would not be justified.

The focused public test is `Svsmo3t2ZeroLagStageDiscriminatorTest`. It checks the actual model's predictor/corrector against independently written scalar recurrences and the retained source's first two-step increments. It is a recurrence diagnostic, not a new full-solver trajectory qualification. Exact source provenance and complete traces remain in the private publication evidence archive.
