import argparse
import jpype
import jpype.imports
from jpype.types import *

import sys
from pathlib import Path

import numpy as np

parser = argparse.ArgumentParser(description="InterPSS Py Command Line Interface")
parser.add_argument("simutype", help="simulation type", choices=["aclf", "ca"])
parser.add_argument("format", help="case file format", choices=["ieee", "psse"])
parser.add_argument("input", help="case file path")

args = parser.parse_args()
print(args.simutype, args.format, args.input)

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

# InterPSS core related classes
algo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net)

algo.loadflow()

# basic load flow results summary, showing the bus type, voltage magnitude and angle and bus net power  	
print(ipss.AclfOutFunc.loadFlowSummary(net))

# Shutdown JVM
jpype.shutdownJVM()