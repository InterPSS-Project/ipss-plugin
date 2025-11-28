import os

import jpype
import numpy as np

from src.config.config_mgr import ConfigManager, JvmManager


class IeeeLoadFlowTool:
    def __init__(self, config_path=None):
        """
        Initialize the IeeeLoadFlowTool.
        
        Args:
            config_path (str, optional): Path to the configuration file. 
                                         Defaults to config/config.json in the project root.
        """
        self.config = ConfigManager.load_config(config_path)
        JvmManager.initialize_jvm(self.config)
        self._init_classes()

    def _init_classes(self):
        # InterPSS core related classes
        self.LoadflowAlgoObjectFactory = jpype.JClass("com.interpss.core.LoadflowAlgoObjectFactory")
        
        # ODM related classes
        self.IeeeCDFAdapter = jpype.JClass("org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter")
        self.ODMAclfParserMapper = jpype.JClass("org.interpss.odm.mapper.ODMAclfParserMapper")
        self.IEEECDFVersion = jpype.JClass("org.ieee.odm.adapter.ieeecdf.IeeeCDFAdapter.IEEECDFVersion")
        
        # InterPSS aclf result exchange related classes
        self.AclfResultExchangeAdapter = jpype.JClass("org.interpss.plugin.exchange.AclfResultExchangeAdapter")

    def run_loadflow(self, ieee_file_path, bus_ids, branch_ids):
        """
        Run load flow on the given IEEE format file and return results.
        
        Args:
            ieee_file_path (str): Path to the IEEE format file.
            bus_ids (list): List of bus IDs to retrieve results for.
            branch_ids (list): List of branch IDs to retrieve results for.
            
        Returns:
            dict: Dictionary containing volt_mag, volt_ang, p_f2t, q_f2t, and err.
        """
        result = {
            "volt_mag": None,
            "volt_ang": None,
            "p_f2t": None,
            "q_f2t": None,
            "err": None
        }

        try:
            if not os.path.exists(ieee_file_path):
                raise FileNotFoundError(f"IEEE file not found: {ieee_file_path}")

            # Load file
            file_adapter = self.IeeeCDFAdapter(self.IEEECDFVersion.Default)
            file_adapter.parseInputFile(ieee_file_path)
            
            model = file_adapter.getModel()
            if model is None:
                 raise Exception("Failed to parse IEEE file, model is null")

            aclf_net = self.ODMAclfParserMapper().map2Model(model).getAclfNet()
            
            if aclf_net is None:
                raise Exception("Failed to map ODM model to AclfNet")

            # Run Loadflow
            aclf_algo = self.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclf_net)
            aclf_algo.loadflow()
            
            # Check convergence if needed, but standard flow just runs it.
            
            # Extract results using Adapter
            ex_adapter = self.AclfResultExchangeAdapter(aclf_net)
            
            # Fill Bus Results
            ex_adapter.setBusIds(bus_ids)
            if ex_adapter.fillBusResult():
                # Use NumPy to transfer data in bulk
                result["volt_mag"] = np.array(ex_adapter.getBusVoltMag())
                result["volt_ang"] = np.array(ex_adapter.getBusVoltAng())
            else:
                result["err"] = "Failed to fill bus results"
                return result
            
            # Fill Branch Results
            ex_adapter.setBranchIds(branch_ids)
            if ex_adapter.fillBranchResult():
                # Use NumPy to transfer data in bulk
                result["p_f2t"] = np.array(ex_adapter.getBranchPf2t())
                result["q_f2t"] = np.array(ex_adapter.getBranchQf2t())
            else:
                result["err"] = "Failed to fill branch results"
                return result

        except Exception as e:
            result["err"] = str(e)
            # Print stack trace for debugging if needed
            import traceback
            traceback.print_exc()
            
        return result
