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
- Extend longer-duration, active rate/position limits, backlash/hysteresis, bypass and tuning-transition qualification under both methods; distinguish inactive allocated diagnostics from live physical signals.
- Preserve exact allocation/ICON decoding and the distinction between source-native and typed frequency/deadband conventions.
- Check parameter copying, partial gate curves and initialization residuals through the complete import path.
- Retain the [prepared-curve execution contract](dynamic-models/hyg3-prepared-curves.md) and complete pre/post state equivalence when extending the runtime.

## WSIEG1

- Support both integration methods with one coherent owner for shared turbine/shaft states and committed limiter histories.
- Independently qualify primary-shaft trips and subsequent secondary-shaft behavior.
- Exercise rate/position limits, deadband transitions and turbine bypass paths.
- Preserve the distinct extended governor equations, shared turbine ownership and machine-base scaling.

## CMLDBLU1

- Support both methods for the complete composite/motor state vector and network-injection lifecycle without advancing owned states twice.
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
