# Dynamic-model implementation work

Models are listed in implementation priority order. Each section describes the remaining work.

Every model must support both modified-Euler and staggered integration through
shared equations and state storage. Qualify initialization, states/derivatives,
limiter and event histories, and independent coupled SMIB trajectories for each
method. Preserve reusable numerical buffers and explicit integration/clock
policies; create scoped model commits and retain incomplete qualification tasks.

## PSS2B

- Retain direct shared-state staggered participation, the existing modified-Euler execution, sampled measurement histories and audited ownership/predictor guards.
- Extend the accepted coupled voltage/angle matrix to production-quarter-cycle and longer-duration runs.
- Finish strict independent controller-state and sampled-output lifecycle qualification across selectors and limiter combinations.
- Preserve separate mappings for the standard record and the existing typed extension.
- Resolve sampled-output lifecycle and finite-step trajectory differences against complete independent captures.
- Retain the [compiled runtime contract](dynamic-models/pss2b-compiled-runtime.md), accepted derivative history and zero-duration event checks when extending qualification.

## HYG3U1

- Retain the direct shared-state contract, reusable numerical storage and accepted backlash/hysteresis histories.
- Retain the independently qualified compensation coordinates, direct-speed feedback correction and both-method state/boundary gates through five seconds at quarter-cycle steps.
- Retain independent active valve/gate-limit and recorded-gate backlash/history checks under both methods, with separate input-jump and scheduled-network-event integration policies.
- Extend longer-duration, hysteresis, bypass and tuning-transition qualification; resolve strict range-normalized acceptance of the small-response active-limit runs while preserving the unmodified reference ranges.
- Preserve exact allocation/ICON decoding and independent source-native/typed frequency and backlash conventions.
- Check parameter copying, partial gate curves and initialization residuals through the complete import path.
- Retain the [prepared-curve execution contract](dynamic-models/hyg3-prepared-curves.md) and complete pre/post state equivalence when extending the runtime.

## WSIEG1

- Retain the [direct shared-state contract](dynamic-models/wsieg1-shared-runtime.md), reusable numerical storage, audited measurement/preparation eligibility and one coherent turbine owner under both integration methods.
- Retain the independent excited primary/secondary-trip matrix, active-owner state gates and held secondary output; preserve raw inactive allocated-state diagnostics separately.
- Extend active rate/position-limit and hysteresis qualification, independent bypass/time-constant correction variants, reconnection and longer-duration coverage.
- Verify initialization residuals and shared-shaft power-base edge cases across the full import path.
- Preserve the distinct extended governor equations, shared turbine ownership and machine-base scaling.

## CMLDBLU1

- Retain the [independently checked low-voltage current and heating law](dynamic-models/cmldblu1-compressor-current.md), fresh trial-voltage currents and reusable numerical storage under both methods.
- Retain the [strict standalone compressor import and coupled controls](dynamic-models/acmtblu1-shared-import.md), per-load ownership and explicit accepted-timer precision. Resolve frequency/continuous-state and active protection qualification, and the separate composite low-voltage static-admittance discrepancy.
- Retain the [shared motor runtime](dynamic-models/cmldblu1-shared-runtime.md), direct leaf-state participation under both methods, reusable rate/current buffers and post-expansion ownership checks.
- Retain the [compressor parameter and operating-point corrections](dynamic-models/cmldblu1-compressor-initialization.md), independently checked legacy characteristic and physical reactive compensation; finish zone internal-coordinate and thermal qualification.
- Retain independently checked legacy tap/grid, reactive allocation and physical current-balance initialization under both methods. Resolve feeder-floor preparation, zero-step behavior and remaining motor-coordinate discrepancies; the complete model remains unqualified.
- Retain the [independently checked compressor stall/restart histories](dynamic-models/cmldblu1-compressor-lifecycle.md) under both methods. Resolve remaining coupled input preparation, allocated angle observations, bypasses, active protection/reclosure and warm restoration without advancing owned states twice.
- Compare every internal state and published diagnostic against independent captures.
- Investigate the remaining composite-load trajectory discrepancy and tighten the accuracy gates after it is resolved.
- Preserve bus/load-specific ownership, explicit MVA-base handling and the legacy electronic-load recovery convention.

