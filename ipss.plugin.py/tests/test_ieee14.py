import sys
from pathlib import Path

import pytest

# do not use the class approach here
from tests.ieee.ieee_tool import IeeeLoadFlowTool
import numpy as np
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
    plugin_core_src = project_root.parent / "ipss.plugin.core" / "src" / "main" / "java"
    if 'jar_path' in config:
        config['jar_path'] = str(plugin_core_src) + ":" + config['jar_path']

    JvmManager.initialize_jvm(config)
    yield
    # Shutdown JVM is typically not done in tests to avoid issues with other tests if they run in same process
    # but if this is the only test file or run in isolation it's fine.
    # jpype.shutdownJVM()


@pytest.fixture(scope="module")
def init_test_data():
    """Provide IEEE14 test data file path and IDs."""
    ieee_file_path = str(script_dir / "testData" / "ieee" / "IEEE14Bus.ieee")
    bus_ids = [
        "Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6", "Bus7", "Bus8",
        "Bus9", "Bus10", "Bus11", "Bus12", "Bus13", "Bus14"
    ]
    branch_ids = [
        "Bus1->Bus2(1)", "Bus1->Bus5(1)", "Bus2->Bus3(1)", "Bus2->Bus4(1)",
        "Bus2->Bus5(1)", "Bus3->Bus4(1)", "Bus4->Bus5(1)", "Bus4->Bus7",
        "Bus4->Bus9(1)", "Bus5->Bus6(1)", "Bus6->Bus11(1)", "Bus6->Bus12(1)",
        "Bus6->Bus13(1)", "Bus7->Bus8(1)", "Bus7->Bus9(1)", "Bus9->Bus10(1)",
        "Bus9->Bus14(1)", "Bus10->Bus11(1)", "Bus12->Bus13(1)", "Bus13->Bus14(1)"
    ]
    return {
        "file_path": ieee_file_path,
        "bus_ids": bus_ids,
        "branch_ids": branch_ids
    }


def test_ieee14_loadflow(start_jvm, init_test_data):
    """Test load flow calculation for IEEE 14-bus system."""
    # Step 1: Configure and Start the JVM (Handled by start_jvm fixture)

    # Step 2: Load data and create the Network Model
    # ODM related classes
    from org.ieee.odm.adapter.ieeecdf import IeeeCDFAdapter
    from org.interpss.odm.mapper import ODMAclfParserMapper
    from org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter import IEEECDFVersion

    # create instances of the classes we are going to used
    fileAdapter = IeeeCDFAdapter(IEEECDFVersion.Default)
    fileAdapter.parseInputFile(init_test_data["file_path"])
    aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()

    # Step 3: Run Load Flow Algorithm
    # InterPSS core related classes
    from com.interpss.core import LoadflowAlgoObjectFactory

    aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)
    aclfAlgo.loadflow()

    # Step 4: Process the simulation results
    # InterPSS aclf result exchange related classes
    from org.interpss.plugin.exchange import AclfResultExchangeAdapter

    exAdapter = AclfResultExchangeAdapter(aclfNet)

    # Create bus result bean set and fill it with load flow results
    exAdapter.setBusIds(init_test_data["bus_ids"])
    exAdapter.fillBusResult()

    volt_mag = np.array(exAdapter.getBusResultBean().volt_mag, dtype=np.double, copy=False)
    volt_ang = np.array(exAdapter.getBusResultBean().volt_ang, dtype=np.double, copy=False)

    # Create branch result bean set and fill it with load flow results
    exAdapter.setBranchIds(init_test_data["branch_ids"])
    exAdapter.fillBranchResult()

    p_f2t = np.array(exAdapter.getBranchResultBean().p_f2t, dtype=np.double, copy=False)
    q_f2t = np.array(exAdapter.getBranchResultBean().q_f2t, dtype=np.double, copy=False)

    result = {
        "volt_mag": volt_mag,
        "volt_ang": volt_ang,
        "p_f2t": p_f2t,
        "q_f2t": q_f2t,
        "err": None
    }

    # Check for errors
    assert result["err"] is None, f"Load flow failed with error: {result['err']}"

    # Verify results exist
    assert result["volt_mag"] is not None, "Voltage magnitude should not be None"
    assert result["volt_ang"] is not None, "Voltage angle should not be None"
    assert result["p_f2t"] is not None, "Active power flow should not be None"
    assert result["q_f2t"] is not None, "Reactive power flow should not be None"

    # Print results for debugging
    print("\nBus Voltage Magnitude:")
    print(f"mag: {result['volt_mag']}")
    print("\nBus Voltage Angle:")
    print(f"ang: {result['volt_ang']}")
    print("\nBranch P f2t:")
    print(f"p_f2t: {result['p_f2t']}")
    print("\nBranch Q f2t:")
    print(f"q_f2t: {result['q_f2t']}")

    # Basic validation of values
    assert len(result["volt_mag"]) > 0, "Voltage magnitude array should not be empty"
    assert result["volt_mag"][0] == pytest.approx(1.06, abs=1e-3), \
        f"Expected Bus1 voltage magnitude ~1.06, got {result['volt_mag'][0]}"
