package org.interpss.plugin.QA.model;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Map;

import org.apache.commons.math3.complex.Complex;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
import com.interpss.core.net.Branch;

/**
 * A checker to verify the model imported is correct. The network model is
 * created by reading a solved power flow case.
 * 
 * @author ghou
 * 
 */
public class ModelChecker {
	private AclfNetwork net;	
	private double mismatchThre = 0.01;	
	//private double smallZThre = 0.0005;	
	private ArrayList<AclfBus> sortedMismatchBus = new ArrayList<AclfBus>();
	
	public ModelChecker(AclfNetwork net){
		this.net = net;
	}

	public void checkModel() throws InterpssException {

		// step 1: perform XfrZ adjustment per XFR Z correction table
		net.adjustXfrZ();

		// TODO: check why this step is necessary.
		for (Branch bra : net.getSpecialBranchList()) {
			if (bra instanceof Aclf3WBranch) {
				Aclf3WBranch w3Bra = (Aclf3WBranch) bra;
				// TODO check
				w3Bra.calculateStarBusVoltage();
			}
		}

		// step 3: build mismatch table
		Hashtable<String, Double> mismatchTable = new Hashtable<>();
		for (AclfBus bus : net.getBusList()) {
			Complex mis = bus.mismatch(AclfMethodType.NR);
			if (bus.isActive() && mis.abs() > mismatchThre) {
				mismatchTable.put(bus.getId(), mis.abs());				
			}
		}

		// step 4: sort mismatch table
		ArrayList<Map.Entry<?, Double>> sorted = sortValue(mismatchTable);	
		
		// step 5: display check result
		int totalBusNum = net.getNoActiveBus();		
		int size = sorted.size();
		System.out.println("Aclf Model check complete. Total bus # = "+ totalBusNum+", "
				+ " # of bus with mismatch greater than threshold (" + this.mismatchThre + ") = "+ size);
		if(size ==0 ){			
			return;
		}			
		System.out.println("Bus active and reactive pwoer mismatch in descending order: \n");
		for (int i = 0; i < size; i++){
			String id = (String) sorted.get(i).getKey();
			AclfBus bus = net.getBus(id);
			this.sortedMismatchBus.add(bus);
			Complex mis = bus.mismatch(AclfMethodType.NR);	
			mis = formatComplex(mis);
			System.out.println(bus.getId() + ", mismatch = "
					+ mis.toString());
		}			
	}
	
	
	private Complex formatComplex(Complex num) {
		double real = roundTwoDecimals(num.getReal());
		double img = roundTwoDecimals(num.getImaginary());
		return new Complex(real, img);
	}
	private double roundTwoDecimals(double d) {
	      DecimalFormat twoDForm = new DecimalFormat("#.###");
	      return Double.valueOf(twoDForm.format(d));
	 }
/*
	private boolean isSmallZBranchConnected(String busId, AclfNetwork net,
			double smallZ) {
		boolean smallZBra = false;
		AclfBus b = net.getBus(busId);
		for (Branch bra : b.getConnectedBranchList()) {
			if (bra.isActive() && bra instanceof AclfBranch) {
				if (((AclfBranch) bra).getZ().abs() < smallZ)
					smallZBra = true;
			}
		}
		return smallZBra;
	}
*/
	/**
	 *  sort a hashtable
	 * @param t hashtable	 
	 * @return
	 */
	private ArrayList<Map.Entry<?, Double>> sortValue(
			Hashtable<?, Double> t) {
		// Transfer as List and sort it
		ArrayList<Map.Entry<?, Double>> l = new ArrayList(t.entrySet());
		Collections.sort(l, new Comparator<Map.Entry<?, Double>>() {
			public int compare(Map.Entry<?, Double> o1, Map.Entry<?, Double> o2) {
				return o2.getValue().compareTo(o1.getValue());
			}
		});	
		return l;		
	}
	
	public AclfNetwork getNet() {
		return net;
	}

	public void setNet(AclfNetwork net) {
		this.net = net;
	}

	public double getMismatchThre() {
		return mismatchThre;
	}

	public void setMismatchThre(double mismatchThre) {
		this.mismatchThre = mismatchThre;
	}

	public ArrayList<AclfBus> getSortedMismatchBus() {
		return sortedMismatchBus;
	}

}
