# Case 3 network residual and cold-limit diagnostics

The local current imbalance is caused primarily by stopping the network
iteration before the voltage-dependent injections have fully converged.
Output recording and the final controller refresh do not explain it. This
finding concerns the local network equation, not the remaining discrepancy
against the supplied dynamic trajectories. No production equation, input,
initial state, or acceptance limit was changed.

## Read-only equation checkpoints

A fresh Case 3 diagnostic records the last network right-hand side, solved
voltage, and physical aggregate generator power at each of 42 network
solutions through 0.02 seconds. All 43 output rows and all three startup
snapshots reproduce the earlier run exactly. The 6,578 physical channels
include both cold history rows. This is a short diagnostic, not an acceptance
or runtime benchmark.

An independent reconstruction splits the current residual into coefficient
rounding, linear-solve/right-hand-side rounding, the change in injection at
the final solved voltage, and the subsequent output refresh. The sum closes
to within 1.19e-12 pu at every recorded solution.

| Contribution at 0.02 s | Maximum current magnitude (pu) | Bus |
| --- | ---: | ---: |
| Total physical current residual | 0.00222845 | 2029 |
| Injection change at the final solved voltage | 0.00223275 | 2029 |
| Linear solve and right-hand-side rounding | 0.00051686 | 5401 |
| Matrix coefficient rounding | 0.00014173 | 5295 |

These are complex-current magnitudes, not voltage errors or NRMAE scores.
The maxima occur at different buses and cannot be added as scalars.
After the solve, refreshing controllers and recording changes aggregate
terminal power by at most 3.67e-13 MVA over the whole observed prefix.

The older 1e-6 trajectory has a maximum physical current residual of
0.00058258 pu at 0.02 seconds. Its converter voltage-input path predates the
current correction, so that number is not a paired tolerance-only comparison
against the new checkpoints. The earlier
[paired Case 3 controls](case3-prefault-localization-2026-10-19.md), using their
own identical runtime, show that tightening convergence alone does not
resolve the supplied-reference trajectory mismatch. This audit provides no
basis to change convergence tolerance to claim reference acceptance.

## Cold exciter limit inventory

The stored cold points of 1,108 exciter instances were checked against their
entered limits: 3,622 checks, zero values outside the checked ranges with a
1e-8 comparison allowance. The inventory covers the four exciter model
families in the six case snapshots. It distinguishes integral states from
block outputs and includes voltage-dependent field limits where applicable.

This inventory does not prove the unrecorded reference states or later
limiter behavior. Case 6 uses the existing diagnostic schema/preparation
control and is not acceptance evidence for the original Case 6 input.

## Evidence and remaining work

Programs, exact diagnostic sources and runtime hashes, binary equation
checkpoints, numerical decompositions, source-model removal inventory, and
cold-limit records are retained in the private evidence set
`2026-10-19-case3-network-residual-diagnostic`.

The supplied model-removal commands explicitly target the two unmodeled
generators. The user subsequently confirmed that both were netted before
load conversion in the supplied runs. The separate
[unmodeled-generation diagnostic](case3-unmodeled-generation-diagnostic-2026-10-19.md)
records that confirmation and retains their current-P/impedance-Q profile.
The frozen-source comparison does not establish a cause for the early voltage
discrepancy. The six-case, 36-event goal remains open.
