# Hybrid fault diagnostics and converged event-frequency reference

## Corrected implementation defect

The event handler solves the Norton network once and rebases bus-frequency
memory to that first linear iterate. A nonlinear converter network can still
change voltage angles during the subsequent converged `INITIAL` solve. Treating
that remaining zero-time angle change as motion over the next integration
interval creates a spurious frequency pulse.

`PsseDStabSolver.networkSolutionStep` now completes the rebase at the converged
event endpoint, before differential integration. It uses the existing core
frequency-measurement hook. It does not change the filter state/output, time
constants, controller equations, integration method or network iteration count.
Failed network solutions, inactive buses, disabled events and alternative
measurement-block types are not rebased by this adapter hook. This is an
adapter correction, not a change to every generic core solver.

A strict invariant fails before the fix with a **0.080853579-radian** mismatch
between the frequency reference and the converged bus angle. It passes after
the fix at both timestep sizes. Separate checks preserve zero-time frequency
output, verify that disabled events leave the reference untouched, and assert
that the shared hybrid group advances only once per integration stage.

## Independent small-case comparison

The four-bus case has 50/100-MVA renewable units and an infinite-bus equivalent.
The exact HPLNTDU/REAXDU records are imported through the production reader.
At 0.05 s, active/reactive references step and a three-phase j0.1-pu fault is
applied at Bus 2; it clears at 0.10 s, three cycles at 60 Hz. The original
reference API call specifies impedance `[0,10]` ohms with units selector 3 and
**100 kV conversion base**, not the bus's 230-kV nominal base. The new manifest
records both representations explicitly.

All thirteen plant states, four downstream states, four bus magnitudes, both
units' P/Q and measured Bus 2 frequency are compared at every raw sample.
Negative-time initialization history and both sides of each event remain in
the paired traces. Raw reference exports retain all 89 channels, including
additional plot variables and angles.

This is a **bounded diagnostic, not accepted exact fault-trajectory parity**.
The fixed physical frequency-reference invariant and the independent-tool
trajectory claim are deliberately separate:

- The largest instantaneous power differences remain approximately 0.02533 pu
  for Unit 1 and 0.03795 pu for Unit 4 on the 100-MVA system base, at fault
  application. They do not shrink simply by halving the timestep.
- The largest voltage difference is approximately 5.65e-4 pu, also at the
  instantaneous event boundary.
- The plant voltage-filter residual reduces from 0.0095783 to 0.0049117 pu at
  half step. Several PI/command-lag residuals likewise approximately halve.
- Native measured-frequency callback parity remains open: the residual is
  approximately 7.5e-4 pu even after correcting the implementation's spurious
  reference shift. The native trace itself has a short post-event frequency
  transient associated with its evolving algebraic endpoint; complete causal
  callback equivalence has not been established.
- Reference-slew states retain the previously documented cross-method peak
  residual. Their time constants scale as 2h, so reducing h does not reduce h/T.

No artificial current delay, fitted gain or altered reference data is introduced
to conceal the remaining mismatch. Channel-specific diagnostic envelopes retain
the counterexample explicitly; they must not be read as a VERIFIED label.
Every channel's maximum error and time are retained in the test log.

## Legacy bounded wind comparison

The corrected upstream frequency signal moves the existing cross-dialect ANDES
turbine-speed error from 3.04e-7 to 5.51e-6 pu at its original timestep. This
exceeded that comparison's former 5.0e-6 envelope. Half- and quarter-step
diagnostics increase the residual to 6.15e-6 and 6.49e-6, so this was not treated
as a disappearing truncation error.

Source-controlled A/B runs isolate the cause: old and corrected event handling
produce byte-identical traces across all fourteen recorded channels when the
plant frequency-control path is disabled. With it enabled, the changed upstream
signal propagates into the coupled mechanical trajectory. The drivetrain source,
parameters and reference CSV are unchanged.

Two legacy **bounded cross-dialect** envelopes change: turbine speed from
5.0e-6 to 6.1e-6 pu, above the observed 5.51e-6 error, and shaft torque from
5.0e-5 to 9.5e-5 pu, above the observed 8.55e-5 error. The old/new A/B maximum
trajectory shifts are 5.81e-6 and 9.50e-5 respectively, and both disappear
completely with the upstream frequency path disabled. All other channel limits
remain unchanged. No exact/native model conformance bound is
relaxed. Both A/B pairs, the failing original gate and reduced-step diagnostics
are retained. The temporary fixture/control-flag and timestep changes are
restored before the final regression gates.

## Reproducible artifact

The portable [probe](../../tools/hplntd_fault_probe.py) reads the exact frozen
DYR bytes from the existing public resource rather than retyping the schema.
From the repository root, in the licensed reference scripting environment:

```
python tools/hplntd_fault_probe.py unique-coarse-a 0.0005
python tools/hplntd_fault_probe.py unique-coarse-b 0.0005
python tools/hplntd_fault_probe.py unique-fine-a 0.00025
python tools/hplntd_fault_probe.py unique-fine-b 0.00025
```

Each run requires a fresh tag and creates a new private directory under
`target/hplntd-fault-reference-*`. It retains runnable inputs, source probe,
native output, CSV, log, actual parameter/allocation audits and a hashed
manifest. Runtime/model-library/document identities are recorded without
absolute user paths. A failed initialization or nonconverged reference is
rejected rather than used as an oracle.

Both cold repeats at each timestep are byte-identical to the previously frozen
raw CSV: 405 samples at 0.5 ms and 805 at 0.25 ms. The new probe also reproduces
the same input hashes and actual model constants. These are two datasets with
repeatability evidence, not four additional model profiles.

## Remaining work

Final gates: **47 focused tests, 379 core-unit tests and 1,493 selected
SMIB/model/solver tests**, zero failures/errors. Six existing optional core
skips and one existing diagnostic skip remain. The wording guard and all six
guard tests also pass. Source snapshots, raw cold runs, paired traces, A/B
inputs/results, failed diagnostics and XML reports are sealed in private
artifact `2026-10-18-hplntd-fault-frequency`. The seal verifies the restored
standard fixture/configuration, all other baseline Java/build sources, the
unchanged companion jar, generated inventory and prior immutable evidence.

Independent coupled fault acceptance remains open for instantaneous current
and frequency callbacks and the other bounded residuals above. The documented
alternate hosts, disabled/zero-integral loops, relative links, initial inactivity,
all-off restart and shunt-switching work also remains open. Runtime-family
presence stays 27/27 bounded paths. No full Texas2k dynamics are run here.
