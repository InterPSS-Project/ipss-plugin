# REECE local PI reference scope

Native PSS/E 36.7 standard `REECE`, eight ICONs and 79 CONs. These two cold-paired
profiles check only QFLAG=2 and PEFLAG=1 local PI components. They do not prove
complete REECE1 import, the remaining controller blocks or coupled SMIB parity.

Each file is MIME Base64 of deterministic gzip (mtime=0), decoded to original
UTF-8 numeric tokens and SHA-256 checked by `ReeceLocalPiNativeReference`.
There is no input resampling or curve fitting.

- Inputs: native filtered Pe (EC_STATE1), terminal Q, prepared/ramped Pref
  (EC_VAR5), external Qref step (EC_VAR16), Iqmin, Iqmax, voltage-dip flag.
- Checkpoints: input row, original native float time, reactive PI STATE2,
  active PI STATE7, positive reactive-current command (EC_VAR14).
- Initialization uses solved terminal Q/V and P, not PI state fitting.
- All nonnegative-time input rows are retained. Negative history remains raw.
  Post-event duplicate rows have explicit indices in the profile definition.
  Checkpoints retain each 5 ms, the final row and four rows either side of the
  reference event. Both event sides are kept; duplicate rows do not advance time.

The external Pref step EC_VAR15 is not the prepared ramp output EC_VAR5. The
fine-step case exercises this distinction on the first event sample.
Current limits are replayed as block-boundary inputs; tests require them to be
inactive throughout these profiles, so they cannot fit the PI trajectories.

The exact probe, public input RAW/DYR, native logs/output/manifests and cold-pair
audit are kept in the local private slice archive referenced by the report.
Simulator-driving Python remains private under repository policy.
