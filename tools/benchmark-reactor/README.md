# Build benchmark source checkpoints

This reactor builds the sibling `core` checkout and this plugin checkout
together. Use the paired source commits recorded in a benchmark checkpoint.
It resolves the core dependency from that source build, avoiding an older core
artifact that may remain in the local Maven repository.

The expected directory layout is `core/` beside `ipss-plugin/`. Java 21, Maven
and the usual external dependencies are required. From the plugin root:

```powershell
python tools/run_bounded.py --log target/source-checkpoint-build.log -- mvn -o -f tools/benchmark-reactor/pom.xml -pl com.interpss:ipss.core.test,com.interpss:ipss.test.plugin.core -am test "-Dtest=St4bBridgeInputReuseTest,St4bCompiledStageTest,Pss2aCompiledStageTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-DargLine=-Xmx2g"
```

Choose a new log name for each run. Adjust the selected tests to the change;
this example is a focused gate, not the complete registered suite.

Finish builds and tests before timing. Retain the runner source, settings,
effective classpath, dependency hashes, raw repetitions and full-recording
hashes with the paired source commits. Private inputs and evidence archives
referenced by a checkpoint remain necessary to reproduce that benchmark.
