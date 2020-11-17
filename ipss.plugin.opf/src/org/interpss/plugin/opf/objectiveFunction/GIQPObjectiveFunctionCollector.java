package org.interpss.plugin.opf.objectiveFunction;

import org.interpss.plugin.opf.common.OPFLogger;

import cern.colt.matrix.impl.SparseDoubleMatrix1D;
import cern.colt.matrix.impl.SparseDoubleMatrix2D;

import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.core.common.curve.QuadraticCurve;
import com.interpss.core.net.Bus;
import com.interpss.opf.dep.BaseOpfNetwork;
import com.interpss.opf.dep.OpfGenBus;

public class GIQPObjectiveFunctionCollector extends BaseObjectiveFunctionCollector{	
	SparseDoubleMatrix2D G = null;
	SparseDoubleMatrix1D a = null;
	private double penalty = 0;	
	
	public GIQPObjectiveFunctionCollector(BaseOpfNetwork opfNet){
		super(opfNet);		
		this.G = new SparseDoubleMatrix2D(numOfVar,numOfVar);
		this.a = new SparseDoubleMatrix1D(numOfVar );
		this.penalty = opfNet.getAnglePenaltyFactor();		
		this.setUandA();
	}
	
	public SparseDoubleMatrix2D buildG(){					
		return G;
	}
	
	public SparseDoubleMatrix1D buildA(){
		return a;
	}
	
	private void setUandA() {		
		//double baseMVA=opfNet.getBaseKva()/1000.0;		
		int genIndex=0;
		try {
			for (Bus b: opfNet.getBusList()){
				if(opfNet.isOpfGenBus(b)){
					NumericCurveModel incType = ((OpfGenBus)b).getIncCost().getCostModel();
					if(!incType.equals(NumericCurveModel.QUADRATIC)||
							((OpfGenBus)b).getIncCost().getQuadraticCurve()==null){
						OPFLogger.getLogger().severe("QP solver requires quadratic gen cost funtion for generator at bus: "
								+b.getNumber());						
					}else{
						QuadraticCurve quaCur = ((OpfGenBus)b).getIncCost().getQuadraticCurve();
						double constSq = quaCur.getA(); // para for square item
						double constLn = quaCur.getB(); // para for linear item						
						
						G.set(genIndex, genIndex, constSq*2);						
						a.set(genIndex,  constLn);	
						
						genIndex++;
					}
				}
			}
			// To make sure the inverse of G be possible, set a small number to other diagnoal element			
			for(int i= genIndex; i<G.rows();i++){
				G.set(i, i, 0.0001);
			}			
			
		}catch(Exception e){
			OPFLogger.getLogger().severe(e.toString());
		}		
	}	

}
