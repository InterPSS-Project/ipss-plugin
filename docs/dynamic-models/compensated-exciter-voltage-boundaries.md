# Compensated sensing and physical exciter supply boundaries

Equation-based exciters must honor the same machine-voltage input contract as
CML `mach.vt`. An input annotation alone does not evaluate that contract inside
an explicitly coded initialization or differential-equation method. Reading raw
bus magnitude there silently bypasses an attached voltage compensator.

Use the existing `IeeeVoltageCompensatedMachine.sensedVoltage(machine)` boundary
for AVR sensing. It respects the machine's `ICMLMachineVoltageProvider`, including
IEEEVC and the supported machine-specific compensation convention, and retains
the framework's machine-base terminal-magnitude fallback. Do not reconstruct
impedance/current phasors independently in each exciter. The helper introduces
no new device, model state, control mode or case-specific override.

## Consistent sensing throughout the lifecycle

The same measurement feeds the initialized transducer state/reference, its
positive-time-constant RHS, and its algebraic bypass/direct-error path. Updating
only initialization would create a spurious residual at the first step; updating
only the RHS would initialize the reference at a different electrical boundary.
Predictor and corrector stages sample their live input as before. Zero-duration
calls and derivative/state history ownership are unchanged.

This correction covers AC7B/ESAC7B and AC8B/ESAC8B engines, REXSYS, ESDC2A and its
ESDC1A/DC1C/DC2C inherited profiles, DC4B, DC4C, ST6B, ST6C, ESST2A, DC3A/ESDC3A,
ESAC4A, and IEEET4's direct rheostat/error selectors. Inheritance must not hide an
older raw-sensing engine behind a newer source model name.

## Physical terminal inputs remain distinct

A compensator changes the regulated measurement, not the available exciter
power supply. Retain physical terminal phasors/magnitude and current in compound
sources, rectifier loading, potential multipliers and terminal-scaled ceilings.
Examples are AC7B's potential factor, DC4B's regulator scaling, ST6B's VRMULT
supply, ESDC2A's selected voltage-dependent bounds, REXSYS FLIMF limits, and the
compound/bridge circuits in DC4C, ST6C and ESST2A. A blanket replacement of every
terminal-voltage read would incorrectly move those physical boundaries.

ST1C already sensed the compensated signal, but also reused it in final field
limits. Its final clamp instead uses physical machine-base terminal voltage:

```text
lower = Vt * VRMIN
upper = Vt * VRMAX - KC * IFD
EFD   = clamp(preField, lower, upper)
```

Its inverse startup range-correction geometry uses the same physical Vt. That
preserves the engine's existing runtime initialization policy without editing
entered limit data. It does not establish that policy as an exact native
out-of-range startup behavior: an independent invalid-start probe clipped and
reported a suspect condition rather than expanding VRMAX. That initialization
policy remains outside the native qualification of the runtime ceiling correction.

## ST6C field voltage at a switching boundary

An ST6C with algebraic sensing and output has an immediate change in field
voltage when the accepted network voltage changes. The fault recorder emits two
rows at the same event time. The first uses the pre-event field voltage and
the second reevaluates the shared regulator and bridge equations from the
post-event electrical inputs with the accepted controller states. It does not
write the machine field voltage or advance the regulator.

The ST6C evaluator takes the compensated voltage for sensing and the physical
terminal phasors for the bridge. The limiter port exposes an independent frozen
output when OEL3C has a positive measurement lag. A limiter without a qualified
read-only output returns an unavailable value, so the recorder uses the cached
field voltage in that case. The recorder limits the new evaluation to
GENTPJ1 with algebraic ST6C sensing and output, positive feedback lag,
terminal-fed supply, and no attached stabilizer.

Reference-simulator component testing with the source ST6C, UEL2C, and OEL3CU stack verified
the immediate change at fault application. The read-only component regression
checks the accepted output, controller states, and all other recorded channels
against the previous execution. An independent synthetic fixture also checks
the field output for both voltage inputs without advancing the controller.

## ESST4B feedback at a switching boundary

For ESST4B with an active amplifier lag, the reference field voltage immediately
after switching uses the new terminal bridge supply and the accepted pre-event
field voltage in the inner feedback gain. The amplifier and both integrator
states remain at their accepted values. Re-solving the feedback loop with the
new bridge overstates the immediate response to a voltage drop when the inner
feedback gain is positive. The paired recorder supplies the field voltage from the left
row to the right row, and the exciter computes the corresponding output
without advancing its state.

Native probes checked unsaturated fault application and clearing at two timesteps,
a clearing at the modulation limit, and a zero-feedback-gain control.
A matched Comanche rerun changed only post-event field-voltage samples.
Bypassed amplifier configurations continue to use the established observer
and need separate qualification for their feedback timing.

## Evidence and limits

Synthetic contracts cover both compensation signs, initialized equilibrium,
live predictor/corrector samples, bypasses/direct error, absent/zero compensation,
inherited profiles, original-data retention, and independent physical supply
responses. Independent component captures confirm compensated sensing for AC7B
and AC8B, representative REXSYS/DC4B sensing, physical AC7B potential supply,
and physical ST1C upper/lower ceilings.

These checks qualify input/constraint boundaries. They do not promote every
covered model to complete nonlinear trajectory acceptance or establish a large
system's stability. Private source vectors, exact-tool provenance and coupled
case outcomes remain in the separate QA repository. Full replay must retain its
actual executable/configuration hashes and numerical/stability gates.
