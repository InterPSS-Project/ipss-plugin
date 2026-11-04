# Parallel model validation

The registered core-plugin suite at `63e2e6280` ran 4,742 tests: 217 failures,
2 errors and 13 skips. This is a failed integration gate, not a successful
qualification of the complete model set. No merge to the main development
branch is justified by this run.

The retained failures include CHVDC2 controller and coupled-network trajectories,
governor and stabilizer accuracy gates, compressor thermal observations, renewable
network convergence, endpoint-recording integration and system smoke tests.
Each needs an isolated baseline before attributing it to the latest changes.
Test thresholds, the network iteration cap and reference samples remain unchanged.

Subsequent scoped model fixes require their own checks and do not supersede this
full-suite result. The accepted-endpoint recovery-clock fix and the initial
current-ceiling preparation fix have independent focused evidence. The work list
continues to distinguish these verified behaviors from complete model acceptance.

The complete XML reports and bounded log are retained privately in
`verification-artifacts/parallel-model-full-suite-20261104/validation.zip`.
SHA-256: `e88218a76152c7ae774a6f4ce6641f6a2827a9850d156238ce946742c2e3f534`.
Private logs are not copied into public documentation.

After integration, the CDC6T/SVSMO3T2 selection passed all 326 tests and the
composite-tap/relay/conformance selection passed all 73 tests, with no failures,
errors or skips. These checks cover the combined scoped fixes and their adjacent
contracts. They do not close the independent full-suite failures above.
Their logs, XML reports and source identity are retained in the private
`verification-artifacts/parallel-model-integration-20261104` evidence directory.

The subsequent complete three-phase reactor test run at `a7b571884` passed:

| Module | Tests | Failures/errors | Skipped |
| --- | ---: | ---: | ---: |
| Plugin core | 494 | 0 | 4 |
| Three-phase plugin | 468 | 0 | 28 |

The skips comprise optional native-solver availability checks and opt-in export
or diagnostic cases. They are not counted as executed successes. This confirms
adjacent plugin compatibility, not completion of the failed registered dynamic
model reference gates. Complete reports and the bounded log are retained in
`verification-artifacts/parallel-plugin-compatibility-20261104`.

The separate core-engine reactor at `3b1292682` subsequently completed 3,797
tests with zero failures/errors and 16 skips. Its source tree was unchanged.
This validates the engine baseline used by the plugin work; it does not replace
the plugin's independent dynamic-model trajectory gates. Reports and the bounded
log are retained in `verification-artifacts/parallel-core-engine-20261104`.

After the electrical/mechanical event-rate corrections, HYG3 stage guards and
battery-controller corrections, a fresh registered suite at `e90050aaf` ran
4,756 tests: 215 failures, two errors and 13 skips. Comparing every failing test
identity with the earlier baseline found no new failures. The two resolved
identities are the weak-grid battery conformance test and endpoint recording
test, which now explicitly use certified bounded network acceleration. Existing
accuracy gates remain unchanged; this run still fails the main-branch merge gate.
Complete reports are retained in
`verification-artifacts/combined-event-controller-suite-20261104/validation.zip`,
SHA-256 `2f1396b944a1aa3bce55e2b74923864e03955841c9627b842913c20fa285f6a5`.

The subsequent explicit electrical-controller coupling refactor at `0541c260d`
passed 104 converter/controller/network/solver tests without failures, errors or
skips. Existing REECA and fixed-command behavior is preserved; this capability
contract alone does not qualify new controller implementations or change network
fast-path eligibility. Reports are retained in
`verification-artifacts/electrical-controller-contract-integration-20261104`.
