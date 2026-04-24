import argparse

parser = argparse.ArgumentParser(description="InterPSS Py Command Line Interface")
parser.add_argument("simutype", help="simulation type", choices=["loadflow", "contingency"])
parser.add_argument("input", help="input file path")

args = parser.parse_args()
print(args.simutype, args.input)
