package org.interpss.plugin.optadj.algo;

import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.math3.optim.linear.Relationship;
import org.interpss.plugin.optadj.optimizer.BaseStateOptimizer;
import org.interpss.plugin.optadj.optimizer.bean.DeviceConstrainData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;

/**
 * 
 * @author Donghao.F
 * 
 * 
 * 
 */
public class AclfNetATCOptimizer extends AclfNetContigencyOptimizer {
	
	private static final Logger log = LoggerFactory.getLogger(AclfNetATCOptimizer.class);
	
	Set<AclfLoad>  controlLoadSet;
	
	Set<AclfGen>  controlGenSet;
	
    public AclfNetATCOptimizer(ContingencyAnalysisAlgorithm dclfAlgo, BaseStateOptimizer optimizer) {
		super(dclfAlgo);
		this.setOptimizer(optimizer);
	}
	
	@Override
	protected void buildGenConstrain() {
		controlGenMap.forEach((no, gen) -> {
			getOptimizer().addConstraint(new DeviceConstrainData(0, Relationship.GEQ, 0, no));

			getOptimizer().addConstraint(new DeviceConstrainData(0, Relationship.LEQ, 1000, no));
		});
	}

	@Override
	protected void buildLoadConstrain() {
		controlLoadMap.forEach((no, load) -> {
			DeviceConstrainData data = new DeviceConstrainData(0, Relationship.LEQ, 0, no);
			data.setLoad(true);
			getOptimizer().addConstraint(data);
			data = new DeviceConstrainData(0, Relationship.GEQ, -1000, no);
			data.setLoad(true);
			getOptimizer().addConstraint(data);
		});
	}

	@Override
	protected Set<AclfLoad> buildControlLoadSet() {
		return this.controlLoadSet;

	}

	@Override
	protected Set<AclfGen> buildControlGenSet() {
		return this.controlGenSet;

	}


	public void setControlLoadSet(Set<String> controlLoadSet) {
		this.controlLoadSet = this.dclfAlgo.getAclfNet().createAclfLoadNameLookupTable(true).values().stream()
				.filter(load -> controlLoadSet.contains(load.getId())).collect(Collectors.toSet());
	}


	public void setControlGenSet(Set<String> controlGenSet) {
		this.controlGenSet = this.dclfAlgo.getAclfNet().createAclfGenNameLookupTable(true).values().stream()
				.filter(gen -> controlGenSet.contains(gen.getId())).collect(Collectors.toSet());;
	}
	
	
}
