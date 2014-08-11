package org.interpss.QA.compare.dep;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;
import static org.interpss.CorePluginFunction.BusLfResultBusStyle;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.QA.result.QAAclfBranchRec;
import org.interpss.QA.result.QAAclfBusRec;
import org.interpss.QA.result.QABranchRec;
import org.interpss.QA.result.QABusRec;
import org.interpss.QA.result.QAResultContainer;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethod;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;

/**
 * Network model comparator
 * 
 * @author mzhou
 *
 * @param <TBusRec>
 * @param <TBranchRec>
 */
@Deprecated
public class DepNetModelComparator<TBusRec extends QABusRec, TBranchRec extends QABranchRec> {
	public static double VMagErr = 0.0001;   //pu
	public static double VAngErr = 0.01;     //deg
	public static double PQErr = 0.001;      //pu
	
	public static enum ResultFileType {PSSE, PWD, PSLF};
	
	protected ResultFileType resultType;
	
	protected QAResultContainer<TBusRec, TBranchRec> qaResultSet = null;

	protected AclfNetwork net = null;
	public void setAclfNetork(AclfNetwork net) { this.net = net; }
	
	protected List<String> errMsgList = new ArrayList<String>();
	
	public DepNetModelComparator() {
	}	
	
	public DepNetModelComparator(AclfNetwork net) {
		this.net = net;
	}

	public DepNetModelComparator(AclfNetwork net, QAResultContainer<TBusRec, TBranchRec> qaResultSet) {
		this.net = net;
		this.qaResultSet = qaResultSet;
	}
	
	public List<String> getErrMsgList() {
		return this.errMsgList;
	}
	
	protected void addErrMsg(String msg) {
		this.errMsgList.add("\n" + msg);
	}

	protected void addErrMsg(String msg, String lineStr) {
		this.errMsgList.add("\n" + msg + "\n                                         ->" + lineStr);
	}
	
	/**
	 * compare the result info loaded from a result file with the network model. The
	 * comparison result are stored in the errMsgList.
	 * 
	 * @param ignoreBranchStatus if true, ignore branch status comparison. It is used when
	 *                           small-Z branch is turned of 
	 * @return false if there is runtime issue.
	 */
	public boolean compareNetElement() { return compareNetElement(false); }
	public boolean compareNetElement(boolean ignoreBranchStatus) {
		// base mva should match
		if (!NumericUtil.equals(this.qaResultSet.getBaseMva()*1000.0, net.getBaseKva())) {
			addErrMsg("Network base Mva do not match - net, result file: " + 
					net.getBaseKva()*0.001 + ", " + this.qaResultSet.getBaseMva());
		}
		
		compareBusElement();
		
		compareBranchElement(ignoreBranchStatus);
		
		return true;
	}

	/**
	 * compare bus objects in the network
	 */
	public void compareBusElement() {
		//  bus info should match
		for (Bus b : net.getBusList()) {
			QAAclfBusRec rec = (QAAclfBusRec)this.qaResultSet.getBusResult(b.getId());
			if (b.isActive() && rec == null)
				addErrMsg("Active Bus not found in the result file, " + b.getId());
			else {
				AclfBus bus = (AclfBus)b;
				if (!NumericUtil.equals(bus.getGenP(), rec.genp, 0.0001))
					addErrMsg("Bus gen p mismatch, " + b.getId() + ",  " +
							+ bus.getGenP() + "(m)  "+ rec.genp + "(r)");
				if (!NumericUtil.equals(bus.getLoadP(), rec.loadp, 0.0001))
					addErrMsg("Bus load p mismatch, " + b.getId() + ",  " +
							+ bus.getLoadP() + "(m)  "+ rec.loadp + "(r)");
			}
		}
	}

	/**
	 * compare branch objects in the network
	 * 
	 * @param ignoreBranchStatus
	 */
	public void compareBranchElement(boolean ignoreBranchStatus) {
		//  branch info should match
		for (Branch branch : net.getBranchList()) {
			QAAclfBranchRec rec = (QAAclfBranchRec)this.qaResultSet.getBranchResult(branch.getId());
			if (branch.isActive() && rec == null) {
				addErrMsg("Active Branch not found in the result file, " + branch.getId());
				String id = ToBranchId.f(branch.getToPhysicalBusId(), branch.getFromPhysicalBusId(), branch.getCircuitNumber());
				if (this.qaResultSet.getBranchResult(id) != null)
					addErrMsg("Branch in reverse direction found:  " + branch.getId());
				if (branch.getFromBus().isActive() || branch.getToBus().isActive()) {
					addErrMsg("Branch connects to active bus: " + 
								branch.getFromBus().getId() + "(" + (branch.getFromBus().isActive()?"active":"inactive") + ")" + 
								branch.getToBus().getId() + "(" + (branch.getToBus().isActive()?"active":"inactive") + ")");
				}
			}
			else if (rec != null) {
				if (!ignoreBranchStatus && 
						(branch.isActive() && !rec.onStatus || !branch.isActive() && rec.onStatus)) {
					addErrMsg("Branch has differnt on/off status:  " + branch.getId() + "  model branch:" +
				        (branch.isActive()?"on":"off") + " result branch:" + (rec.onStatus?"on":"off") + " " + rec.from_p);
				}
			}
		}
	}
	
