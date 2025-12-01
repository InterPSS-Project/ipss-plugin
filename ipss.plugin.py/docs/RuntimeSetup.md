Runtime Env Setup
==============

##### JPype Setup

InterPSS Python API is based on the [JPype lib](https://jpype.readthedocs.io/en/latest/index.html)

 - First, make sure you have Java Runtime Environment (JRE) or OpenJDK version 8 or higher is installed on your machine.  Note: our initial testing is based on JDK21, OpenJDK 17.
 OpenJDK can be downloaded from [its website](https://www.openlogic.com/openjdk-downloads)

 - For JPype, please follow the [document on JPype website](https://jpype.readthedocs.io/en/latest/install.html) to install it. Using miniconda to create a virtual environment to install JPype is recommended.

##### config.json

InterPSS Python API uses the config.json located in the config dir to configure the Java env and the log.

	{
	  "jvm_path": "<JVM path>",
	  "jar_path": "lib/ipss_runnable.jar",
	  "log_config_path": "config/log4j2.xml"
	}
