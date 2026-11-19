# Experimental equivalent-circuit power flow

`com.interpss:ipss.plugin.ecf` owns the experimental equivalent-circuit
network, branch, load, transformer and power-flow classes formerly under
`com.interpss.core.algo.ecf` in the network core. Their new package is
`org.interpss.plugin.aclf.ecf`. The four derivative/network/solver test classes
live in this module and run with its Maven tests.

Build and test with `mvn -pl ipss.plugin.ecf -am install` from the plugin root.
The artifact depends on the network core and Java numerical libraries, without
the general importer plugin, OR-Tools, COPT, Gurobi or native optimizer bindings.
Its inclusion in the development reactor does not change normal load-flow
defaults or register it as a default core solver.

ACOPF in `ipss-optimization` consumes this small artifact explicitly. Ordinary
power-flow/EDCLF algorithms, network objects and shared sparse primitives remain
in core. The current load equations are constant-power; this move adds no BIG
implementation and changes no equations or convergence settings.
