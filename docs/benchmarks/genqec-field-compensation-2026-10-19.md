# GENQEC field-compensation input and product-limit evidence

> Later contract extension: the negative-input/current-direction probes in
> [Signed field-compensation input and runtime magnitude](../dynamic-models/genqec-kw-magnitude.md)
> supersede this historical report's nonnegative-only input guard and unchanged-runtime
> statements. The earlier positive-Kw evidence remains valid.

Status: validation defect corrected and scoped verification accepted, not
complete machine parity. Private source/run/report hashes are retained under
`2026-10-19-genqec-field-compensation`; the scoped implementation commit is
recorded in Git history.

## Confirmed defect and source contract

The installed generator specification, pages 47–49, permits Kw>=0 and limits
Kw times d-axis current to [-0.4,0.4]. The d-axis diagram was visually checked.
The [PowerWorld GENQEC reference](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEC.htm)
identifies Kw as the d-axis current multiplier. Native and PowerWorld runs both
accept the tested Kw=1 and Kw=1.5 inputs without requested correction.

`GenqecData` incorrectly required Kw<1 and also admitted NaN. Two independent
constructor guards fail before the fix. The record now requires finite Kw>=0,
preserves the supplied multiplier and imposes no artificial parameter cap.
The existing runtime still clips the product Kw*Id; it does not clip Kw itself.
No differential, field-current or network equation is changed. GENQEJ's separate
Kis current-dependent saturation input is not reinterpreted as this compensation.

## Exact input and initialization evidence

Three standard native profiles differ only in the final Kw constant: 0, 1 and
1.5. Both timesteps (0.5 and 0.25 ms) were generated twice cold, giving twelve
native runs. All eighteen channels, terminal phasors, six states and 605/1,205
raw rows are retained, including negative history and duplicate event sides.
Entered constants match allocated constants at native storage precision.

On the common 100-MVA machine/system fixture, initial Id is approximately
0.325 pu. Native initial Efd is 1.48248446 at Kw=0, 2.19627357 at Kw=1 and
2.47080731 at Kw=1.5. Multiplying each by
`1 - clamp(Kw*Id,-0.4,0.4)` recovers the same initial field numerator within
1e-6. This checks the input meaning and initialization without fitting states.
InterPSS initialization differs by at most 8.75e-7 pu in these profiles.

The Kw=1 native fault trace activates both product limits: the raw product
ranges from about -0.4929 to 2.24125 and returns to about 0.31891 pu. The
Kw=1.5 initial point is already on the positive limit. The raw product is
retained rather than replaced by its clipped value in the audit summary.

## Runtime checks and limitations

The actual machine electrical solver replays all six frozen phasor, rotor and
field-voltage inputs. Four electrical states, the saturation-factor diagnostic
and terminal P/Q are scored at every native row. Forced mechanical inputs are
not claimed as predicted mechanical trajectory acceptance. Bounds are separate
for every profile and channel, with timestep refinement required.

The largest field-state E'q replay error falls from about 5.59e-5 to 2.81e-5 pu
under step halving. Maximum P/Q errors fall from about 0.002623/0.017028 to
0.001403/0.008926 pu. First post-fault/clearing callbacks dominate these
residuals; an instantaneous solved event-post phasor is not supplied by the
last native pre-event sample. Complete coupled/event-post parity remains open.

An independent unsaturated forward-equation sweep verifies the field-current
denominator in positive-limit, negative-limit and inactive regions to 1e-10.
A deliberately unbounded-product mutation fails that guard, and the original
runtime is restored before final gates. A coarse initial phase grid missed the
inactive region; its coverage assertion failed, so the test input sweep was
made dense enough to exercise all three regions. That was a test-coverage gap,
not a runtime equation defect, and the exploratory report is retained separately.

Six strict-import coupled runs additionally check initialization, stationary
pre-fault equilibrium, all finite states, actual three-cycle voltage depression
and both product-limit signs. They do not establish independent full-network
trajectory acceptance. Twelve new PowerWorld cold runs retain all eleven
channels and 603/1,203 samples with no model overrides or requested correction.
Their raw traces repeat exactly; post-initialization parameter round-trip and
complete cross-tool acceptance are still open.

## Checklist

- [x] Capture both original validation failures; remove the artificial Kw<1
  cap and reject negative/nonfinite multipliers.
- [x] Preserve exact native input cardinality and all non-Kw parameters.
- [x] Cold-repeat twelve native and twelve commercial profiles.
- [x] Verify native initial field denominator, both limit signs and release.
- [x] Verify bounded electrical replay and six imported fault profiles.
- [x] Reject the removed-product-limit mutation and restore runtime source.
- [x] Pass 23 focused, 406 plugin-core (six optional skips) and 1,569 scoped
  SMIB/model/solver checks (one existing diagnostic skip), with zero
  failures/errors. Six offline probe guards and six language-policy checks pass.
- [x] Audit all 1,800 unchanged Java/POM baseline files and seven owned Java
  sources. Keep the runtime machine bytecode unchanged; retain final source,
  reference and report hashes for the scoped implementation commit.
- [ ] Close complete coupled trajectories, instantaneous event-post inputs,
  further winding/time-zero, compensation interactions and lifecycle coverage.

The final verification baseline includes the independently committed renewable
rate-history update `1b981eb9b`; earlier exploratory gates used its parent.
Unrelated shared-checkout edits are excluded from the scoped verification.
No full Texas2k dynamic simulation is run for this slice.
