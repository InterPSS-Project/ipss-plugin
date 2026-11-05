# Seven-state battery-controller two-step integration

REECC1 now implements the explicit electrical-controller coupling capability.
The optional staggered solver can integrate its seven published coordinates
through the existing two-step history kernel. The default solver retains the
existing controller path. Network compensation and deferred-current eligibility
are unchanged.

Rates use one common old boundary. Measurement filters and unrestricted battery
energy continue through voltage dips; the specified control states freeze.
Directional PI saturation holds the blocked integral and clears its retained
rate, while opposite-direction recovery remains enabled. Current priority,
voltage/current tables, SOC output limits and parameter preparation reuse the
existing model equations. Prepared coefficients are not recomputed from a local
integration interval. Commands are published before upstream state advancement
and survive subsequent algebraic refreshes. Zero-duration refresh does not consume
derivative history; reinitialization resets it. Named state/rate access exposes
all seven coordinates without allocating maps during stepping. Immutable current-
limit curves and their active point counts are prepared once; repeated refreshes
reuse these arrays rather than constructing them on every evaluation. The default path
honestly reports no stored derivative evaluation rather than inventing zeros.

Both methods pass the 15 unchanged coupled-fault channel ceilings. The fixture
explicitly enables qualified bounded network acceleration, retaining cap 50.
Matched comparison uses 1e-4 pu network tolerance; the staggered constructor's
unmodified 1e-6 default is separately tested.

| Quantity (maximum absolute error) | Default solver, 1e-4 | Staggered, 1e-4 | Staggered default, 1e-6 |
| --- | ---: | ---: | ---: |
| Voltage measurement | 8.26178e-4 | 5.52828e-4 | 7.73349e-4 |
| Active-power measurement | 1.00570e-4 | 8.08858e-5 | 1.68646e-4 |
| Reactive PI | 1.31422e-5 | 1.31813e-5 | 1.31525e-5 |
| Voltage PI | 2.96540e-4 | 2.32448e-4 | 2.60185e-4 |
| Reactive lag | 0 | 0 | 0 |
| Power order | 8.91626e-6 | 8.91626e-6 | 8.91626e-6 |
| Energy state | 1.30749e-8 | 8.87383e-9 | 9.17344e-9 |
| Bus 1 voltage | 7.61174e-4 | 6.39780e-4 | 8.64782e-4 |
| Bus 2 voltage | 7.36940e-4 | 6.18102e-4 | 8.36142e-4 |
| Bus 3 voltage | 9.02800e-6 | 7.48265e-6 | 1.01267e-5 |
| Active power | 1.25142e-4 | 1.00739e-4 | 2.78985e-4 |
| Reactive power | 4.12432e-5 | 3.65187e-5 | 4.60616e-5 |
| Active-current command | 2.39238e-4 | 1.22205e-4 | 2.18712e-4 |
| Reactive-current command | 1.66531e-3 | 7.03299e-4 | 1.38146e-3 |
| SOC output | 5.31023e-8 | 4.87386e-8 | 4.87386e-8 |

Paired charge/discharge input replays independently exercise active SOC bounds.
All rows are compared. The first repeated negative-time initialization output is
held, matching the recorded startup protocol; the first advancing interval ends
at zero. With this explicit protocol, maximum default-path energy/SOC errors are
1.501e-4; two-step energy errors are at most 1.626e-7 and SOC errors at most
6.518e-8. Both methods match the command-blocking sample exactly; active-current
command errors remain below 6.903e-9 across every sample (gate 1e-8). New two-step gates are 2e-7 and 8e-8 respectively; the existing default
3.5e-4 gates are unchanged. Both methods match the paired direct-voltage flat
control within 6.903e-9 (gate 1e-8).

Analytic tests cover old-boundary derivatives, inactive selectors, Q/P current
priority, reactive lag, directional voltage-PI saturation/recovery, voltage-dip
freezing with retained history, zero-duration refresh, one-time event rebase,
invalid duration rejection and reinitialization. Existing REECA/fixed-command
lifecycle tests continue to pass.

Qualification remains scoped: the existing coupled-fault comparator retains its
previous fault-edge and negative-time startup exclusions; the new energy and direct-voltage replays retain
all startup/duplicate samples. Remote sensing, attached upstream controllers,
converter bypass, all PI-boundary profiles, and broader system cases still need
independent trajectory qualification. This is not a universal state/limiter/event
contract or a declaration that every REECC configuration is verified.

Private validation logs, source hashes and links to immutable captures are at
`verification-artifacts/reecc1-two-step-integration-20261104`.

Validation: 32 focused tests, zero failures/errors/skips, including the shared
variable-step history test. No production default or tolerance was changed.
