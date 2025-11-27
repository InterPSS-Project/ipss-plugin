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

# PSS/E output related classes
from org.interpss.display.impl import AclfOut_PSSE
from org.interpss.display.impl.AclfOut_PSSE import Format

# ODM related classes
from org.ieee.odm.adapter.psse.raw import PSSERawAdapter
from org.interpss.odm.mapper import ODMAclfParserMapper
from org.ieee.odm.adapter.IODMAdapter import NetType
from org.ieee.odm.adapter.psse.PSSEAdapter import PsseVersion

# InterPSS aclf result exchange related classes
from org.interpss.plugin.exchange import AclfResultExchangeAdapter

# InterPSS utility classes
from org.interpss.numeric.util import PerformanceTimer

# Create instances of the classes we are going to use
adapter = PSSERawAdapter(PsseVersion.PSSE_33)

# Use platform-independent path handling for test data
raw_path = str(script_dir.parent / "testData" / "psse" / "ACTIVSg25k.RAW")
adapter.parseInputFile(raw_path)
net = ODMAclfParserMapper().map2Model(adapter.getModel()).getAclfNet()

algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
# the following two settings are false by default, but they are critical for some real-world networks due to data quality issues
algo.getDataCheckConfig().setTurnOffIslandBus(True)
algo.getDataCheckConfig().setAutoTurnLine2Xfr(True)

# Run power flow
algo.getLfAdjAlgo().setApplyAdjustAlgo(False)
algo.loadflow()

busIds = []
net.getBusList().forEach(lambda bus: busIds.append(bus.getId()))
print(f"{len(busIds)} buses")

exAdapter = AclfResultExchangeAdapter(net)

# Create bus result bean set and fill it with load flow results
exAdapter.setBusIds(busIds)
exAdapter.fillBusResult();

timer = PerformanceTimer()
net.getBusList().forEach(lambda bus: 
        bus.getVoltageMag())
timer.log("iterate bus set(0)")   

timer.start()
bus_result = exAdapter.getBusResultBean()
for busInfo in bus_result.volt_mag: 
        x = busInfo
timer.log("iterate bus set(1)")   

timer.start()
volt_mag = np.array(exAdapter.getBusResultBean().volt_mag,  dtype=np.double, copy=False)
for busInfo in volt_mag: 
        x = busInfo
timer.log("iterate bus set(2)")   

    
# Shutdown JVM
jpype.shutdownJVM()
