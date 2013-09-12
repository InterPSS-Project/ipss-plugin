package org.interpss.datamodel.bean;

import java.util.List;

import org.interpss.datamodel.bean.datatype.BranchValueBean;
import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

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

	public String 
			f_id, // branch from side bus id
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

	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		BaseBranchBean bean = (BaseBranchBean)b;

		if (!this.f_id.equals(bean.f_id)) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.f_id is not equal"); eql = 1; }
		if (!this.t_id.equals(bean.t_id)) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.t_id is not equal"); eql = 1; }
		if (!this.cir_id.equals(bean.cir_id)) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.cir_id is not equal"); eql = 1; }

		if (this.f_num != bean.f_num) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.f_num is not equal"); eql = 1; }
		if (this.t_num != bean.t_num) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.t_num is not equal"); eql = 1; }

		if (this.status != bean.status) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.status is not equal"); eql = 1; }

		if (this.z.compareTo(bean.z) != 0) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.z is not equal"); eql = 1; }
		if (this.shunt_y.compareTo(bean.shunt_y) != 0) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.shunt_y is not equal"); eql = 1; }

		if (!NumericUtil.equals(this.mvaRatingA, bean.mvaRatingA, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.basemvaRatingA is not equal"); eql = 1; }
		if (!NumericUtil.equals(this.mvaRatingB, bean.mvaRatingB, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.basemvaRatingB is not equal"); eql = 1; }
		if (!NumericUtil.equals(this.mvaRatingC, bean.mvaRatingC, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.basemvaRatingC is not equal"); eql = 1; }
		
		if (this.bra_code != bean.bra_code) {
			IpssLogger.ipssLogger.warning("BaseBranchBean.bra_code is not equal"); eql = 1; }

		return eql;
	}	
	
	public boolean validate(List<String> msgList) {
		return true;
	}
}
