package org.interpss.pssl.util.tool;

public class CARecord {
		String fromBus="";
		String toBus="";
		String cirId="";
		double value=0;
		double limit=0;
		double percent =0;
		
		public CARecord(String fromBus,String toBus,String cirId,
				double value,double limit,double percent){
			this.fromBus=fromBus;
			this.toBus=toBus;
			this.cirId=cirId;
			this.value=value;
			this.limit=limit;
			this.percent=percent;
		}
		
		public String getFromBus() {
			return fromBus;
		}
		public void setFromBus(String fromBus) {
			this.fromBus = fromBus;
		}
		public String getToBus() {
			return toBus;
		}
		public void setToBus(String toBus) {
			this.toBus = toBus;
		}
		public String getCirId() {
			return cirId;
		}
		public void setCirId(String cirId) {
			this.cirId = cirId;
		}
		public double getValue() {
			return value;
		}
		public void setValue(double value) {
			this.value = value;
		}
		public double getLimit() {
			return limit;
		}
		public void setLimit(double limit) {
			this.limit = limit;
		}
		public double getPercent() {
			return percent;
		}
		public void setPercent(double violatePercent) {
			this.percent = violatePercent;
		}
		
		public String toString(){
			return "\nElement:" +fromBus+"->"+toBus+" ("+cirId+")"+", pf value: "+value
					+", "+"limit: "+limit+" ,"+"Percent: "+percent;
			
		}
	
}
