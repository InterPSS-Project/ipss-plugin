# Case 2 controller stack crossover

A fresh frozen endpoint run measured 7.592151 seconds median, with all seven
measurements between 7.549092 and 7.666781 seconds after 32 warmups. The full
recording matches the retained production baseline byte for byte.

Replacing its controller and staggered-integration classes with the audited
production group produced a 12.178111-second median. This mixed diagnostic
runtime is not the production runtime, which previously measured about 9.4
seconds. The 4.586-second difference therefore cannot be assigned directly to
production controller cost. It demonstrates that execution-stack interactions
matter and that isolated micro-optimization results cannot be added together.

The mixed runtime preserves all timestamps, event sides and 4,810 channels.
Its largest voltage difference is `4.2633e-14` pu and its largest rotor-angle
difference is `1.5462e-11` degrees. It is not bit-identical, and this diagnostic
has no accepted internal-state equivalence qualification or independent
reference accuracy qualification. Compiled-stage and preparation counters
confirm that its intended optimized controller paths were active.

Both timing arms include fresh case preparation and full recording, use one
logical processor, a quarter-cycle step and `1e-5` voltage-correction tolerance.
The [evidence index](checkpoints/case2-controller-stack-crossover-2026-10-21.json)
retains every measured value, the raw comparison, failed preliminary attempts,
the separate counter audit, class inventories and archive hashes. The mixed
runtime was not adopted. The frozen endpoint executable was subsequently
[selected as the working performance baseline](case2-fast-baseline-selection-2026-10-21.md).
