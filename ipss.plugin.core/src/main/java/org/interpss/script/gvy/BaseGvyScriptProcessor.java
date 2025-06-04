package org.interpss.script.gvy;

import groovy.lang.GroovyShell;

/**
 * Base Groovy script processor implementation.
 *
 */
public abstract class BaseGvyScriptProcessor {
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
	 */
	public GroovyShell getShell() {
		return this.shell;
	}
}
