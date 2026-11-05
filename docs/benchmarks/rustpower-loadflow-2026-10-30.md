# RustPower AC load-flow comparison — 2026-10-30

This is the original baseline. The subsequent maintained solver changes and
matched measurements are in the [optimization report](rustpower-optimization-worklog-2026-10-30.md).

RustPower 0.5.2's Python KLU solver was faster than the cached InterPSS load-flow
API on all three cases in RustPower's published performance table. Both solved
the same nodal equations from compensated flat starts in **4, 4, and 6 Newton
iterations**, respectively. These results measure the existing APIs; they do
not identify the time attributable to programming language alone.

## Local measurements

Windows 11, Intel Core i7-13700KF, Temurin Java 21.0.6, Python 3.12.4.
InterPSS core **1.4.6**, plugin **1.4.8**, Java KLU **2.0.2**. The current
MATPOWER parser and network builder were compiled into an isolated benchmark
directory; the solver runtime used the versioned Maven artifacts. The plugin
checkout was `717139a6ac2cebbd7825545870e6bd8066154a09`. Unrelated working-tree
changes were present and are recorded in the environment receipt.

Arithmetic means, milliseconds per solve, including all retained samples:

| Case | Iterations | RustPower Python / native KLU | InterPSS / Java KLU | InterPSS / CSJ | Java KLU time / RustPower time |
| --- | ---: | ---: | ---: | ---: | ---: |
| IEEE 39 | 4 | 0.015855 | 0.507309 | 0.348104 | 32.00 |
| IEEE 118 | 4 | 0.031104 | 0.809351 | 0.795719 | 26.02 |
| PEGASE 9241 | 6 | 12.798971 | 205.834828 | 266.968359 | 16.08 |

Median and 95th percentile, milliseconds:

| Case | RustPower median / p95 | InterPSS Java KLU median / p95 | InterPSS CSJ median / p95 |
| --- | ---: | ---: | ---: |
| IEEE 39 | 0.012700 / 0.023700 | 0.455450 / 0.789511 | 0.317000 / 0.516320 |
| IEEE 118 | 0.027700 / 0.046305 | 0.745701 / 1.166015 | 0.753150 / 1.013169 |
| PEGASE 9241 | 12.528100 / 14.369540 | 198.668500 / 251.085161 | 245.179000 / 391.069400 |

Three fresh processes per implementation. Each process used 300 warm-up and
1,000 timed solves for each small case, and 30 warm-up and 60 timed solves for
PEGASE. There are **3,000 small-case samples and 180 PEGASE samples per
implementation**, totaling 18,540 timed solves. Java heaps were bounded by
`-Xms256m -Xmx2g`. Processes ran sequentially. CPU affinity, power policy, and
other workstation activity were not controlled. Process-level means are in
`summary.json`; for example, RustPower's IEEE 39 means ranged from 0.01248 to
0.02215 ms. No outliers were removed.

## Published results

