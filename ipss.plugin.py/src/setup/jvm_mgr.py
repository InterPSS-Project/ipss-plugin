import os
import json
import jpype
import numpy as np
from pathlib import Path

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
