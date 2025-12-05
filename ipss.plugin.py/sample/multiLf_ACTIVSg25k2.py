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

busIds = []
net.getBusList().forEach(lambda bus: busIds.append(bus.getId()))
print(f"{len(busIds)} buses")

# Create contingency result container
contResultContainer = ipss.ContingencyResultContainer()

for i in range(10):
    # Create net result bean set and fill it with load flow results
    exAdapter = ipss.ContingencyResultAdapter(net, "continId", None)   # None for outage branch since we do not actually perform any contingency analysis here
    netResult = exAdapter.createInfoBean(busIds, [])
    # Store the result in contingency result container on the Java side
    contResultContainer.getContingencyResultMap().put(f"contingency_{i}", netResult)
    
timer = ipss.PerformanceTimer()
for i in range(10):
    # Access contingency result from container using contingency id
    netResult = contResultContainer.getContingencyResultMap().get(f"contingency_{i}")
    # Access bus voltage magnitude list
    volt_mag = netResult.busResultBean.volt_mag
    for busInfo in volt_mag: 
            x = busInfo
timer.log("iterate bus set(1)")   

timer.start()
for i in range(10):
    # Access contingency result from container using contingency id
    netResult = contResultContainer.getContingencyResultMap().get(f"contingency_{i}")
    # Access bus voltage magnitude list as numpy array
    volt_mag = np.array(netResult.busResultBean.volt_mag,  dtype=np.double, copy=False)
    for busInfo in volt_mag: 
            x = busInfo
timer.log("iterate bus set(2)")   

# Shutdown JVM
jpype.shutdownJVM()
