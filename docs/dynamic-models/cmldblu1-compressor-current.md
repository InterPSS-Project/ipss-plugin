# Compressor low-voltage current and heating

Status: independent constitutive-power and heating checks pass through the
shared modified-Euler and staggered runtime. Complete composite-load trajectory
qualification remains open; the catalog remains `LOADABLE`.

## Corrected running characteristic

For the legacy composite policy, the running constant-impedance branch uses
`P = CompLF * Gstall * V²` and `Q = -Bstall * V²`. A stalled group uses the full
stall conductance without the loading multiplier. Previously the running branch
omitted that multiplier. Its intersection with the running polynomial also
applied the loading factor to only one side, selecting an incorrect transition
voltage. The factor cancels between the two running active-power branches.

The thermal calculation now uses the same running conductance. It retains the
full stall current for stalled groups. Parallel reactive compensation supplies
terminal reactive power and remains outside the motor's heating current.

Every Norton-current request refreshes power at the supplied network voltage,
including below breakdown voltage and during trial network iterations. The
former low-voltage admittance cache could retain power from another voltage or
run/stall state. That unused cache and its preparation are removed. Trial
currents and derivative evaluations leave continuous states and accepted
protection histories unchanged. Exact zero voltage produces finite zero current.

The implementation reuses its primitive complex-division kernel and current
buffer. The warmed publication check measured 96 allocated bytes per callback,
compared with 160 before the change. This measures allocation in one callback,
not a complete-simulation speedup.

## Independent controls and scope

Synthetic standalone compressor captures isolate the current law from the
composite's unqualified internal-voltage observations. They use the shared
compressor parameter sequence with frequency sensitivity disabled. Running,
impedance, transition, stall and restart profiles are captured at 0.5 and 0.25 ms.
Each accepted capture has an identical independent cold repeat. All allocated
states, diagnostics, network boundary channels, startup samples and event-side
duplicates are retained and hashed.

One initial cold pair was inconsistent: an internally frozen voltage/time
observation accompanied an advancing network. Both original captures are
retained as rejected evidence. A fresh unchanged-input cold pair passes the
external-voltage and repeatability checks; no diagnostic storage was cleared.

The new checks feed recorded continuous states and voltage into the kernel;
they do not claim independently integrated temperature or complete network
trajectory parity. At matching run/stall states, the worst errors are:

| Quantity | Maximum error | Regression limit |
|---|---:|---:|
| Active motor power, motor-base pu | 9.518e-7 | 1.2e-6 |
| Reactive motor power, motor-base pu | 1.485e-6 | 2e-6 |
| Temperature derivative, pu/s | 1.206e-7 | 2e-7 |

Trial-voltage power requests satisfy the same power limits. Both methods use
the same equations and satisfy the same checks. Every stall and restart must
occur within one accepted interval of the reference action. At most four samples
per profile may have different discrete states; errors at those samples remain
in the complete unmasked reports. These action-edge errors can be large and
must not be mistaken for complete sample-by-sample trajectory acceptance.

The unchanged implementation failed the current-law comparisons and allocation
gate. All 23 new checks pass after correction. The private evidence archive
retains the failing baseline, accepted and rejected native captures, complete
before/after comparisons, executable dependencies and regression reports.

## Remaining work

- Resolve the composite's upstream internal-voltage and frequency observations,
  then qualify independently integrated continuous states and severe coupled
  faults. The severe repeated-fault comparison remains diagnostic.
- Qualify active protection, bypasses, restoration, production integration
  intervals and longer runs under both integration methods.
- Retain the [standalone import correction and coupled controls](acmtblu1-shared-import.md).
  Those now check exact decoding, source-load ownership and accepted-timer
  precision; frequency, full continuous-state and active protection qualification
  remain separate from these constitutive-current controls.
- Finish the distinct zone-policy reactive and thermal coordinates. The legacy
  loading-factor correction does not establish zone-model equivalence.
