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

from src.config.config_mgr import ConfigManager, JvmManager

# Load configuration file
config_path=str(project_root / "config" / "config.json")
config = ConfigManager.load_config(config_path)
# Initialize and start the JVM
JvmManager.initialize_jvm(config)

# load the Java classes to be used
from com.interpss.core import CoreObjectFactory
from com.interpss.core import LoadflowAlgoObjectFactory

from com.interpss.core.aclf import AclfGenCode
from com.interpss.core.aclf import AclfLoadCode
from com.interpss.core.aclf import AclfBranchCode

from org.apache.commons.math3.complex import Complex
from org.interpss.display import AclfOutFunc

# create instances
#IpssCorePlugin.init()
net = CoreObjectFactory.createAclfNetwork()
net.setBaseKva(100000)

bus1 = CoreObjectFactory.createAclfBus("Bus1", net).get()
bus1.setBaseVoltage(4000.0)
bus1.setGenCode(AclfGenCode.SWING)

bus2 = CoreObjectFactory.createAclfBus("Bus2", net).get()
bus2.setLoadCode(AclfLoadCode.CONST_P)
bus2.setBaseVoltage(4000.0)
bus2.setLoadP(1)
bus2.setLoadQ(0.8)

branch = CoreObjectFactory.createAclfBranch()
net.addBranch(branch, "Bus1", "Bus2")
branch.setBranchCode(AclfBranchCode.LINE)
branch.setZ(Complex(0.05, 0.1))

algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

algo.loadflow()
	  	
print(AclfOutFunc.loadFlowSummary(net))

# shutdown JVM
jpype.shutdownJVM()
