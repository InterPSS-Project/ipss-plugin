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
	 * Evaluate the given Groovy code with added imports.
	 * 
	 * @param groovyCode the Groovy code to evaluate
	 * @return the result of the evaluation
	 */
	public Object evaluate(String groovyCode) {
		return this.shell.evaluate(GVY_IMPORTS + groovyCode);
	}
	
}
