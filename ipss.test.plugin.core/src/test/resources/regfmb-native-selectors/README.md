# REGFMB1U native selector and PLL references

Eleven gzip/base64 resources preserve complete raw native 36.7 CSVs, each with
two cold runs whose CSV bytes are identical. Java checks decoded SHA-256 and
records input DYR digests and selectors. These are 22 formal new native runs;
the first unexcited PLL-fault probe is retained separately, not substituted.

Nine datasets cover current-droop voltage input, power-droop voltage input,
and storage with PLL-selected frequency feedback/P-priority. Each has a 0.5-ms
flat and two-step fault profiles (0.5/0.25 ms). Fault: Bus 2 j1 pu, 0.05-0.10 s,
stop 0.30 s. They support fourteen-state terminal-input replay, not coupled
device acceptance. Raw history and duplicate event sides remain intact.

Two excited-PLL datasets apply delta-speed reference +0.001 pu at 0.025 s,
then Bus 1 j0.001-pu fault at 0.05 s, clearing 0.10 s. Their PLL-only
discriminator proves low voltage removes proportional PI input while the
integral/angle freeze. Native integration retains one history contribution on
the first frozen endpoint. They are not full-controller replay acceptance.

Profiles retain 14 STATEs, 17 VARs, three voltage/angle pairs and converter
angle/P/Q/speed deviation. Machine/system bases are 50/100 MVA, RAW ZSOURCE
0.015+j0.23 pu. All 25 constants and source hashes match regfmb-native-contract.
All runs pass PsseRunQuality. The private 2026-10-18-regfmb-kernel archive retains
scripts, RAW/DYR, actual CON/ICON values, logs, manifests and both cold exports.
Network adapter, exact-wrapper parsing, plant routing, active-limit matrix and
coupled flat/fault/trip acceptance remain open.
