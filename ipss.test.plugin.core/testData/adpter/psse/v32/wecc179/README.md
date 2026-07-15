# WECC179 shared CPF comparison fixture

`wecc_179_v32.raw` is the canonical input used for both InterPSS and ANDES CPF
comparison. Its SHA-256 is pinned in `andes_cpf_reference.properties`, so the
reference cannot silently be applied to different case bytes.

The comparison uses uniform `2.0` load/generation scaling and stops at
`lambda=0.02`, `0.06`, `0.10`, and the near-nose upper-branch point `0.115`.
All 179 voltage magnitudes are checked within `1e-5 pu`. Predictor step counts
are diagnostic only because the two engines adapt their continuation steps
independently.

Generate the ANDES 2.0.0 reference from the repository container directory:

```shell
HOME=/tmp/andes-home andes/.venv311/bin/python \
  ipss-plugin/ipss.test.plugin.core/testData/adpter/psse/v32/wecc179/andes_cpf_reference.py
```

Run the InterPSS parity test:

```shell
cd ipss-plugin
mvn -pl ipss.test.plugin.core \
  -Dtest=ContinuationPowerFlowPsseTest#tracesWecc179PsseCaseToTargetLoading test
```

The ANDES gallery `wecc179.raw` is a PSS/E v33 conversion with renumbered buses
and is not used for this comparison.
