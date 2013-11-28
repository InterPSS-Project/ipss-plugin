package org.interpss.QA.compare.aclf;

import org.interpss.QA.compare.DataComparatorAdapter;
import org.interpss.numeric.util.Number2String;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.core.aclf.AclfBus;

/**
 * AclfBus data comparator
 * 
 * @author mzhou
 *
 */
public class AclfBusDataComparator extends DataComparatorAdapter<AclfBus, AclfBus> {
	@Override public boolean compare(AclfBus baseBus, AclfBus bus) {
		this.msg = "";
		boolean ok = true;
		
		if (!NumericUtil.equals(baseBus.getBaseVoltage(), bus.getBaseVoltage())) {
			this.msg += "\nbus.baseVoltage not equal: " + baseBus.getId() + ", " + baseBus.getBaseVoltage() + "(base), " + bus.getBaseVoltage(); ok = false; 	}

		if (baseBus.getSortNumber() != bus.getSortNumber()) {
			this.msg += "\nbus.sortNumber not equal: " + baseBus.getId() + ", " + baseBus.getSortNumber() + "(base), " +  bus.getSortNumber(); 	ok = false; }

		if (baseBus.isActive() != bus.isActive()) {
			this.msg += "\nbus.status not equal: " + baseBus.getId() + ", " + baseBus.isActive() + "(base), " + bus.isActive(); ok = false;
		}
		
		if (baseBus.getBranchList().size() != bus.getBranchList().size()) {
			this.msg += "\nbus.branchList.size not equal: " + baseBus.getId() + ", " + baseBus.getBranchList().size() + "(base), " + bus.getBranchList().size(); ok = false; }

		if (baseBus.getBusSecList().size() != bus.getBusSecList().size()) {
			this.msg += "\nbus.busSecList.size not equal: " + baseBus.getId() + ", " + baseBus.getBusSecList().size() + "(base), " + bus.getBusSecList().size(); ok = false; }

		if (baseBus.getGenCode() != bus.getGenCode()) {
			this.msg += "\nbus.genCode not equal: " + baseBus.getId() + ", " + baseBus.getGenCode() + "(base), " + bus.getGenCode(); ok = false; }

		if (!NumericUtil.equals(baseBus.getGen(), bus.getGen())) {
			this.msg += "\nbus.gen not equal: " + baseBus.getId() + ", " + Number2String.toStr(baseBus.getGen()) + "(base), " + Number2String.toStr(bus.getGen()); ok = false; 	}
		
		if (baseBus.getLoadCode() != bus.getLoadCode()) {
			this.msg += "\nbus.loadCode not equal: " + baseBus.getId() + ", " + baseBus.getLoadCode() + "(base), " + bus.getLoadCode(); ok = false; }

		if (!NumericUtil.equals(baseBus.getLoad(), bus.getLoad())) {
			this.msg += "\nbus.load not equal: " + baseBus.getId() + ", " + Number2String.toStr(baseBus.getLoad()) + "(base), " + Number2String.toStr(bus.getLoad()); ok = false; 	}

		if (!NumericUtil.equals(baseBus.getShuntY(), bus.getShuntY())) {
			this.msg += "\nbus.shuntY not equal: " + baseBus.getId() + ", " + Number2String.toStr(baseBus.getShuntY()) + "(base), " + Number2String.toStr(bus.getShuntY()); ok = false; 	}

		if (!ok)
			this.msg += "\n";
		return ok;
	}
}
