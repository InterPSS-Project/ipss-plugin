# Fleet boundary diagnostics and revised acceptance

The user revised the fault-and-postfault voltage/frequency NRMAE limit from
0.005 to **0.015**. The [current contract](texas2k-current-acceptance.json)
retains the 0.001 prefault limit, 0.02-degree prefault rotor maximum and
0.2-degree full-ten-second rotor RMSE. Normalization still uses each channel's
full ten-second supplied range. Scoring starts the relaxed period at fault
application and preserves both event sides. Historical artifacts retain their
original thresholds.

## Case 2 rescoring

The complete bus-6307 event was rescored from the unchanged 10,005-row physical
output and supplied full-system reference. The new limits change acceptance
decisions only; no trajectory, alignment, model or initialization changes.

| Required measurement | Passing channels | Worst error | Location |
|---|---:|---:|---|
| Prefault voltage NRMAE | 2,000 / 2,000 | 0.000463305 | Bus 1070 |
| Fault/postfault voltage NRMAE | 2,000 / 2,000 | 0.013741343 | Bus 1011 |
| Prefault rotor maximum, degrees | 102 / 103 | 0.028531358 | Unit 1051/1 |
| Ten-second rotor RMSE, degrees | 102 / 103 | 0.203218697 | Unit 1050/1 |

All 103 generator-speed proxy channels pass both period limits. Worst proxy
NRMAEs are 0.0000638304 prefault and 0.000818356 from fault application.
Generator speed is not direct bus frequency; that requirement remains open.
Range-normalized RMS voltage errors are retained as diagnostics and do not
replace the requested mean-error metric.

## Complete synchronous-stack voltage replay

All 103 synchronous machines and their attached exciter, governor and
stabilizer states were replayed using only prescribed terminal magnitude and
phase. Original parameters and ordinary initialization from the computed cold
operating point are retained. Supplied rotor angle, speed, P/Q, field voltage
and mechanical power are comparison outputs only. No internal reference state
is supplied. The current frozen runtime and read-only physical recorder are
used throughout.

Independent prefault runs retain 1,003 rows per machine. Their first 1,003 rows
and cold snapshots are exactly reproduced by the complete 10,005-row runs.
Duplicate event rows change voltage without advancing differential states;
the next real step uses the existing discontinuity recurrence.

| Prefault comparison | Worst rotor maximum, degrees | Location |
|---|---:|---|
| Local-voltage replay versus coupled local result | 0.001996327 | 5263/1 |
| Supplied-voltage replay versus supplied result | 0.003556100 | 1050/1 |
| Coupled local result versus supplied result | 0.028531358 | 1051/1 |

All 103 supplied-voltage replay rotor channels satisfy the prefault numerical
limit conditionally. The initial controller-state/output difference from the
coupled cold initialization is at most 2.41e-13. This supports investigating
the network inputs to the remaining prefault rotor discrepancy; it does not
establish autonomous system acceptance.

The full replay has an important limitation at 5303/1. Even prescribed *local*
voltage eventually produces 0.970688-degree rotor RMSE against its own coupled
run. Its rotor error is only 2.15e-12 degrees at two seconds, but reaches
4.21628 degrees at ten seconds. Field-voltage error grows from 8.42e-10 pu at
two seconds to 1.45796 pu at eight seconds. There is no comparable jump at
either event boundary. The supplied-voltage replay's 2.70040-degree RMSE at
this unit therefore cannot independently establish a model defect.

A reduced cold-point linearization holds terminal voltage and mechanical power
fixed, with the governor inactive only in that diagnostic. The 24-coordinate
machine/exciter/stabilizer derivative residual is 8.33e-15. Three finite-
difference increments give the same leading poles, approximately
**3.61714 +/- j82.65639 per second**. The reduced two-step polynomial gives
growth rates 3.65992/s at 1 ms and 3.62632/s at 0.5 ms. This supports
amplification under the prescribed-voltage boundary. It does not establish
the full coupled-network spectrum or a complete solver transition matrix.

## Complete converter-fleet replay

