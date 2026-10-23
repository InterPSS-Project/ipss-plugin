# Best two-case source checkpoint restored

The [subsequent timing investigation](best-timing-reproduction-2026-10-23.md)
corrects the compiler mode in the replay recipe and records warmed medians of
8.909746 s / 6.429334 s on quiet CPU 6. The selected working replay now passes
that CPU explicitly; direct commands without `--cpu` retain historical CPU 0.

The working core and plugin repositories now use branch
`restore/best-two-case-20261023`. Plugin numerical sources are restored to
`a60d30e80e8a806d23587ce633722dc92eb44bae`, whose retained measurements report
8.596508 s for Case 1 and 6.519984 s for Case 2.

The corresponding core base is `dd6a03d6e567863db93d8eec4abbcb8b1dfdf095`.
That commit alone does not contain the complete measured configuration: the
timing harness requires an accelerated network solver and helper that existed
only in retained experimental artifacts. Both exact sources were recovered
and committed as `46c8576e617fc0427dc42d3de7f9cafe64e38ff6`. After removing
debug metadata, the recovered solver and helper match the measured runtime
instructions. No equations or convergence criteria were tuned.

## Recovery state

Both repositories retain `backup/before-source-point-rollback-20261023`:

| Repository | Preserved head |
| --- | --- |
| Plugin | `38075576c3a7f7abf80b887429e0e7e069ce27d3` |
| Core | `a342f04ea365d28ab7cfb24454de98f5da51a4bc` |

The original branches, earlier backups, and plugin stash
`6b3355d459e05cb4ef417fcf7f03629208d1723b` remain intact. Supplied model files
and references were preserved. The output-directory junction remains intact.
The new plugin commit adds replay and recovery records without changing the
historical plugin numerical sources.

Both repositories mark the rebuilt checkpoint with
`checkpoint/best-two-case-source-20261023`. The working performance selector
now chooses this source checkpoint; the existing `--selected` replay command
routes to its source rebuild.

## Rebuild and replay

```powershell
python tools/run_bounded.py --log target/best-source-case1.log -- python tools/replay_best_source_checkpoint.py --case 1 --output target/best-source-case1
python tools/run_bounded.py --log target/best-source-case2.log -- python tools/replay_best_source_checkpoint.py --case 2 --output target/best-source-case2
```

Use fresh output and log names on each invocation. The runner checks the source
revisions and hashes of retained dependencies, compiles all 1,836 core and 885
plugin main source files using only external libraries, and compiles the
historical timing harness separately. Archived engine classes are absent from
the runtime classpath. The measured recording, preparation and configuration
fixtures are pinned compiled benchmark helpers, staged through an explicit
class-name whitelist together with their resources. These helpers are separate
from the rebuilt production classes. Compilation completes before simulation
timing begins.

Default replay uses the historical CPU 0 affinity, sixteen alternating warmups,
and five alternating measured pairs. The reported median uses the five
optimized runs. The controller frame remains opt-in in the library; the
historical harness selects it for the optimized arm. Runs retain the pure Java
sparse solver, quarter-cycle timestep, 1e-5 pu network voltage-correction
tolerance, one ST1 local step, original network acceleration and eligibility,
and complete recording. Each case timer includes parsing, power flow,
initialization, simulation, recording and writer closure.

For two complete trajectory checks without a warmed timing claim, add
`--verify-only`. This changes repetition count, not the simulated duration,
recorded channels, sampling, equations, or numerical settings.

## Verification and limits

The dependency-only main-source build passes. Its 3,853 preexisting classes
are byte-identical to the isolated build used for trajectory validation; the
additional recovered helper has the measured instructions. Fifteen focused
controller tests pass with the numerical-frame option disabled and the same
fifteen pass with it enabled. Four initial complete ten-second simulations,
two per case, reproduce the protected full-network binary, selected CSV, and
channel metadata byte for byte. The replay command receives additional
complete-case verification: two runs for each case, with all production classes
rebuilt and only twenty explicitly pinned benchmark support classes staged.
All eight complete recordings match. The existing selected-command route also
passes a construction check without launching additional simulations.

These checks preserve the saved trajectories; independent reference accuracy
remains unresolved. Two verification repetitions are not a warmed performance
benchmark. The historical 8.596508/6.519984-second results are retained evidence,
not a claim that those elapsed times have just been reproduced on today's host.

This source point also restores the older initial-condition reporting code.
The later fix that prevents detailed machine reports from refreshing cached
currents remains on the core backup branch. The verified benchmark leaves
those optional detailed diagnostics disabled.

The [checkpoint index](checkpoints/best-source-point-rollback-2026-10-23.json)
identifies both source revisions, recovery refs, exact archived sources,
reproduction scripts, build commands, validation logs and protected recording
dependencies. Only newly generated duplicate traces are replaced with verified
pointers after their JVMs finish; reference files are not deleted.
