# Svedala CGMES package dependency repair

Svedala is a static CGMES power-flow comparison case, separate from the Texas2k
dynamic benchmarks. Its load flow converged, but the test imported an incomplete
package: four grid profiles without their declared shared Common Data file.

The missing file contains the base-voltage definitions. Without it, some 400 kV
and 11 kV buses received a 100 kV fallback base. Thirty energized low-voltage
buses were then excluded as apparently dead, and the incomplete electrical
model produced excessive voltage differences.

## Fix

The Svedala test now loads its existing equipment, topology, steady-state and
solved-state profiles together with the shared Common Data and both existing
border files. This follows the
[upstream assembly instructions](https://github.com/entsoe/relicapgrid#how-to-assemble-file-packages-for-import).
The four existing grid profiles and supplied solved-state values are unchanged.

The added
[Common Data file](https://github.com/entsoe/relicapgrid/blob/5fee55c758f27fc37412f95cd3f4a86bdf6a4645/Instance/commonData/Grid/cimxml/Grid_CommonData_CGM-CD.xml)
is pinned to upstream commit `5fee55c758f27fc37412f95cd3f4a86bdf6a4645`.
Its model identifier, `1b65ada7-39ca-42b8-a278-39c457ed82c3`, matches the
dependency declared by the existing Svedala equipment profile. Attribution,
source location and the upstream license accompany the unmodified file.

Before solving, the test checks that all declared base-voltage references
resolve and verifies representative formerly misbased 400 kV and 11 kV buses.
The original dependency repair retained the 0.02 pu voltage tolerance, 85%
voltage acceptance floor and angle requirements. The subsequent tolerance change
below tightens the Svedala default to 0.006 pu. No production Java or solver
configuration changed.

## Results

| Measure | Four-profile package | Complete package |
| --- | ---: | ---: |
| Compared buses | 86 | 116 |
| Voltages within 0.02 pu | 68 / 86 (79.07%) | 116 / 116 (100%) |
| Excluded dead buses | 32 | 2 |
| Worst absolute voltage error | 0.144028 pu | 0.005757 pu |
| Worst physical voltage error | 14.402803 kV | 0.336492 kV |

The per-unit bases differ between columns; the physical kV error is included
to show that the repair improves the electrical solution as well as its
normalization. These two worst-error measures can occur at different buses.

A controlled diagnostic adding only Common Data already produces the corrected
116-bus result. Adding both border dependencies produces exactly the same
per-bus voltages. This isolates the missing shared definitions as the cause;
the permanent test uses the complete package.

The full `CGMESCasP4AclfSmokeStubTest` class passes: **21 tests, zero failures,
errors or skips**. Svedala reports 116 voltage-and-angle matches, zero missing
buses and two dead buses. The same pure Java runtime and comparison settings
were used throughout the diagnostics.

## Scope and evidence

The [repair index](checkpoints/svedala-common-data-fix-2026-10-23.json) pins
baseline and corrected per-bus observations, bounded logs, commands, test XML,
input hashes, dependency provenance and the test change. It also records that
the production source inventories match the preceding full validation.

This change resolves the Svedala failure from that full run. The other two
previously failing gates—8,387-bus PQ convergence and Case 1 no-event voltage
drift—are outside this repair. No milestone commit or merge was created. The
complete reactors were not rerun for this test-package-only change; the 21-case
CGMES comparison class was rerun in full.

## Follow-up: 0.006 pu voltage tolerance

At the user's request, the ReliCap Svedala test's default voltage comparison
tolerance is now **0.006 pu**, reduced from 0.02 pu. Other cases, the 85% match
floor, angle requirements and numerical solver settings retain their existing
values. The original repair evidence index above remains a historical record.

The complete `CGMESCasP4AclfSmokeStubTest` class was rerun with the tightened
default: **21 tests passed, zero failures, errors or skips**. All 116 compared
Svedala buses satisfy 0.006 pu; the previously measured worst error is
0.005757389 pu. The bounded log is
`target/svedala-tolerance-006-20261023.log`, with fresh test XML under
`.verification-build/svedala-tolerance-006-20261023/ipss.test.plugin.core/surefire-reports/`.
