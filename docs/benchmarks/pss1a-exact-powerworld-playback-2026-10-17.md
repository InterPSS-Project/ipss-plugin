# Exact typed PSS1A PowerWorld playback

## Scope and source contract

This slice adds exact-model PowerWorld **recorded-speed playback** evidence for
the existing InterPSS PSS1A engine. It does not replace its earlier equivalent
IEEEST comparison, prove a native PTI PSS1A schema, or accept the complete SMIB
network trajectory. No production equations, gains, limits or initial states
were changed to fit a reference.

The [PowerWorld PSS1A documentation](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS1A.htm)
and its official diagram specify the local speed path as a T6 transducer,
Ks/T5 washout, second-order denominator `1+A1*s+A2*s^2`, two lead-lags, output
limits and voltage gate. Diagram order, rather than loose parameter labels,
is the source contract. The archived SVG identifies the consulted diagram:
SHA-256 `b42954d61c5fe4746829b2cabd05ab0b8b91a8a76bac9723b2f6813034658eda`.

PowerWorld Education/Evaluation 24, file/product version `24.2026.9.3`,
executable identity is 186,878,264 bytes,
SHA-256 `06e5b23f5511440efa682000b63114f88c1dc919264ccfcc30cbca9adadcac0c`.
The current executable is not substituted for the older executable provenance
attached to historical references.

An initial direct fourteen-value PSS1A DYR import was **rejected by PowerWorld**:
incorrect parameter count, model not read. Its generic zero stabilizer columns
are not a reference. The successful path loads the existing GENROU/ESST1A base
DYR and creates `Stabilizer_PSS1A` explicitly through documented typed AUX fields.
The fourteen controller values are:

```
Ics A1    A2     T1  T2   T3  T4   T5 T6   Ks Lsmax Lsmin Vcu Vcl
1   .061 .0017  .30 .03  .30 .03  5  .020 5  .05   -.05  0   0
```

Automatic correction is disabled. Exact model type and all sixteen exported
fields (including bus and ID) agree with those declared values before and after
initialization in all four successful runs. This typed construction must not
be presented as validation of the existing fourteen-value DYR reader dialect.

## Reproducibility and event contract

The two-bus public SMIB uses its unmodified RAW and GENROU/ESST1A DYR. Bus 1 has
a three-phase `j0.2` pu fault from 0.05 to 0.10 s (three cycles at 60 Hz).
Runs finish at 1 s, with 0.5 and 0.25 ms steps. Both profiles are cold-repeated;
all 25 exported channels and complete raw CSV bytes match within each pair.
An additional cold replay using the frozen runner and specification reproduces
the original 0.5 ms CSV byte-for-byte.

- 0.5 ms: 2,003 raw rows; raw SHA-256
  `c69ee8e3d3ebe6223c20ac8d8de27f9768db13f71ffe6bb049b4ea092b30e470`.
- 0.25 ms: 4,003 raw rows; raw SHA-256
  `d7dd1f253c67b6f7ab23ca144ca865c7517f4c9a5973e56f4609314fd29d5bbd`.

Raw byte hashes above intentionally differ from the runner's LF-canonical text
hashes. The independent audit checks both contracts, input/runner/spec hashes,
successful initialization, completed progress markers, finite CSV width/values,
and actual named stabilizer-state columns. Stalled parameter-export diagnostics
are preserved as failures, not silently reused as references.

The first duplicate at each event time is event-pre. The two additional
instantaneous event-post rows remain in immutable raw output; the InterPSS
endpoint recorder does not supply corresponding instantaneous observations.
All regular endpoints, including event-pre samples, are compared offline.
Public tests score all seven controller channels at 201 fixed 5 ms checkpoints,
including the event times; they replay every recorded speed sample, not an
optimized or sparse fitted input.

## State-coordinate diagnosis and result

The CML registry owns the initialized blocks; the model's Java block fields can
remain null. The first diagnostic recorder failed by reading those fields.
That failure was corrected in the observation code, not in the controller.

