# Composite-load omitted distribution elements

The legacy and zone composite runtimes omit the transformer when `Xxf == 0`
and omit the feeder when `Xfdr == 0`. An omitted feeder ignores its supplied
resistance. An omitted transformer ignores its fixed taps and voltage-control
settings. The original input constants remain available for diagnostics.

Initialization uses the same bus object on both sides of each omitted element.
It creates only the remaining internal buses and physical branches; it does not
substitute small impedances or attach self-loop branches. Previously, an
all-omitted severe-fault control produced nonfinite network voltages. Treating
a resistance-only feeder as present also introduced an initial load-voltage
error of 0.02303 pu against the independent control.

## Ownership when nodes merge

Component capacitors add to an existing bus shunt instead of replacing it.
Current balance uses only the composite's own capacitor contribution. When
both elements are omitted, the composite captures the remaining parent load
and explicitly allocates its compressor, static and electronic operating points.
The engine initializes leaf loads again after expanding the network; those
allocations prevent the second call from using the combined host-bus demand.
The final bus totals include the composite and the remaining source loads,
while each dynamic component retains its own initial P/Q.

`LD1PACImpl.setInitialLoadAllocation()` binds this operating point before its
first initialization. It rejects nonfinite/nonpositive active power, a
conflicting source-record owner, and a changed allocation after initialization.
An identical allocation can be reapplied. This is initialization data, not a
second runtime state or a fixed current correction.

The shared motor equations, reusable derivative/current buffers and accepted
histories remain the integration owners under modified Euler and staggered
integration. Distribution omission adds no work to their derivative kernels.

## Qualification and limits

Independent coupled SMIB controls cover transformer-only omission, feeder-only
omission, both omitted, ignored transformer controls, a resistance-only omitted
feeder, a compressor mixture and a mixed induction-motor allocation. Each is
checked at 0.5 and 0.25 ms under both integration methods. Raw cold-repeat
captures, all recorded channels and both event-side samples are retained;
unused allocated diagnostics are not cleared. Unselected cold captures remain
separate from the accepted repeat pairs.

The reference controls give identical physical trajectories with altered
settings on an omitted transformer and with altered resistance on an omitted
feeder. Tests also require exact node identities, positive branch impedances,
initial component allocations, complete voltage/power/angle comparisons and
per-channel error bounds. Merged-bus flat controls retain an unrelated load,
an existing shunt and optional colocated generation under both methods,
without a fixed compensating current.

These checks qualify the omission rules and selected legacy boundaries. They
do not establish complete internal-state parity, severe low-voltage static
admittance behavior, full zone-model trajectory parity, warm restoration or
production-step/long-duration acceptance. The catalog remains `LOADABLE`.

The public [composite-load specification](https://www.wecc.org/sites/default/files/documents/meeting/2024/WECC%20Comp%20Load%20Model%20Specification_final.pdf)
also describes omission of zero-reactance transformer and feeder elements.
Exact reference provenance and the version-specific controls are retained in
the private evidence manifests.
