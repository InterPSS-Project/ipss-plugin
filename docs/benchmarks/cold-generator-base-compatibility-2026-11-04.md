# Cold generator base conversion: default module compatibility

The corrected cold-energization cache refresh passes both default compatibility
modules. Exact test and skip identities match the preceding ordinary-cache run;
no tests were added, removed or newly skipped by this validation.

| Module | Tests | Failures | Errors | Skipped |
| --- | ---: | ---: | ---: | ---: |
| `ipss.plugin.core` | 494 | 0 | 0 | 4 |
| `ipss.plugin.3phase` | 468 | 0 | 0 | 28 |

The 169 XML reports are authoritative. The reactor allowed continuation after
assertion failures so both modules would execute, but the XML audit confirms
zero failures and errors. No extended/private test selection was enabled.
Existing skips remain the same optional native-solver and diagnostic controls.

Validation used plugin source `fbc98ce57`, core source `7f2a4056a`, and the
isolated artifact `1.4.8-cold-generator-base-candidate`. The core JAR SHA-256 is
`38e8e89ae416e8563646f179c1e0309f266ea4f7ee2ab25f6b2b27e506683149`.
The build profile was `compatibility-cold-base-20261104`, with a 2 GiB test heap
and bounded logs. Default dependency versions and runtime settings were not
changed.

Complete logs, XML, identity comparison and the reproducible audit script are
retained privately in `verification-artifacts/compatibility-cold-base-20261104`.
Its `validation.zip` SHA-256 is
`a7ac94e0c3af28da8ae3a642f592ec52a43c076f6009078dc1900d93da5b323d`.

This compatibility result is separate from dynamic-model reference acceptance.
The registered dynamic-model suite and its existing open accuracy gates are
reported independently; passing these two modules does not close those gates.
