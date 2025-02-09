package org.interpss.plugin.opf.test.solver;

import static org.junit.Assert.*;

import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.ieee.odm.adapter.IODMAdapter;
import org.ieee.odm.adapter.opf.matpower.OpfMatpowerAdapter;
import org.ieee.odm.model.opf.OpfModelParser;
import org.interpss.mapper.odm.ODMOpfParserMapper;
import org.interpss.plugin.opf.solver.IOpfSolver.constraintHandleType;
import org.interpss.plugin.opf.solver.lpsolve.LpsolveSolver;
import org.interpss.plugin.opf.util.OPFResultOutput;
import org.junit.Test;

import com.interpss.opf.OpfNetwork;
import com.interpss.simu.SimuContext;
import com.interpss.simu.SimuCtxType;
import com.interpss.simu.SimuObjectFactory;

public class OpfLPSolveSolverTest {

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
		
		LpsolveSolver solver = new LpsolveSolver(opfNet, constraintHandleType.AllIn);
		solver.solve();
		System.out.println(OPFResultOutput.opfResultSummary(opfNet));
		
	}

}
