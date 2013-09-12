package org.interpss.datamodel.bean;

import java.util.List;

import org.interpss.datamodel.bean.datatype.ComplexBean;
import org.interpss.numeric.util.NumericUtil;

import com.interpss.common.util.IpssLogger;

public class BaseBusBean extends BaseJSONBean {
		
	public long 
	    number;    
	
	public int status;
	
	public double
		base_v,				// bus base voltage
		v_mag= 1.0,          // bus voltage in pu		
		v_ang = 0.0,		// bus voltage angle
	    vmax = 1.1,
	    vmin = 0.9;
		
	public ComplexBean
	     gen, 					// bus generation
	    load, 					// bus load
	    shunt;
	
	public long 
		area =1, 				// bus area number/id
		zone =1;				// bus zone number/id	
		
	public BaseBusBean() {}
	
	@Override public int compareTo(BaseJSONBean b) {
		int eql = super.compareTo(b);
		
		BaseBusBean bean = (BaseBusBean)b;

		if (this.number != bean.number) {
			IpssLogger.ipssLogger.warning("BaseBusBean.number is not equal, " + this.number + ", " + bean.number); eql = 1; }

		if (!NumericUtil.equals(this.base_v, bean.base_v, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.base_v is not equal, " + this.base_v + ", " + bean.base_v); eql = 1; }
		if (!NumericUtil.equals(this.v_mag, bean.v_mag, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.v_mag is not equal, " + this.v_mag + ", " + bean.v_mag); eql = 1;	}
		if (!NumericUtil.equals(this.v_ang, bean.v_ang, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.v_ang is not equal, " + this.v_ang + ", " + bean.v_ang); eql = 1; }
		if (!NumericUtil.equals(this.vmax, bean.vmax, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.vmax is not equal, " + this.vmax + ", " + bean.vmax); eql = 1; }
		if (!NumericUtil.equals(this.vmin, bean.vmin, CMP_ERR)) {
			IpssLogger.ipssLogger.warning("BaseBusBean.vmin is not equal, " + this.vmin + ", " + bean.vmin); eql = 1; }

		if (this.gen.compareTo(bean.gen) != 0) {
			IpssLogger.ipssLogger.warning("BaseBusBean.gen is not equal"); eql = 1; }
		if (this.load.compareTo(bean.load) != 0) {
			IpssLogger.ipssLogger.warning("BaseBusBean.load is not equal"); eql = 1; }
		if (this.shunt.compareTo(bean.shunt) != 0) {
			IpssLogger.ipssLogger.warning("BaseBusBean.shunt is not equal"); eql = 1; }
		
		if (this.area != bean.area) {
			IpssLogger.ipssLogger.warning("BaseBusBean.area is not equal, " + this.area + ", " + bean.area); eql = 1; }
		if (this.zone != bean.zone) {
			IpssLogger.ipssLogger.warning("BaseBusBean.zone is not equal, " + this.zone + ", " + bean.zone); eql = 1; }
		
		return eql;
	}	

	public boolean validate(List<String> msgList) { 
		return true;
	}
}
