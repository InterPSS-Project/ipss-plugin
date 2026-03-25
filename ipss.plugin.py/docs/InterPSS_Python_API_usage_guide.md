# InterPSS Python API — Usage Guide

This guide describes the small Python layer in `ipss.plugin.py` that exposes InterPSS **Java** APIs to Python via **[JPype](https://jpype.readthedocs.io/)**: configuration and JVM startup (`config.py`), curated Java imports (`interpss.py`), and file adapters that build [`AclfNetwork`](https://github.com/interpss/ipss-core) instances from IEEE CDF and PSS®E RAW (`adapter/input_adapter.py`).

For **prerequisites** (JDK, JPype install, conda), see [`RuntimeSetup.md`](RuntimeSetup.md). For a short env overview, see [`InterPSSEnvSetup.md`](InterPSSEnvSetup.md).

---

## 1. Configuration and JVM startup (`src/config.py`)

### `ConfigManager.load_config(config_path=None)`

- Loads **JSON** from a file.
- If `config_path` is omitted, defaults to **`config/config.json`** relative to the **project root** (two levels above `config.py`: `Path(__file__).resolve().parents[2] / "config" / "config.json"`).
- **`jvm_path`**: `{HOME}` is expanded to `os.environ['HOME']`.
- **`jar_path`** and **`log_config_path`**: if not absolute, they are resolved against the **project root** (parent of the `config/` directory that contains the JSON file).

Typical keys:

| Key | Purpose |
|-----|---------|
| `jvm_path` | JVM library path (e.g. `libjli` on macOS/Linux; see your JDK layout). |
| `jar_path` | Classpath JAR (e.g. `lib/ipss_runnable.jar` relative to project root). |
| `log_config_path` | Optional Log4j2 XML, passed as `-Dlog4j.configurationFile=...` |

### `JvmManager.initialize_jvm(config)`

- Starts JPype **once**; if `jpype.isJVMStarted()` is already true, it returns without restarting.
- Builds JVM arguments: `-ea`, `-Djava.class.path=<jar_path>`, and optionally `-Dlog4j.configurationFile=<log_config_path>`.
- `ConfigManager.load_config` raises **`FileNotFoundError`** if the JSON file does not exist; `JvmManager` does not catch or remap that.

**Usage pattern:**

```python
from pathlib import Path
from src.config import ConfigManager, JvmManager

project_root = Path(__file__).resolve().parents[1]  # adjust to your layout
config = ConfigManager.load_config(project_root / "config" / "config.json")
JvmManager.initialize_jvm(config)
```

---

## 2. Curated Java imports (`src/interpss.py`)

The module defines a class `ipss` whose **class body** imports Java packages and Python helpers so you can access them as a single namespace:

```python
from src.interpss import ipss

net = ipss.CoreObjectFactory.createAclfNetwork()
algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
```

### What is re-exported on `ipss`

| Area | Symbols (examples) |
|------|----------------------|
| Apache Commons Math | `ipss.Complex` |
| Core factories | `ipss.CoreObjectFactory`, `ipss.LoadflowAlgoObjectFactory` |
| ACLF enums | `ipss.AclfGenCode`, `ipss.AclfLoadCode`, `ipss.AclfBranchCode` |
| Display / exchange | `ipss.AclfOutFunc`, `ipss.AclfOut_PSSE`, `ipss.PSSEOutFormat`, `ipss.AclfResultExchangeAdapter`, `ipss.ContingencyResultAdapter`, `ipss.ContingencyResultContainer` |
| Utilities | `ipss.PerformanceTimer` |
| File adapters (Python) | `ipss.PsseRawFileAdapter`, `ipss.IeeeFileAdapter` |

You can still import Java classes directly from JPype (e.g. `from com.interpss.core import CoreObjectFactory`) after the JVM is started.

---

## 3. Input adapters (`src/adapter/input_adapter.py`)

Both adapters parse a file through **ODM IEEE adapters**, then map to InterPSS via `ODMAclfParserMapper().map2Model(...).getAclfNet()`.

### `IeeeFileAdapter`

- **Class attribute** `version` exposes `IEEECDFVersion` from `org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter`.
- **`createAclfNet(file_path=None, version=IEEECDFVersion.Default)`** — returns a Java `AclfNet` / `AclfNetwork` object.

### `PsseRawFileAdapter`

- **Class attribute** `version` exposes `PsseVersion` from `org.ieee.odm.adapter.psse.PSSEAdapter`.
- **`createAclfNet(file_path=None, version=None)`** — pass a **PSSERawAdapter**-compatible `PsseVersion`; if your RAW version is known, set it explicitly to avoid parser ambiguity.

**Usage:**

```python
from src.interpss import ipss

# After JvmManager.initialize_jvm(config) ...

net = ipss.IeeeFileAdapter.createAclfNet("/path/to/ieee.cdf", ipss.IeeeFileAdapter.version.Default)
# or
net = ipss.PsseRawFileAdapter.createAclfNet("/path/to/case.raw", None)  # or a PsseVersion enum from ipss.PsseRawFileAdapter.version
```

Exact enum literals depend on the ODM JAR version shipped in `ipss_runnable.jar`.

---

## 4. End-to-end workflow

1. Install JPype and a supported JDK; configure `config/config.json` (see [`RuntimeSetup.md`](RuntimeSetup.md)).
2. **`ConfigManager.load_config(...)`** and **`JvmManager.initialize_jvm(config)`**.
3. Build a network: **`ipss.CoreObjectFactory.createAclfNetwork()`** or **`ipss.IeeeFileAdapter` / `ipss.PsseRawFileAdapter`**.
4. **Load flow** (example): `ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)` then `algo.loadflow()` (Java API).
5. Use **`ipss.AclfOut_PSSE`** / **`ipss.AclfOutFunc`** or **`ipss.AclfResultExchangeAdapter`** for output or exchange as needed.

---

## 5. Project layout reference

```
ipss.plugin.py/
  config/
    config.json          # jvm_path, jar_path, log_config_path
  src/
    config.py            # ConfigManager, JvmManager
    interpss.py          # ipss namespace
    adapter/
      input_adapter.py   # IeeeFileAdapter, PsseRawFileAdapter
  docs/
    InterPSS_Python_API_usage_guide.md   # this file
    RuntimeSetup.md
    InterPSSEnvSetup.md
```

---

## 6. Related documentation

- [`RuntimeSetup.md`](RuntimeSetup.md) — JPype, JDK, `config.json` template.
- [`InterPSSEnvSetup.md`](InterPSSEnvSetup.md) — Short `ipss` import examples.
