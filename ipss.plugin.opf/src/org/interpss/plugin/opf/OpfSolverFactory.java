package org.interpss.plugin.opf;

import org.interpss.plugin.opf.common.OPFException;
import org.interpss.plugin.opf.solver.IOpfSolver;
import org.interpss.plugin.opf.solver.apache.lp.ApacheLPSolver;
import org.interpss.plugin.opf.solver.giqpsolve.GIQPSolver;
import org.interpss.plugin.opf.solver.lpsolve.LpsolveSolver;

import com.interpss.opf.dep.BaseOpfNetwork;
import com.interpss.opf.dep.OpfNetwork;

public class OpfSolverFactory {
	public static OpfSolverFactory opfSolverFactory =null;
	static {
		opfSolverFactory = new OpfSolverFactory();
	}
	
	public static LpsolveSolver createLpsolveLPSolver(BaseOpfNetwork opfnet,IOpfSolver.constraintHandleType type){
		LpsolveSolver solver = new LpsolveSolver(opfnet, type);
		return solver;
	}
	public static GIQPSolver createGIQPSolver(BaseOpfNetwork opfnet,IOpfSolver.constraintHandleType type){
		GIQPSolver solver = new GIQPSolver(opfnet, type);
		return solver;
	}
	
	public static ApacheLPSolver createApacheLPSolver(BaseOpfNetwork opfnet,IOpfSolver.constraintHandleType type){
		ApacheLPSolver solver = new ApacheLPSolver(opfnet, type);
		return solver;
	}
	
	public static IOpfSolver createOPFSolver(OPFSolverEnum solver,BaseOpfNetwork opfnet,
			IOpfSolver.constraintHandleType type) throws OPFException {
		if ( solver == OPFSolverEnum.LpsolveLPSolver ) 
			return new LpsolveSolver(opfnet, type);
		else if ( solver == OPFSolverEnum.GIQPSolver )
			return new GIQPSolver(opfnet, type);
		else if ( solver == OPFSolverEnum.ApacheLPSolver )
			return new ApacheLPSolver(opfnet, type);
		throw new OPFException("Error - unsupported solver type");
	}

}
