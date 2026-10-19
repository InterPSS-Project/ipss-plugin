# Case 3 unmodeled-generation diagnostic

Changing the two unmodeled generators from negative current-P/impedance-Q
loads to negative constant-P/Q loads is not supported as a correction by this
diagnostic. At 0.05 seconds, its immediate voltage effect at bus 3054 is
−0.0000798 pu, while the existing simulation-minus-reference error is
−0.0041141 pu. The change moves that voltage farther from the supplied result.
No production equation, input, state, or acceptance limit was changed.

## Source preparation remains unresolved

The original Cases 3–6 contain active units 5045/1 and 7099/2 with wind
controller records but no generator/converter model. Neither unit appears in
the supplied netting instructions. The current runtime converts their solved
generation to negative loads using the confirmed current-P/impedance-Q
profile. Case 3 contributions are:

| Unit | Active generation (MW) | Reactive generation (Mvar) |
| --- | ---: | ---: |
| 5045/1 | 34.514984 | 29.937023 |
| 7099/2 | 13.607986 | −11.802962 |

The retained reference operating manual states that an active generator
without a generator model invalidates initialization and prevents a subsequent
dynamic run. It does not establish the preparation used for these supplied
outputs. Whether these units were netted, disconnected, or given additional
models remains an open input question; no replacement model is inferred.
Exact manual provenance remains in private evidence.

## Frozen-source comparison

At each sampled time, an independent network calculation reconstructs the
synchronous Norton sources and converter current amplitudes from the saved
physical device outputs. Both the existing load profile and the constant-P/Q
alternative are solved with those sources fixed. Only the two specified units
change representation. Reference values are used afterward to report errors.

| Time (s) | Bus 3054 simulation − reference (pu) | Immediate P/Q − I/Z voltage shift at bus 3054 (pu) | Largest absolute shift across 2,000 buses (pu) |
| --- | ---: | ---: | ---: |
| 0.02 | −0.0004167 | −0.0000337 | 0.0000407 |
| 0.05 | −0.0041141 | −0.0000798 | 0.0000941 |
| 0.10 | −0.0064270 | −0.0000399 | 0.0000474 |

These are independent frozen-source calculations, not a corrected dynamic
trajectory or a bound on later feedback. The alternative therefore cannot be
ruled out solely from this table, but there is no evidence here to adopt it.

## Validation and limits

- All bytes of the 203-row source prefix match the current Case 3 run,
  including both cold history rows and all physical channels.
- Original passive stamps, solved shunts, machine admittances, converter
  admittances, and static-load admittances reconstruct the recorded network
  matrix within 9.10e-13 pu.
- Both offline equations converge below 2e-11 pu without residual cancellation
  or fitted injections.
- Resolving the baseline with frozen reconstructed sources differs from the
  saved complex voltages by up to 0.0006872 pu at the sampled times. This
  observation/solution discrepancy limits interpretation of the control and
  is not hidden by an offset.

The reproducible program, prefix-equivalence check, numerical solutions,
channel table, and PNG/PDF figure are retained in the private evidence set
`2026-10-19-case3-unmodeled-load-response`. The six-case, 36-event comparison
goal remains open.
