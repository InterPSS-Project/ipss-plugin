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

## Full registered suite

The full suite on clean commit `54dd55213` executed 5,017 tests across 756
report files: 245 failures, two errors and 13 skips. Relative to the preceding
4,959-test core-candidate run, no previously passing test failed and no existing
failure was resolved. Seventeen newly registered strict comparisons fail:
one CDC resistance trajectory, eight CHV resistance trajectories and eight SVS
deadband pulse trajectories. Four earlier symbolic-target rejection test
identities were replaced by exact-target acceptance and missing-target checks
after independent relay evidence established the correct semantics.

Private XML reports, bounded log and identity audit:
`verification-artifacts/full-parallel-models-20261104/validation.zip`, SHA-256
`a291045c60b4b6c4c118f7cbe75b7bd0a64c88a974d4eb66db1a24ecb64b8e1e`.
This run predates the generic accepted-projection history API; that opt-in API
has a separate focused validation and currently enables no production model.

The integrated generic API at `84b0f4956`, plus its suite registration, passed
49 checks across four classes, with no failures, errors or skips. Private
reports and bounded log:
`verification-artifacts/projection-contract-integrated-20261104/validation.zip`,
SHA-256 `8bc9c431102bc464f9ac4fe307562ffca12360464942c4f2923d3365f9d7f1c5`.

The SVS-specific opt-in remains isolated in candidate `730125104`. It brings
enabled and unequal-gain staggered pulse comparisons within the existing gate,
but worsens the middle-band pulse comparison after a threshold crossing changes
by one step. Modified-Euler profiles remain byte-identical. This candidate is
not included in the integrated runtime; physical-current publication and full
trajectory qualification remain open.
