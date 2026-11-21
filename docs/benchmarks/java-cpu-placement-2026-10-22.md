# Single-CPU placement check

Core placement does not close the performance gap. CPU 8 wins three of five pairs with a median paired saving of only 0.013720 seconds. Its 8.770990-second arm median remains above the target; CPU 0's median is 8.928111 seconds. The ranges overlap, and CPU 0 produces the fastest individual run at 8.480178 seconds. The existing affinity default is retained.

This diagnostic compares the unchanged pure Java Case 1 baseline on logical CPU 0 and logical CPU 8. Every simulation runs on exactly one allowed logical processor. The eight-second objective and independent-reference accuracy remain open.

The workstation reports a 13th-generation desktop processor with 24 logical CPUs. The retained CPU-set query identifies logical CPUs 0 and 8 as different physical cores in the same higher-performance EfficiencyClass (1); CPUs 16-23 report class 0. CPU 0 was already in the higher-performance class. Microsoft documents that higher EfficiencyClass values indicate faster, less power-efficient processors, and that equal CoreIndex values identify shared core execution resources. [CPU-set structure documentation](https://learn.microsoft.com/en-us/windows/win32/api/winnt/ns-winnt-system_cpu_set_information). The snapshot also retains parked/scheduling flags, but these are not measurements of frequency, sustained thermal behavior or contention during the run.

Sixteen alternating warmups precede five order-alternating measurement pairs in one JVM. Before each run an external affinity helper pins the whole JVM process to CPU 0 or CPU 8 and verifies the one-bit mask. Java waits for that acknowledgement before starting complete-case timing. The helper verifies the same mask after each run before allowing the next. Affinity handshakes are outside the case timer, as initial affinity setup is in the existing benchmark. The simulation runtime uses one active processor, serial GC and synchronous compilation. No parallel numerical workers or native sparse backend are introduced.

Fresh loading, preparation, initialization, ten simulated seconds, every recording and writer closure remain timed; compiler and GC work inside that interval remain included. The quarter-cycle step, 1e-5 voltage-correction tolerance, one ST1 step and existing experimental network path are unchanged. Both placements use the same compiled simulation classes and model configuration.

| Placement | Median total, s | Range, s | Median process CPU, s |
| --- | ---: | ---: | ---: |
| Logical CPU 0 | 8.9281112 | 8.4801778-9.0223596 | 8.7031250 |
| Logical CPU 8 | 8.7709896 | 8.7219682-8.9291527 | 8.4062500 |

CPU 8's median paired saving is 0.0137202 seconds, with 3/5 faster pairs. Independent arm medians and the median of paired differences are distinct statistics. The raw order, process CPU time, compilation time and GC time are retained for every run.

All 26 complete recordings match the frozen baseline byte for byte, including channel metadata, selected CSV and full-network binary samples with event-side duplicates. Every run retains 18,340 network iterations. The affinity records verify mask 1 or 256 before and after each timed run; every mask contains exactly one bit. Model outputs and numerical work are therefore unchanged by this placement check. It is not new independent-reference accuracy evidence.

This is a Case 1 placement diagnostic, not a six-case acceptance test. Shared workstation activity, clock behavior and sibling-thread contention are not controlled or measured here, so any observed difference is not attributed to a specific hardware mechanism. Production source, affinity defaults and supplied references remain unchanged. Source/classes for the harness, helper, API topology snapshot, commands, raw timings, per-run affinity, input/runtime identities and recording hashes are retained. Large identical generated recordings share retained storage only after equality checks.
