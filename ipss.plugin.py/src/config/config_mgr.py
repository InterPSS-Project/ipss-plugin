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
