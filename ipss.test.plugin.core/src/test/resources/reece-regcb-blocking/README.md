# Native REGCB/REECE hardware-blocking boundary

These gzip/base64 resources preserve the complete raw UTF-8 CSV, including
negative initialization history and duplicate event sides. The reference Java
class checks each raw SHA-256 before use and records the cold-repeat digest.

PSS/E 36.7, standard REGCB1 (two ICONs/seven CONs), standard REECE (eight
ICONs/79 CONs), three-bus 100-MVA SMIB. REGCB normal PQ priority is 1, matching
REECE; REECE fault priority remains explicitly 0. A Bus 1 three-phase fault
of j0.01 pu starts at 0.1 s and clears at 0.15 s; end time is 0.4 s.
Steps are 0.5 and 0.25 ms, with 805 and 1,605 raw samples and 46 channels.
The immutable input DYR digest is recorded in the reference class.

DSMDL.DLL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.
ModelLibrary.pdf SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.

All four formal cold runs pass the existing log/CSV quality audit. The fine
cold repeat differs only in inactive EC_VAR2 (PF-angle storage with PFFlag=0);
both original hashes are retained, not silently canonicalized. Scripts,
RAW/DYR, logs, manifests and both cold exports are retained in the private
2026-10-18-reece-native-import verification archive.

The regression proves only the blocking boundary: when all four native current
bounds EC_VAR9..12 are zero, REGC_VAR6..7 and P/Q are zero even though lag and
internal-voltage states are nonzero. InterPSS must cancel Norton admittance,
not erase those states. Zero commands alone are not hardware blocking.
This is not full coupled-trajectory conformance. The separate Bus 2 j0.001-pu
campaign logged network nonconvergence and is explicitly rejected as evidence.
