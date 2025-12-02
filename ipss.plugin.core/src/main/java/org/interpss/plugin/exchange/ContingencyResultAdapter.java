package org.interpss.plugin.exchange;

import java.util.concurrent.ConcurrentHashMap;

/**
 * Adapter class for the contingency analysis result exchange
 * 
 * @author mzhou
 *
 */
public class ContingencyResultAdapter {
	// a concurrent hash map to hold the contingency analysis result info beans
	private ConcurrentHashMap<String, AclfResultExchangeAdapter> contingencyResultMap;
	
	/** 
	 * Constructor
	 * 
	 */
	public ContingencyResultAdapter() {
		contingencyResultMap = new ConcurrentHashMap<>();
	}
	
	/** Get the contingency result map
	 * 
	 * @return the contingency result map
	 */
	public ConcurrentHashMap<String, AclfResultExchangeAdapter> getContingencyResultMap() {
		return contingencyResultMap;
	}
}
