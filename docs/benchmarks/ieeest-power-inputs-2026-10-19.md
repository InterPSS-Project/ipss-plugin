# IEEEST electrical and accelerating-power input verification

## Source-backed correction

The [zero-numerator follow-up](ieeest-zero-numerator-2026-10-19.md) implements
the separate T5=0 lag convention and physical DC initialization. It retains
ordinary washout storage/stages and the bounds in this report; its rejected
native nonzero-DC initializations are not counted as accepted trajectories.

The native source table defines mode 3 as generator electrical power and mode 4
as accelerating power. The existing mode-4 implementation selected mechanical
power alone: its electrical-power coefficient was zero. The correction changes
that coefficient to minus one, retaining the existing mechanical coefficient,
initial operating-point references, filter equations, integration and limits.

The failing-before test gives +0.05 rather than -0.15 for simultaneous
delta(Pm)=0.05 and delta(Pe)=0.2. A second fails because equal power changes
produce +0.2 rather than zero. Permanent predictor/corrector tests now verify
both signs and cancellation independently of a trajectory tolerance.

## Native contract, not a typed-model alias

The native PSS1A correspondence table uses an IEEEST record with two selectors
and seventeen constants. Its Tr maps to A3, A4/A5/A6 are zero, and Tw supplies
both T5 and T6. This is distinct from the fourteen-value typed PSS1A interface.
The [typed PSS1A source](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS1A.htm)
and [IEEEST source](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20IEEEST.htm)
remain separately identified. This slice neither aliases model identifiers nor
establishes that the compatibility reader's literal PSS1A record is a native
format. Exact source pages and document/library identities are pinned privately.

The native allocation is two ICONs, seventeen CONs, seven STATEs and two VARs.
The voltage-derivative VARs are not defined physical power-input channels for
the selected modes. Initial pilots recorded their unused contents, including
very large finite memory values; those pilots remain counterexamples. The
accepted probe audits the allocation but records all seven published states
and physical channels, not unused derivative memory. No raw pilot is rewritten.

## Eight independently repeated SMIB profiles

Electrical/accelerating power each have flat and three-cycle fault profiles
at 0.5/0.25 ms, ending at 0.5 s. The public two-bus case uses the existing core
GENROU machine, unchanged ESST1A and IEEEST constants, 100 MVA and zero stator
resistance. Bus 1 has a three-phase j0.2 pu fault from 0.05 to 0.10 s.
All sixteen native runs converge with unchanged constant/selector readbacks;
all eight complete raw CSV pairs match byte-for-byte. The eighteen allocated
generator/exciter/stabilizer state channels agree with live reads after the
declared twelve-significant-digit CSV serialization. Two default-runner cold
runs also reproduce the preceding generator reference exactly.

Lossless fixtures preserve every CSV byte/hash, negative history, both duplicate
interval/event boundaries, all seven stabilizer states and 35 total columns.
The unused VAR audit does not remove any published state or accepted raw row.

## Input and output checks

Recorded Pe/Pm values are replayed as controller inputs, not copied stabilizer
states or optimized waveforms. Operating-point references come from the first
recorded input sample. Every raw row samples the exact mode-3 or mode-4 power
combination within 1e-12 pu. Negative history and duplicate event sides use zero
duration; numerical storage cannot advance. Signed zero remains untouched in
the raw fixtures, while the state-invariance guard uses numerical equality.

All raw Vstab playback rows have separate fixed profile bounds. Electrical
fault maxima are 1.164e-3/3.345e-4 pu at the coarse/fine steps, below
1.3e-3/3.7e-4 bounds. Accelerating-power maxima are 1.124e-3/2.879e-4 pu,
below 1.25e-3/3.2e-4 bounds. Each fine-step maximum must be below 40 percent
of its coarse counterpart. Both output-limit directions must occur in every
native fault profile. No existing benchmark tolerance is widened.

The electrical-power flat reference has a constant native initialization/output
arithmetic residual of about 2.480e-5 pu; its local output bound is 3e-5, without
forcing a corresponding initial state offset. Accelerating-power flat replay
remains zero, with a 1e-12 bound. These are bounded Vstab play-in claims, not
seven-state coordinate or complete network trajectory acceptance.

Coupled core-machine flat/fault traces retain every native Vstab comparison row
as diagnostics. Accelerating-power coupled maxima are about 1.122e-3/4.228e-4
pu; complete generator/exciter/stabilizer state and event-post parity remains
open. Finiteness alone is not model verification.

## TODO and acceptance scope

- [x] Capture the two failing-before input-sign/cancellation tests.
- [x] Correct only mode 4's electrical-power term; preserve mode 3 and core reuse.
- [x] Generate eight exact cold pairs with allocation, input and live-state audits.
- [x] Bound every raw Vstab playback row, require step refinement and both limits.
- [x] Complete final focused/core/scoped-SMIB gates and seal tested evidence.
- [ ] Derive and verify all seven native state coordinates, broader input/base/
  resistance profiles, T5=0 semantics, remote measurements and lifecycle cases.
- [ ] Resolve coupled event parity and separately classify the typed PSS1A
  compatibility record before complete family acceptance.

No full Texas2k dynamics are run. This corrects and verifies the selected power
input branches; it does not declare every IEEEST/PSS1A profile VERIFIED.

Final gates pass 87 focused, 410 plugin-core (six existing skips) and 1,790
scoped SMIB/model/solver tests (one existing skip), with zero failures/errors.
All 53 offline probe/wording checks pass. Source reconciliation covers 1,863
Java/POM files and 26 lossless fixture parts, including the latest committed
converter voltage/event-memory changes. The private evidence set is
`2026-10-19-ieeest-power-inputs`: exact cold pairs, unused-VAR pilots,
failing-before input guards, rejected signed-zero assertion, complete playback/
coupled diagnostics, source contracts and final terminal reports are retained.


## Nonzero-resistance mode-3 follow-up

For imported round-rotor hosts, mode 3 reads machine-base terminal active
power from the most recent stator evaluation. That value and the input
reference use the same physical measurement. The existing algebraic
measurement supplies it to the annotated and typed stage evaluators.
Mode 4 retains its mechanical-minus-air-gap signal at the nominal
stator frequency.

The native mode-3 filter recurrence discriminates terminal power from
air-gap power for a nonzero-resistance machine. A separate native mode-4
probe with the same constants confirms the air-gap term. A regression
checks the input during both solver stages, changes terminal voltage to
produce independently calculated terminal MW, and verifies that reading
the measurement leaves the swing input intact.
