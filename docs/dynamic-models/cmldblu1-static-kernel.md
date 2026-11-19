# Composite static-load equations and linear specialization

Status: the shared static-load component has independent moderate-fault
qualification for constant impedance, constant power, and mixed P/Q
polynomials under modified Euler and staggered integration. Its exact linear
specialization reduces allocation while retaining fresh power observations.
This does not complete the composite model's severe-fault, motor, restoration,
electronic-history or long-duration qualification. Catalog status remains
`LOADABLE`.

## Verified legacy polynomial convention

For the independently captured legacy interface, let `r = |V| / |V_initial|`.
Its active characteristic is

`P = P_initial * (P1c*r^P1e + P2c*r^P2e + 1-P1c-P2c) * (1+Pfrq*df)`.

The reactive characteristic uses the corresponding Q coefficients and
`Q_initial`. Constants represent the supplied record without fitting them to
a trajectory. The retained controls exercise elevated and nominal initial
voltages and equal or different P/Q polynomial shapes. This distinguishes the
legacy convention from normalizing an absolute-voltage polynomial at the
initial operating point. In an elevated-voltage mixed control, the latter
changes active power by approximately `0.001576 pu`; the observed legacy
ratio characteristic differs from the capture by less than `6e-7 pu`.

Legacy static reactive initialization uses the magnitude of `PFs`, preserving
the original signed input. The [power-factor and low-voltage controls](cmldblu1-static-low-voltage.md)
keep that dialect policy separate from the typed/zone signed-Q convention.

The [generic WECC composite-load specification](https://www.wecc.org/sites/default/files/documents/meeting/2024/WECC%20Comp%20Load%20Model%20Specification_final.pdf)
expresses an absolute-voltage normalization. Exact legacy captures establish
the convention implemented here. That distinction must be retained when
qualifying another model dialect. This slice preserves the existing ratio
equations and does not redefine the zone-scoped model's preparation.

## Exact constant-admittance specialization

`Cmldznu2AlgebraicLoadModel` prepares eligibility once from its immutable model
data. Each active P/Q term must have exponent two. Coefficients must form an
exact nonnegative partition, both residual constant terms must be exactly
zero, and both frequency coefficients must be zero. A representable residual,
including one caused by subtraction order, retains generic evaluation.

For this polynomial, `S(V) = S_initial * (|V|/|V_initial|)^2`. The initial
admittance `Y_initial = conjugate(S_initial)/|V_initial|²` already supplies the
complete physical current. Its Norton correction is algebraically zero:

`I_Norton = Y_initial*V - conjugate(S(V)/V) = 0`.

The specialization returns the immutable zero correction and keeps the physical
admittance in the network matrix. It still calculates and publishes current
P/Q at every trial voltage, preserving the public load-power observations and
recording boundary. It adds no integration vector, alternate model equations,
expression lookup or per-step model discovery. Both integration methods use
the same component.

A nonzero accumulated load change, replacement of the original admittance
object, a nonfinite input, mixed powers or frequency sensitivity uses the
existing generic current equation. Complete shedding and restoration therefore
retain the established fixed-stamp compensation. No small-current tolerance
or approximate coefficient matching controls eligibility.

The specialization changes floating-point evaluation of a mathematical
cancellation. Across complete before/after moderate-fault recordings, the
largest difference over every numerical field is `6.33e-15`. Generic fallback
unit checks preserve the original numerical-library arithmetic bit for bit.
The independent trajectory checks remain separate from that equivalence result.

## Independent evidence and limits

`CompositeStaticPolynomialSmibTest` pins immutable cold-repeat captures, all
startup samples, both sides of each fault, raw constants and executed scripts.
Static-only controls retain all active component diagnostics in the first
channel partition, every allocated state and coupled generator boundary
channels. Comparisons declare the nonnegative-time window and accumulated
binary32 reference clock; the original startup samples remain in the archive.

The coupled controls include two intervals and both integration methods. The
worst external voltage range-normalized RMSE is approximately `3.24e-5`, and
the worst rotor-angle RMSE is below `3e-5°`. Every static P/Q observation is
also compared, with explicit method-specific bounds for finite-step network
coupling. The recorded-input equation check has separate `6e-6 pu` active and
`3e-6 pu` reactive limits. Frequency sensitivity remains disabled in these
independent controls; the specialization rejects nonzero frequency coefficients.

`CompositeStaticAdmittanceTest` covers exact eligibility, fresh trial-voltage
power, signed reactive demand, retained floating residuals, coefficient
cancellation, frequency dependence, load changes, complete shedding,
restoration, replacement stamps, nonfinite fallback and warmed allocation.

A matched class-overlay experiment verifies the actual loaded model hash.
Warmed linear publication falls from approximately **64 to 32 allocated bytes
per call**; the remaining object is its fresh immutable power observation.
Mixed-polynomial publication remains approximately 64 bytes. This is a kernel
allocation result, with no complete-simulation speedup claim.

Reference archive SHA-256:
`4b7a71503be4eeda85abc44ac89b3827649de93bcd75f06a9cbc5ec77eb824a6`.
Exact tool provenance, source/runtime hashes, cold outputs, rejected alternative
normalization calculations and complete before/after streams remain in private
verification artifacts.

The [whole static current port](cmldblu1-static-low-voltage.md) now implements
the independently established low-voltage preparation for the qualified merged
node configuration. Its preparation depends on the first network iterate, so
that configuration uses a current port instead of this linear specialization.
Other configurations retain this kernel. Severe-fault switching with mixed
components or represented distribution, and inconsistent internal observations,
still require separate qualification before complete composite acceptance.
