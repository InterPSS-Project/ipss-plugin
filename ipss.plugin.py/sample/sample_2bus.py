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

from src.config import ConfigManager, JvmManager

# Load configuration file
config_path=str(project_root / "config" / "config.json")
config = ConfigManager.load_config(config_path)
# Initialize and start the JVM
JvmManager.initialize_jvm(config)

# import InterPSS modules
from src.interpss import ipss

# create instances
#IpssCorePlugin.init()
net = ipss.CoreObjectFactory.createAclfNetwork()
net.setBaseKva(100000)

bus1 = ipss.CoreObjectFactory.createAclfBus("Bus1", net).get()
bus1.setBaseVoltage(4000.0)
bus1.setGenCode(ipss.AclfGenCode.SWING)

bus2 = ipss.CoreObjectFactory.createAclfBus("Bus2", net).get()
bus2.setLoadCode(ipss.AclfLoadCode.CONST_P)
bus2.setBaseVoltage(4000.0)
bus2.setLoadP(1)
bus2.setLoadQ(0.8)

branch = ipss.CoreObjectFactory.createAclfBranch()
net.addBranch(branch, "Bus1", "Bus2")
branch.setBranchCode(ipss.AclfBranchCode.LINE)
branch.setZ(ipss.Complex(0.05, 0.1))

algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

algo.loadflow()
	  	
print(ipss.AclfOutFunc.loadFlowSummary(net))

# shutdown JVM
jpype.shutdownJVM()
