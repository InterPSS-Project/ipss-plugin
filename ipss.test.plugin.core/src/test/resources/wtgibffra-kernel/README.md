# WTGIBFFRA kernel-only native references

PSS/E 36.7, public three-bus RAW, REGCA1/REECD/WTGIBFFRA and PLBVF1 frequency
playback. These are partial recorded-input kernel references, not import,
coupled-network, automatic-recovery or complete-model acceptance.

Each `.b64` is MIME Base64 of deterministic gzip (mtime=0) of UTF-8 text.
Decoding is lossless; no input resampling, rounding or fitting is performed.
`WtgibffraNativeReference` checks the decoded SHA-256 and defines all 45 declared
constants, timesteps, row counts, native transitions and raw CSV identities.

- `inputs`: every nonnegative-time raw row, columns P_PU,FDEV3 (frequency
  deviation). Replay uses `1+FDEV3` and predictor/corrector endpoint inputs.
- `checkpoints`: zero-based input-row index, unmodified native time, all four
  IB_STATEs, then all seventeen IB_VARs. Selection is every nominal 5 ms, final
  sample, and four rows on each side of every native mode change.
- All inputs use nominal row*step for InterPSS integration; native float time
  remains in checkpoints. Negative initialization history remains in raw data.
  There are no duplicate nonnegative-time rows in these profiles.

Each of the seven profiles was independently cold-run twice with identical raw
CSV bytes. Repository policy keeps simulator-driving Python programs private.
The exact probe is retained in the local sealed archive
`ipss.test.plugin.core/testData/private/verification-artifacts/2026-10-18-wtgibffra-foundation-final/psse_wtgibffra_native_probe.py`.
Reproduce from the repository root with a licensed native PSS/E 36.7 Python runtime
and fresh output tags (existing directories are never overwritten):

```text
python <archive>/psse_wtgibffra_native_probe.py kernel-long-coarse-a long-recovery .0005
python <archive>/psse_wtgibffra_native_probe.py kernel-long-fine-a long-recovery .00025
```

Repeat with suffix `b`. Other coarse-step modes are `zero-band-dip`,
`zero-peak`, `persistent`, `below-power`, and `invalid-band`; the retained tags
use `kernel-below` for `below-power`. The probe writes relative staged inputs,
all channels, native log and SHA-256 manifest under `target/wtgibffra-native-TAG`.
Its generic manifest says diagnostic; the slice audit/report defines precisely
which profiles/channels received partial kernel acceptance.

Ignored/disabled data and nonpositive cooldown probes remain diagnostic only.
Native automatic/too-short recovery differs from the published energy balance.
Do not use those native outputs to fit the published kernel.

## Coupled governed-SMIB extension

`coupled-coarse.b64` and `coupled-fine.b64` retain full native channel tokens
at selected checkpoints, decoded and hashed by `WtgibffraCoupledReference`.
The header adds nominal tick and explicit post-event side before the unmodified
native CSV columns. Tick counts advance only for nonduplicate timestamps.
Negative initialization history remains in the sealed raw files. Selection
keeps every 5 ms, the last row, all post-event duplicates and four rows either
side of every WTGIBFFRA mode transition. No interpolation is used.

Protocol: add a 100 MW/20 Mvar load at Bus 2, GENCLS H=3/D=0 and TGOV1
R=.05/T1=.1/Vmax=1.5/Vmin=0/T2=0/T3=.1/Dt=0 at Bus 3. Apply
`increment_gref(3,'1',-.01)` at 0.05 s. Explicitly set the global native
bus-frequency measurement time constant to 0.05 s, matching InterPSS. WTGIBFFRA
uses remote Bus 3 and 1.2 s user recovery. The existing public converter and
REECD parameters are unchanged. Each timestep has two byte-identical cold runs.

All four inertia STATEs, seventeen VARs, seven REECD STATEs, generator/governor
STATEs and network channels remain in checkpoint data. Tests explicitly exclude
inactive REECD PIV/Vcomp storage from parity, preserve raw reset discrepancies,
and report synchronous-machine speed diagnostically rather than claiming full
GENCLS/governor conformance. Native REECD VAR6 is the external auxiliary input;
the inertia contribution is the documented sum of its first three STATEs.

The final runnable native probe, raw RAW/DYR, logs, output and manifests are
retained under the private native-import slice archive documented in the report.
Simulator-driving Python remains private under repository policy.