InterPSS stores `y` and `ydot` in the second-order block. PowerWorld's published
"Filter 1" uses the cascade coordinate `z = ydot + (A1/A2)*y`, for which
`zdot=(u-y)/A2` and `ydot=z-(A1/A2)*y`. Comparing raw `ydot` with `z` falsely
suggested a roughly 0.1 state error. The derived observation transform removes
that coordinate mismatch without changing the integration realization.

The audit reads the second state reflectively **only in test code**, because
the current CML API has no public second-state accessor. Existing named-state
keys remain unchanged. A production semantic second-state observation API is
still a separate follow-up; this test does not introduce reflection into runtime
model evaluation.

Across all regular endpoints, exact recorded-speed playback gives maximum
Vstab errors of **4.2504e-6 pu at 0.5 ms** and **8.5727e-7 pu at 0.25 ms**.
Every controller channel improves under step halving. At the permanent 5 ms
checkpoints the corresponding maxima are 4.2423e-6 and 8.5651e-7 pu. Independent
bounds are channel-specific; the test also requires a reduction by more than
40 percent in every channel, immutable input/checkpoint fingerprints with
negative mutation guards, and non-mutating repeated state observations.

The complete network comparison remains a **diagnostic, not accepted parity**:
Bus 1 maximum voltage error is approximately 3.265e-4 pu at 0.5 ms and
3.280e-4 pu at 0.25 ms; maximum Q error is 0.2201 and 0.2212 Mvar. These do not
shrink under step halving. Exact input playback localizes much of the larger
controller error to different network/machine inputs, but does not identify
the remaining network discrepancy's root cause. No wide new network tolerance
is introduced to declare it solved.

## Regression and evidence

`Pss1aPowerWorldPlaybackConformanceTest` is explicitly registered in the core
suite and selected by `DynamicModelSmibTestSuite`. The generated stabilizer
inventory retains the equivalent primary link and separately discloses this
supplementary exact typed playback evidence and its limitations.

The final selected SMIB/model regression passes **1,160 tests, zero failures or
errors, one existing opt-in REGCA1 diagnostic skipped**. The plugin-core unit
regression passes **130 tests, zero failures or errors, six optional skips**
(KlusolveX native checks and AUX fixtures). The final 301 SMIB/module reports
are counted from fresh Surefire XML, not inferred from a quiet Maven exit.
All scoped test/data/inventory files match the tested source byte-for-byte.

Run IDs are `pss1a-pw-playback-smib-public-registry-20261017` and
`pss1a-pw-playback-unit-20261017`, with `extended-tests,isolated-verification`,
the core version override below, and selection
`DynamicModelSmibTestSuite,DynamicModelCatalogTest` for the first run.
The unit run selects `ipss.plugin.core` with `-am`. An earlier source-copy
mistake produced an empty focused selection; that run is preserved as a
diagnostic and is not counted as PSS1A verification. The corrected focused
selection and all subsequent complete SMIB runs execute the two new tests.

The ignored private `2026-10-17-pss1a-exact-playback` archive preserves successful
and failed runs, source diagrams, frozen scripts/specs, raw trajectories,
independent identity/parameter/hash audit, four per-channel comparison reports,
source snapshots and fresh test reports. Its **2,656 evidence files** are sealed
and hash-verified with a SHA-256 inventory; raw evidence is not force-added to Git.
Verification uses the separate existing lifecycle checkout and isolated
`1.4.3-regfma-native` core dependency. Concurrent renewable integration edits in
the shared checkout are excluded, not overwritten. Full Texas2k/WECC/IEEE39
dynamic simulations are neither required nor run for this slice.

## Remaining acceptance work

- Resolve complete-network boundary differences and instantaneous event-post
  observations before claiming full exact-model SMIB agreement.
- Independently activate input selectors, upper/lower limits and voltage gates;
  the speed profile does not prove those inactive branches.
- Audit native PSS1A input dialect separately; typed AUX creation does not
  establish native PTI parameter cardinality or source-list aliases.
- Provide a supported semantic CML second-state observation API if required
  by production instability diagnostics.
- Continue the renewable evidence inventory and the other model/profile gaps
  in the development plan. The overall coverage goal is incomplete.
