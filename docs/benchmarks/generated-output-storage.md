# Storage limits for generated benchmark output

Generated traces and logs must have a storage budget before a long run starts.
The defaults retain 2 GiB of free space, cap each benchmark trace at 512 MiB,
and cap captured command output at 16 MiB. A limit produces a failed run with an
explicit reason. Samples, channels, integration steps, and duplicate event
timestamps are not silently changed.

## Long-running commands

Use the bounded command runner instead of redirecting unlimited console output:

```text
python tools/run_bounded.py --log target/my-run.log -- mvn -o -pl ipss.test.plugin.core -am test -Dtest=CorePluginTestSuite -DargLine=-Xmx2g
```

The runner checks available space before launch and every 0.1 seconds while
the command runs. It stops its process tree when the reserve or log limit is
reached, retains the captured prefix, and records the reason and exit code in
`my-run.log.status.json`. Existing logs and status files are never overwritten.
The command's own exit code is preserved when no storage limit is hit.

On Windows, a launcher waits for assignment to a private Job Object before it
starts the command. The job owns all descendants and terminates them when its
last handle closes, including when a parent exits early or the runner is
interrupted. This uses the operating system's documented
[process-tree lifetime control](https://learn.microsoft.com/en-us/windows/win32/procthread/job-objects).
If assignment fails, the requested command is never launched.

`--min-free-gib` and `--max-log-mib` select explicit budgets for a reviewed run.
Keep both positive. The guard checks the filesystem containing the log, so put
the log on the same drive as the generated results.

## Trace writers

`BenchmarkOutputGuard` is used by the IEEE39 benchmark exporter and both Texas2k
trace writers. It checks free space before opening a new file, bounds actual
written bytes, and checks disk space again at approximately 1 MiB intervals.
Existing files are never replaced. The full-network recorder keeps its gzip
format, and the guard counts compressed bytes written to disk.

The IEEE39 exporter also estimates trace size before entering its simulation
loop. The former 10-microsecond export would exceed the default file budget
and is rejected before writing the large trace. If every sample is required,
provision storage and explicitly review a larger budget; do not reduce sampling
without updating the declared comparison protocol.

The `BenchmarkOutputGuard` section of
`ipss.test.plugin.core/src/test/resources/test-config/ipss.test.plugin.core.json`
contains the positive byte budgets `ipss.benchmark.maxFileBytes` and
`ipss.benchmark.minFreeBytes`. Review larger budgets in that fixture rather
than using JVM-wide overrides. These writer limits do not change numerical
simulation settings; existing defaults and storage enforcement are unchanged.

## Log retention and cleanup

The integration-test logger rolls at 10 MiB and retains three numbered gzip
archives. Its fixed archive names prevent a new archive allowance on each date.
Console capture has its separate 16 MiB limit in the bounded runner.

Cleanup does not run automatically. Supplied references and manifest-listed
evidence dependencies remain protected. Old generated outputs may be removed
after checking that they are not active or retained dependencies. Avoid copying
entire private evidence trees into temporary builds, and enable optional full
network/state diagnostics only when the current comparison requires them.

On October 19, the user-authorized cleanup removed two old generated simulation
CSVs and one diagnostic log, totaling 4,538,413,078 bytes. Supplied references
were retained. The local cleanup record identifies the exact paths and sizes.

## Checks

The Java checks exercise estimated and actual file limits, disk-space loss during
writing, existing-file protection, and exact compressed round trips including
duplicate timestamps. The Python checks exercise refusal before launching on a
full disk, a falling free-space reserve, a noisy child process, existing-log and
status protection, invalid limits, propagation of the command's exit code,
descendant termination before and after parent exit, and failed job assignment.
The seven original Java checks are supplemented by JSON-budget validation and
immutable 64-bit configuration checks. The Python checks also cover the bounded
runner. A real Maven invocation
through the bounded runner also completed and retained its exit status.
