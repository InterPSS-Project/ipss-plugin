# Algebraic observation and physical publication

The zero-firing-time branch exposes an algebraic command at an event without immediately publishing that command as physical current. Retained independent event duplicates show command approximately 0.4445 pu while physical current remains 0.3045 pu; the next positive interval publishes 0.4445 pu. No added filter, delay constant or changed acceptance threshold is used.

`PhysicalStepTrial` separates begin, publication, acceptance validation, acceptance and discard. Solvers prepare the registry once during initialization. Every owner validates acceptance before any owner commits; commit must not throw after successful validation. Cleanup visits every pending owner and preserves the primary exception. Initialization discards pending owners before rebuilding the registry. Direct integration-stage calls do not implicitly create solver-owned trials.

The model allocates reusable primitive checkpoints only for zero firing time. Monitor observations copy into reusable arrays. Getters and derivative evaluation do not advance publication. Repeated network evaluations/publications do not resample the held command. A positive outer interval brackets local numerical substeps once; low-level shared-state callers must explicitly provide this boundary. Ordinary standalone predictor/corrector calls provide the same boundary automatically when no solver-owned trial exists, preserving base-solver compatibility.

Discard restores this model's numerical coordinates, protection histories, observation buffers and physical current. It does not restore other devices, solver derivative history, or network voltages. External bank switching and scheduled network-event acceptance are rejected while this trial is open; existing after-step dispatch remains the physical-action owner. This is model-local rollback, not a whole-simulation transaction.

## Held deadband sensor discriminator

The independent middle-band pulse enters its held interval when the measured
voltage first crosses the 0.995 pu lower boundary. At the deciding endpoint,
the reference sensor state is 0.995000422001 pu. A trial that clears the prior
lag slope at every held endpoint brings the preceding current and voltage
trajectories within a few micro-units of the reference, but its sensor state is
0.994999876911 pu at this endpoint. It therefore holds the output lag one
interval late and retains a 0.003149 pu output-state offset. The same trial
reduces the peak voltage error but raises the full middle-band voltage NRMSE
from about 0.00977 to 0.02223 against the unchanged 0.001 gate. A history
clear alone is not qualified.

The reference sensor samples obey a binary32-rounded two-step recurrence at
the recorded network inputs. Casting only the production sensor coordinate to
binary32 at accepted endpoints was tested in isolation and reverted: seven of
ten complete pulse comparisons still fail, and middle-band staggered voltage
NRMSE changes only from about 0.009772 to 0.009760. This rules out a lone
sensor-storage cast as the correction. Resolve the upstream voltage/input
boundary and the first held-interval history together before revisiting the
production model; retain both event-side samples and the existing limits.

## Independent qualification

The implementation is based on `df2b42f37`, without the isolated SVS derivative-history candidate `730125104`. Full selection ran 114 tests with seven unchanged strict pulse accuracy failures, zero errors and skips. The separate prepared-dispatch/publication selection ran four passing checks before the standalone compatibility follow-up.

Across complete retained pulse samples, zero-time firing staggered voltage NRMSE improves from 0.0215376 to 0.0000167073, passing the unchanged 0.001 gate. Modified-Euler improves from 0.0562129 to 0.0189993 and still fails. All other pulse failure identities remain visible. Finite-firing-time CSVs are not byte-identical to the retained baseline: maximum numeric difference is 5.39e-13 over all recorded values; most profiles remain below 1e-14. This does not qualify remaining coarse-step or deadband-history discrepancies.

Exact source provenance, complete samples, bounded logs, XML reports and artifact hashes are retained privately. The core artifact is an explicit isolated candidate; default dependencies are unchanged.

Standalone compatibility, prepared dispatch and recorded-input replay follow-up: seven tests passed, zero failures/errors/skips. Private archive SHA-256: `7b8e53a3fa8b6b4c114d32c161db4080d552623fa00d6c889f62965641e02f26`.

Standalone/base-core predictor-corrector acceptance ends at the numerical pair, before any subsequent base-core network failure. Only the supported wrapper owns post-network acceptance/discard; standalone pair completion does not promise whole-step retry.
