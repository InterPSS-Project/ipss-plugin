import jpype
from pathlib import Path

# Get script directory for reliable path resolution across platforms
script_dir = Path(__file__).resolve().parent

# set jvm path
#jvm_path = jpype.getDefaultJVMPath()
jvm_path = "/Library/Java/JavaVirtualMachines/jdk-21.jdk/Contents/Home/lib/libjli.dylib"
print(f"JVM Path: {jvm_path}")

# set the JAR path using platform-independent path handling
jar_path = str(script_dir.parent / "lib" / "ipss_runnable.jar")

# start the JVM with the jar path
jpype.startJVM(jvm_path, "-ea", f"-Djava.class.path={jar_path}")

# load the Java classes to be used
CoreObjectFactory = jpype.JClass("com.interpss.core.CoreObjectFactory")
LoadflowAlgoObjectFactory = jpype.JClass("com.interpss.core.LoadflowAlgoObjectFactory")

AclfGenCode = jpype.JClass("com.interpss.core.aclf.AclfGenCode")
AclfLoadCode = jpype.JClass("com.interpss.core.aclf.AclfLoadCode")
AclfBranchCode = jpype.JClass("com.interpss.core.aclf.AclfBranchCode")

Complex = jpype.JClass("org.apache.commons.math3.complex.Complex")
AclfOutFunc = jpype.JClass("org.interpss.display.AclfOutFunc")

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
