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
from src.config.config_mgr import ConfigManager, JvmManager

# Load configuration file
config_path=str(project_root / "config" / "config.json")
config = ConfigManager.load_config(config_path)
# Initialize and start the JVM
JvmManager.initialize_jvm(config)

#
# Step 2:  Load data and create the Network Model
#

from src.adapter.input_adapter import IeeeFileAdapter
#from org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter import  IEEECDFVersion

file_path = str(script_dir.parent / "tests" / "testData" / "ieee" / "IEEE14Bus.ieee")
aclfNet = IeeeFileAdapter.createAclfNet(file_path)

#
# Step 3:  Run Load Flow Algorithm
#

# InterPSS core related classes
#from com.interpss.core import CoreObjectFactory
from com.interpss.core import LoadflowAlgoObjectFactory

aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)

aclfAlgo.loadflow()

#
# Step 4:  Process the simulation results
#

# InterPSS output related classes
from org.interpss.display import AclfOutFunc

# InterPSS utility classes
from org.interpss.numeric.util import PerformanceTimer

# InterPSS aclf result exchange related classes
from org.interpss.plugin.exchange import AclfResultExchangeAdapter

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(AclfOutFunc.loadFlowSummary(aclfNet))

timer = PerformanceTimer()

# Define a set of bus ids
bus_ids = ["Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6", "Bus7", "Bus8", "Bus9", "Bus10", "Bus11", "Bus12", "Bus13", "Bus14"]
branch_ids = ["Bus1->Bus2(1)", "Bus1->Bus5(1)", "Bus2->Bus3(1)", "Bus2->Bus4(1)", "Bus2->Bus5(1)", "Bus3->Bus4(1)", "Bus4->Bus5(1)", "Bus4->Bus7",
                   "Bus4->Bus9(1)", "Bus5->Bus6(1)", "Bus6->Bus11(1)", "Bus6->Bus12(1)", "Bus6->Bus13(1)", "Bus7->Bus8(1)", "Bus7->Bus9(1)", "Bus9->Bus10(1)",
                   "Bus9->Bus14(1)", "Bus10->Bus11(1)", "Bus12->Bus13(1)", "Bus13->Bus14(1)"]

# Create net result bean set and fill it with load flow results
exAdapter = AclfResultExchangeAdapter(aclfNet)
netResult = exAdapter.createNetInfoBean(bus_ids, branch_ids)

print("Net Info:")
print("Has Element Info:", netResult.hasElemInfo)

print("Bus Voltage:")
# Use NumPy to transfer data in bulk
volt_mag = np.array(netResult.busResultBean.volt_mag, dtype=np.double, copy=False)
print(f"mag: {volt_mag}")

volt_ang = np.array(netResult.busResultBean.volt_ang, dtype=np.double, copy=False)
print(f"ang: {volt_ang}")    

print("Branch Flow:")

# Use NumPy to transfer data in bulk
p_f2t = np.array(netResult.branchResultBean.p_f2t, dtype=np.double, copy=False)
print(f"p_f2t: {p_f2t}")

# Use NumPy to transfer data in bulk
q_f2t = np.array(netResult.branchResultBean.q_f2t ,dtype=np.double, copy=False)
print(f"q_f2t: {q_f2t}")    
    
timer.log("Time: ")    

# 
# Step-5: Shutdown JVM
#
jpype.shutdownJVM()
