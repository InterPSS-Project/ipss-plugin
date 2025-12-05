import jpype
import jpype.imports
from jpype.types import *

import sys
from pathlib import Path

import numpy as np

# Get script directory for reliable path resolution
script_dir = Path(__file__).resolve().parent
project_root = script_dir.parent
if str(project_root) not in sys.path:
    sys.path.append(str(project_root))

#  Configure and Start the JVM

from src.config import ConfigManager, JvmManager

# Load configuration file
config_path=str(project_root / "config" / "config.json")
config = ConfigManager.load_config(config_path)
# Initialize and start the JVM
JvmManager.initialize_jvm(config)

# import InterPSS modules
from src.interpss import ipss

file_path = str(script_dir.parent / "tests" / "testData" / "psse" / "ACTIVSg25k.RAW")
net = ipss.PsseRawFileAdapter.createAclfNet(file_path, ipss.PsseRawFileAdapter.version.PSSE_33)

algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
# the following two settings are false by default, but they are critical for some real-world networks due to data quality issues
algo.getDataCheckConfig().setTurnOffIslandBus(True)
algo.getDataCheckConfig().setAutoTurnLine2Xfr(True)

# Run power flow
algo.getLfAdjAlgo().setApplyAdjustAlgo(False)
algo.loadflow()

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
# print(ipss.AclfOutFunc.loadFlowSummary(net))

# uncomment the line below to print out more detailed power flow results in PSS/E style
# print(ipss.AclfOut_PSSE.lfResults(net, ipss.PSSEOutFormat.GUI))

# Create results directory if it doesn't exist
results_dir = script_dir / "results"
results_dir.mkdir(exist_ok=True)

results_filename = str(results_dir / "ACTIVSg25k_lf_results.txt")
output_file = open(results_filename, "w")

output_file.write(str(ipss.AclfOut_PSSE.lfResults(net, ipss.PSSEOutFormat.GUI).toString()))
output_file.close()

print(f"Detailed results saved to {results_filename}")
    
# Shutdown JVM
jpype.shutdownJVM()
