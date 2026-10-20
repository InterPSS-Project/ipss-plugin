# Case 1 governor boundary replay

Recorded speed and electrical power explain most of the mechanical-power
discrepancy at the five machines observed in the original bus-7249 event.
The same conditional check covers all 30 governor occurrences in the six
original Case 1 events. It does not establish autonomous network acceptance.

## Independent initialization and replay control

Each governor is loaded from the unchanged original input records and
initialized from the local computed cold mechanical and electrical power.
All ten stored coordinates for each general governor and all four for each
hydro governor reproduce the retained cold snapshot within 1e-12. No supplied
internal state, mechanical-power trajectory or fitted reference offset is
loaded into the controller.

For the first event, replaying the local speed and air-gap power reproduces
the coupled run's mechanical power within 7.78e-16 pu. This checks the replay
recurrence against the complete network run, including two cold-history
rows and both sides of fault application and clearing. The replay uses the
same 0.5 ms step, derivative history and discontinuity averaging.

The supplied-input replay instead prescribes recorded speed and electrical
power, converted from system base to the original machine base. All observed
machines have zero stator resistance. Their governor references remain
constant. Mechanical power is solely an output used for comparison.

| Unit | Coupled mechanical-power RMSE, pu | Supplied-input governor RMSE, pu | Reduction |
| --- | ---: | ---: | ---: |
| 7146/1 | 0.000133492 | 0.00000330907 | 97.5% |
| 7189/1 | 0.000145478 | 0.0000155292 | 89.3% |
| 7190/1 | 0.000249928 | 0.00000528066 | 97.9% |
| 7192/1 | 0.000126534 | 0.00000398411 | 96.9% |
| 7193/1 | 0.000307383 | 0.00000314194 | 99.0% |

Power errors use the original machine MVA base. These conditional reductions
localize the input boundary; they do not prove that governor residuals have
no effect after full-network feedback.

## All six original Case 1 events

The extended replay includes 28 general-governor and two hydro-governor
occurrences. Each fault retains 20,005 rows; each generator trip retains
20,004 rows. Original trip targets disconnect at their event and report zero
mechanical power thereafter. All other controllers continue to evolve.

Across all 30 mechanical-power trajectories, the worst RMSE is 0.0000257167 pu
and the worst instantaneous discrepancy is 0.0000613042 pu, both at hydro
unit 6042/1 in the bus-6187 fault. All 288 supplied governor-state channel
occurrences are compared. While their machines remain connected, the largest
state difference is 0.0000460087, at the turbine lead-lag state of 5297/1.
State coordinates follow the retained model diagram: the hydro second slot
is desired gate, the general governor's acceleration slot is its stored
speed lag, and its temperature slot excludes the direct lead contribution.

## Disconnected state observations

The three tripped units' supplied mechanical-power channels become zero,
but their 30 exported internal-state slots do not all freeze. Eighteen have
exactly constant nonzero increments, eight are constant, and four have varying
increments. The largest drift is -0.128746 in the electrical-power measurement
slot of 1078/1. It decreases by exactly 0.000007152557373046875 per sample after
the trip. This is an observation of the supplied slots, not a claim about
unobserved internal execution or the cause of that behavior.

The replay freezes disconnected controllers. Their raw full-window state
discrepancies are preserved separately from the connected-state comparison.
This lifecycle/observation difference is not used to alter network injections
or to conceal any failing state channel.

## Evidence and remaining work

Private evidence set `2026-10-19-case1-governor-boundary-and-dense-controls`
retains both first-event replays, all six supplied-input replays, all channel
metrics, raw supplied state arrays, exact producers, frozen runtime hashes,
original input records and cold-state checks. It also retains the completed
dense network controls and their comparison evidence.

The first-event governor check supports investigating upstream electrical
and speed inputs, including the coupled machine/excitation/network response.
It does not resolve the original absolute-angle errors, direct bus-frequency
coverage, other cases or the production runtime target. No model parameters,
production equations or acceptance limits are changed by this diagnostic.
