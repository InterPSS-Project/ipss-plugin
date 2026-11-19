# Case 3: complete current-runtime prefault confirmation

The full prefault second was rerun with the same frozen runtime as the current
Case 2, 4 and 5 checks, including the frequency-history correction and validated
converter network-voltage path. Original inputs, ordinary computed initialization,
0.5 ms steps, two cold-history samples and the 1e-4 dynamic network tolerance
are retained.

All 2,003 rows and 6,578 physical channels are byte-identical to the earlier
complete Case 3 converter-voltage correction run. Both compressed physical
outputs have SHA-256
`0e7b98fb4f181f7ee1df49db4caf1f0431516740ed96a2f0df7512b127654a8c`.
Thus the earlier prefault mismatch is not explained by an outdated runtime.
This does not establish correctness of every model or coupling boundary.

All six supplied event histories contribute to comparison. Machine terminal
voltages now receive the same voltage acceptance check as explicitly named
bus-voltage channels, closing the coverage gap described in the
[Cases 4 and 5 report](case45-current-prefault-2026-10-19.md).

| Prefault metric | Worst value | Location | Passing occurrences |
|---|---:|---|---:|
| Bus-voltage NRMAE | 0.175381223 | Bus 3054 | 0 / 30 |
| Machine terminal-voltage NRMAE | 0.216868261 | Unit 1073/1 | 0 / 30 |
| Rotor maximum error, degrees | 17.208856270 | Unit 1073/1 | 0 / 30 |
| Generator-speed proxy NRMAE | 0.131762397 | Unit 6082/1 | 0 / 30 |

Every normalized error uses that channel occurrence's full ten-second supplied
reference range. Speed remains a proxy, not direct bus-frequency evidence.
No offset, time shift, state or parameter is fitted. The run completes 2,002
network calls and 41,117 iterations, with at most 49 iterations per call.
Its concurrent diagnostic elapsed time is not production performance evidence.

Private evidence set `2026-10-19-case3-current-prefault-confirmation` retains the
runner, exact classpath and input hashes, complete output, all comparisons,
plots and the byte-equivalence verification. The source of the early coupled
voltage divergence remains unresolved; no production equation or acceptance
limit changes in this slice. All six cases and 36 original events remain required.