The [upstream performance table](https://github.com/chengts95/rustpower/blob/3eedbb13485a58c586b00fc07cff4c969a945d0c/README.md#performance-comparison)
reports the following RustPower timings in milliseconds:

| Case | Published Windows Python KLU | Published Windows Rust KLU | Published Linux Python KLU | Published Linux Rust KLU |
| --- | ---: | ---: | ---: | ---: |
| IEEE 39 | 0.044 | 0.023 | 0.021 | 0.014 |
| IEEE 118 | 0.080 | 0.059 | 0.040 | 0.031 |
| PEGASE 9241 | 21.0 | 20.0 | 11.53 | 11.36 |

The published hosts were an i7-10700K on Windows and a Core Ultra 7 288V on
Linux. Those values are historical context, not a speed ratio against this
workstation. The local run reproduced the **Python NewtonSolver path**, not
the Rust-native executable or the entire upstream multi-package table.
Although the inspected repository manifest declares version 0.5.4, its README
table uses 0.5.2; the local wheel was deliberately pinned to **0.5.2**, with
its `klu` feature verified.

## Protocol and correctness

The harness follows the RustPower numerical path in
[`bench_py_pp_numba.py`](https://github.com/chengts95/rustpower/blob/3eedbb13485a58c586b00fc07cff4c969a945d0c/scripts/bench_py_pp_numba.py):
cached topology and solver context, compensated flat voltages before every
solve, maximum 20 iterations, and a numerical tolerance of **1e-6 pu**.
RustPower timing includes `set_v_init()`, `solve()`, and `get_scalc()`.
InterPSS timing includes the bus-voltage reset and `loadflow(false)` on the
same initialized algorithm and network, including its generator-Q allocation
and public API bookkeeping. Import, compilation, initial setup, output, and
validation are outside the timers. InterPSS message logging was disabled for
timed solves. Q limits, tap controls, damping, variable-update limits, and
voltage-dependent load adjustments were disabled. This is a numerical
load-flow comparison, not a controller-workflow benchmark.

The shared cases came from **pandapower 3.5.4** (`case39`, `case118`, and
`case9241pegase`). A pandapower solve with Numba and
`lightsim2grid=False` supplied the reference voltages and original Ybus/Sbus.
The reference tolerance was 1e-6 MVA, equivalent to 1e-8 pu on these 100-MVA
cases. This follows the upstream reference preparation; its numerical solver
column uses 1e-6 pu. Reference angles were rotated to a zero-degree slack.

The export preserves the nodal model: all fixed branch-end admittances were
moved to equivalent bus shunts using the fixed tap ratios. This retains the
transformer equivalent conductance in IEEE 118. Ybus was independently rebuilt
after that transformation; the maximum element change across the cases was
7.28e-12 pu. Missing machine MVA ratings were filled with the system base,
which does not alter static load-flow equations. Unity taps were normalized
to MATPOWER's line convention; InterPSS's data checker restored transformer
classification where terminal base voltages differed.

Each timed call was required to converge with a positive iteration count.
Final voltages from every process/backend were checked against the original
pandapower Ybus/Sbus, independently of InterPSS's internal convergence flag:

- Largest InterPSS P/Q equation residual: **5.32e-8 pu**.
- Largest InterPSS complex-voltage difference from the reference: **4.53e-9 pu**.
- Largest RustPower P/Q equation residual: **3.32e-11 pu**.
- Every implementation used the same iteration counts, 4 / 4 / 6.

An optional LightSim2Grid 1.0.0 Windows wheel crashed during native import in
the initial environment. It was removed from the isolated environment before
measurements. There are no local LightSim2Grid timings. The diagnostic fault
trace was retained separately. Pandapower was used for model preparation and
accuracy checks; its execution time and memory use were not benchmarked.

## Reproduction and retained evidence

From the repository root, create a fresh environment and retain a fresh output
directory for each run:

```powershell
python -m venv ipss.test.plugin.core/target/rustpower-venv
ipss.test.plugin.core/target/rustpower-venv/Scripts/python.exe -m pip install rustpower==0.5.2 pandapower==3.5.4 numpy==2.4.6 scipy==1.18.1 numba==0.68.1
python tools/run_bounded.py --log ipss.test.plugin.core/target/rustpower-classpath-new.log -- mvn -o -pl ipss.test.plugin.core dependency:build-classpath "-Dmdep.outputFile=target/rustpower-classpath-new.txt"
python tools/run_bounded.py --log ipss.test.plugin.core/target/rustpower-new.log -- ipss.test.plugin.core/target/rustpower-venv/Scripts/python.exe -u tools/rustpower_benchmark.py --classpath ipss.test.plugin.core/target/rustpower-classpath-new.txt --output ipss.test.plugin.core/target/rustpower-new
```

The benchmark sources are `tools/rustpower_benchmark.py` and
`tools/benchmarks/RustPowerLoadflowBenchmark.java`. The original run's
environment remains under `target/rustpower-benchmark-20261030/venv`.

Retained evidence is under
`ipss.test.plugin.core/target/rustpower-benchmark-20261030/` (about 7 MiB):

- `summary.json`, all nine raw timing CSVs, and all eighteen InterPSS voltage CSVs.
- `environment.json`, including exact runtime JAR SHA-256 hashes, package
  versions, source commit, and working-tree status; `requirements-freeze.txt`.
- The three shared MATPOWER files, original Ybus matrices, and reference arrays.
- `external-validation-*.json`, `interpss-validation.json`, and compiled classes.
- Pinned upstream benchmark sources and the evidence checksum manifests.

The bounded log is
`ipss.test.plugin.core/target/rustpower-benchmark-20261030.log`; its status
receipt records completion with exit code 0. The failed optional-library import
trace is `ipss.test.plugin.core/target/rustpower-smoke-debug-20261030.log`.
