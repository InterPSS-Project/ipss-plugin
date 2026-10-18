# REECE typed-runtime terminal-input replay

These twelve immutable datasets are derived from native PSS/E 36.7 three-bus
runs, not InterPSS output. `native-coarse/fine` reuse the foundation's two
cold-paired QFLAG=2/PEFLAG=1 runs. Ten additional profiles cover voltage control,
the cascaded Q/voltage PIs, power-factor reactive lag, and positive/negative
reference excursions with active current limiting, at 0.5 and 0.25 ms.

Each Base64 file decodes through gzip to headerless UTF-8 rows with columns:

```text
time_s,P_PU,Q_PU,V1,external-Pref-step,external-Qref-step,
EC_STATE0,EC_STATE1,EC_STATE2,EC_STATE3,EC_STATE4,EC_STATE5,EC_STATE6,EC_STATE7,
native-Ipcmd,native-positive-Iqcmd,native-prepared-Pref
```

Every nonnegative-time row is retained, including duplicate event sides.
Initialization uses physical terminal P/Q/V, never fitted observed states.
Only terminal P/Q/V and external reference events drive the runtime replay.
Reference/current-limit/filter/PI outputs are not replay inputs.

Native VAR14 is an algebraic callback observation before the terminal-Q channel
is refreshed. The saturation discriminator proves its published equation using
the previous callback's terminal Q and Ipcmd headroom feedback to float precision;
using same-row Q gives a 0.04028 pu residual. Branch replay observations refresh
only that algebraic Q input, assert every dynamic state/reference is unchanged,
and restore the current endpoint. Integration still uses both endpoints. No
state timestamps or event rows are shifted, interpolated or discarded.
Native prior-Ipcmd headroom versus instantaneous algebraic headroom remains
an explicitly open convention difference; no delay is fitted into the runtime.

`ReeceBranchNativeReference` freezes decoded and both raw cold-run digests.
All timestamps and used channels repeat exactly. Two raw cold pairs differ
only in unused native VAR2 with PFFLAG=0 (uninitialized PF-angle padding);
both original files and manifests are preserved. No raw artifact is rewritten.

The native zero-time prepared-Pref callback discrepancy is explicitly unaccepted.
The replay reports that channel separately; no event sample is dropped. Native
single-precision accumulation floors can worsen when the step is halved;
convergence assertions apply to the declared downstream dynamic channels,
not every filter or integral. These profiles do not establish complete import,
all selector/limiter/lifecycle branches, coupled native fault or PowerWorld parity.

See [runtime evidence and remaining gaps](../../../../../docs/benchmarks/reece-runtime-2026-10-18.md).
