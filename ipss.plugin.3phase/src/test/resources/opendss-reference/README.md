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
