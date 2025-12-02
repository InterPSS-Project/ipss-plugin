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

from src.adapter.input_adapter import PsseRawFileAdapter
#from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion

file_path = str(script_dir.parent / "tests" / "testData" / "psse" / "ACTIVSg25k.RAW")
net = PsseRawFileAdapter.createAclfNet(file_path, PsseRawFileAdapter.version.PSSE_33)

# InterPSS core related classes
from com.interpss.core import LoadflowAlgoObjectFactory

algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
# the following two settings are false by default, but they are critical for some real-world networks due to data quality issues
algo.getDataCheckConfig().setTurnOffIslandBus(True)
algo.getDataCheckConfig().setAutoTurnLine2Xfr(True)

# Run power flow
algo.getLfAdjAlgo().setApplyAdjustAlgo(False)
algo.loadflow()

# InterPSS aclf result exchange related classes
from org.interpss.plugin.exchange import AclfResultExchangeAdapter
from org.interpss.plugin.exchange import ContingencyResultAdapter

# InterPSS utility classes
from org.interpss.numeric.util import PerformanceTimer

busIds = []
net.getBusList().forEach(lambda bus: busIds.append(bus.getId()))
print(f"{len(busIds)} buses")

contResultAdapter = ContingencyResultAdapter()
exAdapter = AclfResultExchangeAdapter(net)

for i in range(10):
    # Create net result bean set and fill it with load flow results
    netResult = exAdapter.createNetInfoBean(busIds, [])
    # Store the result in contingency result adapter on the Java side
    contResultAdapter.getContingencyResultMap().put(f"contingency_{i}", netResult)
    
timer = PerformanceTimer()
for i in range(10):
    netResult = contResultAdapter.getContingencyResultMap().get(f"contingency_{i}")
    # Access bus voltage magnitude list
    volt_mag = netResult.busResultBean.volt_mag
    for busInfo in volt_mag: 
            x = busInfo
timer.log("iterate bus set(1)")   

timer.start()
for i in range(10):
    netResult = contResultAdapter.getContingencyResultMap().get(f"contingency_{i}")
    # Access bus voltage magnitude list as numpy array
    volt_mag = np.array(netResult.busResultBean.volt_mag,  dtype=np.double, copy=False)
    for busInfo in volt_mag: 
            x = busInfo
timer.log("iterate bus set(2)")   

    
# Shutdown JVM
jpype.shutdownJVM()
