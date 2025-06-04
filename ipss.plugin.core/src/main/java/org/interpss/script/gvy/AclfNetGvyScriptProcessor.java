package org.interpss.script.gvy;

import com.interpss.core.aclf.AclfNetwork;

import groovy.lang.Binding;
import groovy.lang.GroovyShell;

/**
 * AclfNetwork Groovy script processor implementation.
 *
 */
public class AclfNetGvyScriptProcessor extends BaseGvyScriptProcessor {
	private AclfNetwork aclfNet;
	
	/**
	 * Constructor
	 * 
	 * @param aclfNet
	 */
	public AclfNetGvyScriptProcessor(AclfNetwork aclfNet) {
		this.aclfNet = aclfNet;
		
	  	// 创建Binding对象，用于传递变量
        Binding binding = new Binding();
        binding.setVariable("aclfnet", aclfNet);
        
        this.shell = new GroovyShell(binding);
	}
	
	/**
	 * Get the AclfNetwork instance.
	 * 
	 * @return the AclfNetwork instance
	 */
	public AclfNetwork getAclfNet() {
		return this.aclfNet;
	}
}
