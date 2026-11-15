# Type-J voltage-state realization

## Why the equation owner is separate

`Gentpj1Machine` owns four internal voltage states: E''q, E''d,
E'q and E'd. A winding-flux realization that reconstructs subtransient
voltage algebraically is not generally interchangeable. Differentiating that
reconstruction introduces transient-state derivative terms that are absent
from the Type-J equations. Agreement at equilibrium does not establish
agreement after a voltage disturbance.

The implementation therefore extends the existing `RoundRotorMachineImpl`
for network attachment, reference-frame transforms, parameter access and
mechanical integration stages. Its electrical equation adapter owns the four
voltage states. `GenqecMachine` retains its independent equations. The existing
builder shares identity/base/source-resistance wiring through `RoundRotorMachine`.
Type-J physical values come directly from `Gentpj1Data`; its legacy compatibility
`getGenqecData()` view must never supply parameters to the builder or validator.

## Algebraic boundary and saturation

Use machine-base terminal voltage and stator current in the rotor d/q frame.
With generator current positive out of the machine, define:

```
psi = hypot(Vq + Ra*Iq + Xl*Id, Vd + Ra*Id - Xl*Iq)
saturationInput = psi + Kis*hypot(Id, Iq)
sd = 1 + S(saturationInput)
sq = 1 + (Xq/Xd)*S(saturationInput)
XdppSat = Xl + (Xdpp-Xl)/sd
XqppSat = Xl + (Xqpp-Xl)/sq

Eppd - Vd = Ra*Id - XqppSat*Iq
Eppq - Vq = Ra*Iq + XdppSat*Id
```

The Type-J saturation curve is the scaled quadratic `B*(x-A)^2/x`, fitted
at the supplied 1.0 and 1.2 points. It differs from the unscaled quadratic.
The q-axis factor also differs from the d-axis factor. `Kis=0` removes the
current-dependent addition; it does not remove ordinary saturation.

Current and saturation are coupled. A safeguarded scalar Newton solve with a
bracketed fallback makes the returned current and saturation mutually
consistent. The fixed Norton admittance is a network preconditioner;
compensation current retains saturation and saliency. It does not replace the
physical stator impedance with a fixed impedance.

These Epp coordinates are voltages. Their stator relation has no extra shaft
speed multiplier. Air-gap electrical power equals terminal real power plus
`Ra*(Id^2+Iq^2)`. A flux-state convention that caches electrical torque must
not be substituted for this power convention.

## Four electrical equations

```
IfdXad = sd * ((Xd-Xdpp)*Epq - (Xd-Xdp)*Eppq)/(Xdp-Xdpp)

d(Eppq)/dt = (sd*(Epq-Eppq) - (Xdp-Xdpp)*Id)/Tdopp
d(Eppd)/dt = (sq*(Epd-Eppd) + (Xqp-Xqpp)*Iq)/Tqopp
d(Epq)/dt = (Efd-IfdXad)/Tdop
d(Epd)/dt = sq*((Xq-Xqp)*Eppd - (Xq-Xqpp)*Epd)
             /((Xqp-Xqpp)*Tqop)
```

For the supported salient branch `Tqop=0`, Epd is constrained to zero and
its derivative is removed. Zero subtransient time constants and collapsed
transient/subtransient winding denominators are rejected explicitly; they
require a separately derived algebraic reduction.

`calculateIfd(EXCITER)` returns the rotor-state field-current signal, not the
latest field-voltage command. The existing core conversion supplies the
machine-base field-current view. This distinction matters while exciter
rectifier loading and the rotor field are changing.
The field-current output retains its sign. Independent deep-underexcited
holdpoints, including negative field current, match the same rotor equation;
there is no extra zero-current floor. A synthetic held-boundary electrical
equilibrium regression protects this signed behavior without changing entered
machine parameters or adding a limiter.

## Mechanical and integration semantics

With air-gap electrical **power** as above:

```
d(speed)/dt = (Pm - Pe - PeNegativeSequence - dampingPower)/(2*H*speed)
dampingPower = Dcore*0.01*baseFrequency*(speed-1)
```

The actual-speed denominator follows the model power convention. Removing it
or applying speed only to one power term changes the disturbed mechanical
trajectory even if initialization is identical. `Dcore` is the configured
core coefficient; the builder converts the entered damping value into that
convention. The retained small speed excursions discriminate the net-power
speed division, but do not independently resolve damping placement at large
off-nominal speed; that remains a qualification boundary.

Modified Euler stores the accepted start state and first derivative at the
predictor, then reconstructs the corrector from that start. A zero-duration
network/event refresh recalculates algebraic outputs without overwriting an
outstanding predictor or integrating time again. Reinitialization invalidates
old algebraic caches before rotor-angle initialization. This avoids reusing
flux/current from the previous operating point.

The four native voltage coordinates and speed/angle are available as named
states. Previously published winding aliases remain algebraic observations;
they are not additional integrated states. Deferred-current publication is
not enabled for this equation owner.

## Qualification boundaries

Public synthetic regressions cover the equation boundary, saturation,
initialization, predictor/corrector history and same-time refresh. Retained
independent small-network evidence additionally checks field current,
leading-reactive operating points, electrical right-hand sides and the
power/speed convention. Source-specific profiles and exact simulator
provenance remain in private QA evidence.

Component boundary/RHS agreement, a coupled small-network trajectory gate,
and full-network event stability are separate acceptance claims. Passing one
does not imply the others. The support classification must not be promoted to
fully verified while a required coupled trajectory gate remains unresolved.
The supplied model parameters and system stability thresholds are not tuning
variables for this correction.


## Native parameter preservation and coupled stage origins

The native Type-J record and builder previously passed through the compatibility
compound data constructor, which applies another model's reactance corrections.
In the removed-q-transient branch it can cap Xq'' at one quarter of Xq and then
reduce Xl. Independent native import/initialization retains the entered Type-J
values. Their alteration changes saturation, stator currents and field voltage;
a separate voltage-state equation owner does not undo an earlier data change.

Type-J now validates finite, supported nonsingular source data directly and
preserves the entered values. Its builder shares only identity/base/resistance
attachment with the other models and wires its own native parameters. Validation
uses the native data, never the legacy normalized view. Nonstandard but
nonsingular q-axis ordering is reported without modifying it: independent
initialization accepts the retained boundary. Unsupported collapsed winding
denominators and zero subtransient-time reductions remain rejected. The
compatibility API remains available but is explicitly documented as a conversion,
not an authoritative Type-J parameter view. No new parameter container is added.

A second issue concerns coupled modified-Euler stages. The shared machine
lifecycle applies mechanics before electrical state integration. Electrical
current/flux derivatives must nevertheless use the voltage in the rotor frame
at that same stage's entry angle. Reading the advanced mechanical angle creates
a first-order sequential split. Type-J now captures only the two stage-entry
d/q voltage scalars at `nextStep()` entry. The electrical RHS uses them; network
current publication after the update still uses the actual new rotor angle.
The context is released even on failure, and direct electrical-only calls retain
their ordinary current boundary. No duplicate integrated state is introduced.

Synthetic regressions compare complete coupled steps with independently
initialized electrical-only predictors and correctors at the same stage origins.
Both stages agree to 1e-12 after the correction; the previous owner fails.
An analogous WT1G1 fix preserves its stage-entry shaft speed in the electrical
RHS, alongside the separately corrected current-voltage mechanical power input.
All 14 Type-J/related-machine checks and 20 focused induction checks pass with
unchanged reference limits. Private independent ten-second probes show large
reductions in field, angle and frequency error. Whole-network trajectories and
supplied-horizon acceptance remain separate gates.
