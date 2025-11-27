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
from org.interpss.display.impl.AclfOut_PSSE import Format as PSSEOutFormat
from org.interpss.odm.mapper import ODMAclfParserMapper
#from org.ieee.odm.adapter.IODMAdapter import NetType
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
    
# Shutdown JVM
jpype.shutdownJVM()
