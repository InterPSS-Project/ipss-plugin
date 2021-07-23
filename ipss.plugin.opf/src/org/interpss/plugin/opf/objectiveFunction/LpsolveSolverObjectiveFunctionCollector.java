package org.interpss.plugin.opf.objectiveFunction;

import org.interpss.numeric.datatype.Point;
import org.interpss.plugin.opf.common.OPFLogger;

import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.core.common.curve.PieceWiseCurve;
import com.interpss.opf.OpfBus;
import com.interpss.opf.OpfNetwork;

import lpsolve.LpSolve;
import lpsolve.LpSolveException;

public class LpsolveSolverObjectiveFunctionCollector extends BaseObjectiveFunctionCollector{
	
	public LpsolveSolverObjectiveFunctionCollector(OpfNetwork opfNet){
		super(opfNet);
	}
	
	/*public void genCostFunctionRefinement(LpSolve lpsolver, int refineNum) throws LpSolveException{
		int genIndex = 1;		
		int totalVar = numOfVar;
		int totalVarIdx = numOfVar+1;
		
		try {
			for (Bus b: opfNet.getBusList()){					
				if(opfNet.isOpfGenBus(b)){
					NumericCurveModel incType = ((OpfGenBus)b).getIncCost().getCostModel();
					if(!incType.equals(NumericCurveModel.PIECE_WISE)||
							((OpfGenBus)b).getIncCost().getPieceWiseCurve()==null){
						IpssLogger.ipssLogger.severe("LP solver requires piecewise linear gen cost funtion for generator at bus: "
								+b.getNumber());						
					}else{
						//lpsolver.setColName(genIndex, "Pg" + (b.getSortNumber()+1));
						PieceWiseCurve pw = ((OpfGenBus)b).getIncCost().getPieceWiseCurve();
						int np = pw.getPoints().size();
						double[] mw = new double[np];
						double[] price = new double[np];
						int pcnt = 0;						
						for (Point p: pw.getPoints()){
							mw[pcnt] = p.x;
							price[pcnt] = p.y;							
							pcnt++;
						}						
						// total points after segment refinement
						// points after refinement
						int refNp = (np-1)*(refineNum+1) - (np-2); 
						double[] refMw = new double[refNp];
						double[] refPrice = new double[refNp];
						
						refMw[0] = mw[0];
						refPrice[0] = price[0];		
						
						for (int i =1; i<np; i++){	
							int segcnt = 1;					
							double segSize = (mw[i]-mw[i-1])/refineNum;
							double sl = (price[i]- price[i-1])/(mw[i]-mw[i-1]);
							double deltaPrice = sl* segSize;
							for (int j = (i-1)*refineNum+1;  j <= i*refineNum; j++){
								refMw[j] = mw[i-1] + segcnt*segSize;								
								refPrice[j] = price[i-1]+ segcnt*deltaPrice;
								segcnt++;
							}														
						}							
						double[] slope = new double[refNp-1];
						int[] colno = new int[2];
						double[] row = new double[2]; 
						double rh = 0;
						for (int i=1; i<refNp;i++){
							slope[i-1] = refPrice[i];		
							rh = slope[i-1]*refMw[i]-refPrice[i]*refMw[i];
							colno[0] = genIndex;
				            row[0] = slope[i-1];	
				            colno[1] = totalVarIdx;
				            row[1] = -1; 
				             add the row to lpsolve 
				            lpsolver.addConstraintex( 2, row, colno, LpSolve.LE, rh);						
						}
						totalVarIdx++;
						genIndex++;
						totalVar++;	
					}
				}			
			}			
		}catch(Exception e){
			IpssLogger.ipssLogger.severe(e.toString());
		}			
		
	}*/
	public void genCostFunctionRefinement(LpSolve lpsolver) throws LpSolveException{
		int genIndex = 1;		
		int totalVar = numOfVar;
		int totalVarIdx = numOfVar+1;		
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
									
									createNewConstraint(lpsolver, genIndex, totalVarIdx, slope[cnt], xj[cnt], cj[cnt]);
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
	
	private void createNewConstraint(LpSolve lpsolver, int genIndex,
			int totalVarIdx, double slope, double xj, double cj) {
		int[] colno = new int[2];
		double[] row = new double[2];				
		double rh = slope * xj - cj;
		colno[0] = genIndex;
		row[0] = slope;
		colno[1] = totalVarIdx;
		row[1] = -1;		
		try {
			lpsolver.addConstraintex(2, row, colno, LpSolve.LE, rh);
		} catch (LpSolveException e) {			
			e.printStackTrace();
		}
	}
	
	public void processGenCostFunction(LpSolve lpsolver) throws LpSolveException{		
		try {			
			int[] col = new int[numOfGen];
			double[] obj = new double[numOfGen];
			int colStart = numOfGen+numOfBus+1;
			for (int i =0; i<numOfGen; i++){			
				col[i] = colStart;
				obj[i] = 1;
				colStart++;
			}		
			lpsolver.setObjFnex(numOfGen,obj, col);
			
		}catch(Exception e){
			OPFLogger.getLogger().severe(e.toString());
		}		
		
	}
	
	
	
	

}
