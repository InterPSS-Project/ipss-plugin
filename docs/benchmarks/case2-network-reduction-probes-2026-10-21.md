# Case 2 network traversal and convergence probes

Neither isolated change demonstrated a substantial full-case speed improvement.
Production source remains unchanged, and the eight-second production target
remains unachieved. These experiments follow the
[execution-path profiles](case2-execution-path-profile-2026-10-21.md).

## Complete-case measurements

| Isolated change | Control median | Candidate median | Candidate minus control |
| --- | ---: | ---: | ---: |
| Skip iterators for known empty device lists | 9.418165 s | 9.476394 s | +0.058229 s |
| Bound insignificant complex-voltage corrections | 9.483132 s | 9.471200 s | -0.011931 s |

Each experiment runs in its own JVM with 32 alternating warmups followed by
seven control/candidate pairs. Each pair runs the control first. The same
class overlay is used for both arms, with only its feature switch changed.
The order is deterministic, not randomized. Timing ranges overlap in both
experiments; the small magnitude-bound median reduction is not an established
speed benefit. Two of seven traversal candidates and three of seven
magnitude-bound candidates beat their adjacent controls.

Every case uses one logical processor, verified affinity mask 1, ten simulated
seconds, a `1e-5` complex-voltage correction tolerance, and a `1/240` second
step. Fresh preparation, initialization and full recording are included.
Compilation and garbage collection during measured cases remain in the timings;
no measurements are removed or adjusted. Builds and other profiling campaigns
were kept out of these timing windows.

Measured seconds, in order:

- Traversal control: 9.3792464, 9.4433389, 9.4462055, 9.7436708, 9.3844776, 9.4005173, 9.4181654.
- Traversal candidate: 9.4214767, 9.4552599, 9.3174403, 9.5544178, 9.4763942, 9.9108880, 9.8063443.
- Magnitude control: 9.3118139, 9.4148572, 9.3474952, 9.5130042, 9.4831316, 9.5686342, 9.5739394.
- Magnitude candidate: 9.2830717, 9.5258147, 9.4712002, 9.5486120, 9.2973047, 9.6095004, 9.3848199.

## What was changed and checked

The traversal prototype wraps 14 solver loops. It recognizes the exact built-in
generator, bus-device and branch-device list classes. For an empty recognized
list it uses the shared empty iterator; all other lists retain their ordinary
iterator. Membership is read anew on each traversal. There is no cached device
inventory. The 16 passing focused tests cover custom-list observability, live
membership, nonempty ordering and mutation failures, callback lifecycle and
network convergence.

The magnitude prototype changes only the existing rectangular convergence scan.
For finite differences, `2 * max(abs(dx), abs(dy))` bounds the existing complex
magnitude calculation. When this finite bound cannot change either the current
maximum or the convergence decision, it skips the magnitude arithmetic. It
otherwise retains the original division and square-root sequence. It still
reads and validates every active voltage and updates every history entry.
The 16 passing focused tests include 12,000 randomized differential scans,
exact tolerance boundaries, first-leader ties, extreme values, invalid/inactive
buses, and both publication modes. They compare exact diagnostics and stored
history, not just convergence success.

Across the two experiments, all 92 full cases preserve the binary and selected
channel recordings. The binary SHA-256 is
`123b1cce4ad4d4087e7dc294b47031bb1bfc52436dd086d588e531d5936f675f`,
covering 2,405 samples and 4,810 channels with event-side duplicates. Verified
identical generated files share storage while retaining their original paths.
Supplied references are unchanged. These checks establish nonregression against
the retained trajectory; independent-reference accuracy remains unresolved.

## Consequence for the next change

The comparison of frozen bytecode and its recovered ST4 source confirms a larger
mechanism to investigate: the frozen model caches the complete rectifier bridge
result within an evaluation scope. Production's numeric input cache avoids some
phasor and compound-voltage arithmetic, but repeated output reads still evaluate
the field-current-dependent bridge result. The frozen scope cannot simply be
copied: custom getters, graph changes, live machine inputs and failure cleanup
need explicit handling. A scoped experiment should isolate that mechanism while
retaining the current controller lifecycle and equations.

The [evidence index](checkpoints/case2-network-reduction-probes-2026-10-21.json)
identifies a verified 259-member private archive with sources, compiled overlays,
tests, commands, statuses, timing data and recording hashes. Neither prototype
changes the selected working baseline or production source.
