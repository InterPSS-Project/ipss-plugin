# Aclf Investigation Observations

## Texas2kBusAclfInvestigation

- Case loaded from PSSE v36 Texas 2k data, then compared between imported PSSE snapshot (`netPsse`) and InterPSS re-solved result (`net`).
- Loadflow is run with NR + non-divergent mode, `tolerance=1e-4`, `maxIterations=50`.
- The investigation highlights an inconsistency at `Bus7400`:
	  - Reported `genQ = -16.74503` is far below the minimum reactive limit (commented as around `-0.0799` for units shown).
	  - Despite reactive-limit pressure, voltage setpoint is still `1.0 pu`, suggesting an unrealistic or invalid operating point in the original PSSE result.
- Branch-focused debug check is done on `Bus7366->Bus7400(1)` to compare InterPSS vs original PSSE-state behavior.
- Main conclusion in code comments: PSSE-side result at this bus appears physically inconsistent with generator Q limits.

## EInterconnectAclfInvestigation

- Case loaded from PSSE v33 Eastern Interconnect data and inspected for control topology before solving:
- Before InterPSS solve, the case is flagged as non-converged with large mismatches (example comments mention high mismatch around `Bus3522` and `Bus3571`).
- Loadflow is then run with NR + non-divergent mode, `tolerance=1e-4`, `maxIterations=50` (controls left enabled in current code).
- QA comparisons are performed vs the PSSE snapshot for:
	  * bus voltage differences
	  * generator P output differences
	  * branch flow differences
- Additional commented analysis notes indicate:
	  - Some PV-bus voltage regulation behavior differs from PSSE snapshot.
	  - InterPSS tends to enforce voltage setpoint when reactive margin exists.
	  - This enforcement is a likely source of branch-flow deltas.

## Cross-case takeaways

- Both investigations suggest the imported PSSE snapshots may represent partially inconsistent or non-converged states.
- InterPSS re-solving produces a more constraint-consistent operating point (especially around voltage/Q-limit interactions), which can legitimately diverge from snapshot branch flows and bus quantities.
- The QA diff utilities are being used correctly as diagnostic tools to separate data-quality/snapshot issues from solver behavior.
