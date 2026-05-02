import jpype
import jpype.imports
from jpype.types import *

import sys
import os
from pathlib import Path
import argparse

import numpy as np

#
# Parse cmd arguments
#

# Define cmd arguments
parser = argparse.ArgumentParser(description="InterPSS Py Command Line Interface")
parser.add_argument("simutype", help="simulation type", choices=["aclf", "ca"])
parser.add_argument("format", help="case file format", choices=["ieee", "psse"])
parser.add_argument("input", help="case file path")
parser.add_argument("cont_file", nargs="?", default=None, help="contingency definition file path (only required for 'ca' simutype)")
parser.add_argument("monitor_file", nargs="?", default=None, help="monitoring file path (only required for 'ca' simutype)")

args = parser.parse_args()
# print(args.simutype, args.format, args.input, args.contDef, args.contResult)

#
# Configure and Start the JVM
#

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

#
# Load data and create the Network Model
#

# import InterPSS modules
from src.interpss import ipss

file_path = str(script_dir / args.input)
if args.format == "ieee":
    net = ipss.IeeeFileAdapter.createAclfNet(file_path)
elif args.format == "psse":
    net = ipss.PsseRawFileAdapter.createAclfNet(file_path)
else:
    print("Invalid format")
    exit(1)

#
# Perform the simulation tasks
#

# convert str "data/psse/ieee9_v31.raw" to "psse_ieee9_v31"
out_file_prefix = args.input.split("/")[-1]
out_file_prefix = out_file_prefix.split(".")[0]
#out_file_prefix = args.format + "_" + out_file_prefix

# get the parent directory name, "data/ieee/ieee118.ieee" -> "data/ieee"
input_dir = str(Path(args.input).parent)
results_dir = script_dir / input_dir / "result"
results_dir.mkdir(parents=True, exist_ok=True)

if args.simutype == "aclf":
    # create Loadflow algorithm and run loadflow calculation
    algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

    configFilename = str(project_root / "config" / "aclf_run.json")
    aclfRunConfig = ipss.AclfRunConfigRec.loadAclfRunConfig(configFilename)

    #aclfRunConfig.configAclfRun(algo, aclfRunConfig.polarCoordinate, aclfRunConfig.includeAdjustments, False)
    aclfRunConfig.configAclfRun(algo)
    #print(aclfRunConfig.toString())

    algo.loadflow()

    # basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power
    # print(ipss.AclfOutFunc.loadFlowSummary(net))

    # create the data frame adapter
    dfAdapter = ipss.AclfNetDFrameAdapter()
    # adapt the network model to the data frame
    dfAdapter.adapt(net)
    # get the data frames
    dfBus = dfAdapter.getDfBus()
    dfGen = dfAdapter.getDfGen()
    dfLoad = dfAdapter.getDfLoad()
    dfBranch = dfAdapter.getDfBranch()

    # write the data frames to csv files
    ipss.DFrameCsv.saver().save(dfBus, str(results_dir / (out_file_prefix + "_DF_bus.csv")))
    ipss.DFrameCsv.saver().save(dfGen, str(results_dir / (out_file_prefix + "_DF_gen.csv")))
    ipss.DFrameCsv.saver().save(dfLoad, str(results_dir / (out_file_prefix + "_DF_load.csv")))
    ipss.DFrameCsv.saver().save(dfBranch, str(results_dir / (out_file_prefix + "_DF_branch.csv")))
elif args.simutype == "ca":
    algo = ipss.DclfAlgoObjectFactory.createContingencyAnalysisAlgorithm(net)
    algo.calculateDclf(ipss.DclfMethod.INC_LOSS)

    # Import contingency definitions from the JSON file.
    cont_path = str(script_dir / args.cont_file)
    contingencRecs = ipss.ContingencyFileUtil.importContingenciesFromJson(ipss.JavaFile(cont_path))

    # Create the DCLF contingency list.
    dclfContList = ipss.DclfContingencyHelper(algo).createDclfContList(contingencRecs)

    # Import monitored branches from JSON.
    mon_path = str(script_dir / args.monitor_file)
    monitoredBranches = ipss.ContingencyFileUtil.importMonitoredBranchRecordsFromJson(ipss.JavaFile(mon_path))

    # Extract branch IDs and pass them as a Java Set<String>.
    monitoredBranchIds = ipss.JavaHashSet()
    for record in monitoredBranches:
        monitoredBranchIds.add(record.getBranchId())

    # Define contingency analysis configuration.
    dclf_config = ipss.DclfContingencyConfig()
    dclf_config.setDclfInclLoss(True)
    dclf_config.setOverloadThreshold(90)  # in percentage

    # Perform contingency analysis.
    threads = os.cpu_count()
    print(f"Using {threads} threads for contingency analysis")

    results = ipss.ParallelDclfContingencyAnalyzer.performContingencyAnalysis(
        net,
        dclfContList,
        monitoredBranchIds,
        dclf_config.getOverloadThreshold(),
        dclf_config.isDclfInclLoss(),
        threads
    )

    dfAdapter = ipss.DclfContingencyDFrameAdapter()
    dfCaRec = dfAdapter.adapt(results)

    # Write CA dataframe to CSV.
    ipss.DFrameCsv.saver().save(
        dfCaRec, str(results_dir / (out_file_prefix + "_DF_contingency.csv"))
    )
else:
    print("Invalid simulation type")
    exit(1)

# Shutdown JVM
jpype.shutdownJVM()
