import json
from typing import Any
from autogen_core.tools import FunctionTool

class SessionStore:
    def __init__(self):
        self.aclfNet: any = None

def make_tools(store: SessionStore, script_dir: str, ipss: any):
    async def load_network_model(model_name: str) -> dict[str, any]:
        """Load network model

        参数:
            model_name: Network model name

        返回:
            dict: When successful:
                {
                    "model_name": 模型名称
                }
            Description: Load model into SessionStore for subsequent tool calls
            error:
                {
                    "error": "Error message"
                }

        示例:
            {"model_name": "IEEE14Bus.ieee"}
        """
     
        file_path = str(script_dir.parent / "tests" / "testData" / "ieee" / model_name)
        print(f"File Path: {file_path}")
        store.aclfNet = ipss.IeeeFileAdapter.createAclfNet(file_path)
        print(f"AclfNet loaded: {model_name}")

        return {"model_name": model_name}

    async def run_loadflow(model_name: str) -> dict[str, any]:
        """Run loadflow analysis

        参数:
            model_name: Network model name

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

        aclfAlgo = ipss.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(store.aclfNet)

        aclfAlgo.loadflow()

        status = "Load flow converged" if store.aclfNet.isLfConverged() else "Load flow not converged"
        return {"convergence_status": status}

    return [load_network_model, run_loadflow]
