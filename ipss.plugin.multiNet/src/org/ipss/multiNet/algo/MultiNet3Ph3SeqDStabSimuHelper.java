package org.ipss.multiNet.algo;

import java.util.Hashtable;

import org.ipss.multiNet.equivalent.NetworkEquivalent;

/**
 * The fault is limited to be applied within the three-phase modeling
 * subsystem, while all other subsystems are modeled in three-sequence.
 * @author Qiuhua Huang
 *
 */
public class MultiNet3Ph3SeqDStabSimuHelper extends AbstractMultiNetDStabSimuHelper{

	@Override
	public void prepareBoundarySubSystemMatrix() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateSubNetworkEquivMatrix() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateSubNetworkEquivMatrix(String subNetworkId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Hashtable<String, NetworkEquivalent> solvSubNetAndUpdateEquivSource() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public boolean solveBoundarySubSystem() {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean solveSubNetWithBoundaryCurrInjection() {
		// TODO Auto-generated method stub
		return false;
	}

}
