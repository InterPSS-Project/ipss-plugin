# Bounded network-convergence repair

Four release checks stopped at the imported solver's fifty-iteration ceiling:
the endpoint-aligned monitor, the REECC1 reference trajectory, the Texas2k
2024 summer-peak fault, and the high-renewables generator trip. A diagnostic
control using the former iteration allowance passes the same assertions, with
a maximum of ninety network iterations. This control identifies slow algebraic
fixed-point convergence; it is not a proposed production setting.

The repair retains the fifty-iteration ceiling, original voltage tolerance and
norm, minimum iteration count, model parameters, integration scheme and existing
trajectory assertions. No supplied reference or model equation is changed.

## Numerical path

Core `1.4.5` supplies an explicitly enabled, bounded Anderson fallback. Ordinary
iteration runs first. After the first third of the configured budget (at least
eight iterations), the solver considers the most optimistic contraction ratio
from four recent corrections. Only when even that ratio predicts budget
exhaustion does it check source eligibility and start a depth-three history.
Fresh evaluations build the history before any proposal. Solves projected to
finish normally keep their ordinary voltage sequence and avoid the eligibility
inventory scan.

The fallback uses the existing small, reorthogonalized least-squares calculation.
Large coefficients can be necessary for a slowly contracting map. Instead of
discarding every such direction, the bounded mode scales the proposed correction
so its largest rectangular component is at most four times the current maximum
voltage correction. Nonfinite candidates are rejected. The previous experimental
coefficient-rejection policy and its explicit experimental selection remain
available for existing callers.

Convergence is still tested on a fresh ordinary network evaluation, before
mixing. A small extrapolated displacement cannot establish convergence. History
is reset for every network call, including event-side calls. Budget exhaustion
still fails the current stage before integration, output or clock advancement.
The immutable network-solve diagnostics and callback cleanup contract remain
in force. No alternate network matrix or factorization cache is introduced.

The numerical method follows the fixed-point acceleration formulation described
by [Walker and Ni](https://epubs.siam.org/doi/10.1137/10078356X). The late activation,
displacement bound and device qualification are safeguards qualified by this
repository's regressions, not a claim of convergence for arbitrary equations.

## Device contract

`DynamicDevice.supportsNetworkIterationAcceleration()` defaults to false.
An opt-in device must produce the same current for voltage A after an A/B/A
evaluation sequence at frozen integration states. Output evaluation and network
callbacks must not advance differential coordinates, control history, timers or
accumulated algebraic corrections. Derived electrical caches may be refreshed.
Wrappers must preserve this whole-device contract.

The imported solver enables the fallback only for known frozen machine sources
and explicitly qualified devices. REGCA1 qualifies with a positive regulator
lag and its iteration-dependent voltage-memory option disabled. REGFMA1 qualifies
because its current limit and current map refresh electrical outputs without
advancing integration coordinates; an excursion regression checks this directly.

Unknown devices, active dynamic loads, scripted devices, active branch devices,
custom current-table subclasses and sequence-current injections retain ordinary
iteration. A no-op wrapper that delegates the capability keeps the same numerical
path as its wrapped device. Stateful callbacks do not acquire eligibility merely
by implementing the network lifecycle interface.

## Regression evidence

- A real sparse equation with a known slowly contracting fixed point converges
  within fifty evaluations; its unaccelerated control exhausts that budget.
- An inconsistent equation still exhausts fifty evaluations. It cannot pass by
  producing a small mixed step.
- Fast sparse solves preserve their exact original voltage sequence.
- A normally converging solve that takes more than sixteen evaluations also
  remains bit-identical to ordinary iteration, without applying acceleration.
- Ineligible and nonfinite histories reset safely; the older experimental policy
  keeps its existing rejection behavior.
- Device guards cover iteration memory, algebraic controller bypass, unknown
  devices, inactive devices and custom current providers.
- All four original checks, callback transparency and converter reference checks
  retain their original assertions. An early-activation candidate passed those
  checks but changed three already-convergent wind references. Its full-reactor
  failure is retained; it is not the accepted policy. The wind controls finish
  within twelve ordinary evaluations. Budget prediction avoids changing that
  convergent path rather than relaxing their trajectory limits.

The final full-plugin results and evidence hashes are recorded in the companion
[checkpoint](checkpoints/network-convergence-repair-2026-10-24.json).

Core `1.4.4` has already been published. This source change therefore uses a new
core version, `1.4.5`; plugin `1.4.7` declares that matching dependency.

## Final validation

| Registered module | Tests | Failures | Errors | Existing skips |
| --- | ---: | ---: | ---: | ---: |
| `ipss.core.test` | 3106 | 0 | 0 | 0 |
| `ipss.plugin.3phase` | 468 | 0 | 0 | 28 |
| `ipss.plugin.core` | 445 | 0 | 0 | 6 |
| `ipss.test.plugin.core` | 3343 | 0 | 0 | 54 |

The final plugin run enables the existing extended-test profile. The 100 focused checks also pass without skips. No registered accuracy assertion is weakened and no new test is excluded. The production-code probe completes the original four acceptance checks with a maximum of 50 iterations; the ordinary diagnostic needs 90.
