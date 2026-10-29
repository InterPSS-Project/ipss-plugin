# REGCC reference fixtures

These are lossless copies of independently generated three-bus references.
Each profile retains its RAW input, exact DYR input and complete CSV. Decode
base64; decompress gzip only for CSV files. `profiles.json` records the original
CSV SHA-256 and the explicit accuracy classification.

Fifteen profiles were regenerated cold twice. Eleven have bounded trajectory
gates; four remain diagnostics (`limit-q`, two `minimum` grids and `electrical`).
No startup row, event side or recorded channel is removed. The electrical
profile includes upstream states and variables; other profiles have 27 channels.

Reproduce from the repository root using the reference probe:

```text
python tools/regcc_reference_probe.py --batch <unique-name> --profile fault --step .0005 --repeat a
python tools/regcc_reference_probe.py --batch <unique-name> --profile fault --step .0005 --repeat b
```

The installed reference environment is required. Use the bounded launcher as
specified by the repository instructions. Generate the full declared matrix
before running `tools/check_regcc_artifacts.py --root target/<unique-name>`.
Its optional `--stage` flag updates these encoded copies only after integrity,
sample/channel counts and cold-pair identity pass.

`settings.json` controls the registered comparison. Enabling
`requireAllTrajectories` also asserts the diagnostic ceilings and currently
fails. The complete model is not labeled verified. See
`docs/benchmarks/regcc-model-2026-10-28.md` for errors and remaining work.
