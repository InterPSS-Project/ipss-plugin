# Default discrete-transformer tap normalization

Core data checking now rounds a discrete transformer's controlled-side ratio
to the nearest valid position, anchored at its lower limit and bounded by its
upper limit. This is enabled by default, including when automatic tap adjustment
is disabled. Continuous controls and controls without a step size are unchanged.
Invalid nonfinite or negative grid data fails validation.

IEEE39 transformer 6–31 imports at 1.07000 and becomes 1.06875 during load-flow
data checking, matching the benchmarking results. The benchmark requires no manual override.
To preserve imported off-grid ratios explicitly:

```java
algo.getDataCheckConfig().setAutoSnapTransformerTap(false);
```

`TransformerTapDataCheckTest` covers default behavior, idempotence, both sides,
bounds, continuous controls, invalid grids, the opt-out, and EMF copy/reset.
`Ieee39TransformerTapDataCheckTest` exercises both choices through real RAW import.
The focused core tap suite passes 164 tests.

## Legacy guide-case reference

The guide-case swing-power assertions previously described unsnapped RAW taps.
The default-path test now uses benchmark reference results; its existing 0.01
MW/Mvar tolerances are unchanged. The second test explicitly opts out and retains
the historical expectations.

Input: `ipss.test.plugin.core/testData/adpter/psse/PSSE_GuideSample.raw`

SHA-256: `d39496f2efb0805f310a0e3ffdd5c250dfaa240cd354ab8c8e73a5cdeee052a4`

Native commands after `psspy.psseinit(50)`, with the input staged locally:

```python
psspy.readrawversion(0, '30', 'PSSE_GuideSample.raw')
psspy.solution_parameters_5(intgar2=50, realar6=1.e-4)
psspy.fnsl([0, 0, 0, 1, 0, 0, 99, 0])
assert psspy.solved() == 0
ierr, power = psspy.macdt2(3011, '1', 'PQ')
assert ierr == 0
```

The swing output is **258.63946533203125 MW + j103.6063461303711 Mvar**.
The 1e-4 MVA setting here is the static power-flow mismatch tolerance, separate
from the IEEE39 dynamic benchmark's 1e-6 network tolerance. A cold repeat gives
identical summary values. Local evidence is retained under
`target/guide-default-taps-native-1e4/` and its `-repeat` counterpart.

The full core run completed 2,894 tests with two frequency-expectation failures
in `TestStaticLoadModel`. The registered plugin run completed 1,700 tests with
25 skipped and five failures: two guide-case assertions resolved here, and the
three existing PSS2B/TGOV3D/WESGOVD trajectory comparisons still under investigation.
This tap change does not imply full dynamic-model regression acceptance.
