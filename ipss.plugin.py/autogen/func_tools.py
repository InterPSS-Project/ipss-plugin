import json
from typing import Any
from autogen_core.tools import FunctionTool

# define session store class
class SessionStore:
    def __init__(self):
        self.aclfNet: any = None

# define tools and functions
def create_tools(session: SessionStore, script_dir: str, ipss: any):
    async def load_network_model(model_name: str, format: str) -> dict[str, any]:
        """Load network model

        参数:
            model_name: Network model name
            format: Network model format
                - "ieee": IEEE network model
                - "psse": PSSE network model

        返回:
            dict: When successful:
                {
                    "model_info": model information
                }
            Description: Load model into SessionStore for subsequent tool calls
            error:
                {
                    "error": "Error message"
                }

        示例:
            {"model_name": "IEEE14Bus.ieee"}
        """

        if (format == "psse") :
            file_path = str(script_dir.parent / "tests" / "testData" / "psse" / "IEEE9Bus" / model_name)
            print(f"File Path: {file_path}")
            # Automatically determine PSSE version by parsing the file
            psseVersion = ipss.IpssAdapter.parsePsseVersion(file_path)
            session.aclfNet = ipss.IpssAdapter.importAclfNet(file_path).setFormat(ipss.IpssAdapter.FileFormat.PSSE).setPsseVersion(psseVersion).load().getImportedObj()
        else :
            file_path = str(script_dir.parent / "tests" / "testData" / "ieee" / model_name)
            print(f"File Path: {file_path}")
            session.aclfNet = ipss.IeeeFileAdapter.createAclfNet(file_path)
            
        print(f"AclfNet loaded: {model_name}")
        

        model_info = f"""
            Number of Active Buses: {session.aclfNet.getNoActiveBus()}
            Number of Active Branches: {session.aclfNet.getNoActiveBranch()}
            Total Generation (MW): {session.aclfNet.totalGeneration(ipss.UnitType.mVA).getReal():.2f}
            Total Load (MW): {session.aclfNet.totalLoad(ipss.UnitType.mVA).getReal():.2f}
        """

        return {"model_info": model_info}

    async def run_loadflow(model_name: str, format: str) -> dict[str, any]:
        """Run loadflow analysis
        参数:
            model_name: Network model name
            format: Network model format
                - "ieee": IEEE network model
                - "psse": PSSE network model

        返回:
            dict: When successful:
                {
                    "convergence_status": "Load flow converged" or "Load flow not converged"
                }
            error:
                {
                    "error": "Error message"
                }
        示例:
            {"convergence_status": "Load flow converged"}
        """

        aclfAlgo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(session.aclfNet)

        aclfAlgo.loadflow()

        status = "Load flow converged" if session.aclfNet.isLfConverged() else "Load flow not converged"
        return {"convergence_status": status}

    return [load_network_model, run_loadflow]
