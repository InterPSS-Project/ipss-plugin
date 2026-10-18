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
