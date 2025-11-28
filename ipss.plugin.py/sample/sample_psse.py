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

# ODM related classes
from org.ieee.odm.adapter.psse.raw import PSSERawAdapter
from org.interpss.odm.mapper import ODMAclfParserMapper
from org.ieee.odm.adapter.IODMAdapter import NetType
from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion

# create instances of the classes we are going to used
adapter = PSSERawAdapter(PsseVersion.PSSE_30)

# Use platform-independent path handling for test data
raw_path = str(script_dir.parent /  "tests" / "testData" / "psse" / "IEEE9Bus" / "ieee9.raw")
adapter.parseInputFile(raw_path)
net = ODMAclfParserMapper().map2Model(adapter.getModel()).getAclfNet()

# InterPSS core related classes
from com.interpss.core import LoadflowAlgoObjectFactory

algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

algo.loadflow()

# InterPSS output related classes
from org.interpss.display import AclfOutFunc

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(AclfOutFunc.loadFlowSummary(net))

# PSS/E output related classes
from org.interpss.display.impl import AclfOut_PSSE
from org.interpss.display.impl.AclfOut_PSSE import Format

# print out more detailed power flow results in PSS/E style
# print(AclfOut_PSSE.lfResults(net, PSSEOutFormat.GUI))

# Shutdown JVM
jpype.shutdownJVM()
