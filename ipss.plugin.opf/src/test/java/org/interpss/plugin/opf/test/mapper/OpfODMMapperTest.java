package org.interpss.plugin.opf.test.mapper;

import static org.junit.Assert.*;


import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.opf.matpower.OpfMatpowerAdapter;
import org.ieee.odm.model.opf.OpfModelParser;
import org.interpss.mapper.odm.ODMOpfParserMapper;
import org.interpss.numeric.datatype.Point;
import org.junit.Test;

import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfGen;
import com.interpss.opf.OpfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class OpfODMMapperTest {

	@Test
	public void test() {
		final LogManager logMgr = LogManager.getLogManager();
		Logger logger = Logger.getLogger("OPF_Matpower Logger");
		logger.setLevel(Level.INFO);
		logMgr.addLogger(logger);
		
		IODMAdapter adapter = new OpfMatpowerAdapter();
		assertTrue(adapter.parseInputFile("testdata/matpower/case3bus.m"));
		
		
		
		OpfModelParser parser = (OpfModelParser) adapter.getModel();
		
		SimuContext simuCtx = SimuObjectFactory.createSimuNetwork(SimuCtxType.OPF_NET);
		if (!new ODMOpfParserMapper().map2Model(parser, simuCtx)) {
  	  		System.out.println("Error: ODM model to InterPSS SimuCtx mapping error, please contact support@interpss.com");
  	  		return;
		}
		OpfNetwork opfNet = simuCtx.getOpfNet();
		assertTrue(opfNet.getNoBus()==3);
		
		OpfBus bus1 = opfNet.getBus("Bus1");
		
		OpfGen gen1 = bus1.getOpfGen();
		System.out.println("Gen 1 PQ limit[ max, min]:"+gen1.getOpfLimits().getPLimit().getMax()+","+gen1.getOpfLimits().getPLimit().getMin());
		assertTrue(gen1.getOpfLimits().getPLimit().getMax()==2.00);
		assertTrue(gen1.getOpfLimits().getPLimit().getMin()==0.20);
		
		assertTrue(gen1.getIncCost().getCostModel()==NumericCurveModel.PIECE_WISE);
		//Please note the startup and shutdown cost is not included yet, as they are not used in OPF, but they
		// will be necessary for unit commitment problem.
		// power-price points have been converted to pu -->(pu, $/pu);
		Point pt1= gen1.getIncCost().getPieceWiseCurve().getPoints().get(0);
		assertTrue(pt1.x == 0.2); //original power point 20 MW, so in pu it is 20/100 = 0.2 pu
		assertTrue(pt1.y == 10*100); //original price $/MW = 200/20 = 10, so price $/pu = 10*MVABase = 1000
		
	}
	
	

}
