package org.interpss.pssl.util.tool;

import java.util.Hashtable;


public class ContingencyResult {
	
	private String label="";
	private Hashtable<String,CARecord> violateRecords = new Hashtable<String,CARecord>();
	
	public String getLabel(){
		return this.label;
	}
	
	public void setLabel(String cntgLabel){
		this.label=cntgLabel;
	}
	
	public Hashtable<String,CARecord> getViolateContRecords(){
		return this.violateRecords;
	}
	
	public String toString(){
		StringBuffer cntgSB=new StringBuffer();
		cntgSB.append("\n\nContingency Label: "+label);
		for(CARecord rec:violateRecords.values()){
			cntgSB.append(rec.toString());
		}
		return cntgSB.toString();
	}
	

	

}
