# OEL4C typed runtime, base/lifecycle coverage and coupled diagnostics

## Implemented scope

`DStabNetworkBuilder.addOel4c` now attaches the existing five-constant kernel to
an explicitly compatible summation-point exciter. The current bounded host is
AC1C; no exciter or machine is replaced. Core GENROU is reused. The existing
exclusive auxiliary-limiter port rejects duplicates and competing limiters;
disabled/takeover connections remain rejected. Standard record import is not
implemented by this slice, so the catalog's loadable limiter count remains five.

Reactive power is recomputed from this machine's network current boundary:

```
Qmachine = imag(V * conjugate(INorton - V * Ymachine)) * Snetwork / Smachine
Qref=0: effective pickup = owner static Qmax(network pu) * Snetwork / Smachine
```

It does not use bus-aggregate Q or the machine's cached event-averaged reactive
power. Missing default Qmax or invalid bases fail early initialization instead
of silently choosing a pickup. Explicit nonzero Qref does not require Qmax.
Network/owner bases of 100/50, 100/100 and 100/200 MVA are exercised on real core
machine fixtures. Initialization remains deferred until the owning exciter has
initialized; an initially active proportional-only limiter is included in the
exciter reference equilibrium.

The runtime contributes no load, admittance or current injection. Network
iterations refresh only algebraic observations. Opaque, owner-bound kernel
checkpoints restore accepted integral/timer history when a predictor or corrected
trial is disabled or its subsequent network solution is rejected. Acceptance
requires the matching corrected step and occurs once only. The arithmetic
preflight also checks the actual core corrector-difference expression, rejecting
overflow before mutation; the core integrator itself is unchanged.

## SMIB and regression coverage

Fifteen host/base/lifecycle tests and twenty kernel tests pass. Twenty-two typed
SMIB cases cover two steps (0.5/0.25 ms), seven standard profiles, and additional
50/200-MVA flat/fault variants. Profiles include stationary inactive and initially
active equilibria, delayed/zero-delay faults, integral-only, lower saturation and
default Qmax. The three-phase fault lasts from 0.05 to 0.10 seconds (three cycles
at 60 Hz); runs end at 0.5 seconds. All 33,050 exported rows retain their original
pre/post-event observations, semantic integral/timer values and physical outputs.
Zero-time fault processing is asserted not to advance integral or timer history.

Final gates: 331 focused, 435 core and 2,287 registered scoped SMIB/model tests,
zero failures/errors and seven existing skips, plus 261 offline checks. The final
source snapshot includes the separately committed hydro-limit correction already
present in the shared repository. Existing limiter exports, all portable
references and earlier evidence archives remain unchanged.

## Independent coupled comparison—not acceptance

The twelve matching 100-MVA cases are compared against the frozen whole
PowerWorld references, retaining every one of the 18,032 paired raw rows.
Seventeen common coordinates are scored independently at RMSE < 0.001, with
all/regular/pre/post groups, peaks and peak times. P/Q use the declared 100-MVA
base, speed is deviation from nominal, relative rotor angle is radians and timer
is seconds. There is no interpolation, clock adjustment, sample omission or
fitting. The comparator has eight portable safeguard tests, including exact
limiter-parameter, base, network and machine/exciter input matching.

The raw reference retains all 21 fields. Its duplicate exported PI-output field
is identified as an alias of the scored limiter output. Its integer operating
mode is explicitly not equated with the typed negative-error delay gate; the
undocumented fourth auxiliary field also remains unscored. Two rotor angles
are converted into one relative-angle coordinate. All unmatched coordinates
are enumerated in the report, not silently discarded or claimed equivalent.

Worst all-row RMSE across both steps and all profiles:

| Physical coordinate | Worst RMSE |
|---|---:|
| Bus 1 voltage | 1.43e-4 pu |
| Bus 2 voltage | 4.66e-5 pu |
| Generator speed deviation | 4.88e-7 pu |
| Reference speed deviation | 4.17e-8 pu |
| Relative rotor angle | 1.79e-5 rad |
| Generator P | 1.85e-4 pu |
| Generator Q | 9.78e-4 pu |
| Field voltage | 1.75e-4 pu |

Those physical channels pass the numerical criterion in every matched case.
Only the two flat cases pass every compared coordinate. All ten fault cases
still fail one or more exciter/limiter coordinates, including field current,
zero-time sensor/output values, lead-lag/rate-feedback and integral-only expiry.
Several peaks are at post-event rows; regular-row lead-lag and expiry differences
also remain. These failures are retained and prevent trajectory acceptance.

The [permanent measured-input report](oel4c-recorded-input-2026-10-20.md) and
[foundation source contract](oel4c-foundation-2026-10-20.md) remain valid historical
evidence. Detailed matched-case metrics and the new runtime traces are sealed on
the verification drive to avoid growing the nearly full system drive. No earlier
archive is deleted or relocated.

## Remaining TODO

- [x] Typed AC1C summation attachment and exclusive ownership.
- [x] Machine-base Q/Qmax measurement, early validation and initialization equilibrium.
- [x] Complete accepted-state rollback for disabled/rejected trials.
- [x] Twenty-two typed SMIB flat/fault/base cases with full event observations.
- [x] All-row independent coupled diagnostics on seventeen common coordinates.
- [ ] Resolve all ten fault-case channel failures without fitting or discarding samples.
- [ ] Add strict standard record import and preprocessing/ownership rejection tests.
- [ ] Establish complete required channel acceptance and broaden compatible hosts where proved.

No full Texas2k dynamics are run. Typed availability is not `LOADABLE` record
support or `VERIFIED` independent trajectory acceptance.
