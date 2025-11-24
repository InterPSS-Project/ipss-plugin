package org.interpss.plugin.exchange.bean;

/**
 * Base class for exchange analysis result info bean
 * 
 * @author mzhou
 *
 */
public abstract class BaseExchangeBean {
	// the length of the result arrays
	public int lenght;
	// the exchange object id array
	public String[] ids;
	
	/** Constructor
	 * 
	 * @param ids the exchange object id array
	 */
	public BaseExchangeBean(String[] ids) {
		this.lenght = ids.length;
		this.ids = ids;
	}
}
