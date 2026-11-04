# GEWPLT2 observation-record foundation

GEWPLT2 is not currently a supported runtime model. The new decoder preserves
its miscellaneous-record identifier separately from its referenced generator
bus and unit ID. It does not attach a plant controller or substitute another
wind model.

The published record has allocation `512 0 2 0 0 17`: two ICON fields, zero
constants, zero differential states and seventeen VAR slots. This is evidence
for an observation wrapper, not for the plant-controller equations previously
listed in the implementation work list. The meanings, bases and update stages
of the seventeen outputs still require exact legacy vendor documentation.

The [published technical study, appendix A](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/GEN-2016-050_LOISIS.pdf)
provides the wrapper allocation and generator-target fields. No project-specific
inputs or statistics are reproduced here. The installed model-library and
vendor-model manuals contain no GEWPLT2 entry. Their newer wind-family models
cannot establish legacy equivalence merely through similar names.

`Gewplt2NativeRecord.decode` checks the exact wrapper, allocation, cardinality,
character fields and positive record/target identities. General USRMSC
tokenization now retains the actual model name, so unknown miscellaneous
models remain identifiable in unsupported-import reporting. GEWPLT2 remains
absent from the loadable catalog and is not silently ignored as an attached
device.

## Remaining work

- Obtain the exact legacy model manual and a compatible executable or library.
- Establish each output VAR's source, scale, initialization, event-side update
  and behavior when its referenced generator is absent, removed or disabled.
- Bind observation ownership to the exact converter/controller host without
  adding differential states or assuming a generic wind-model alias.
- Capture independent flat, disturbance and lifecycle observations; check all
  seventeen outputs under both integration methods with full event samples.

Focused schema tests establish record handling only. There is no claim of
equation, coupled SMIB, trajectory or system acceptance.
