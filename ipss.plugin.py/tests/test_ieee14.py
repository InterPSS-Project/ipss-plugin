import sys
from pathlib import Path

import pytest

from tests.ieee.ieee_tool import IeeeLoadFlowTool

# Add project root to sys.path to allow importing ipss module
script_dir = Path(__file__).resolve().parent
project_root = script_dir.parent
if str(project_root) not in sys.path:
    sys.path.append(str(project_root))


@pytest.fixture(scope="module")
def ieee_tool():
    """Initialize the IeeeLoadFlowTool once for all tests in this module."""
    return IeeeLoadFlowTool(config_path=str(project_root / "config" / "config.json"))


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


def test_ieee14_loadflow(ieee_tool, init_test_data):
    """Test load flow calculation for IEEE 14-bus system."""
    # Run load flow
    result = ieee_tool.run_loadflow(
        init_test_data["file_path"],
        init_test_data["bus_ids"],
        init_test_data["branch_ids"]
    )

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