	/**
	 * Assign bus voltage stored in the QAResult object into the net object and then
	 * find the bus with mismatch > err. The smallZThreshold is for excluding small
	 * Z branches in the bus mismatch calculation, since it might cause large mismatch
	 * because of limit digits in the result file. 
	 * 
	 * @param err
	 * @param smallZThreshold
	 * @return
	 */
	public List<AclfBus> getLargeMisBusList(double err, double smallZThreshold) throws InterpssException {
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			QAAclfBusRec rec = (QAAclfBusRec)this.qaResultSet.getBusResult(b.getId());
			if (bus.isActive()) {
				bus.setVoltageMag(rec.vmag);
				bus.setVoltageAng(rec.vang);
			}
		}
		
		List<AclfBus> busList = new ArrayList<AclfBus>();
		double max = 0.0;
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (!bus.connect2ZeroZBranch()) {
				Complex  mis = bus.mismatch(AclfMethod.NR);
				if (mis.abs() > err) {
					busList.add(bus);
				}
				if (mis.abs() > max)
					max = mis.abs();				
			}
		}
		System.out.println("max mismatch: " + max);
		return busList;
	}

	private boolean is3WXfrStarBus(String id) {
		return id.length() > 10;
	}
	
	/**
	 * Assign bus voltage stored in the QAResult object into the net object and then
	 * find the bus with the largest mismatch. The smallZThreshold is for excluding small
	 * Z branches in the bus mismatch calculation, since it might cause large mismatch
	 * because of limit digits in the result file  
	 * 
	 * @return
	 */
	public AclfBus getLargestMisBus(double smallZThreshold) throws InterpssException {
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			QAAclfBusRec rec = (QAAclfBusRec)this.qaResultSet.getBusResult(b.getId());
			if (rec == null) {
				if (b.isActive() && !is3WXfrStarBus(b.getId())) 
					IpssLogger.getLogger().warning("Active AclfNet Bus not found in QAResult, " + b.getId() + " # of connected active branches " + b.nActiveBranchConnected());
			}
			else {
				if (bus.isActive()) {
					bus.setVoltageMag(rec.vmag);
					bus.setVoltageAng(rec.vang);
				}
			}
		}
		
		AclfBus misbus = null;
		double max = 0.0;
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			Complex  mis = bus.mismatch(AclfMethod.NR);
			if (mis.abs() > max && !bus.connect2ZeroZBranch()) {
				max = mis.abs();
				misbus = bus;
			}
		}
		return misbus;
	}	
	
	/**
	 * Transfer the bus state to a String for display purpose
	 * 
	 * @param busId
	 * @return
	 */
	public StringBuffer busInfo(String busId) {
		AclfBus bus = this.net.getBus(busId);
		return busInfo(bus);
	}
	
	/**
	 * Transfer the bus state to a String for display purpose
	 * 
	 * @param bus
	 * @return
	 */
	public StringBuffer busInfo(AclfBus bus) {
		StringBuffer buf = new StringBuffer();
		Complex  mis = bus.mismatch(AclfMethod.NR);		
		buf.append("largest mismatch: " + mis.abs() + 
				"  @" + bus.getId() + "\n" + 
		                    "\nBus LF info: \n\n" + BusLfResultBusStyle.f(net, bus));
		// output the original LF info in the result file for comparison
		if (this.qaResultSet != null) {
			String str = this.qaResultSet.getBusResult(bus.getId()).strData;
			if (str != null && !str.equals(""))
					buf.append("\nReault file info: \n\n" + 
			                   this.qaResultSet.getBusResult(bus.getId()).strData);
		}
		
		// display debug info of the bus and connected buses and branches
		buf.append("\n\n\nBus/Branch debug info: \n\n" + bus.toString(net.getBaseKva()));
		for (Branch b : bus.getBranchList()) {
			AclfBranch bra = (AclfBranch)b;
			try {
				buf.append("\n\n" + bra.getOppositeBus(bus).toString(net.getBaseKva()));
			} catch (InterpssException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			buf.append("\n\n" + bra.toString(net.getBaseKva()));
		}

		// display debug info the connected branches
		return buf;
	}
}
