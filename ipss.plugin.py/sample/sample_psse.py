import jpype
import jpype.imports
from jpype.types import *

from pathlib import Path

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

# create instances of the classes we are going to used
adapter = PSSERawAdapter(PsseVersion.PSSE_30)

# Use platform-independent path handling for test data
raw_path = str(script_dir.parent / "testData" / "psse" / "IEEE9Bus" / "ieee9.raw")
adapter.parseInputFile(raw_path)
net = ODMAclfParserMapper().map2Model(adapter.getModel()).getAclfNet()

algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

algo.loadflow()

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(AclfOutFunc.loadFlowSummary(net))

# print out more detailed power flow results in PSS/E style
# print(AclfOut_PSSE.lfResults(net, PSSEOutFormat.GUI))

# Shutdown JVM
jpype.shutdownJVM()
