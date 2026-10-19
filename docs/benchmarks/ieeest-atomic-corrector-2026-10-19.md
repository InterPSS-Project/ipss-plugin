# IEEEST second-order lag: atomic modified-Euler corrector

## Root cause and fix

The model-specific `SecondOrderLagBlock` implements
`K/(1+a*s+b*s^2)`. For its second-order branch:

```
y' = r
r' = (K*u-y-a*r)/b
predicted = initial + h*f(initial,u0)
corrected = initial + h/2*(f(initial,u0)+f(predicted,u1))
```

Both endpoint slopes must be evaluated from the same predicted vector. The
old corrector changed `y` before evaluating the rate slope, so that slope read
corrected output together with predicted rate. The new code computes both
slopes first, then updates both states. No transfer-function coefficient,
limiter, shared CML integrator, parser, initialization convention or named-state
key changes.

The old extra rate correction is
`-h/(2*b)*(corrected_y-predicted_y)`. In the deterministic discriminator
`K=2, a=0.4, b=0.2, h=0.01, y0=0.3, r0=0, u0=0.4, u1=-0.2`, the correct
rate is `-0.00525`; the old block returned `-0.005253125`.
This is an equation-stage error, not a reference-simulator parameter fit.

The extra term is third order locally, so the old implementation can still
show second-order convergence. Convergence alone is not a sufficient test of
the simultaneous-vector stage equation.

## Permanent regression tests

Seven unit tests cover the changing-input predictor/corrector equation, an
independent multi-step two-state recurrence at two timesteps and three damping
values, bit-identical first-order limits including the existing epsilon boundary,
the algebraic limit, zero-duration calls followed by a real step, stationary
equilibrium and pure observers, and an undamped oscillator against its analytic
solution and a Taylor-remainder method bound. The two vector-stage tests fail
before the fix; the other five already pass. Both vector tests pass afterward.

The first-order and algebraic paths remain unchanged; there is no substitution
of an implicit corrector or a different integration method.

## Independent comparisons and remaining acceptance gap

All existing input-selector, power-input, zero-numerator and derivative fixtures
are reused without changing their raw histories, parameters, event rows, state
coordinates or acceptance bounds. Forty declared profiles across four fixture
families are replayed by the focused tests. The 139 lossless fixture parts remain
unchanged. No new reference inputs or regenerated reference CSVs are introduced.

The old private evidence seals remain immutable and retain their original cold
repeats. The new seal links their complete hashed inventories and records the
current tested sources, dependencies, failed-before reports, final reports and
all-row before/after derivative-state diagnostics.

The fix does **not** resolve derivative-mode clipped fault-output parity.
The current local Vstab maxima are 0.0354476/0.0765543 pu at coarse/fine steps;
remote maxima are 0.1000001/0.0320415 pu. These remain diagnostics, not accepted
under wider output bounds. The zero-numerator terminal-state playback error
changes from 1.25635e-5/3.14902e-6 to 1.22257e-5/3.06543e-6 pu, inside its
unchanged bounds. Native input memory is unchanged by this corrector fix.

## TODO

Final gates pass 137 focused, 427 core and 1,825 scoped SMIB/model/solver tests,
with zero failures/errors and seven existing skips. All 59 offline checks pass.
Source reconciliation covers 1,874 Java/POM files and 139 unchanged fixture
parts. Full inventory validation covers 5,688 files in four linked prior seals.

- [x] Failing-before simultaneous-vector regression.
- [x] Atomic lag corrector, unchanged first-order/algebraic behavior and analytic
  timestep-convergence checks.
- [x] Existing focused SMIB reference bounds pass without alteration.
- [x] Complete final core and scoped SMIB/model/solver regression gates.
- [ ] Resolve derivative-mode fault-output numerical staging and full coupled
  trajectory acceptance without fitting or widening bounds.
- [ ] Broader denominator/zero branches, machine bases and lifecycle coverage.

No full Texas2k dynamics are run. This fixes one concrete integration-stage bug;
it does not declare the entire IEEEST model VERIFIED.
