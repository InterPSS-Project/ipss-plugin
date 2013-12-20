package org.interpss.QA.compare.aclf;

import java.util.ArrayList;
import java.util.List;

import org.interpss.QA.compare.IDataComparator;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfNetwork;

/**
 * AclfNetwork model comparator
 * 
 * @author mzhou
 *
 */
public class AclfNetModelComparator implements IDataComparator<BaseAclfNetwork<?,?>, BaseAclfNetwork<?,?>> {
	// limit max number of output msg size
	public static int MaxMsgSize = 100;
	
	private List<String> msgList = new ArrayList<>();
	
	private IDataComparator<BaseAclfNetwork<?,?>, BaseAclfNetwork<?,?>> netComparator;
	private IDataComparator<AclfBus, AclfBus> busComparator;
	private IDataComparator<AclfBranch, AclfBranch> branchComparator;
	
	/**
	 * constructor
	 * 
	 * @param netComparator
	 * @param busComparator
	 * @param branchComparator
	 */
	public AclfNetModelComparator(IDataComparator<BaseAclfNetwork<?,?>, BaseAclfNetwork<?,?>> netComparator, 
			     IDataComparator<AclfBus, AclfBus> busComparator, 
			     IDataComparator<AclfBranch, AclfBranch> branchComparator) {
		this.netComparator = netComparator;
		this.busComparator = busComparator;
		this.branchComparator = branchComparator;
	}

	/**
	 * constructor
	 * 
	 */
	public AclfNetModelComparator() {
		this.netComparator = new AclfNetDataComparator();
		this.busComparator = new AclfBusDataComparator();
		this.branchComparator = new AclfBranchDataComparator();
	}
	
	@Override public boolean compare(BaseAclfNetwork<?,?> baseNet, BaseAclfNetwork<?,?> net) {
		boolean status = true;
		this.msgList.clear();
		
		if (!netComparator.compare(baseNet, net)) {
			status = false;
			this.addMsg(netComparator.getMsg());
		}		

		for (AclfBus bus : baseNet.getBusList()) {
			AclfBus bus1 = net.getBus(bus.getId());
			if (bus1 == null) {
				this.addMsg("Bus cannot be found in the network to be compared: id " + bus.getId());
			}
			else {
				if (!busComparator.compare(bus, bus1)) {
					status = false;
					this.addMsg(busComparator.getMsg());
				}			
			}
		}

		for (AclfBranch branch : baseNet.getBranchList()) {
			AclfBranch branch1 = net.getBranch(branch.getId());
			if (branch1 == null) {
				this.addMsg("Branch cannot be found in the network to be compared: id " + branch.getId());
			}
			else {
				if (!branchComparator.compare(branch, branch1)) {
					status = false;
					this.addMsg(branchComparator.getMsg());
				}			
			}
		}
		
		return status;
	}
	
	private void addMsg(String s) {
		if (this.msgList.size() < MaxMsgSize)
			this.msgList.add(s);
		else if (this.msgList.size() == MaxMsgSize)
			this.msgList.add("\nException msg > " + MaxMsgSize + " ...\n\n");
	}
	
	@Override public String getMsg() {
		StringBuffer buffer = new StringBuffer();
		for (String s : this.msgList) {
			buffer.append(s);
		}
		return buffer.toString();
	}
}
