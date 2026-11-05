# Two-step electrical-controller boundary

`TwoStepElectricalController` makes converter/controller coupling explicit:
algebraic refresh, positive-step advance, accepted command publication, and
history rebasing are separate operations. The existing REECA and fixed-command
implementations implement this contract directly. Their equations and numerical
storage remain unchanged; the converter dispatches through the capability rather
than choosing between concrete controller classes. The staggered solver uses the
same capability for eligibility.

Refresh must not consume continuous integration history or timers. Commands
published by an accepted advance must survive later algebraic refreshes; this
preserves the REECA old-boundary/current-limit sequence. Reinitialization clears
history. Public advances reject negative/nonfinite durations before mutation;
zero duration retains the established algebraic-only behavior. No new arrays,
adapters, snapshots or per-step allocations are added by the dispatch layer.

A diagnostic compared the original dispatch against this boundary over 500 steps
for each existing controller type, including voltage application/restoration and
history rebasing. All named states and published commands match exactly at every
sample. Raw text differs only because immutable-map iteration order varies across
JVMs; sorting field names within each map, while preserving all numeric strings
and every sample, gives identical canonical traces. Raw files and the diagnostic
source are retained privately under
`verification-artifacts/two-step-electrical-controller-contract-20261104`.

Permanent tests cover existing atomic startup equations, held derivative histories,
zero-duration refresh, reinitialization, fixed-command plant history, invalid
step/stage rejection without state mutation, and independent controller boundary
checks. This capability does not itself add REECC numerical integration support;
that controller still requires an exact state/derivative implementation and
independent trajectory qualification before opting in.
