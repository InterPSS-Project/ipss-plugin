import jpype
import jpype.imports
from jpype.types import *

import sys
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

args = parser.parse_args()
# print(args.simutype, args.format, args.input)

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

if args.simutype == "aclf":
    # create Loadflow algorithm and run loadflow calculation
    algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)
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
    # convert str "data/psse/ieee9_v31.raw" to "psse_ieee9_v31"
    file_name = args.input.split("/")[-1]
    file_name = file_name.split(".")[0]
    if args.format == "ieee":
        out_file_prefix = "ieee_" + file_name
    elif args.format == "psse":
        out_file_prefix = "psse_" + file_name
    else:
        print("Invalid format")
        exit(1)

    results_dir = script_dir / "result" / out_file_prefix
    # print(str(results_dir / (out_file_prefix + "_DF_bus.csv")))
    ipss.DFrameCsv.saver().save(dfBus, str(results_dir / (out_file_prefix + "_DF_bus.csv")))
    ipss.DFrameCsv.saver().save(dfGen, str(results_dir / (out_file_prefix + "_DF_gen.csv")))
    ipss.DFrameCsv.saver().save(dfLoad, str(results_dir / (out_file_prefix + "_DF_load.csv")))
    ipss.DFrameCsv.saver().save(dfBranch, str(results_dir / (out_file_prefix + "_DF_branch.csv")))
elif args.simutype == "ca":
    algo = ipss.ContingencyAlgoObjectFactory.createContingencyAlgorithm(net)
    algo.contingency()
else:
    print("Invalid simulation type")
    exit(1)

# Shutdown JVM
jpype.shutdownJVM()