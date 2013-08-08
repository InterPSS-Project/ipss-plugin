package org.interpss.ext.pwd;

import org.interpss.ext.AclfBusExtension;

/**
 *  AclfBus extension for representing PowerWorld custom fields
 * 
 *  {Gen_CustomString:2=DZONE_1, Gen_CustomString=Sub1_14.9_G1, Gen_CustomString:1=G1, SubStation=Sub1}
 * 
 * @author mzhou
 *
 */
public class AclfBusPWDExtension extends AclfBusExtension {
	public static final String SubStation = "SubStation";
	public static final String Gen_CustomString_1 = "Gen_CustomString:1";
	public static final String Load_CustomString = "Load_CustomString";
	public static final String Shunt_CustomString = "Shunt_CustomString";
	
	private static final long serialVersionUID = 1L;

	public String getSubstationName(){
		return this.get(SubStation);
	}
	
	
	/**
	 * “SubstationName_EquipmentName”. EquipmentName = Gen_CustomString:1
	 * 
	 * @return
	 */
	public String getGenOutageId() {
		if (this.get(SubStation) != null && this.get(Gen_CustomString_1) != null)
			return this.get(SubStation) + "_" + this.get(Gen_CustomString_1);
		else 
			return null;
	}
	
	/**
	 * get Shunt equipment name
	 * 
	 * @return
	 */
	public String getShuntEquipName() {
		return this.get(Shunt_CustomString);
	}

	/**
	 * get Load equipment name
	 * 
                <nvPair>
                    <name>Load_CustomString</name>
                    <value>AGAWAM_115_6X</value>
                </nvPair>
                
	 * @return
	 */
	public String getLoadEquipName() {
		if (this.get(Load_CustomString) != null) {
			String str = this.get(Load_CustomString);
			return str.substring(str.lastIndexOf('_')+1, str.length());
		}
		else
			return null;
	}
}
