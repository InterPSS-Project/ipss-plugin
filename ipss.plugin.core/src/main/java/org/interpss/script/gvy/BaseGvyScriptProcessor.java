package org.interpss.script.gvy;

import groovy.lang.GroovyShell;

/**
 * Base Groovy script processor implementation.
 *
 */
public abstract class BaseGvyScriptProcessor {
	public static final String GVY_IMPORTS = 
			"import org.apache.commons.math3.complex.Complex;";
	
	protected GroovyShell shell;
	
	/**
	 * Constructor
	 */
	public BaseGvyScriptProcessor() {
	}
	
	/**
	 * Get the GroovyShell instance.
	 * 
	 * @return the GroovyShell instance
	 *
	public GroovyShell getShell() {
		return this.shell;
	}
	 */
	
	public Object evaluate(String groovyCode) {
		return this.shell.evaluate(GVY_IMPORTS + groovyCode);
	}
	
}
