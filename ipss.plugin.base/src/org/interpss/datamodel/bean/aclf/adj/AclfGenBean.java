package org.interpss.datamodel.bean.aclf.adj;

import java.util.List;

import org.interpss.datamodel.bean.BaseJSONBean;
import org.interpss.datamodel.bean.aclf.AclfBusBean;

/**
 * Bean class for storing AclfGen info
 * 
 * @author sHou
 *
 */
public class AclfGenBean extends BaseJSONBean{
	
	public String id;			// generator identifier to distinguish from multiple 
								// gens connected at the same bus
	
	public int status;          // generator service status
	
	public double 
		pgen,					// MW output
		qgen,					// MVAR output
		pmax,					// max MW output
		pmin,					// min MW output
		qmax,					// max MVAR output
		qmin;					// min MVAR output
	
	//public AclfBusBean connectedBus;		// the bus that this gen is connected to	
	
	public double scheduledVol;  // gen scheduled voltage
	
	public String remoteVControlBusId;  // remote control bus id

	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		AclfGenBean bean = (AclfGenBean)b;

		String str = "ID: " + this.id + " AclfGenBean.";
		
		if (!this.id.equals(bean.id)) {
			logCompareMsg(str + "id is not equal, " + this.id + ", " + bean.id); eql = 1; }
		
		if (this.status != bean.status){
			logCompareMsg(str + "status is not equal, " + this.status + ", " + bean.status); eql = 1; }	


		if (this.pgen != bean.pgen) {
			logCompareMsg(str + "pgen is not equal, " + this.pgen + ", " + bean.pgen); eql = 1; }	
		if (this.qgen != bean.qgen) {
			logCompareMsg(str + "qgen is not equal, " + this.qgen + ", " + bean.qgen); eql = 1; }
		if (this.pmax != bean.pmax) {
			logCompareMsg(str + "pmax is not equal, " + this.pmax + ", " + bean.pmax); eql = 1; }	
		if (this.pmin != bean.pmin) {
			logCompareMsg(str + "pmin is not equal, " + this.pmin + ", " + bean.pmin); eql = 1; }
		if (this.qmax != bean.qmax) {
			logCompareMsg(str + "qmax is not equal, " + this.qmax + ", " + bean.qmax); eql = 1; }
		if (this.qmin != bean.qmin) {
			logCompareMsg(str + "qmin is not equal, " + this.qmin + ", " + bean.qmin); eql = 1; }
		
		
		/*if(this.connectedBus.compareTo(bean.connectedBus) != 0 ){
			logCompareMsg(str + "connected bus is not equal, " + this.connectedBus + ", " + bean.connectedBus); eql = 1; }
		*/
		if (this.scheduledVol != bean.scheduledVol) {
			logCompareMsg(str + "scheduled voltage is not equal, " + this.scheduledVol + ", " + bean.scheduledVol); eql = 1; }
		if (this.remoteVControlBusId.equals(bean.remoteVControlBusId)) {
			logCompareMsg(str + "remote voltage control bus id is not equal, " + this.remoteVControlBusId + ", " + bean.remoteVControlBusId); eql = 1; }
		
		return eql;
	}	
	
	@Override
	public boolean validate(List<String> msgList) {
		// TODO Auto-generated method stub
		return false;
	}
	

}
