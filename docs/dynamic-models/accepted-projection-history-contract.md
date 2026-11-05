# Accepted-state derivative-history invalidation

The staggered participant contract now permits an explicitly accepted model
projection to invalidate selected prior derivative coordinates. The solver
continues to own and clear that history. Models neither implement their own
two-step recurrence nor overwrite numerical history buffers.

`hasAcceptedDerivativeHistoryInvalidation()` defaults to false. When it returns
true after a successful positive-duration `completeStaggeredStep`, the solver
queries `isAcceptedDerivativeHistoryInvalidated(index)` and clears only selected
coordinates. Both queries must be pure. Unaffected slopes, other participants,
input-discontinuity averaging and modified-Euler integration are unchanged.
The default false path does not scan coordinates or allocate storage.

Zero-duration callbacks do not clear history. If the completion callback throws,
solver history is not partially cleared. This is not a transaction covering
model mutations inside a failing callback or a later failed network solve; those
rollback responsibilities remain separate and are not claimed here.

This generic change opts in no production model. Each model must independently
justify every selected coordinate and its accepted boundary. In particular,
resetting one state does not by itself establish which downstream derivative
histories are invalidated. Qualification must retain complete reference gates,
including cases where a correct projection changes later threshold crossings.

The generic contract and existing staggered solver/exciter/voltage-coordinate selection pass 49 tests with no failures, errors or skips. Three focused tests exercise the actual solver completion helper: legacy preservation, selective invalidation with zero-duration preservation, and rejected completion without partial history clearing.

Private evidence: verification-artifacts/accepted-projection-contract-20261104/validation.zip.
SHA-256: 159d37b1f13a8b4b1f957ddbbf89a748fa34112ea56f1baa823da5e195a51647.
