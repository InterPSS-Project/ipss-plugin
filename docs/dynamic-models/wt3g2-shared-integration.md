# WT3G2 shared converter integration

The bare WT3G2 converter exposes its five existing numerical coordinates through
`StaggeredEulerParticipant`: active-current lag, internal-voltage lag, PLL
integral, PLL angle and LVPL voltage sensor. Modified Euler and the shared solver
evaluate the same derivative implementation. The converter's Norton source and
physical equivalent reactance remain unchanged.

Evaluation does not advance numerical state or controller history. Trial state
publication validates every coordinate before changing the model, then applies
the existing LVPL and PLL projections. An enclosing solver can restore a saved
admissible state without hidden history changes. Ordinary predictor/corrector
ownership rejects orphan correctors, repeated predictors and mismatched intervals;
reinitialization clears pending ordinary integration history.

The staggered solver explicitly binds bare WT3G2 devices and checks attachment
identity. WT3E1 and WT3T1 attachments remain rejected in this slice: accepting a
converter while silently leaving its dependent controls unintegrated would be
incorrect. Those controls require their own shared participants, dependency
ordering and coupled qualification before this restriction can be removed.

## Validation boundary

`Wt3g2SharedStateTest` checks identical ordinary/shared modified-Euler stages,
pure derivative observations, trial restoration, atomic rejection of nonfinite
states, limiter projection, initialization and stage ownership. It also runs a
bare-converter SMIB fault under both solvers. The final five-state method
difference decreases from approximately 3.79e-6 to 9.03e-7 when the interval is
halved from 0.5 to 0.25 ms. This is an integration consistency check, not an
independent reference acceptance claim.

The existing converter import/limit tests and independent modified-Euler
trajectory gate remain passing. That older trajectory gate excludes event-adjacent
samples; it must not be represented as full event-boundary qualification.
Independent staggered trajectories, complete event-side samples, the attached
electrical/mechanical/pitch stack and production-step qualification remain open.
