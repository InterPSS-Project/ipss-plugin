# Pure Java current-assembly execution profile

The profile retains 1,501 Case 1 and 1,050 Case 2 main-thread samples with the complete-case method on their stack. This is sampled call-stack attribution, not exclusive wall-clock timing or a new performance result. In particular, frequent leaf attribution to the custom-current hook conflicts with the earlier coarse timer showing that hook takes less than a millisecond; leaf location alone must not be read as causal method cost.

| Sample category | Case 1 | Case 2 |
| --- | ---: | ---: |
| PSS2A | 189 | 59 |
| ST1 | 135 | 33 |
| ST4 | 80 | 23 |
| current dispatch or other device | 76 | 66 |
| current machine output | 21 | 11 |
| current static load | 108 | 100 |
| network acceleration | 106 | 128 |
| other | 424 | 235 |
| other network | 316 | 361 |
| recording | 46 | 34 |

The collector requests 1 ms execution/native-method sampling with 128-frame stacks. Actual retained sample counts are much lower than one sample per millisecond of runtime; requested period must not be used as a duration multiplier. Native-method events describe JVM/runtime stacks and do not select a native sparse solver. Both simulations use the pure Java sparse implementation. There are no lost bytes or truncated eligible main-thread stacks in either recording.

Twelve complete fresh-case warmups precede three profiled runs per case. Recording starts before warmup 10, allowing three additional warmups with the recorder active. Analysis uses the three measured complete-case time windows and requires runCase on the main-thread stack; output verification and hard-link work after runCase are excluded. All 30 complete recordings match the retained baseline exactly. One logical CPU is pinned and verified, the network step is 1/240 second, voltage-correction tolerance is 1e-5 and ST1 uses one local step. Existing network acceleration and all recording remain enabled.

Static-load stacks account for more samples than synchronous-machine output stacks within current injection in both cases, but inlining and sampling-location effects limit precise cost comparisons. The earlier classification cache already avoids rebuilds; its sampled leaf frequency is not proof of repeated classification. Controller work remains substantial, especially PSS2A in Case 1. Terminal-voltage sensing appears in the stacks as a smaller opportunity, motivating a separate guarded immutable-voltage magnitude screen.

The first profile reader could not serialize Java collection entry objects. It was repaired to emit ordinary maps and reprocessed the saved Case 1 recording; the simulation was not rerun. Case 2 then completed. Initial source/error, final reader, profiles, commands, runtime/input identities and recording dependencies are retained. No production change or accuracy acceptance follows from this profile. The full eight-second goal remains open.
