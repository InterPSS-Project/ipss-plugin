# Case 3 controller observations with prescribed voltage

The retained synchronous-machine replay reproduces supplied stabilizer output
closely when driven by reconstructed reference terminal voltage. Using its
retained local voltage history instead produces much larger discrepancies.
This supports the upstream voltage boundary as a priority; it does not prove
the complete autonomous network or every controller state correct.

Both existing replay modes were repeated with read-only observations added.
Each retains 30 machines and their exciters, governors and stabilizers over
2,003 rows through the prefault second, including two cold-history rows.
Their compressed physical output files are byte-identical to the corresponding
earlier replays, and cold snapshots are equal. No new reference state is
loaded into initialization or prescribed during integration.

The reference-voltage mode uses terminal magnitudes and phases reconstructed
from the supplied machine boundary data. It evolves the complete local
machine/controller stack. The observed stabilizer output is compared with
the supplied VOTHSG channel; that channel is not an imposed controller input.

| Prefix endpoint | Largest VOTHSG error using retained local voltage (pu) | Largest VOTHSG error using reference voltage (pu) |
| --- | ---: | ---: |
| 0.02 seconds | 0.000403093 | 0.00000750301 |
| 0.10 seconds | 0.00421920 | 0.00000750301 |
| 1.00 seconds | 0.200000 | 0.0000655481 |

The largest one-second reference-voltage residual is at 1073/1; the largest
local-voltage residual is at 5263/1. The supplied and local inputs are the
unchanged earlier paired boundary histories. These results do not compare
two newly generated autonomous network trajectories or establish an additional
acceptance threshold for stabilizer output.

Each mode also retains 60,090 device records and 1,021,530 finite scalar
observations from the named-state APIs. These include controller outputs and
storage coordinates, not that many independent differential states. An
inventory identifies 1,038 exported reference-state channels across the six
original Case 3 events. Published slot-to-coordinate comparisons remain to be
mapped explicitly; the named-state observations alone do not prove agreement
with every reference internal state.

Private evidence set `2026-10-19-full-event-and-controller-observations`
retains exact producers, frozen dependencies, both observation streams,
physical-output equivalence checks, reference inventory and all 30 stabilizer
channel comparisons. No production equation, original parameter, initial
state or acceptance limit changes. The six-case accuracy goal remains open.
