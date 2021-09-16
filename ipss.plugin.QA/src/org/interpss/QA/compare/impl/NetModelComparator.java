package org.interpss.QA.compare.impl;

import static com.interpss.common.util.NetUtilFunc.ToBranchId;
import static org.interpss.CorePluginFunction.BusLfResultBusStyle;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.datamodel.bean.aclf.AclfBranchBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;
import org.interpss.datamodel.bean.aclf.BaseAclfNetBean;
import org.interpss.datamodel.bean.base.BaseJSONUtilBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.Aclf3WBranch;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;
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
public class NetModelComparator<TBus extends AclfBusBean<TBusExt>, 
                                TBra extends AclfBranchBean<TBraExt>, 
                                TBusExt extends BaseJSONUtilBean, 
                                TBraExt extends BaseJSONUtilBean,
                                TNetExt extends BaseJSONUtilBean> {
	public static boolean CompareBus = true;
	public static boolean CompareBusVolt = true;
	public static boolean CompareBusPower = true;
	public static boolean CompareBusShunt = true;
	
	public static boolean CompareBranch = true;
	public static boolean CompareBranchData = true;
	public static boolean CompareBranchFlow = true;
	
	
	public static double VMagErr = 0.0001;   //pu
	public static double VAngErr = 0.01;     //deg
	public static double PQErr = 0.001;      //pu
	public static double TapErr = 0.001;      //pu
	
	public static enum ResultFileType {PSSE, PWD, PSLF};
	
	protected ResultFileType resultType;
	
	protected BaseAclfNetBean<TBus, TBra, TBusExt, TBraExt, TNetExt> qaResultSet = null;
	/*
	private Hashtable<String,String> busLookupTable = new Hashtable<>();
	public void setBusLookupTable(Hashtable<String,String> table) { this.busLookupTable = table; }
	*/
	protected AclfNetwork net = null;
	public void setAclfNetork(AclfNetwork net) { this.net = net; }
	
	protected List<String> errMsgList = new ArrayList<String>();
	
	public NetModelComparator() {
	}	
	
	public NetModelComparator(AclfNetwork net) {
		this.net = net;
	}

	public NetModelComparator(AclfNetwork net, BaseAclfNetBean<TBus, TBra, TBusExt, TBraExt, TNetExt> qaResultSet) {
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
		if (!NumericUtil.equals(this.qaResultSet.base_kva, net.getBaseKva())) {
			addErrMsg("Network base Mva do not match - net, result file: " + 
					net.getBaseKva()*0.001 + ", " + this.qaResultSet.base_kva);
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
		for (AclfBus bus : net.getBusList()) {
			if (!bypassBus(bus.getId())) {
				AclfBusBean<TBusExt> rec = this.qaResultSet.getBus(bus.getId());
				if (bus.isActive() && rec == null)
					addErrMsg("Active Bus not found in the result file, " + bus.getId());
				else {
					if (!bus.isSwing()) {
						if (bus.isGen())
							if (!NumericUtil.equals(bus.getGenP(), rec.gen.re, 0.0001))
								addErrMsg("Bus gen p mismatch, " + bus.getId() + ",  " +
									+ bus.getGenP() + "(m)  "+ rec.gen.re + "(r)");
						if (bus.isLoad())
							if (!NumericUtil.equals(bus.getLoadP(), rec.load.re, 0.0001))
								addErrMsg("Bus load p mismatch, " + bus.getId() + ",  " +
									+ bus.getLoadP() + "(m)  "+ rec.load.re + "(r)");
					}
				}
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
			TBra rec = this.qaResultSet.getBranch(branch.getId());
			if (this.resultType == ResultFileType.PSSE) {
				/*
				 * 		Bus3002->3WNDTR_3002_3001_3011_1(1) : Bus3002->3WNDTR(1)
				 *      3WNDTR_3002_3001_3011_1->Bus3001(1) : Bus3001->3WNDTR(1) 
				 */
				if (rec == null) 
					rec = this.qaResultSet.getBranch(branch.getFromBus().getId(), "3WNDTR", branch.getCircuitNumber());
				if (rec == null) 
					rec = this.qaResultSet.getBranch(branch.getToBus().getId(), "3WNDTR", branch.getCircuitNumber());
			}
			if (branch.isActive() && rec == null) {
				addErrMsg("Active Branch not found in the result file, " + branch.getId());
				String id = ToBranchId.f(branch.getToBusId(), branch.getFromBusId(), branch.getCircuitNumber());
				if (this.qaResultSet.getBranch(id) != null)
					addErrMsg("Branch in reverse direction found:  " + branch.getId());
				if (branch.getFromBus().isActive() || branch.getToBus().isActive()) {
					addErrMsg("Branch connects to active bus: " + 
								branch.getFromBus().getId() + "(" + (branch.getFromBus().isActive()?"active":"inactive") + ")" + 
								branch.getToBus().getId() + "(" + (branch.getToBus().isActive()?"active":"inactive") + ")");
				}
			}
			else if (rec != null) {
				if (!ignoreBranchStatus && 
						(branch.isActive() && rec.status == 0 || !branch.isActive() && rec.status == 1)) {
					addErrMsg("Branch has differnt on/off status:  " + branch.getId() + "  model branch:" +
				        (branch.isActive()?"on":"off") + " result branch:" + (rec.status==1?"on":"off"));
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
		setBusVoltage();
		
		List<AclfBus> busList = new ArrayList<AclfBus>();
		double max = 0.0;
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			if (!bus.isConnect2ZeroZBranch()) {
				Complex  mis = bus.mismatch(AclfMethodType.NR);
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
		setBusVoltage();
		
		AclfBus misbus = null;
		double max = 0.0;
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			Complex  mis = bus.mismatch(AclfMethodType.NR);
			if (mis.abs() > max && !bus.isConnect2ZeroZBranch()) {
				max = mis.abs();
				misbus = bus;
			}
		}
		return misbus;
	}	
	
	/**
	 * Assign bus voltage stored in the QAResult object into the net object
	 */
	private void setBusVoltage() {
		for (Bus b : net.getBusList()) {
			AclfBus bus = (AclfBus)b;
			AclfBusBean<TBusExt> rec = this.qaResultSet.getBus(b.getId());
			if (rec == null) {
				if (b.isActive() && !is3WXfrStarBus(b.getId())) 
					IpssLogger.getLogger().warning("Active AclfNet Bus not found in QAResult, " + b.getId() + " # of connected active branches " + b.nActiveBranchConnected());
			}
			else {
				if (bus.isActive()) {
					bus.setVoltageMag(rec.v_mag);
					bus.setVoltageAng(Math.toRadians(rec.v_ang));
				}
			}
		}
		
		// calculate 3W Xfr star bus voltage
		if (this.resultType == ResultFileType.PSSE) {
			for ( Branch bra : net.getSpecialBranchList()) {
				if (bra instanceof Aclf3WBranch) {
					Aclf3WBranch xfr3W = (Aclf3WBranch)bra;
					xfr3W.calculateStarBusVoltage();
					AclfBus star = (AclfBus)xfr3W.getStarBus();
					star.setVoltage(xfr3W.getVoltageStarBus());
				}
			}
		}
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
		Complex  mis = bus.mismatch(AclfMethodType.NR);		
		buf.append("largest mismatch: " + mis.abs() + 
				"  @" + bus.getId() + "\n" + 
		                    "\nBus LF info: \n\n" + BusLfResultBusStyle.f(net, bus));
		// output the original LF info in the result file for comparison
		if (this.qaResultSet != null) {
			if (!bypassBus(bus.getId())) {
				String str = this.qaResultSet.getBus(bus.getId()).info;
				if (str != null && !str.equals(""))
						buf.append("\nReault file info: \n\n" + 
				                   this.qaResultSet.getBus(bus.getId()).info);
			}
		}
		
		// display debug info of the bus and connected buses and branches
		buf.append("\n\n\nBus/Branch debug info: \n\n" + bus.toString(net.getBaseKva()));
		for (Branch b : bus.getBranchList()) {
			AclfBranch bra = (AclfBranch)b;
			try {
				buf.append("\n\n" + bra.getOppositeBus(bus).toString(net.getBaseKva()));
			} catch (InterpssException e) {
				e.printStackTrace();
			}
			buf.append("\n\n" + bra.toString(net.getBaseKva()));
		}

		// display debug info the connected branches
		return buf;
	}
	
	
	private boolean bypassBus(String id) {
		if (this.resultType == ResultFileType.PSSE &&
				id.startsWith("3WNDTR"))
			return true;
		return false;
	}	
}
