# Plugin 1.4.10

This version uses Core 1.4.8 and includes the current committed controller,
dynamic-load and converter work. It promotes the Desktop coupled-convergence
fix into the Plugin source so clients can use the ordinary release version.

Both coupled solvers accept success only after a complete comparison of
consecutive network solutions. Zero or one allowed iteration cannot establish
convergence. Exhausted budgets preserve each solver's failure contract, and
nonfinite sequence voltages cannot pass the convergence test. Tolerances and
iteration limits are unchanged by this fix.

The CDC6T allocation regression primes its complete counter/measurement batch
before checking warmed storage reuse. The 100,000 measured trials and existing
allocation threshold are preserved; no controller equation changes are involved.

## HVDC and reactive-device scope

- CDC6T: named-link record decoding and an explicit typed runtime are present.
  Automatic dynamic-record attachment remains unsupported. Complete dynamic
  qualification is open, including the retained finite-resistance modified-Euler
  terminal-voltage accuracy gate. No catalog acceptance label is promoted here.
- CSVGN5: exact import and runtime attachment are available.
- SVSMO1T2 and SVSMO1T3: switched-shunt import and runtime attachment are available.
- SVSMO3T2: named FACTS ownership and current-controller runtime are present;
  support remains partial. Broader limiter, production-step and long-duration
  qualification remain open.

Loadability is separate from complete independent trajectory acceptance. The
existing support matrix and per-model evidence documents retain those scopes.
