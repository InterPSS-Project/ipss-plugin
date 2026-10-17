# EXDC2 native PSS/E dialect correction

The May 2026 approved-model table maps the PSLF rows `exdc2` and `exdc2a`
to the same native PSS/E name, `EXDC2`. The implementation catalog previously
mapped the second row to the separate EXDC2A runtime and accepted that
PowerWorld/PSLF name in the PSS/E DYR parser. This overstated native model
coverage and admitted a different parameter meaning in the same input slot.

## Source contracts

The provided WECC May 2026 PDF, page 1, identifies the model columns explicitly.
Its SHA-256 is
`b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.
The [published source](https://www.wecc.org/sites/default/files/documents/progress_report/2026/Approved%20Dynamic%20Models%20May%202026.pdf)
is recorded in `WeccApprovedDynamicModelCatalog`.

The installed PSS/E 36.7.0 Model Library, printed pages 270-271 (PDF pages
282-283), defines native EXDC2's 16 parameters and five states. Printed pages
296-297 define the distinct native IEEEX2 model. The library PDF SHA-256 is
`40df1c7e72fcb626e60b7f76180d4f866439c333398eb66354dde21567bb846d`.
The local PDF table and diagrams were rendered for visual review; proprietary
pages are not published with this report.

| Contract | Name | Parameter J+11 | Feedback storage |
|---|---|---|---|
| Native PSS/E | EXDC2 | Switch | One rate-feedback integrator |
| PowerWorld/PSLF | EXDC2A | Tf2, a second time constant | Additional lag |
| Native PSS/E | IEEEX2 | Tf2 | Two published feedback integrators |

PowerWorld documents these distinct fields in its
[EXDC2_PTI](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXDC2_PTI.htm),
[EXDC2A](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXDC2A.htm),
and [IEEEX2](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20IEEEX2.htm)
references. EXDC2_PTI marks Switch unused. An equal record length does not
make the layouts interchangeable, and renaming an EXDC2A record to EXDC2 is
not a supported data conversion.

## Change

- Map the approved `EXDC2A` source-list row to native `EXDC2`.
- Remove EXDC2A from the native DYR catalog and dispatch path. Strict import
  rejects it as unsupported, retains the source name and location, and explains
  the Switch/Tf2 distinction. No substitute exciter is attached.
- Retain `addExcExdc2a` and the explicit runtime API for the separate topology.
  Its equation-level and solver-equilibrium tests now construct it directly.
- Regenerate the native support matrix. Approved PSS/E exciter coverage
  remains 64 source-list rows; two rows share the same EXDC2 implementation.
- Keep native EXDC2 and IEEEX2 equations, parameter order, checkpoints, and
  trajectory tolerances unchanged.

This changes import compatibility for the previously accepted non-native
EXDC2A name. Callers needing that topology can use its typed builder API;
native PSS/E input must retain its original model name and parameter semantics.

## Verification

All 56 focused catalog, strict import/report, builder, equilibrium, explicit
EXDC2A equation, native EXDC2 trajectory, and ANDES EXDC2 trajectory tests pass
with zero failures, errors, or skips.
The rejection test also loads a valid native EXDC2 record afterward and checks
that the attached runtime is exactly Exdc2Exciter.

The existing PSS/E 36.7 EXDC2 comparison retains its bounds. The ANDES 2.0.0
line-trip comparison records maximum errors of 7.4191e-6 pu speed,
2.1512e-4 pu EFD, and 1.9562e-5 pu bus voltage. These results apply to the
existing benchmark profiles; this slice creates no new simulator trajectory.

The full registered reactor regression, including explicit selection of
CorePluginTestSuite, exits zero on 2026-10-17:

```text
mvn -q test -Pextended-tests --fail-at-end
    -Dtest=Test*,*Test,*Tests,*TestCase,CorePluginTestSuite,!Wecc240JaxComparisonTest
    -Dsurefire.failIfNoSpecifiedTests=false
```

Surefire reports written during this run contain 2,687 tests: 2,574 passed,
113 skipped, zero failures/errors. Module totals are 129 core-plugin tests
(4 skipped), 468 three-phase tests (28 skipped), and 2,090 integration tests
(81 skipped). The unrelated untracked WECC240/JAX test is explicitly excluded;
its source is unchanged. The local log is
`target/exdc2-native-boundary-regression.log`.

Private Texas2k inputs remain unavailable, so its six-case dynamic and
15-fault simulation gates are unexecuted for this slice. A green public
regression does not establish Texas2k acceptance or complete the larger
exciter/stabilizer evidence audit.
