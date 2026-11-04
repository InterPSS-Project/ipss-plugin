# REECC1 attached plant and remote-sensing qualification

This slice adds independent recorded-input qualification for an attached REPCA1
plant controller. It does not change production equations, integration defaults,
network tolerance or the existing coupled-fault acceptance ceilings.

The synthetic flat and fault controls each contain 1,205 rows at a 0.25 ms
interval. Each cold pair is byte-identical across the complete recorded CSV.
The fault is applied at 0.05 s and cleared at 0.10 s. Both profiles retain the two
repeated run-boundary timestamps, including the flat profile's repeated samples.
All seven electrical states, seven plant states, current commands, SOC and
absolute plant reference inputs are compared. Recorded bus voltage, angle,
frequency and machine P/Q drive the controller boundary. The initial repeated
negative-time sample is held; the first advancing interval ends at zero.

The default controller path and the optional two-step path use their own existing
equations and history rules. Only actual fault boundaries rebase two-step
history. No gains, state offsets, event times or input samples were fitted.

| Maximum absolute error | Default controller path | Two-step path |
| --- | ---: | ---: |
| Electrical voltage measurement | 6.60651e-4 | 1.94839e-6 |
| Electrical reactive PI | 7.86329e-6 | 1.17976e-6 |
| Electrical voltage PI | 2.42347e-4 | 2.24422e-7 |
| Electrical power order | 2.12130e-5 | 1.00621e-7 |
| Plant reactive measurement | 4.51782e-4 | 1.89474e-6 |
| Plant reactive PI | 7.15119e-6 | 1.73234e-7 |
| Plant lead/lag | 3.41438e-5 | 6.02453e-7 |
| Plant active-power measurement | 1.76012e-4 | 3.85400e-7 |
| Plant active PI | 6.53086e-6 | 9.79994e-8 |
| Plant active lag | 2.69681e-5 | 1.91628e-7 |
| Accepted active-current command | 1.73321e-4 | 7.10633e-7 |
| Accepted reactive-current command | 8.81990e-4 | 1.14474e-4 |
| SOC | 4.64113e-8 | 4.40595e-8 |

The flat controls stay below 4.625e-8 across every compared channel. The new
regression uses explicit per-channel physical-unit ceilings: inactive states
1e-10, flat controls 1e-7, two-step dynamic states 3e-6, and two-step reactive
command 1.5e-4. Default dynamic-state bounds range from 1e-5 to 8e-4;
the reactive command bound is 1e-3. Energy and SOC have separate tighter bounds.
These are new replay gates; no existing trajectory gate was relaxed.

## Accepted commands versus provisional algebraic outputs

The two-step converter consumes `getTwoStepIpcmd()` and `getTwoStepIqcmd()`.
A zero-duration electrical refresh can change `getIpcmd()` and `getIqcmd()`
without replacing those accepted commands. Comparing the provisional reactive
output to the reference's published current command produced a 0.00942369 pu
apparent error at clearing. Comparing the accepted command reduces it to
0.000114474 pu. Both ports remain in the retained full-resolution diagnostic
traces; no event-side sample was removed. This is an observation-coordinate
correction, not an equation or limiter change. The prior coupled-fault test
retains its existing command mapping and ceilings in this slice.

## Remote sensing remains only analytically qualified

The declared remote selector is retained by the Java model. A new analytic test
checks both integration paths: the selected bus drives voltage measurement and
voltage-dip freezing independently of the converter's local bus voltage.

In two cold independent flat captures, the entered remote selector 2 was changed
to 0 during reference initialization. The recorded measurement then followed
local bus voltage. This repeatable normalization is retained as diagnostic
evidence, not accepted as a remote-bus trajectory oracle. Its cause has not been
established; the implementation does not silently copy that normalization.

Full network feedback with the attached plant, independently exercised remote
sensing, upper/active-power upstream limits, and additional attachment configurations remain
open. These replay results do not establish their qualification.

Private immutable inputs, complete output samples, source/readback manifests,
paired equality checks and both command-port traces are retained under
`verification-artifacts/reecc1-plant-remote-20261104`. The test is extended and
explicitly skips when those private references are unavailable.

Validation: 48 focused tests passed with no failures, errors or skips, including
the existing coupled REECC fault tests and REPCA controller tests. Neutral
language and whitespace checks passed.

## Active upstream reactive lower limit

A further cold pair sets REPCA QMIN to 0.04 pu while retaining Kpg at 0.5 and
all other controls. Initial Q is 0.0474446 pu, so initialization lies inside the
bounds. Readback and pinned hashes identify the exact entered values. The two
complete 1,205-row captures are byte-identical. The unbounded control remains
available in the earlier capture set.

The independent PI input crosses the lower bound for 570 samples; 569 following
transitions retain the integral exactly. The full downstream electrical and
upstream plant states are replayed for both methods with the same ceilings as
the preceding plant-fault replay. Default maximum reactive-command error is
8.89417e-4 pu; two-step maximum is 8.80066e-5 pu. Two-step electrical and plant
state errors remain below 2.420e-6. The plant reactive integral and lead/lag
errors are respectively 1.646e-8 and 2.238e-8 in the two-step path. The output
reference remains above the absolute 0.04 pu bound throughout this profile.

An earlier attempted probe changed the neighboring active PI gain instead of
QMIN. Its unchanged readback exposed the setup error; that pair is retained as
rejected-intent evidence and is not used by any qualification test. The corrected
pair is independently captured under a new profile name and immutable manifest.

Active-limit inputs and readbacks are in
`verification-artifacts/reecc1-plant-qbound-20261104`; replay traces and the
46-test passing validation log are retained separately in
`verification-artifacts/reecc1-plant-qbound-validation-20261104`. No production
model change or old acceptance-limit change was required for this limit profile.
The separate accepted-event power correction is documented in
[Accepted converter power](regca-accepted-event-power.md).
