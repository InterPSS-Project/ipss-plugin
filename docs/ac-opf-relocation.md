# ACOPF verification relocated

Continuous ACOPF solvers, formulation tests, and importer/reference comparisons
now belong to `ipss-optimization`, on `codex/equivalent-circuit-ac-opf`.
See that repository's `docs/ac-opf-api.md` and `docs/ac-opf-relocation.md`.

The shared MATPOWER network importer and DC-OPF adapter checks remain here.
This plugin does not depend on optimization. Its core suite no longer registers
the moved ACOPF tests; optimization plugin discovers and runs them directly.
