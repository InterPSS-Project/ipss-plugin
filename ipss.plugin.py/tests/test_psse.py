import sys
from pathlib import Path

import pytest

import jpype
import jpype.imports

# Add project root to sys.path to allow importing ipss module
script_dir = Path(__file__).resolve().parent
project_root = script_dir.parent
if str(project_root) not in sys.path:
    sys.path.append(str(project_root))


@pytest.fixture(scope="module")
def start_jvm():
    """Initialize and start the JVM once for all tests in this module."""
    from src.config.config_mgr import ConfigManager, JvmManager
    config_path = str(project_root / "config" / "config.json")
    config = ConfigManager.load_config(config_path)

    # Add local compiled classes to classpath to pick up changes in AclfResultExchangeAdapter
    # project_root is ipss.plugin.py, so .parent is the repo root
    #plugin_core_src = project_root.parent / "ipss.plugin.core" / "src" / "main" / "java"
    #if 'jar_path' in config:
    #    config['jar_path'] = str(plugin_core_src) + ":" + config['jar_path']

    JvmManager.initialize_jvm(config)
    yield
    # Shutdown JVM is typically not done in tests to avoid issues with other tests if they run in same process
    # but if this is the only test file or run in isolation it's fine.
    # jpype.shutdownJVM()


@pytest.fixture(scope="module")
def init_test_data():
    psse_file_path = str(script_dir / "testData" / "psse" / "IEEE9Bus" / "ieee9.raw")
    return {
        "file_path": psse_file_path
    }


def test_loadflow(start_jvm, init_test_data):
    # Load data and create the Network Model
    from src.adapter.input_adapter import PsseRawFileAdapter
    from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion

    aclfNet = PsseRawFileAdapter.createAclfNet(init_test_data["file_path"], PsseVersion.PSSE_30)

    # Run Load Flow Algorithm
    # InterPSS core related classes
    from com.interpss.core import LoadflowAlgoObjectFactory

    aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)
    aclfAlgo.loadflow()

    assert aclfNet.isLfConverged(), "LF should be converged"
