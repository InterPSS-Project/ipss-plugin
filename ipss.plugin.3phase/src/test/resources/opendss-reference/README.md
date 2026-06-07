# OpenDSS Reference Results

These CSV fixtures were generated from the upstream IEEE feeder DSS files using
DSS-Python through `opendssdirect.py`.

Sources:

- https://github.com/PauloRadatz/py-dss-interface-examples/tree/main/feeders
- https://github.com/dss-extensions/DSS-Python/

The IEEE123 fixture is used as an end-to-end parser and distribution power-flow
comparison. The IEEE13 fixture is retained as a parser/reference import check;
the current forward/backward sweep implementation does not yet support the
original IEEE13 feeder's three parallel single-phase regulator transformer
branches as one radial upstream path for power-flow comparison.

`opendss-load-model-mini-dss-python-voltage-reference.csv` is generated from
`testData/feeder/OpenDSSLoadModelMini/Master.dss` and validates OpenDSS load
models 1 through 8 against a stiff, 0.90 pu source DSS-Python reference.

`capcontrol-mini-dss-python-capacitor-reference.csv` is generated from
`testData/feeder/OpenDSSCapControlMini/*.dss` and validates capacitor control
final state and terminal kvar before using voltage comparison as an acceptance
signal.

`storage-mini-dss-python-storage-reference.csv` is generated from
`testData/feeder/OpenDSSStorageMini/Master.dss` and validates scheduled storage
discharge/charge terminal P/Q before using voltage comparison. The charging
step uses `kWhStored=500`, `state=charging`, and `%charge=100`; setting `kw`
after `state=charging` causes DSS-Python/OpenDSS to return the device to
discharging/idling behavior.

`pvsystem-mini-dss-python-generator-reference.csv` is generated from
`testData/feeder/OpenDSSPVSystemMini/Master.dss`, based on the official EPRI
OpenDSS PVSystem example. It validates PVSystem terminal P/Q for the explicit
initial irradiance/temperature state and the first daily irradiance/temperature
update before any voltage comparison is used.