## TIOCR1

- Qualify protection updates under both methods while keeping relay timers and physical actions out of trial derivative evaluations.
- Extend independent checks to protection coordination, reset/re-pickup and transfer-trip combinations.
- Verify both monitored branch ends, remote load actions and breaker timing.
- Preserve shed admittance across network rebuilds and refresh voltage-dependent current corrections during network iterations.

## SVSMO3T2

- Retain the [direct current-controller contract](dynamic-models/svsmo3t2-current-runtime.md), exact named FACTS import and accepted base SMIB fault gates.
- Retain prepared MSS switching, exact history replay, physical-stamp lifecycle and the coupled staggered/endpoint-clock matrix; finish repeated-operation modified-Euler qualification and extend production-step/duration coverage.
- Retain accepted blocking/output-history, internal current-feedback, event projection and complete definite/thermal rating-envelope gates under both methods; extend rating coverage to production steps and finish cached-diagnostic lifecycle qualification.
- Retain the independent trip-command/network-removal matrix under both methods, reference-defined active-state gates and read-only committed histories; qualify external switching, coupled reconnection and severe trip trajectories.
- Finish independent coupled/state qualification of slow reset, nonlinear droop, rating limits, blocking/trips and bypass combinations; the extended matrix remains diagnostic.
- Resolve finite-stage differences without fitting parameters; extend validation, lifecycle and production-step/long-duration tests before promoting the partial catalog status.

## GEWGD1

- Establish the auxiliary role, exact schema and equations from authoritative vendor evidence.
- Bind the auxiliary to its converter host before selecting reusable numerical blocks.
- Add import, initialization, named-state and independent trajectory checks.

## GEWPLT2

- Add USRMSC identity/ownership and plant-to-unit binding.
- Implement the exact plant-controller equations, limits and lifecycle after its dependent converter interfaces are available.
- Add import, initialization, named-state and independent trajectory checks.

## GEWTA2

- Establish the exact aerodynamic-controller schema and equations.
- Implement mechanical-input bindings, initialization and limits with the converter/drive-train host.
- Add import, named-state, event and independent trajectory checks.

## GEWTE2

- Implement the exact electrical-controller variant and its converter attachment.
- Verify equations, limits, initialization and state/history lifecycle before reusing another variant's engine.
- Add independent trajectory checks; do not alias to GEWTECU1 without evidence of equivalence.

## GEWTG2

- Obtain the authoritative legacy vendor schema/equations and implement the converter host.
- Define electrical, mechanical and plant-controller interfaces before attaching dependent models.
- Qualify initialization, named states, current limits, events and coupled trajectories against an independent implementation.

## GEWTP2

- Establish and implement the exact pitch-controller schema and equations.
- Verify actuator limits, initialization and state/history lifecycle with its converter host.
- Add import, named-state, event and independent trajectory checks.

## GEWTT1

- Implement the exact mechanical/drive-train variant and its converter bindings.
- Verify initialization, torque/speed conventions, named states and integration lifecycle.
- Add import, event and independent trajectory checks.

## CDC6T

- Extend the record reader for named DC-link ownership and preserve quoted device identifiers.
- Implement the dynamic HVDC controller and network coupling; static DC power-flow support does not establish dynamic support.
- Add exact import, initialization, named-state, limiter/event and independent trajectory checks.

## CHVDC2U1

- Add USRDCL named-link decoding and the exact allocation/schema.
- Implement dynamic HVDC controls and verify equations before sharing an engine with another variant.
- Add exact import, initialization, named-state, limiter/event and independent trajectory checks.

## URGS3T

- Establish and implement the extended gas-turbine governor, limits and characteristics.
- Preserve its exact source schema and state/history lifecycle; the simple GAST model is not a substitute.
- Add exact import, initialization, named-state, limiter/event and independent trajectory checks.
