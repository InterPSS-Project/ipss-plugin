# InterPSS Python API – Runtime Environment

This workspace contains a fully configured runtime environment for the
[InterPSS Python Plugin](https://github.com/InterPSS-Project/ipss-plugin/tree/master/ipss.plugin.py),
verified with the IEEE 14-Bus load-flow sample.

## Runtime Stack

| Component | Version |
|-----------|---------|
| Java (OpenJDK) | **21.0.10** |
| Python | 3.10.12 |
| JPype1 | 1.7.0 |
| NumPy | 1.21.5 |
| InterPSS Core Lib | **1.0.16** |
| InterPSS Plugin Core | **1.0.16** |
| IEEE ODM PSS | **1.0.1** |
| IEEE ODM Schema | **1.0.1** |

## Directory Layout

```
.
├── config/
│   ├── config.json          # JVM path, classpath, log4j2 config
│   └── log4j2.xml           # Log4j2 configuration
├── lib/
│   ├── ipss_runnable.jar    # Main InterPSS JAR (= ipss.plugin.core-1.0.16.jar)
│   └── deps/                # 126 dependency JARs (InterPSS + 3rd-party)
├── src/
│   ├── __init__.py
│   ├── config.py            # ConfigManager & JvmManager
│   ├── interpss.py          # ipss class – pre-imported Java types
│   └── adapter/
│       ├── __init__.py
│       └── input_adapter.py # IeeeFileAdapter, PsseRawFileAdapter
├── sample/
│   └── sample_ieee14.py     # ✅ Verified IEEE 14-Bus load-flow sample
├── tests/
│   └── testData/ieee/
│       └── IEEE14Bus.ieee   # IEEE Common Data Format test case
├── logs/                    # Runtime log output
├── upload/                  # Uploaded JAR files (originals)
└── README.md                # This file
```

## Configuration (`config/config.json`)

```json
{
  "jvm_path": "/usr/lib/jvm/java-21-openjdk-amd64/lib/server/libjvm.so",
  "jar_path": "lib/ipss_runnable.jar:lib/deps",
  "log_config_path": "config/log4j2.xml"
}
```

- **`jvm_path`** – Path to the JDK 21 shared library (the InterPSS 1.0.16 JARs
  are compiled for Java 21, class file version 65).
- **`jar_path`** – Colon-separated classpath; `lib/deps` is a directory and all
  `*.jar` files inside it are added automatically by `ConfigManager`.
- **`log_config_path`** – Log4j2 XML controlling console + rolling-file logging.

## Quick Start

```bash
# From the project root directory:
python3 sample/sample_ieee14.py
```

## Verification Output

The sample successfully:
1. Starts the JVM with JDK 21
2. Parses the IEEE 14-Bus Common Data Format file
3. Runs Newton-Raphson load flow (converged in 2 iterations)
4. Prints the load-flow summary table
5. Extracts bus voltages and branch power flows via NumPy arrays

Key result snippet:
```
Load Flow Converged !
Bus1  Swing  1.06000   0.00°   Pg=2.3239  Qg=-0.1655
Bus2  PV     1.04500  -4.98°   Pg=0.4000  Qg=0.4355
...
Bus14        1.03553 -16.03°   Pl=0.1490  Ql=0.0500
```

## Key Dependencies

The `lib/deps/` folder contains all transitive dependencies resolved via Maven,
including:

- **InterPSS**: `ipss.core.lib-1.0.16`, `ipss.plugin.core-1.0.16`
- **IEEE ODM**: `ieee.odm_pss-1.0.1`, `ieee.odm.schema-1.0.1`
- **Sparse Solver**: `JKLU-1.0.0` + `BTFJ-1.0.1`, `AMDJ-1.0.1`, `COLAMDJ-1.0.1`
- **JAXB**: `jaxb-api-2.3.1`, `jaxb-runtime-2.3.3`, `jaxb-impl-2.3.1`, `jaxb-core-2.3.0`
- **Logging**: `log4j-api/core-2.25.4`, `slf4j-api-1.7.36`
- **Math**: `commons-math3-3.6.1`, `ojalgo-51.2.0`, `colt-1.2.0`
- **Others**: `gson-2.13.1`, `groovy-4.0.27`, `mvel2-2.5.2.Final`, `poi-5.4.0`, etc.

**Please note**: `ipss_runnable.jar`, `ipss.core.lib-1.0.16`, `ipss.plugin.core-1.0.16`,`ieee.odm_pss-1.0.1`, `ieee.odm.schema-1.0.1` need to be updated.
