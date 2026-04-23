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

#
#  Step 1:  Configure and Start the JVM
#
from src.config import ConfigManager, JvmManager

# Load configuration file
config_path=str(project_root / "config" / "config.json")
config = ConfigManager.load_config(config_path)
# Initialize and start the JVM
JvmManager.initialize_jvm(config)

#
# Step 2:  Load data and create the Network Model
#

# import InterPSS modules
from src.interpss import ipss

file_path = str(script_dir.parent / "tests" / "testData" / "ieee" / "IEEE14Bus.ieee")
aclfNet = ipss.IeeeFileAdapter.createAclfNet(file_path)

#
# Step 3:  Run Load Flow Algorithm
#

aclfAlgo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)

aclfAlgo.loadflow()

#
# Step 4:  Process the simulation results
#

dfAdapter = ipss.AclfNetDFrameAdapter()

dfAdapter.adapt(aclfNet)
dfBus = dfAdapter.getDfBus()
dfGen = dfAdapter.getDfGen()
dfLoad = dfAdapter.getDfLoad()
dfBranch = dfAdapter.getDfBranch()

print("Number of rows with filter in dfBus: " + str(dfBus.height()))
print("Number of rows with filter in dfGen: " + str(dfGen.height()))
print("Number of rows with filter in dfLoad: " + str(dfLoad.height()))
print("Number of rows with filter in dfBranch: " + str(dfBranch.height()))

results_dir = script_dir / "results"
ipss.DFrameCsv.saver().save(dfBus, str(results_dir / "Ieee14Bus_DF_bus.csv"))
ipss.DFrameCsv.saver().save(dfGen, str(results_dir / "Ieee14Bus_DF_gen.csv"))
ipss.DFrameCsv.saver().save(dfLoad, str(results_dir / "Ieee14Bus_DF_load.csv"))
ipss.DFrameCsv.saver().save(dfBranch, str(results_dir / "Ieee14Bus_DF_branch.csv"))

# 
# Step-5: Shutdown JVM
#
jpype.shutdownJVM()
