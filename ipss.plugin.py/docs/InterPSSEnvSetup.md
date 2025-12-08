InterPSS Simulation Env Setup
==============

InterPSS Java objects are imported to the Python env through JPyte. In the ipss module in the src/interpss.py, a set of pre-defined InterPSS Java objects are imported.

	# import InterPSS module
	from src.interpss import ipss

	# create InterPSS instances
	net = ipss.CoreObjectFactory.createAclfNetwork()
	net.setBaseKva(100000)

	# another way to import InterPSS Java objects
	from com.interpss.core import CoreObjectFactory
	net = CoreObjectFactory.createAclfNetwork()
