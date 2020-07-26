package org.interpss.threePhase.basic.acsc.impl;

import java.util.Queue;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.interpss.threePhase.basic.acsc.Acsc3PNetworkTemp;
import org.interpss.threePhase.basic.dstab.DStab3PBranch;
import org.interpss.threePhase.basic.dstab.DStab3PBus;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.BaseAcscBus;
import com.interpss.core.acsc.XfrConnectCode;
import com.interpss.core.acsc.impl.BaseAcscNetworkImpl;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.sparse.impl.SparseEqnComplexMatrix3x3Impl;

public class Acsc3PNetworkTempImpl extends BaseAcscNetworkImpl<AcscBus, AcscBranch> implements Acsc3PNetworkTemp {
	
	protected ISparseEqnComplexMatrix3x3 yMatrixAbc =null;

	
	private boolean isDeltaConnected(XfrConnectCode code){
		return code ==XfrConnectCode.DELTA ||
				code== XfrConnectCode.DELTA11;
	}
	
	private void BFSSubTransmission (double phaseShiftDeg, Queue<DStab3PBus> onceVisitedBuses){
		
		//Retrieves and removes the head of this queue, or returns null if this queue is empty.
	    while(!onceVisitedBuses.isEmpty()){
			DStab3PBus  startingBus = onceVisitedBuses.poll();
			startingBus.setBooleanFlag(true);
			startingBus.setIntFlag(2);
			
			if(startingBus!=null){
				  for(Branch connectedBra: startingBus.getBranchList()){
						if(connectedBra.isActive() && !connectedBra.isBooleanFlag()){
							try {
								Bus findBus = connectedBra.getOppositeBus(startingBus);
								
								//update status
								connectedBra.setBooleanFlag(true);
								
								//for first time visited buses
								
								if(findBus.getIntFlag()==0){
									findBus.setIntFlag(1);
									onceVisitedBuses.add((DStab3PBus) findBus);
									
									// update the phase voltage
									Complex vpos = ((AclfBus)findBus).getVoltage();
									Complex va = vpos.multiply(phaseShiftCplxFactor(phaseShiftDeg));
									Complex vb = va.multiply(phaseShiftCplxFactor(120.0d));
									Complex vc = vb.multiply(phaseShiftCplxFactor(120.0d));
									
									((DStab3PBus) findBus).set3PhaseVoltages(new Complex3x1(va,vb,vc));
								}
							} catch (InterpssException e) {
								
								e.printStackTrace();
							}
							
						}
				 }
			 
			}
			
	      }
	}
	
	private Complex phaseShiftCplxFactor(double shiftDeg){
		return new Complex(Math.cos(shiftDeg/180.0d*Math.PI),Math.sin(shiftDeg/180.0d*Math.PI));
	}

	@Override
	public ISparseEqnComplexMatrix3x3 formYMatrixABC() throws Exception {
		yMatrixAbc = new SparseEqnComplexMatrix3x3Impl(getNoBus());
		
		for(BaseAcscBus<?,?> b:this.getBusList()){
			if(b instanceof DStab3PBus){
				int i = b.getSortNumber();
				DStab3PBus ph3Bus = (DStab3PBus) b;
				yMatrixAbc.setA(ph3Bus.getYiiAbc() ,i, i);
			}
			else
				throw new Exception("The processing bus # "+b.getId()+"  is not a threePhaseBus");
		}
		
		for (AcscBranch bra : this.getBranchList()) {
			if (bra.isActive()) {
				if(bra instanceof DStab3PBranch){
					DStab3PBranch ph3Branch = (DStab3PBranch) bra;
					int i = bra.getFromBus().getSortNumber(),
						j = bra.getToBus().getSortNumber();
					yMatrixAbc.addToA( ph3Branch.getYftabc(), i, j );
					yMatrixAbc.addToA( ph3Branch.getYtfabc(), j, i );
				}
				else
					throw new Exception("The processing branch #"+bra.getId()+"  is not a threePhaseBranch");
			}
			
		}
	
		return yMatrixAbc;
	}

	@Override
	public boolean run3PhaseLoadflow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public ISparseEqnComplexMatrix3x3 getYMatrixABC() {
		
		return this.yMatrixAbc;
	}
}
