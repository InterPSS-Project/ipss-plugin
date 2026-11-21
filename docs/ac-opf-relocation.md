# ACOPF verification relocated

Continuous ACOPF solvers, formulation tests, and importer/reference comparisons
now belong to `ipss-optimization`, on `codex/equivalent-circuit-ac-opf`.
See that repository's `docs/ac-opf-api.md` and `docs/ac-opf-relocation.md`.

The shared MATPOWER network importer and DC-OPF adapter checks remain here.
This plugin does not depend on optimization. Its core suite no longer registers
the moved ACOPF tests; optimization plugin discovers and runs them directly.

Experimental equivalent-circuit power-flow models, solver and derivative tests
now live in the dedicated `ipss.plugin.ecf` artifact. The new package is
`org.interpss.plugin.aclf.ecf`. This lightweight module is independent of the
general importer plugin and external optimizers; ACOPF declares it explicitly.
