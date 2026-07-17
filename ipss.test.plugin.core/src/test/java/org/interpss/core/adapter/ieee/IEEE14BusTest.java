package org.interpss.core.adapter.ieee;

import org.interpss.CorePluginFactory;
import org.interpss.CorePluginTestSetup;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.numeric.datatype.Unit.UnitType;
import org.interpss.util.AclfNetJsonComparator;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;

import org.junit.jupiter.api.Test;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.JacobianMatrixType;
import com.interpss.core.aclf.adpter.AclfSwingBusAdapter;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.core.algo.NrMethodConfig;

public class IEEE14BusTest extends CorePluginTestSetup {
	@Test 
	public void polarCoordinateTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	algo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
		
		assertTrue(aclfNet.isLfConverged());
	}
	
	@Test 
	public void xyCoordinateTest() throws Exception {
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testData/adpter/ieee_format/ieee14_comma.ieee")
				.getAclfNet();	
		
	  	LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(aclfNet);
	  	
	  	algo.setLfMethod(AclfMethodType.NR);
	  	algo.setMaxIterations(20);
	  	algo.setTolerance(0.0001);
	  	
		// at this point, the network is in polar coordinate
		// transfer voltage (mag, ang) to (Vx, Vy) when changing the coordinate	in the setPolarCoordinate() method.
	  	aclfNet.setPolarCoordinate(false);
	  	
	  	NrMethodConfig nrMethodConfig = algo.getNrMethodConfig();
	  	//nrMethodConfig.setCoordinate(JacobianMatrixType.NR_XY_COORDINATE);
	  	// re-configure the Nr solver with the updated config
	  	algo.getLfCalculator().getNrSolver().reConfigSolver(nrMethodConfig);
		
	  	algo.loadflow();
		System.out.println("MaxMismatch: " + aclfNet.maxMismatch(AclfMethodType.NR));
		
		//System.out.println(AclfOutFunc.loadFlowSummary(aclfNet));
		
		assertTrue(aclfNet.isLfConverged());
	}
}

