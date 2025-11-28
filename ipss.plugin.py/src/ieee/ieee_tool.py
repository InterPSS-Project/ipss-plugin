import os
import json
import jpype
import numpy as np
from pathlib import Path


class ConfigManager:
    """Manages configuration loading and path resolution."""
    
    @staticmethod
    def load_config(config_path=None):
        """
        Load configuration from file.
        
        Args:
            config_path (str, optional): Path to the configuration file.
                                         Defaults to config/config.json in project root.
        
        Returns:
            dict: Configuration dictionary with resolved paths.
        """
        if config_path is None:
            # Default to config/config.json in project root
            config_path = Path(__file__).resolve().parents[2] / "config" / "config.json"
        
        if not os.path.exists(config_path):
            raise FileNotFoundError(f"Config file not found at: {config_path}")
        
        with open(config_path, 'r') as f:
            config = json.load(f)
        
        # Expand HOME environment variable
        if 'jvm_path' in config:
            config['jvm_path'] = config['jvm_path'].replace("{HOME}", os.getenv('HOME'))
        
        # Resolve paths relative to project root (parent of config directory)
        project_root = Path(config_path).parent.parent
        
        if 'jar_path' in config and not os.path.isabs(config['jar_path']):
            config['jar_path'] = str(project_root / config['jar_path'])
        
        if 'log_path' in config and not os.path.isabs(config['log_path']):
            config['log_path'] = str(project_root / config['log_path'])
        
        return config


class JvmManager:
    """Manages JVM initialization and lifecycle."""
    
    @staticmethod
    def initialize_jvm(config):
        """
        Initialize the Java Virtual Machine.
        
        Args:
            config (dict): Configuration dictionary containing jvm_path, jar_path, and log_path.
        """
        if jpype.isJVMStarted():
            print("JVM already started, skipping initialization.")
            return
        
        jvm_path = config.get('jvm_path')
        jar_path = config.get('jar_path')
        log_path = config.get('log_path', 'logs/ipss.log')
        
        # Create log directory if it doesn't exist
        log_dir = os.path.dirname(log_path)
        if log_dir and not os.path.exists(log_dir):
            os.makedirs(log_dir)
        
        print(f"Starting JVM with path: {jvm_path}")
        print(f"Classpath: {jar_path}")
        
        try:
            jpype.startJVM(
                jvm_path,
                "-ea",
                f"-Djava.class.path={jar_path}",
                f"-Dlog.path={log_path}"
            )
            print("JVM started successfully.")
        except Exception as e:
            print(f"Failed to start JVM: {e}")
            raise


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
