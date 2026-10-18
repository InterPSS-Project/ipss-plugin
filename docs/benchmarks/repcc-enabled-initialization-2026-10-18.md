# REPCC enabled-switching initialization follow-up

## Change and evidence boundary

The continuous-state initializer no longer rejects `MSSflag=1`. The source
diagram separates the thirteen continuous states from the discrete shunt
timers and statuses; switching enablement does not change those equilibrium
equations. The existing physical boundary remains responsible for resolving
actual devices and requiring their full nominal system-base G+jB.

Authority is the same Model Library section 27.4 frozen in the
[runtime report](repcc-native-runtime-2026-10-18.md), document SHA-256
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The continuous diagram and switching flowchart were visually rechecked.
No gain, limiter, initial state or recorded reference was adjusted.

The new discriminator fails before the fix at the enabled-switching rejection.
After the fix it compares all thirteen initial states plus voltage, reactive
and active references against ten already frozen, hash-checked native shunt
profiles: both signs, slow/fast bands and lockout, each at two timesteps.
The absolute comparison bound is 1e-7, allowing native single-precision
initialization/export roundoff. This is initialization evidence only, not
acceptance of the subsequent native lockout trajectory.

An additional coupled typed SMIB check constructs the ten physical devices
from the frozen initial status/admittance records and explicitly supplies
their nominal G+jB. At 0.5 ms and 0.25 ms it runs 30 ms, requires actual
switching, finite plant values and matched plant/switching clocks, checks the
exact cumulative Y increment, rebuilds the matrix and checks persistence,
and confirms repeated accepted-endpoint dispatch is idempotent.
This is a coupled physical regression, not independent transient parity.

## Scope still open

- Standard-file enabled-MSS import stays rejected until per-device input G
  is retained and supplied by the import path. Typed callers must supply it.
- Exact startup/zero-time switching sampling, independently compared coupled
  switching trajectories, discharge-lockout parity and lifecycle remain open.
- Existing disabled-output/proportional-only restrictions are unchanged.
- Runtime family presence remains 26/27; HPLNTD remains unimplemented.
- No full Texas2k dynamics are run.

The existing source-correct discharge lockout and all previous native profile
tolerances are unchanged. This follow-up does not confer complete REPCC
verification.

## Validation

On plugin baseline `a6b69e592`, the final focused gate has 29 tests, the
plugin-core gate has 344 tests (six optional skips), and the selected
SMIB/model/solver gate has 1,406 tests (one existing diagnostic skip).
All three finish with zero failures/errors. The separate five-test
before-fix run retains the expected enabled-initialization error.

The isolated build uses the same frozen companion core `38e269ecf` as the
runtime slice; the shared dependency installation is unchanged. Sources,
reports, logs and the prior reference archive are hash checked before commit.
Evidence is retained in the private archive
`2026-10-18-repcc-enabled-initialization`; earlier archives are not rewritten.
