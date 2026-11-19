# Core candidate rebuild verification

The core source at `7f2a4056a` was cleanly recompiled as the isolated local
artifact `1.4.8-candidate-recompiled-20261104`. Its 2,471 Java class entries are
byte-identical to the previously pinned candidate. The enclosing JAR hashes
differ because they are separately packaged artifacts:

| Artifact | SHA-256 |
| --- | --- |
| Pinned candidate | `38e8e89ae416e8563646f179c1e0309f266ea4f7ee2ab25f6b2b27e506683149` |
| Clean rebuild | `a8e512b2a3e534ac7550ff91b46a4ad8984fb164bd9fdf7d47ca0a5c5d5a8681` |

The exact `CorePluginTestSuite` selection ran against the rebuilt artifact.
All 5,054 test identities matched the pinned-artifact run. The same 244
assertion failures and two errors remained, with no new or resolved failure
identity. Two optional converted-interface SMIB invocations skipped because
the private fixture property was omitted from the full run; both passed in a
separate four-test focused run with the property supplied. No acceptance gate
or default dependency version changed.

The comparison applies to plugin source `efcb2fe9a`, which was compiled before
the subsequent source-reactance diagnostic commit. Rebuilding core alone did
not change the failing model comparisons or qualify the work list.

Private XML, bounded logs and the machine-readable identity audit are retained
at `verification-artifacts/core-candidate-recompiled-20261104/validation.zip`,
SHA-256 `4575a8a5b9fea5e8a63f74579166dcd6e804111aaab357ed792c40c610292f9d`.
