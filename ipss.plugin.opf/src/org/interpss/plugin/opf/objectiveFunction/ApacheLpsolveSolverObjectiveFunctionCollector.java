package org.interpss.plugin.opf.objectiveFunction;

import java.util.List;

import org.apache.commons.math3.linear.OpenMapRealVector;
import org.interpss.numeric.datatype.Point;
import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.core.common.curve.PieceWiseCurve;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;
import com.interpss.opf.datatype.OpfConstraintType;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class ApacheLpsolveSolverObjectiveFunctionCollector extends BaseObjectiveFunctionCollector{
	
	public ApacheLpsolveSolverObjectiveFunctionCollector(OpfNetwork opfNet){
		super(opfNet);
	}
	
	
	public void genCostFunctionRefinement(List<OpfConstraint> cstContainer) {
		int genIndex = 0;		
		int totalVar = numOfVar;
		int totalVarIdx = numOfVar;		
		try {
			for (OpfBus bus: opfNet.getBusList()){	
				//OpfBus bus = (OpfBus)b;
				if(bus.isOpfGen()){
					NumericCurveModel incType = bus.getOpfGen().getIncCost().getCostModel();
					if(!incType.equals(NumericCurveModel.PIECE_WISE)||
							bus.getOpfGen().getIncCost().getPieceWiseCurve()==null){
						OPFLogger.getLogger().severe("LP solver requires piecewise linear gen cost funtion for generator at bus: "
								+bus.getNumber());						
					}else{
						//lpsolver.setColName(genIndex, "Pg" + (b.getSortNumber()+1));
						PieceWiseCurve pw = bus.getOpfGen().getIncCost().getPieceWiseCurve();
						int np = pw.getPoints().size();
						double[] mw = new double[np];
						double[] price = new double[np];
						int pcnt = 0;						
						for (Point p: pw.getPoints()){
							mw[pcnt] = p.x;
							price[pcnt] = p.y;							
							pcnt++;
						}						
						
						/*
						 * the following inequality constraint will be added for each segment
						slope*x-y<= slope*xj_1 - cj_1;
						*/
						String des = "Gen piecewise cost function additional constraint @"+ bus.getId();
						double[] slope = new double[np];
						double[] xj = new double[np];
						double[] cj = new double[np];
						cj[0] = price[0]*mw[0];
						int cnt = 1;
						for (int i = 1; i < np; i++){
							if(mw[i] != mw[i-1]){
								
								if(price[i] == price[i-1]){ // this is a flat line
									slope[cnt] = price[i-1];
									xj[cnt] = mw[i];
									cj[cnt] = cj[cnt-1] + (xj[cnt]-xj[cnt-1])*slope[cnt];
									
									createNewConstraint(cstContainer, genIndex, totalVarIdx, slope[cnt], xj[cnt], cj[cnt],des);
									cnt++;
								}else{ // maybe requires segment refinement.
									
								}							
															
							}								
						}	
						totalVarIdx++;
						genIndex++;
						totalVar++;	
					}
				}			
			}			
		}catch(Exception e){
			OPFLogger.getLogger().severe(e.toString());
		}			
		
	}
	
	private void createNewConstraint(List<OpfConstraint> cstContainer, int genIndex,
			int totalVarIdx, double slope, double xj, double cj, String des) {
		
		//OpfConstraint cst = new OpfConstraint();		
		int id = cstContainer.size();		
		double rh = slope * xj - cj;		
		IntArrayList colNo = new IntArrayList();
		DoubleArrayList val = new DoubleArrayList();
		colNo.add(genIndex);
		val.add(slope);
		colNo.add(totalVarIdx);
		val.add(-1);
		OpfConstraint cst = OpfSolverFactory.createOpfConstraint(id, des, rh, 0, OpfConstraintType.LESS_THAN, colNo, val);
		cstContainer.add(cst);
	}
	
	public OpenMapRealVector processGenCostFunction() {	
		OpenMapRealVector vec =  new OpenMapRealVector(numOfVar + numOfGen);
		
		for (int i = numOfGen + numOfBus; i < numOfVar + numOfGen; i++){	
			vec.setEntry(i, 1);					
		}		
		return vec;
	}
}
