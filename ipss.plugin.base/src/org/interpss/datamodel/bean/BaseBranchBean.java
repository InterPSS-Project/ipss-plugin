package org.interpss.datamodel.bean;

import java.util.List;

import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;

public class BaseBranchBean extends BaseJSONBean {
	/**
	 * branch type code
	 */
	public static enum BranchCode {
		Line, // transmission line
		Xfr, // transformer
		PsXfr, // phase-shifting transformer
		ZBR    // zero impedance line
	};

	public String f_id, // branch from side bus id
			t_id, // branch to side bus id
			cir_id = "1"; // branch circuit id/number

	public long f_num, t_num;
	
	public String f_name, t_name;

	public int status;	

	public ComplexBean 
	    z,					// branch z
	    shunt_y ;     		// branch total shunt y
	
	public double mvaRatingA, mvaRatingB, mvaRatingC;	

	public BranchCode bra_code = BranchCode.Line; // branch type code

	public BaseBranchBean() {
	}

	public boolean validate(List<String> msgList) {
		return true;
	}
}
