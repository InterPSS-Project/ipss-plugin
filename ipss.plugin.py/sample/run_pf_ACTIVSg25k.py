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

# InterPSS core related classes
CoreObjectFactory = jpype.JClass("com.interpss.core.CoreObjectFactory")
LoadflowAlgoObjectFactory = jpype.JClass("com.interpss.core.LoadflowAlgoObjectFactory")

# InterPSS output related classes
AclfOutFunc = jpype.JClass("org.interpss.display.AclfOutFunc")

# PSS/E output related classes
AclfOut_PSSE = jpype.JClass("org.interpss.display.impl.AclfOut_PSSE")
PSSEOutFormat = jpype.JClass("org.interpss.display.impl.AclfOut_PSSE.Format")

# ODM related classes
PSSERawAdapter = jpype.JClass("org.ieee.odm.adapter.psse.raw.PSSERawAdapter")
ODMAclfParserMapper = jpype.JClass("org.interpss.odm.mapper.ODMAclfParserMapper")
NetType = jpype.JClass("org.ieee.odm.adapter.IODMAdapter.NetType")
PsseVersion = jpype.JClass("org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion")

# InterPSS aclf result exchange related classes
AclfResultExchangeAdapter = jpype.JClass("org.interpss.plugin.exchange.AclfResultExchangeAdapter")
AclfBusExchangeInfo = jpype.JClass("org.interpss.plugin.exchange.bean.AclfBusExchangeInfo")
AclfBranchExchangeInfo = jpype.JClass("org.interpss.plugin.exchange.bean.AclfBranchExchangeInfo")

# InterPSS utility classes
PerformanceTimer = jpype.JClass("org.interpss.numeric.util.PerformanceTimer")

# Create instances of the classes we are going to use
adapter = PSSERawAdapter(PsseVersion.PSSE_33)

# Use platform-independent path handling for test data
raw_path = str(script_dir.parent / "tests" / "testData" / "psse" / "ACTIVSg25k.RAW")
adapter.parseInputFile(raw_path)
net = ODMAclfParserMapper().map2Model(adapter.getModel()).getAclfNet()

algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
# the following two settings are false by default, but they are critical for some real-world networks due to data quality issues
algo.getDataCheckConfig().setTurnOffIslandBus(True)
algo.getDataCheckConfig().setAutoTurnLine2Xfr(True)

# Run power flow
algo.getLfAdjAlgo().setApplyAdjustAlgo(False)
algo.loadflow()


# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
# print(AclfOutFunc.loadFlowSummary(net))

# uncomment the line below to print out more detailed power flow results in PSS/E style
# print(AclfOut_PSSE.lfResults(net,PSSEOutFormat.GUI))

# Create results directory if it doesn't exist
results_dir = script_dir / "results"
results_dir.mkdir(exist_ok=True)

results_filename = str(results_dir / "ACTIVSg25k_lf_results.txt")
output_file = open(results_filename, "w")

output_file.write(str(AclfOut_PSSE.lfResults(net, PSSEOutFormat.GUI).toString()))
output_file.close()

print(f"Detailed results saved to {results_filename}")

timer = PerformanceTimer()
busIds = []
net.getBusList().forEach(lambda bus: busIds.append(bus.getId()))
print(f"{len(busIds)} buses")
timer.log("create busIds: ")   

exAdapter = AclfResultExchangeAdapter(net)

# Create bus result bean set and fill it with load flow results
timer.start()
exAdapter.setBusIds(busIds)
exAdapter.fillBusResult();
timer.log("fill bus results: ") 

timer.start()
for busInfo in exAdapter.getBusResultBean().volt_mag: x = busInfo
#   print(f"mag: {busInfo}")
timer.log("iterate bus set: ")   

    
# Shutdown JVM
jpype.shutdownJVM()
