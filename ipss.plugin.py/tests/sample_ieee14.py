import sys
import unittest
from pathlib import Path

# Add project root to sys.path to allow importing ipss module
script_dir = Path(__file__).resolve().parent
project_root = script_dir.parent
if str(project_root) not in sys.path:
    sys.path.append(str(project_root))

from src.ieee.ieee_tool import IeeeLoadFlowTool


class TestIeeeLoadFlow(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        # Initialize the tool once for the test class
        # Assuming config.json is in the project root
        cls.tool = IeeeLoadFlowTool()

    def test_ieee14_loadflow(self):
        # Define path to test data
        ieee_file_path = str(script_dir / "testData" / "ieee" / "IEEE14Bus.ieee")

        # Define bus and branch IDs
        bus_ids = ["Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6", "Bus7", "Bus8", "Bus9", "Bus10", "Bus11", "Bus12",
                   "Bus13", "Bus14"]
        branch_ids = ["Bus1->Bus2(1)", "Bus1->Bus5(1)", "Bus2->Bus3(1)", "Bus2->Bus4(1)", "Bus2->Bus5(1)",
                      "Bus3->Bus4(1)", "Bus4->Bus5(1)", "Bus4->Bus7",
                      "Bus4->Bus9(1)", "Bus5->Bus6(1)", "Bus6->Bus11(1)", "Bus6->Bus12(1)", "Bus6->Bus13(1)",
                      "Bus7->Bus8(1)", "Bus7->Bus9(1)", "Bus9->Bus10(1)",
                      "Bus9->Bus14(1)", "Bus10->Bus11(1)", "Bus12->Bus13(1)", "Bus13->Bus14(1)"]

        # Run load flow
        result = self.tool.run_loadflow(ieee_file_path, bus_ids, branch_ids)

        # Check for errors
        self.assertIsNone(result["err"], f"Load flow failed with error: {result['err']}")

        # Verify results exist
        self.assertIsNotNone(result["volt_mag"])
        self.assertIsNotNone(result["volt_ang"])
        self.assertIsNotNone(result["p_f2t"])
        self.assertIsNotNone(result["q_f2t"])

        # Print results
        print("Bus Voltage Magnitude:")
        print(f"mag: {result['volt_mag']}")
        print("Bus Voltage Angle:")
        print(f"ang: {result['volt_ang']}")
        print("Branch P f2t:")
        print(f"p_f2t: {result['p_f2t']}")
        print("Branch Q f2t:")
        print(f"q_f2t: {result['q_f2t']}")

        # Basic validation of values
        if result["volt_mag"] is not None and len(result["volt_mag"]) > 0:
            self.assertAlmostEqual(result["volt_mag"][0], 1.06, places=3)


if __name__ == '__main__':
    unittest.main()
