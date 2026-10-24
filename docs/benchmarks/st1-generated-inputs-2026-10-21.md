# Generated ST1 input equations

Generating direct Java input arithmetic from the existing ST1 annotations reduces
the warmed Case 1 complete-simulation median from **11.3713 to 10.8259 seconds**,
an observed **4.8%** reduction. Case 2 completes at **7.6068 seconds median**.
Case 1 remains above the eight-second objective, and the early dynamic failures
in Cases 3 and 4 remain unresolved.

## Implementation

`tools/generate_st1_input_equations.py` reads the model's existing input
annotations and the evaluator's slot layout. It emits `St1InputEquations.java`.
The annotations remain the equation definition; the generated file is not a
second handwritten model. Validate regeneration with:

```powershell
python tools/generate_st1_input_equations.py --check
```

The generated calculations preserve operand-read order, sign application,
product reduction, explicit zero terms and summation order. Inputs and parameters
remain live. A compilation-time check of the complete symbol/type/operator
signature selects each generated equation; modified expressions retain the
generic evaluator. The exact standard controller class is required for the
generated path. Existing explicit plan-rebuild requirements after structural
changes still apply.

No integration stages, local substeps, limiter equations, state coordinates,
network iterations, convergence tolerances or recording settings were changed.

## Complete-case timings

Each longer run uses **32 complete warmups and seven measured repetitions** in
one JVM, with verified operating-system affinity to one logical CPU. Every
repetition loads fresh inputs and includes preparation, initialization, startup
history, ten simulated seconds and full recording through writer closure.
The dynamic voltage-correction tolerance is **1e-5 pu**, with a **1/240-second**
step. Load-flow power-mismatch tolerance remains 1e-3 pu.

| Case / evaluator | Median total | Total range | Median preparation | Median dynamics and recording |
| --- | ---: | ---: | ---: | ---: |
| 1 / retained baseline | 11.3713 s | 10.9616–11.4362 s | 0.2500 s | 11.1316 s |
| 1 / generated inputs | 10.8259 s | 10.5570–11.0688 s | 0.2457 s | 10.6063 s |
| 2 / generated inputs | 7.6068 s | 7.4988–7.9861 s | 0.2212 s | 7.3522 s |

Phase medians need not sum to the total median. The Case 1 baseline and candidate
ran sequentially in the same session. An earlier reverse-order screening run,
with twelve warmups and five measurements, showed a 5.3% reduction but retained
more JIT activity; it is retained separately. Timing ranges overlap, so the
measured reduction is not a guarantee for every run or workstation.

Case 2's previous retained median was 7.5728 seconds from a different session.
The new run establishes continued sub-eight-second timing for all seven measured
repetitions, not a measured Case 2 speedup.

## Verification and limitations

- All 117 repetitions in the longer comparison complete under the requested
  numerical and CPU-affinity settings. Each recording contains 2,405 samples,
  including the original event-side duplicates. Case 1 retains 6,190 channels;
  Case 2 retains 4,810.
- Full binary recordings, selected-channel CSVs and channel metadata match the
  respective retained baselines byte for byte. Equality to the existing runtime
  does not establish independent-reference accuracy.
- Three new tests pass for arithmetic equivalence, live callback/read order and
  modified-expression fallback. They are registered in `CorePluginTestSuite`.
  Two existing local-response tests also pass. The existing disabled-controller
  reinitialization test fails with the same load-flow `ClassCastException` in both
  baseline and candidate; its assertion remains unchanged. The broader repository
  compatibility and system gates remain unqualified.
- Cases 3 and 4 retain identical partial recordings and fail at 0.045833 seconds.
  These runs do not provide valid complete-simulation timings.
- A clean build from the retained source archive produces 3,870 classes, all
  byte-identical to the effective validated runtime: 2,373 core, 1,474 plugin and
  23 harness classes.

The [checkpoint index](checkpoints/st1-generated-inputs-2026-10-21.json) identifies
the complete source archive, generator, regression evidence, commands, raw timing
distributions, affinity records and retained output hashes. Rebuild with:

```powershell
python tools/run_bounded.py --log target/st1-source-rebuild.log -- python tools/build_case2_source_checkpoint.py --checkpoint docs/benchmarks/checkpoints/st1-generated-inputs-2026-10-21.json --output target/st1-source-rebuild
```

The objective remains open: Case 1 still needs a further 2.83-second reduction,
and Cases 3–4 require recovery of valid dynamic trajectories before their complete
runtime can be assessed. Independent-reference accuracy remains unresolved.
