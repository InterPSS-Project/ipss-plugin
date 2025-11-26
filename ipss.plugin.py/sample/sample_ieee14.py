import jpype
import jpype.imports
from jpype.types import *

from pathlib import Path

import numpy as np

# Get script directory for reliable path resolution
script_dir = Path(__file__).resolve().parent

# set jvm path
#jvm_path = jpype.getDefaultJVMPath()
jvm_path = "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/lib/libjli.dylib"
# jvm_path = f"{os.getenv('HOME')}/Library/Java/JavaVirtualMachines/corretto-21.0.9/Contents/Home/lib/libjli.dylib"
print(f"JVM Path: {jvm_path}")

# set the JAR path using platform-independent path handling
jar_path = str(script_dir.parent / "lib" / "ipss_runnable.jar")

# Start JVM with proper path separators
jpype.startJVM(jvm_path, "-ea", f"-Djava.class.path={jar_path}")

# InterPSS core related classes
from com.interpss.core import CoreObjectFactory
from com.interpss.core import LoadflowAlgoObjectFactory

# InterPSS output related classes
from org.interpss.display import AclfOutFunc

# ODM related classes
from org.ieee.odm.adapter.ieeecdf import IeeeCDFAdapter
from org.interpss.odm.mapper import ODMAclfParserMapper
from org.ieee.odm.adapter.IODMAdapter import NetType
from org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter import  IEEECDFVersion

# InterPSS aclf result exchange related classes
from org.interpss.plugin.exchange import AclfResultExchangeAdapter

# InterPSS utility classes
from org.interpss.numeric.util import PerformanceTimer

# create instances of the classes we are going to used
fileAdapter = IeeeCDFAdapter(IEEECDFVersion.Default)

# Use platform-independent path handling for test data
file_path = str(script_dir.parent / "testData" / "ieee" / "IEEE14Bus.ieee")
fileAdapter.parseInputFile(file_path)
aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()

aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)

aclfAlgo.loadflow()

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(AclfOutFunc.loadFlowSummary(aclfNet))

timer = PerformanceTimer()

# Define a set of bus ids
bus_ids = ["Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6", "Bus7", "Bus8", "Bus9", "Bus10", "Bus11", "Bus12", "Bus13", "Bus14"]
branch_ids = ["Bus1->Bus2(1)", "Bus1->Bus5(1)", "Bus2->Bus3(1)", "Bus2->Bus4(1)", "Bus2->Bus5(1)", "Bus3->Bus4(1)", "Bus4->Bus5(1)", "Bus4->Bus7",
                   "Bus4->Bus9(1)", "Bus5->Bus6(1)", "Bus6->Bus11(1)", "Bus6->Bus12(1)", "Bus6->Bus13(1)", "Bus7->Bus8(1)", "Bus7->Bus9(1)", "Bus9->Bus10(1)",
                   "Bus9->Bus14(1)", "Bus10->Bus11(1)", "Bus12->Bus13(1)", "Bus13->Bus14(1)"]

exAdapter = AclfResultExchangeAdapter(aclfNet)

# Create bus result bean set and fill it with load flow results
exAdapter.setBusIds(bus_ids)
exAdapter.fillBusResult()

print("Bus Voltage Magnitude:")
# Use NumPy to transfer data in bulk
volt_mag = np.array(exAdapter.getBusVoltMag(), dtype=np.double, copy=False)
print(f"type of volt_mag:{type(volt_mag)}")
print(f"mag: {volt_mag}")

# 报错：AttributeError: 'NoneType' object has no attribute 'volt_mag'
# volt_mag = np.array(exAdapter.busResultBean.volt_mag)
# print(f"mag: {volt_mag}")

# Use NumPy to transfer data in bulk
volt_ang = np.array(exAdapter.getBusVoltAng())
print(f"ang: {volt_ang}")    

# Create branch result bean set and fill it with load flow results
exAdapter.setBranchIds(branch_ids)
exAdapter.fillBranchResult()

# Use NumPy to transfer data in bulk
p_f2t = np.array(exAdapter.getBranchPf2t())
print(f"p_f2t: {p_f2t}")

# Use NumPy to transfer data in bulk
q_f2t = np.array(exAdapter.getBranchQf2t())
print(f"q_f2t: {q_f2t}")    
    
timer.log("Time: ")    

# Shutdown JVM
jpype.shutdownJVM()
