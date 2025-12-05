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

# Load data and create the Network Model
#from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion

file_path = str(script_dir.parent / "tests" / "testData" / "psse" / "IEEE9Bus" / "ieee9.raw")
net = ipss.PsseRawFileAdapter.createAclfNet(file_path, ipss.PsseRawFileAdapter.version.PSSE_30)

# InterPSS core related classes
algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

algo.loadflow()

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(ipss.AclfOutFunc.loadFlowSummary(net))

# print out more detailed power flow results in PSS/E style
print(ipss.AclfOut_PSSE.lfResults(net, ipss.PSSEOutFormat.GUI))

# Shutdown JVM
jpype.shutdownJVM()
