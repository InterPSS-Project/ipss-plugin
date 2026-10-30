# Dynamic-model implementation work

Models are listed in implementation priority order. Each section describes the remaining work.

## PSS2B

- Extend independent checks across the supported input selectors, remote measurements and limiter combinations.
- Preserve separate mappings for the standard record and the existing typed extension.
- Verify derivative-input history and zero-duration event refresh without advancing committed state.

## HYG3U1

- Extend trajectory checks to internal states, gate/valve limits and both controller modes.
- Preserve exact allocation/ICON decoding and the distinction between source-native and typed frequency/deadband conventions.
- Check parameter copying, partial gate curves and initialization residuals through the complete import path.

## WSIEG1

- Independently qualify primary-shaft trips and subsequent secondary-shaft behavior.
- Exercise rate/position limits, deadband transitions and turbine bypass paths.
- Preserve the distinct extended governor equations, shared turbine ownership and machine-base scaling.

## CMLDBLU1

- Compare every internal state and published diagnostic against independent captures.
- Investigate the remaining composite-load trajectory discrepancy and tighten the accuracy gates after it is resolved.
- Preserve bus/load-specific ownership, explicit MVA-base handling and the legacy electronic-load recovery convention.

## TIOCR1

- Extend independent checks to protection coordination, reset/re-pickup and transfer-trip combinations.
- Verify both monitored branch ends, remote load actions and breaker timing.
- Preserve shed admittance across network rebuilds and refresh voltage-dependent current corrections during network iterations.

## SVSMO3T2

- Add named FACTS-device ownership and the exact dynamic SVC/controller realization.
- Establish the authoritative record schema, initialization equations, controls and limits; existing SVSMO1T2 support does not establish equivalence.
- Add import, named-state, limiter/event and independent trajectory checks.

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
