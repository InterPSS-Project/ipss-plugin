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
