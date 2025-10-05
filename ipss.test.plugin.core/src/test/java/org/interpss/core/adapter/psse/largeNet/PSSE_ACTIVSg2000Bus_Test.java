package org.interpss.core.adapter.psse.largeNet;

import static org.interpss.plugin.pssl.plugin.IpssAdapter.FileFormat.PSSE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.ieee.odm.adapter.psse.PSSEAdapter;
import org.ieee.odm.adapter.psse.PSSEAdapter.PsseVersion;
import org.ieee.odm.adapter.psse.raw.PSSERawAdapter;
import org.ieee.odm.model.aclf.AclfModelParser;
import org.interpss.CorePluginTestSetup;
import org.interpss.IpssCorePlugin;
import org.interpss.display.AclfOutFunc;
import org.interpss.odm.mapper.ODMAclfParserMapper;
import org.interpss.plugin.pssl.plugin.IpssAdapter;
import org.junit.Test;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class PSSE_ACTIVSg2000Bus_Test  extends CorePluginTestSetup {
	
	@Test
	public void test_ACTIVSg2000_2016summerpeak_v30() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		
		/*
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_30);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v30/Texas2k/Texas2k_series24_case1_2016summerPeak_v30.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		AclfNetwork net =simuCtx.getAclfNet();
		*/
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v30/Texas2k/Texas2k_series24_case1_2016summerPeak_v30.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_30) 
				.load()
				.getImportedObj();
	  
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		
		// Verify bus voltage magnitudes and angles for key buses
		//bus 1001 voltage mag = 0.9781, angle = -26.51
		assertEquals(net.getBus("Bus1001").getVoltageMag(), 0.9781, 1.0E-4);
		assertEquals(net.getBus("Bus1001").getVoltageAng(), -26.51*Math.PI/180, 1.0E-2);

		//bus 7280 voltage mag = 1.0058, angle = -34.21
		assertEquals(net.getBus("Bus7280").getVoltageMag(), 1.0058, 1.0E-4);
		assertEquals(net.getBus("Bus7280").getVoltageAng(), -34.21*Math.PI/180, 1.0E-2);

		//bus 8160 voltage mag = 1.0059, angle = -51.73
		assertEquals(net.getBus("Bus8160").getVoltageMag(), 1.0059, 1.0E-4);
		assertEquals(net.getBus("Bus8160").getVoltageAng(), -51.73*Math.PI/180, 1.0E-2);

	}

	@Test
	public void test_ACTIVSg2000_2016summerpeak_v34() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		
		/*
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_34);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v34/Texas2k/Texas2k_series24_case1_2016summerPeak_noSub_v34.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		AclfNetwork net =simuCtx.getAclfNet();
		*/
		
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v34/Texas2k/Texas2k_series24_case1_2016summerPeak_noSub_v34.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_34) 
				.load()
				.getImportedObj();
	  
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		// Verify bus voltage magnitudes and angles for key buses
		//bus 1001 voltage mag = 0.9781, angle = -26.51
		assertEquals(net.getBus("Bus1001").getVoltageMag(), 0.9781, 1.0E-4);
		assertEquals(net.getBus("Bus1001").getVoltageAng(), -26.51*Math.PI/180, 1.0E-2);

		//bus 7280 voltage mag = 1.0058, angle = -34.21
		assertEquals(net.getBus("Bus7280").getVoltageMag(), 1.0058, 1.0E-4);
		assertEquals(net.getBus("Bus7280").getVoltageAng(), -34.21*Math.PI/180, 1.0E-2);

		//bus 8160 voltage mag = 1.0059, angle = -51.73
		assertEquals(net.getBus("Bus8160").getVoltageMag(), 1.0059, 1.0E-4);
		assertEquals(net.getBus("Bus8160").getVoltageAng(), -51.73*Math.PI/180, 1.0E-2);

	}

	@Test
	public void test_ACTIVSg2000_2016summerpeak_v35() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		
		/*
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_35);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v35/Texas2k/Texas2k_series24_case1_2016summerPeak_v35.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		AclfNetwork net =simuCtx.getAclfNet();
		*/
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v35/Texas2k/Texas2k_series24_case1_2016summerPeak_v35.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_35) 
				.load()
				.getImportedObj();
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		
		// Verify bus voltage magnitudes and angles for key buses
		//bus 1001 voltage mag = 0.9781, angle = -26.51
		assertEquals(net.getBus("Bus1001").getVoltageMag(), 0.9781, 1.0E-4);
		assertEquals(net.getBus("Bus1001").getVoltageAng(), -26.51*Math.PI/180, 1.0E-2);

		//bus 7280 voltage mag = 1.0058, angle = -34.21
		assertEquals(net.getBus("Bus7280").getVoltageMag(), 1.0058, 1.0E-4);
		assertEquals(net.getBus("Bus7280").getVoltageAng(), -34.21*Math.PI/180, 1.0E-2);

		//bus 8160 voltage mag = 1.0059, angle = -51.73
		assertEquals(net.getBus("Bus8160").getVoltageMag(), 1.0059, 1.0E-4);
		assertEquals(net.getBus("Bus8160").getVoltageAng(), -51.73*Math.PI/180, 1.0E-2);

	}


	@Test
	public void test_ACTIVSg2000_2016summerpeak_v36() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		
		/*
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_36);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		AclfNetwork net =simuCtx.getAclfNet();
		*/
		AclfNetwork net = IpssAdapter.importAclfNet("testData/psse/v36/Texas2k/Texas2k_series24_case1_2016summerPeak_v36.RAW")
				.setFormat(PSSE)
				.setPsseVersion(IpssAdapter.PsseVersion.PSSE_36) 
				.load()
				.getImportedObj();
		
		LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		//System.out.println(AclfOutFunc.loadFlowSummary(net));

		
		// Verify bus voltage magnitudes and angles for key buses
		//bus 1001 voltage mag = 0.9781, angle = -26.51
		assertEquals(net.getBus("Bus1001").getVoltageMag(), 0.9781, 1.0E-4);
		assertEquals(net.getBus("Bus1001").getVoltageAng(), -26.51*Math.PI/180, 1.0E-2);

		//bus 7280 voltage mag = 1.0058, angle = -34.21
		assertEquals(net.getBus("Bus7280").getVoltageMag(), 1.0058, 1.0E-4);
		assertEquals(net.getBus("Bus7280").getVoltageAng(), -34.21*Math.PI/180, 1.0E-2);

		//bus 8160 voltage mag = 1.0059, angle = -51.73
		assertEquals(net.getBus("Bus8160").getVoltageMag(), 1.0059, 1.0E-4);
		assertEquals(net.getBus("Bus8160").getVoltageAng(), -51.73*Math.PI/180, 1.0E-2);
	}

	//@Test
	public void test_ACTIVSg2000_v33() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_33);
		assertTrue(adapter.parseInputFile(
				"testData/adpter/psse/v33/ACTIVSg2000/ACTIVSg2000.raw"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
        
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    AclfNetwork net =simuCtx.getAclfNet();
	    
	  
	    LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}
	

	@Test
	public void test_ACTIVSg2000_summerpeak_v34() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_34);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v34/Texas2k/Texas2k_series24_case3_2024summerpeak_noSub.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
        
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    AclfNetwork net =simuCtx.getAclfNet();
	    
	  
	    LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}

	//@Test
	public void test_ACTIVSg2000_lowload_v34() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_34);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v34/Texas2k/Texas2k_series24_case4_2024lowload.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
        
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    AclfNetwork net =simuCtx.getAclfNet();
	    
	  
	    LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}

	//@Test
	public void test_ACTIVSg2000_highrenewables_v34() throws InterpssException{
		IpssCorePlugin.init();
		//IpssLogger.getLogger().setLevel(Level.WARNING);
		PSSEAdapter adapter = new PSSERawAdapter(PsseVersion.PSSE_34);
		assertTrue(adapter.parseInputFile(
				"testData/psse/v34/Texas2k/Texas2k_series24_case5_2024highrenewables.RAW"));
		AclfModelParser parser =(AclfModelParser) adapter.getModel();
		
        
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.DSTABILITY_NET);
		if (!new ODMAclfParserMapper()
					.map2Model(parser, simuCtx)) {
			System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
			return;
		}
		
		
	    AclfNetwork net =simuCtx.getAclfNet();
	    
	  
	    LoadflowAlgorithm aclfAlgo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
		
		//aclfAlgo.getDataCheckConfig().setAutoTurnLine2Xfr(true);

		//aclfAlgo.getLfAdjAlgo().setPowerAdjAppType(AdjustApplyType.POST_ITERATION);
		//aclfAlgo.getLfAdjAlgo().getPowerAdjConfig().setAdjust(false);
		aclfAlgo.getLfAdjAlgo().setApplyAdjustAlgo(false);
		aclfAlgo.setTolerance(1.0E-6);
		assertTrue(aclfAlgo.loadflow());
		System.out.println(AclfOutFunc.loadFlowSummary(net));
	}		
	
}
