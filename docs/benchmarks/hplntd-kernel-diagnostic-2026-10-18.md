# Hybrid plant kernel: in-progress diagnostic, not accepted

Historical draft findings, retained unchanged below. The integration-method
and clock discrepancies are resolved by the subsequent
[bounded kernel slice](hplntd-kernel-2026-10-18.md); its accepted scope and
remaining implementation gaps supersede this draft status.

## Status

The draft `HplntdInitialization` and `HplntdKernel` implement thirteen named
states, plant-base equilibrium, incremental outputs, frequency-rate bounds and
low/high-voltage freeze/recovery. They are **uncommitted and not accepted**.
No catalog entry, native parser attachment or loadable-runtime claim is added.
The last accepted slice remains downstream lag commit `6600dbea4`.

Six new kernel equation tests pass, alongside nine aggregation/data tests and
six downstream lag tests. The independent replay test deliberately still
fails. Full regression/commit gates have not been claimed for this draft.
The failing provisional 0.001-pu threshold is unchanged; it is not a final
acceptance tolerance. Runtime family presence remains 26/27.

## Source and independent profiles

The Model Library section 36.4, printed pages 1023-1035, supplies the contract.
Source SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The diagram and downstream scaling notes were visually reviewed, and all
parameter/state/VAR tables and initialization/freeze notes were read.
The related [PowerWorld REPC_D documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Bus%20REPC_D.htm)
is independent dialect evidence, not permission to splice wrapper schemas.

Four new four-bus/two-unit profiles were cold-run twice, at 0.0005 and
0.00025 seconds. All eight runs converge, with 89 channels plus time and exact
paired RAW/DYR/probe/CSV bytes. They include measured frequency at bus 2.
The low-voltage profile applies a three-phase bus-2 fault from 0.05 to 0.10
seconds (three cycles at 60 Hz), plus reference steps, and activates voltage
freeze and frequency-rate limiting. The high-voltage case starts above its
high-voltage threshold and proves held controller states, not a rising-voltage
or high-voltage recovery transient. The earlier high-voltage probe did not
activate its threshold and is retained as a diagnostic, not accepted coverage.

The draft replay compares all thirteen states, Pcmd/Qcmd and both incremental
outputs across eight profiles and 4,438 rows. Initial commands come from
downstream device initial commands, not later native state samples. Four older
flat/reference profiles lack measured frequency channels; substituting zero
there is an identified input-coverage limitation, not frequency acceptance.

## Concrete equation error found and corrected

The copied reactive PI logic incorrectly stopped integral motion whenever the
combined proportional-plus-integral output saturated. The activating native
fault trace shows integral storage continuing below its own bound while the
combined output is already limited. Only the new hybrid kernel is corrected:
the reactive integral's non-windup check uses integral storage, not the clipped
combined output. A focused equation test requires that distinction.

Before correction, the coarse reactive-integral residual reached 0.0148847 pu
and remained offset after voltage recovery. After correction, it is 0.00121010
pu coarse and 0.000601952 pu fine. Reactive-command residuals reduce from
0.0126003 to 0.00385466 pu coarse, and from 0.0107972 to 0.00179824 pu fine.
Event-boundary measurement and freeze timing residuals remain; this is not
full trajectory acceptance or proof of all integral-boundary policies.
REPCC is not changed based on evidence for another exact model.

## Unresolved fast reference-slew behavior

The draft inherited a reference-slew realization using a 2*dt lag and clipped
derivative. The diagram specifies rate limits, not that internal lag equation;
this assumption must be independently established before acceptance.

At a 0.05-pu active-reference step, the largest state-10 residual is
0.00546876 pu coarse and 0.00552344 pu fine. State 11 differs by 0.00218751 pu
at both steps. These discrepancies do not disappear by halving dt. They must
not be hidden by enlarging tolerances or changing the general core integrator.

After the event startup and outside active rate limits, the raw reference
errors closely satisfy `e[n] = (e[n-1] + e[n-2])/4`: residuals are below
3.73e-8 pu for state 10 and 7.46e-9 pu for state 11. This is consistent with a
carried-predictor-derivative PEC signature for alpha=1/2, whereas Heun gives
the scalar recurrence `e[n] = 5*e[n-1]/8`. It is a diagnostic inference, not
proof of the closed reference implementation, its event-start history, or
the correct published rate-limiter realization. Production code does not fit
or copy that recurrence.

## Next actions

- Isolate reference-slew equations from integration/history effects using a
  constant-input scalar discriminator and authoritative rate-block definitions.
- Keep the general core modified-Euler behavior unchanged; do not compensate
  for a model-specific mismatch by changing unrelated blocks.
- Resolve fault endpoint sampling and freeze/recovery timing at both steps.
- Add measured frequency to reference-step profiles before claiming all-state
  independent acceptance for those profiles.
- Verify active-PI saturation, integral-bound projection, disabled Q/F paths,
  proportional-only initialization, voltage/PF selectors, base changes and MSS.
- Only after the independent gate passes, tighten each channel, run the full
  scoped core/SMIB regression gates, update acceptance inventory, and commit.

No full Texas2k dynamic simulation is run. Unrelated governor, adapter,
full-network recorder and documentation edits are preserved.
