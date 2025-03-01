package org.interpss.ext.pwd;

import org.interpss.ext.AclfBranchExtension;

/**
 *  AclfBranch extension for representing PowerWorld custom fields
 *  
 *  {CustomString:1=Sub3_230_L34B, CustomString=Line, SubStation=Sub3, EquimentName=L34B}
 * 
 * @author mzhou
 *
 */
public class AclfBranchPWDExtension extends AclfBranchExtension {
	public static final String CustomString = "CustomString";
	public static final String EquimentName = "EquimentName";
	public static final String LineMonEle = "LineMonEle";
	public static final String LSName = "LSName";
	
	private static final long serialVersionUID = 1L;
	
	private boolean caMonitoring = false;
	public boolean isCaMonitoring() { return caMonitoring; }
	public void setCaMonitoring(boolean caMonitoring) { this.caMonitoring = caMonitoring; }

	/**
	 * Defined as "EquipmentType_SubstationName_EquipmentName”. Take Transformer_Sub1_T12 for example, 
	 * it refers to the transformer connected to substation Sub1 (where the “fromBus” is located) 
	 * with the equipment name of T12. 
	 * 
	 * @return
	 */
	public String getBranchOutageId() {
		return this.get(CustomString) + "_" + this.get(AclfBusPWDExtension.SubStation) + "_" + this.get(EquimentName);
	}

	/**
	 * get LineMonEle attribute
	 * 
	 * @return
	 */
	public String getLineMonEle() {
		return this.get(LineMonEle);
	}

	/**
	 * get LSName attribute
	 * 
	 * @return
	 */
	public String getLSName() {
		return this.get(LSName);
	}
}
