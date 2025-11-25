import jpype
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
CoreObjectFactory = jpype.JClass("com.interpss.core.CoreObjectFactory")
LoadflowAlgoObjectFactory = jpype.JClass("com.interpss.core.LoadflowAlgoObjectFactory")

# InterPSS output related classes
AclfOutFunc = jpype.JClass("org.interpss.display.AclfOutFunc")

# ODM related classes
IeeeCDFAdapter = jpype.JClass("org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter")
ODMAclfParserMapper = jpype.JClass("org.interpss.odm.mapper.ODMAclfParserMapper")
NetType = jpype.JClass("org.ieee.odm.adapter.IODMAdapter.NetType")
IEEECDFVersion = jpype.JClass("org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter.IEEECDFVersion")

# InterPSS aclf result exchange related classes
AclfResultExchangeAdapter = jpype.JClass("org.interpss.plugin.exchange.AclfResultExchangeAdapter")
AclfBusExchangeInfo = jpype.JClass("org.interpss.plugin.exchange.bean.AclfBusExchangeInfo")
AclfBranchExchangeInfo = jpype.JClass("org.interpss.plugin.exchange.bean.AclfBranchExchangeInfo")

# create instances of the classes we are going to used
fileAdapter = IeeeCDFAdapter(IEEECDFVersion.Default)

# Use platform-independent path handling for test data
file_path = str(script_dir.parent / "testData" / "ieee" / "IEEE14Bus.dat")
fileAdapter.parseInputFile(file_path)
aclfNet = ODMAclfParserMapper().map2Model(fileAdapter.getModel()).getAclfNet()

aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)

aclfAlgo.loadflow()

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(AclfOutFunc.loadFlowSummary(aclfNet))


# Define a set of bus ids
bus_ids = ["Bus1", "Bus2", "Bus3", "Bus4", "Bus5", "Bus6", "Bus7", "Bus8", "Bus9", "Bus10", "Bus11", "Bus12", "Bus13", "Bus14"]
branch_ids = ["Bus1->Bus2(1)", "Bus1->Bus5(1)", "Bus2->Bus3(1)", "Bus2->Bus4(1)", "Bus2->Bus5(1)", "Bus3->Bus4(1)", "Bus4->Bus5(1)", "Bus4->Bus7",
                   "Bus4->Bus9(1)", "Bus5->Bus6(1)", "Bus6->Bus11(1)", "Bus6->Bus12(1)", "Bus6->Bus13(1)", "Bus7->Bus8(1)", "Bus7->Bus9(1)", "Bus9->Bus10(1)",
                   "Bus9->Bus14(1)", "Bus10->Bus11(1)", "Bus12->Bus13(1)", "Bus13->Bus14(1)"]

exAdapter = AclfResultExchangeAdapter(aclfNet);

# Create bus result bean set and fill it with load flow results
busBeanSet = AclfBusExchangeInfo(bus_ids);
exAdapter.fillBusResult(busBeanSet);

for busInfo in busBeanSet.volt_mag:
    print(f"mag: {busInfo}") 
    
for busInfo in busBeanSet.volt_ang:
    print(f"ang: {busInfo}")    

# Create branch result bean set and fill it with load flow results
braBeanSet = AclfBranchExchangeInfo(branch_ids);
exAdapter.fillBranchResult(braBeanSet);

for braInfo in braBeanSet.p_f2t:
    print(f"p_f2t: {braInfo}") 
    
for braInfo in braBeanSet.q_f2t:
    print(f"q_f2t: {braInfo}")    

# Shutdown JVM
jpype.shutdownJVM()
