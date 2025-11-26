import os
import json
import jpype
import numpy as np
from pathlib import Path

class IeeeLoadFlowTool:
    def __init__(self, config_path=None):
        """
        Initialize the IeeeLoadFlowTool.
        
        Args:
            config_path (str, optional): Path to the configuration file. 
                                         Defaults to config.json in the project root.
        """
        if config_path is None:
            # Default to config.json in project root (assumed to be 2 levels up from this file)
            # this file is in ipss/ieee/ieee_tool.py -> project root is ../../
            config_path = Path(__file__).resolve().parents[2] / "config.json"
        
        self.config = self._load_config(config_path)
        self._init_jvm()
        self._init_classes()

    def _load_config(self, path):
        if not os.path.exists(path):
            raise FileNotFoundError(f"Config file not found at: {path}")
            
        with open(path, 'r') as f:
            config = json.load(f)
        
        # Expand HOME
        if 'jvm_path' in config:
            config['jvm_path'] = config['jvm_path'].replace("{HOME}", os.getenv('HOME'))
        
        # Resolve jar_path relative to config file location
        project_root = Path(path).parent
        if 'jar_path' in config:
             if not os.path.isabs(config['jar_path']):
                 config['jar_path'] = str(project_root / config['jar_path'])
        
        # Handle log path
        if 'log_path' in config:
             if not os.path.isabs(config['log_path']):
                 config['log_path'] = str(project_root / config['log_path'])
                 
        return config

    def _init_jvm(self):
        if not jpype.isJVMStarted():
            jvm_path = self.config.get('jvm_path')
            jar_path = self.config.get('jar_path')
            log_path = self.config.get('log_path', 'logs/ipss.log')
            
            # Create log dir if not exists
            log_dir = os.path.dirname(log_path)
            if log_dir and not os.path.exists(log_dir):
                os.makedirs(log_dir)

            print(f"Starting JVM with path: {jvm_path}")
            print(f"Classpath: {jar_path}")
            
            try:
                jpype.startJVM(jvm_path, "-ea", 
                               f"-Djava.class.path={jar_path}",
                               f"-Dlog.path={log_path}")
            except Exception as e:
                print(f"Failed to start JVM: {e}")
                raise

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
            fileAdapter = self.IeeeCDFAdapter(self.IEEECDFVersion.Default)
            fileAdapter.parseInputFile(ieee_file_path)
            
            model = fileAdapter.getModel()
            if model is None:
                 raise Exception("Failed to parse IEEE file, model is null")

            aclfNet = self.ODMAclfParserMapper().map2Model(model).getAclfNet()
            
            if aclfNet is None:
                raise Exception("Failed to map ODM model to AclfNet")

            # Run Loadflow
            aclfAlgo = self.LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet)
            aclfAlgo.loadflow()
            
            # Check convergence if needed, but standard flow just runs it.
            
            # Extract results using Adapter
            exAdapter = self.AclfResultExchangeAdapter(aclfNet)
            
            # Fill Bus Results
            exAdapter.setBusIds(bus_ids)
            if exAdapter.fillBusResult():
                # Use NumPy to transfer data in bulk
                result["volt_mag"] = np.array(exAdapter.getBusVoltMag())
                result["volt_ang"] = np.array(exAdapter.getBusVoltAng())
            else:
                result["err"] = "Failed to fill bus results"
                return result
            
            # Fill Branch Results
            exAdapter.setBranchIds(branch_ids)
            if exAdapter.fillBranchResult():
                # Use NumPy to transfer data in bulk
                result["p_f2t"] = np.array(exAdapter.getBranchPf2t())
                result["q_f2t"] = np.array(exAdapter.getBranchQf2t())
            else:
                result["err"] = "Failed to fill branch results"
                return result

        except Exception as e:
            result["err"] = str(e)
            # Print stack trace for debugging if needed
            import traceback
            traceback.print_exc()
            
        return result
