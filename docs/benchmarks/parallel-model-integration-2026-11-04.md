# Parallel model integration checks

The combined CHV converter-resistance and SVS deadband selection executed
80 tests: 64 passed, 16 failed, with no errors or skips. These are the eight
strict CHV trajectory failures and eight strict SVS coupled pulse failures
already exposed by the model-specific runs. Preparation, terminal transactions
and narrow deadband equation controls pass. The working branch is not ready
for a main-branch merge.

The run used plugin commit `28b7f834a` plus test-suite registration and tracker
updates, and core candidate `1.4.8-cold-generator-base-candidate` built from
`7f2a4056a`. Core JAR SHA-256:
`38e8e89ae416e8563646f179c1e0309f266ea4f7ee2ab25f6b2b27e506683149`.
Both integration methods and the existing strict comparison limits were retained.

Private reports and the bounded build log are archived at
`verification-artifacts/chv-svs-integrated-20261104/validation.zip`, SHA-256
`cb79ea245662cf1035696fb644db032e2740b8eaa202c73c699a00ad6d62a820`.
Complete trajectories and independent captures remain in the model-specific
evidence archives referenced by their implementation documents.

Remaining work is the CHV inverter angle-coordinate mapping and fault
trajectories, and SVS accepted-projection history and algebraic-state versus
physical-current publication. Passing equation checks do not replace full
trajectory acceptance.