All 96 original converter bundles were independently initialized from the same
computed cold point and replayed over all 10,005 local and supplied voltage
rows. Initial state differences are at most 4.45e-16. The local-voltage check
reproduces **190 of 192 P/Q channels within 1e-8 MW/Mvar**, with the largest
error among those 190 below 8.53e-14. The two exceptions are Q at buses 3113
and 3129: maximum differences are 2.79351 and 0.0781467 Mvar. The isolated
path does not reconstruct their inner network high-voltage correction.
Those channels cannot use this replay as an independent component gate.

With supplied voltage, the largest active-power residuals are 7.29666 MW at
3028/1 and 7.14555 MW at 3098/1. A second run records their states, derivatives,
commands and limit activity without changing any of the 96 physical output
trajectories: the complete compressed physical output is byte-identical.

Both terminal-voltage histories remain inside the electrical dip band and
converter voltage-scaling/correction bands. At each peak, the electrical
controller's reactive command approaches its 1.3-pu current limit. Reactive-
priority current-circle headroom then reduces the active command toward zero,
although the unconstrained active demands are 0.68451 and 0.66600 pu.
Delivered reactive currents remain only -0.31249 and -0.12593 pu, with zero
stored reactive derivative because their initialized recovery-rate boundary is
active. Command headroom and delivered current are distinct quantities.

An approximate command reconstruction from supplied P/V and the retained
two-step recurrence places the current-limit transitions at different times.
Against the local recorded command, the same reconstruction closes within
3.17e-7 and 7.09e-7 pu on the falling-current windows. The inferred reference
commands are observations derived from terminal outputs, not measured internal
states. No time shift is applied to any comparison.

Two explicit measurement controls substitute supplied Q alone, or supplied P
and Q, only at these two controllers. A no-substitution overlay reproduces
all physical output bytes; all 94 other devices remain numerically identical
in every control. Cold initialization is unchanged.

| Unit | Voltage-only peak P error, MW | With supplied Q, MW | With supplied P/Q, MW |
|---|---:|---:|---:|
| 3028/1 | 7.29666 | 5.16200 | 5.16200 |
| 3098/1 | 7.14555 | 4.35382 | 4.35361 |

The substitutions do not eliminate the discrepancy and worsen full-window Q
RMSE. They identify a limiter-sensitive feedback path, but do not justify a
different state clamp, recovery selector, parameter or production equation.
Under the revised goal, these P/Q diagnostics are retained for the broader
network/rotor investigation; they are not additional acceptance requirements.

## Case 3 source-current accounting

An exact secant decomposition partitions the local voltage movement from its
cold point into converter active/reactive current, synchronous source-flux and
rotor-angle changes, and the retained network-equation residual. The original
passive matrix, converted loads and machine Norton terms are preserved. Matrix
reassembly closes within 9.10e-13; voltage accounting closes within 5.1e-15 pu.

At bus 3054:

| Time, s | Local voltage movement, pu | Converter active contribution, pu | Converter reactive contribution, pu | Local minus reference voltage, pu |
|---|---:|---:|---:|---:|
| 0.02 | 0.0219712 | 0.0241556 | -0.00405448 | -0.000416695 |
| 0.05 | 0.0498387 | 0.0708034 | -0.0327833 | -0.00411407 |
| 0.10 | 0.00978506 | 0.0814624 | -0.0892397 | -0.00642704 |

The remaining source and residual terms complete each identity. These large
opposing contributions explain sensitivity; they are contributions to the
*local movement*, not measured source-model errors. No inferred injection is
applied to a simulation. This accounting does not uniquely identify a faulty
device or resolve Case 3's prefault failure.

## Evidence and remaining scope

Private evidence set
`2026-10-19-fleet-boundaries-and-revised-acceptance` retains exact producers,
runtime/input hashes, all completed runs, original failed helper attempts,
corrected comparisons, control traces, plots and revised scoring. No production
model source changed in this slice. The local summary failure in the first
linearization producer was corrected in postprocessing without rerunning Java.

Remaining work includes the Case 2 rotor discrepancies, unresolved prefault
differences in the other original cases, all six cases and 36 original events,
direct bus-frequency evidence and the quarter-cycle runtime target. Neither
conditional replay agreement nor the revised voltage gate proves that full
objective complete.
