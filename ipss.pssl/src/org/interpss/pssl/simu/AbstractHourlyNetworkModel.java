package org.interpss.pssl.simu;

import java.util.List;

import org.interpss.pssl.plugin.IpssAdapter.FileImportDSL;
import org.interpss.pssl.simu.IpssDclf.DclfAlgorithmDSL;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.funcImpl.AclfNetHelper;
import com.interpss.core.funcImpl.ZeroZBranchProcesor;

/**
 * Abstract base Hourly network model object for storing base case and the current AclfNet object.       
 * 
 * @author mzhou
 *
 */
public abstract class AbstractHourlyNetworkModel {
	protected int hour = 0;
	private AclfNetwork aclfNet = null;
	private AclfNetwork basecaseAclfNet = null;
	
	//private ChangeRecorder recorderBaseNet;
	
	/**
	 * Get the basecase AclfNetwork object. 
	 * 
	 * @return
	 */
	public AclfNetwork getBasecaseAclfNet() { 
		//this.basecaseAclfNet.rollback();
		return this.basecaseAclfNet; }

	/**
	 * return hour at which the AclfNetwork object is configured 
	 * 
	 * @return
	 */
	public int getHour() {
		return this.hour;
	}
	
	/**
	 * Set hour for the network model and configure AclfNetwork object for the hour. To
	 * be implemented by sub-class. The aclfNet object should be rollback and bookmarked.
	 * 
	 * @param hr
	 * @return
	 */
	public abstract <T> T setHour(int hr) throws InterpssException;
	
	/**
	 * process the network data for network consolidation
	 * 
	 * @param smallBranchZ
	 * @throws InterpssException
	 */
	public abstract void processNetDataForConsolidation(double smallBranchZ) throws InterpssException;
	
	/**
	 * Get current AclfNetwork object in the network model  
	 * 
	 * @return
	 */
	public AclfNetwork getAclfNet() { 
		return this.aclfNet; }

	/**
	 * Set model hour, and get consolidated AclfNetwork object for the hour. If AclfNet contains
	 * contingency, the contingency outage branch will be protected during the
	 * network consolidation process.   
	 * 
	 * @param hr 
	 * @param smallBranchZ 
	 * @return
	 */
	public AclfNetwork getAclfNet(int hr, double smallBranchZ) throws InterpssException { 
		this.setHour(hr);
		this.getAclfNet().setZeroZBranchThreshold(smallBranchZ);
		
		processNetDataForConsolidation(smallBranchZ);

	  	ZeroZBranchProcesor proc = new ZeroZBranchProcesor();
	  	if (this.getAclfNet().getContingencyList().size() > 0)
	  		proc.setContingencyList(this.getAclfNet().getContingencyList());
	  	this.getAclfNet().accept(proc);
	  	
		return this.getAclfNet(); 
	}		
	
	/**
	 * create network case object based on the File import DSL
	 * 
	 * @param dsl
	 * @throws InterpssException
	 */
	protected void createAclfNetCase(FileImportDSL dsl) throws InterpssException {
		this.basecaseAclfNet = dsl.getImportedObj();
		this.aclfNet = this.basecaseAclfNet;
	}
	

	/**
	 * create a DclfAlgoithm DSL for the hour and perform dead bus analysis for the 
	 * network object of the hour. Island buses (dead bus) are turned-off.
	 * 	
	 * @param hr ED hour
	 * @param deadBusIdList for storing dead bus ids
	 * @return DclfAlgoithm DSL object
	 * @throws InterpssException
	 */
	public DclfAlgorithmDSL createDclfAlgo(int hr, List<String> deadBusIdList) throws InterpssException {	
		// set hr for the network model, Apply all
		// outage and override for the hour
		setHour(hr);
		
		// find island bus in the current AclfNetwork object
		new AclfNetHelper(this.getAclfNet()).findPreContNetworkIslanding(deadBusIdList);

		/*
		 * define DCLF algorithm for the sensitivity analysis and run DCLF analysis.
		 * DCLF results are needed for the Loss analysis and contingency analysis
		 */
		return IpssDclf.createDclfAlgorithm(getAclfNet());	
	}
	
	/**
	 * create a DclfAlgoithm DSL for the hour and perform dead bus analysis for the 
	 * network object of the hour. Island buses (dead bus) are turned-off.
	 * 	
	 * @param hr ED hour
	 * @param deadBusIdList for storing dead bus ids
	 * @return DclfAlgoithm DSL object
	 * @throws InterpssException
	 */
	public DclfAlgorithmDSL createDclfAlgo(int hr, double smallBranchZ, List<String> deadBusIdList) throws InterpssException {	
		// set hr for the network model, Apply all
		// outage and override for the hour
		setHour(hr);
		this.getAclfNet().setZeroZBranchThreshold(smallBranchZ);

		processNetDataForConsolidation(smallBranchZ);

	  	ZeroZBranchProcesor proc = new ZeroZBranchProcesor();
	  	if (this.getAclfNet().getContingencyList().size() > 0)
	  		proc.setContingencyList(this.getAclfNet().getContingencyList());
	  	this.getAclfNet().accept(proc);
	  	
		// find island bus in the current AclfNetwork object
		new AclfNetHelper(this.getAclfNet()).findPreContNetworkIslanding(deadBusIdList);	  	
		
		/*
		 * define DCLF algorithm for the sensitivity analysis and run DCLF analysis.
		 * DCLF results are needed for the Loss analysis and contingency analysis
		 */
		return IpssDclf.createDclfAlgorithm(getAclfNet());	
	}	
	
	/*
	 * for parallel computing implementation
	 */
	
	// TODO to be implemented
	/**
	 * Borrow an AclfNetwork object from the object pool. The AclfNetwork object is 
	 * configure for the ED hour and dead buses (island bus) are identified for the
	 * hourly AclfNetwork object
	 * 
	 * @param hr ED hour
	 * @param deadBusIdList a list storing island bus list in the network
	 * @return
	 * @throws InterpssException
	 */
	public DclfAlgorithmDSL borrowDclfAlgo(int hr, List<String> deadBusIdList) throws InterpssException {
		return null;
	}
	
	// TODO to be implemented
	/**
	 * Return the AclfNetwork object wrapped in the Dclf Algorithm DSL object into the 
	 * object pool.
	 * 
	 * @param algo
	 */
	public void returnDclfAlgo(DclfAlgorithmDSL algo) {
		//algo.destroy();
	}
}
