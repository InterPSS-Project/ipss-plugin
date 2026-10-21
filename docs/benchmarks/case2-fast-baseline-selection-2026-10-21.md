# Case 2 fast baseline selection

The working performance index now selects the retained electrical-endpoint
executable. The latest seven complete-case measurements have a median of
7.592151 seconds and a range of 7.549092 to 7.666781 seconds after 32 warmups.
Fresh setup, initialization and full recording are included, on one logical
processor, for ten simulated seconds at a quarter-cycle step and `1e-5`
network voltage-correction tolerance.

All 2,405 samples and 4,810 channels, including event-side duplicates, have the
same full-recording SHA-256 as the retained production baseline:
`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`.
This preserves the observed Case 2 trajectory. Accuracy against the supplied
independent reference remains unresolved; equivalence is not accuracy approval.

## Saved work

Both repositories are on `perf/case2-frozen-endpoint-20261021`. Each has a
`backup/pre-fast-baseline-20261021` branch pointing to its previous HEAD.

| Repository | Previous HEAD | Stash |
| --- | --- | --- |
| Plugin | `74985520add5240245f79e77e40f2fbd527cdd79` | `4a75e3bb638d0f5ef6fe39dcd0456ee993fcb3fa` |
| Core | `7a9808da4561612b5236d6a03abfe7c6238fb707` | `f10094c5ac3ce4d71a186ca5715c1b34bf226961` |

The plugin stash includes tracked edits and untracked files. The core stash
contains no content difference: its reported machine edit matched HEAD after
Git normalization. Existing stashes and ignored private evidence remain intact.
The crossover evidence index was restored from the plugin stash for this report;
the original stash remains available.

To recover the saved work, first preserve any subsequent local edits, switch
each repository to its backup branch, then apply its stash using the full hash
above. Use `git stash apply`, retaining the stash until recovery is checked.

## Execution and source status

```powershell
python tools/run_bounded.py --log target/selected-fast-replay.log -- python tools/replay_case2_fast_checkpoint.py --selected --output target/selected-fast-replay
```

Choose new output/log names on subsequent runs. The runner verifies archive and
dependency hashes and requires 3 GiB free before staging. Its timed runs include
full recording. It never substitutes classes from mutable build directories.

**The production source tree has not been rolled back.** The fast executable is
assembled from retained classes and overlays, not one historical Git checkout.
Checking out the evidence commit `e2e0a892f` would not reconstruct it. This change
selects an executable baseline and preserves the source trees; it does not claim
that a Maven build of this branch produces that executable. A whole-source
reconstruction, clean build, regression gates and trajectory comparison are
still needed before a source rollback can be declared complete.

See the [selected index](checkpoints/case2-working-performance-baseline.json),
[recovery record](checkpoints/case2-pre-fast-selection-2026-10-21.json),
[endpoint evidence](checkpoints/case2-frozen-endpoint-2026-10-21.json), and
[latest crossover measurements](case2-controller-stack-crossover-2026-10-21.md).
