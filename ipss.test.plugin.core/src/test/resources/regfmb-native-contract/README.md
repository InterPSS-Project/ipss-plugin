# REGFMB1U native contract references

Scope: native 36.7 data/initialization/algebraic-boundary evidence, not an
implemented or loadable dynamic model. The approved-list REGFMB1 name is not
silently made an alias. Exact installed syntax is USRMDL REGFMB1U with wrapper
101 1 6 25 14 17; source impedance comes from machine-base RAW ZSOURCE.

Four gzip/base64 resources preserve complete raw UTF-8 CSVs (41 channels plus
time): flat and three-cycle fault, each at 0.5 and 0.25 ms. There are 605/1,205
raw samples, including two negative-history and two duplicate-event-side rows.
The fault is Bus 2, j1 pu, from 0.05 to 0.10 s; stop time is 0.30 s. Machine
base is 50 MVA, system base 100 MVA, source impedance 0.015+j0.23 pu.

Every reference has two cold native runs with byte-identical raw CSV. The Java
reference class checks its decoded raw digest and records its input DYR digest.
All eight runs pass the existing PsseRunQuality log/CSV audit. Scripts, exact
RAW/DYR, actual native CON/ICON values, manifests, logs and both cold exports
are retained in the private 2026-10-18-regfmb-native-contract archive.

ModelLibrary.pdf SHA-256:
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
DSMDL.DLL SHA-256:
`c77448250fb965bc2d397f88993855cfb792345b0a86a4bc78ad90f66540ebb6`.

The complete 14-state VSM/PLL dynamics, selectors, plant-input routing, active
limits and lifecycle remain to be implemented and independently verified.
Native moving-bound tests consume native filtered measurements explicitly:
they verify those algebraic equations, not full-controller replay or conformance.
