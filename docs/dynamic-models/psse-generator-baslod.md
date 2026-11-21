# Generator base-load flag

The source generator record carries a per-unit identifier and a per-generator
base-load flag (`BASLOD`). In direct RAW revisions 35 and 36 it follows the
active-power limits `PT` and `PB`. In RAWX the importer resolves `baslod` by
name so sparse and reordered field lists use the same semantics. Missing
values default to zero. The flag is included in generator state copies and
exports, including records for offline generators.

At dynamic initialization, InterPSS saves the initialized mechanical power
of each synchronous machine as its reference. The governor computes its
command and updates its internal states as usual. When the solver publishes
the command to the machine swing equation, the machine applies these limits
using the initialized power:

| BASLOD | Mechanical power delivered to the machine |
| --- | --- |
| 0 | Governor command |
| 1 | At most the initialized power |
| 2 | Exactly the initialized power |
| 3 | At least the initialized power |

A new simulation initialization captures a fresh reference. Event-driven
reactivation preserves the reference from that run. The default integration
path and the staggered solver both publish through the same machine method.
A separate governor-block input may still select a model-specific action.
The base-load flag applies to the command produced by the resulting controller.

Native speed-step checks exercised all four settings in both directions.
For flag 2, the governor stayed active and its internal states moved while
delivered mechanical power stayed fixed. A separate staggered integration
fixture checked all four output limits and recorded the live command behind
the flag 2 limit. The direct export regression covers both RAW revisions
and sparse RAWX, including retention of the original active-power limits.
