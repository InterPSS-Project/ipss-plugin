package org.interpss.plugin.opf.constraint.dc;

import java.util.ArrayList;

import org.interpss.numeric.datatype.Point;
import org.interpss.plugin.opf.OpfSolverFactory;
import org.interpss.plugin.opf.common.OPFLogger;
import org.interpss.plugin.opf.constraint.BaseConstraintCollector;
import org.interpss.plugin.opf.constraint.OpfConstraint;

import com.interpss.core.aclf.AclfBus;
import com.interpss.core.common.curve.NumericCurveModel;
import com.interpss.core.common.curve.PieceWiseCurve;
import com.interpss.core.net.Bus;
import com.interpss.opf.cst.ConstraintFactory;
import com.interpss.opf.cst.OpfConstraintType;
import com.interpss.opf.dep.BaseOpfNetwork;
import com.interpss.opf.dep.OpfGenBus;

import cern.colt.list.DoubleArrayList;
import cern.colt.list.IntArrayList;

public class GenPWCostConstraintCollector extends BaseConstraintCollector{
	
	public GenPWCostConstraintCollector(BaseOpfNetwork opfNet,ArrayList<OpfConstraint> cstContainer) {
		super(opfNet,cstContainer);			
	}


	@Override
	public void collectConstraint() {		
		
		int genIndex = 0;
		for (Bus bus : opfNet.getBusList()) {
			AclfBus b = (AclfBus)bus;
			if (b.isGen()) {
				IntArrayList colNo = new IntArrayList();
				DoubleArrayList val = new DoubleArrayList();
				NumericCurveModel incType = ((OpfGenBus)b).getIncCost().getCostModel();
				if (incType.equals(NumericCurveModel.QUADRATIC)){
					OPFLogger.getLogger().severe("Solver requires piecewise linear gen cost funtion" +
							" for generator at bus: "
								+b.getNumber());
				}else{					
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
					
					String des = "Gen piecewise cost function additional constraint @"+ b.getId();	
					double[] slope = new double[np-1];					
					double rh = 0;
					for (int i=1; i<np;i++){						
						OpfConstraint cst = new OpfConstraint();	
						slope[i-1] = (price[i]- price[i-1])/(mw[i]-mw[i-1]);		
						rh = -slope[i-1]*mw[i] + price[i]; // cj- mj*xj
						
						colNo.add(genIndex);
						val.add(slope[i-1]);
						
						colNo.add(this.numOfVar+genIndex);
						val.add(1);					
						
						int id = cstContainer.size();
						cst = OpfSolverFactory.createOpfConstraint(id, des, 0, rh, OpfConstraintType.LARGER_THAN, colNo, val);
						cstContainer.add(cst);					
					}					
					genIndex++;
				}			
				
			}
		}		
	}
	


}
