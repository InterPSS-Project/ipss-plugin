# Multi-terminal DC export preservation

RAW and RAWX export now include multi-terminal DC networks from the dedicated
`BaseAclfNetwork.getHvdcLineMTList()` collection. These objects are not ordinary
special branches. Previously both standalone exporters silently omitted them,
leaving saved AC voltages without their converter injections after re-import.

`PSSEJsonExporter.addMultiTerminalDcSections` writes the canonical `ntermdc`,
`ntermdcconv`, `ntermdcbus` and `ntermdclink` sections, preserving network control
mode/status, converter orders and limits, DC topology and link parameters.
`PSSERawExporter.writeMultiTerminalDcSection` emits each header and its matching
converter, bus and link records in section order for all supported RAW layouts.
No inferred terminal injections are substituted for device equations.

`PSSEJsonExporterRoundTripTest.multiTerminalDcEquipmentSurvivesExport` covers
RAWX and RAW versions 30, 33 and 36, ordinary and solved-state export, two distinct
networks, active/blocked status, terminal attachments, orders, tap settings,
angle limits and DC branch parameters. All 42 exporter tests pass in the local
verification recorded by QA as `logs/plugin-mtdc-export-fixed-20261119.log`.

The omitted equipment cannot be reconstructed from an already incomplete export
alone. The affected private fixture was repaired from its original source MTDC
records, with an exact backup and SHA-256 manifest. Its saved bus-voltage records
and test tolerances were preserved. All 51 QA example/sample checks pass with
current core classes and the exporter changes; broader QA qualification remains
separate.
