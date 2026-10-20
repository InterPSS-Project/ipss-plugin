# Approved excitation-limiter coverage

Generated from `WeccApprovedDynamicModelCatalog.limiters()` and the visually reviewed May 2026 source PDF, page 2. Source SHA-256: `b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.

Nine approved source rows include eight native mappings and one explicitly unconverted model. Five rows currently have bounded loadable runtime paths; this is not complete equation or trajectory verification. A standalone kernel does not increase the loadable count. BASOEL2 import explicitly selects POWERWORLD_DOCUMENTED behavior, not emulation of a particular reference-tool version.

| Source | Native identifier | PowerWorld | Category | Runtime path | Notes |
|---|---|---|---|---|---|
| OEL1 | Not converted (277) | OEL1 | OVER_EXCITATION_LIMITER | missing | Non-IEEE source model; do not invent a native identifier. |
| OEL2C | OEL2C | OEL2C | OVER_EXCITATION_LIMITER | loadable, not fully verified | Runtime path present; complete verification is separate. |
| OEL3C | OEL3C | OEL3C | OVER_EXCITATION_LIMITER | loadable, not fully verified | Bounded AC1C summation-port runtime; complete verification is separate. |
| OEL4C | OEL4C | OEL4C | OVER_EXCITATION_LIMITER | missing | Bounded typed AC1C summation runtime exists; standard import and independent coupled acceptance remain open. |
| OEL5C | OEL5CU1 | OEL5C | OVER_EXCITATION_LIMITER | missing | Preserve the source wrapper identifier; no inferred alias. |
| BASOEL2 | BASOEL2 | BASOEL2 | OVER_EXCITATION_LIMITER | loadable, not fully verified | Exact 3-selector/12-constant import into bounded AC1C summation runtime; POWERWORLD_DOCUMENTED policy; full trajectory acceptance remains open. |
| UEL1 | UEL1 | uel1 | UNDER_EXCITATION_LIMITER | loadable, not fully verified | Runtime path present; complete verification is separate. |
| UEL2 | UEL2 | uel2 | UNDER_EXCITATION_LIMITER | missing | Not interchangeable with UEL2C. |
| UEL2C | UEL2C | UEL2C | UNDER_EXCITATION_LIMITER | loadable, not fully verified | Runtime path present; complete verification is separate. |
