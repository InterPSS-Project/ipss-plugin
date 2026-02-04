package org.interpss.plugin.exchange;

import java.util.concurrent.ConcurrentHashMap;

import org.interpss.plugin.exchange.bean.ContingencyExchangeInfo;

/**
 * Adapter class for the contingency analysis result exchange info container
 * 
 * @author mzhou
 *
 */
public class ContingencyResultExContainer {
	// a concurrent hash map to hold the contingency analysis result info beans
	private ConcurrentHashMap<String, ContingencyExchangeInfo> contingencyResultMap;
	
	/** 
	 * Constructor
	 * 
	 */
	public ContingencyResultExContainer() {
		contingencyResultMap = new ConcurrentHashMap<>();
	}
	
	/** Get the contingency result map
	 * 
	 * @return the contingency result map
	 */
	public ConcurrentHashMap<String, ContingencyExchangeInfo> getContingencyResultMap() {
		return contingencyResultMap;
	}
}
