package org.ipss.multiNet.algo;

import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.apache.commons.math3.linear.FieldLUDecomposition;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.FieldVector;
import org.apache.commons.math3.linear.RealMatrix;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.ipss.multiNet.equivalent.NetworkEquivUtil;
import org.ipss.multiNet.equivalent.NetworkEquivalent;

import com.interpss.common.util.IpssLogger;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;
/**
 * MultiNetDStabSimuHelper is a "helper" class for multi area, positive sequence based Transient Stability simulation. 
 * This is basic multiNetDStabSimuHelper implementation. For  three-phase and/or three-sequence based multi area TS simulation,
 * please refer to the MultiNet3Ph3SeqDstabSimuHelper class
 * @author Qiuhua Huang
 *
 */
public class MultiNetDStabSimuHelper extends AbstractMultiNetDStabSimuHelper{
	

	private FieldMatrix<Complex> Zl = null; // boundary subsystem matrix
	
	
	/**
	 *  in the MultiNetDStabSimuHelper constructor, subnetwork Y matrices are built, the Thevenin equivalent Zth 
	    // matrices are prepared, the interface tie-line to boundary bus incidence matrix is formed.
	 * @param net
	 * @param subNetProc
	 */
	public  MultiNetDStabSimuHelper(DStabilityNetwork net, SubNetworkProcessor subNetProc){
		this.net = net;
		this.subNetProcessor = subNetProc;
		
		//since the interconnection of the subnetworks are preset and kept constant during the simulation, 
		//the corresponding interface branches to subnetwork boundary buses incidence matrix can be prepared. 
		prepareInterfaceBranchBusIncidenceMatrix();
		
		//consolidate the from/toShuntY and Half charging shuntY of the tie-lies to the terminal buses
		processInterfaceBranchEquiv();
		
		this.subNetEquivTable = NetworkEquivUtil.calMultiNetPosSeqTheveninEquiv(this.subNetProcessor);
	}
	

     
    public Hashtable<String, NetworkEquivalent> solvSubNetAndUpdateEquivSource(){
    	 
    	if(subNetEquivTable ==null)
    	     throw new Error (" The subnetwork equivalent hastable is not prepared yet");
    	
    	for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
		
    	    // In order to obtain the Thevenin voltage source viewed at the boundary 
    	    // there should  be no current injection from the interface tie-lines
    		//if(subNet.initDStabNet())
    	     
    		// make sure there is no current injection at the boundary
    		 subNet.setCustomBusCurrInjHashtable(null);
    		 
    	    // perform network solution to get the bus voltages
    	     
    		 subNet.solveNetEqn();
    		 
    	
    	    // the voltages at the boundary buses are the Thevenin voltages 
    		// the busIds are ordered in an ascending manner
    		 // the matrix and source parts are ordered in the same sequence as defined in BoundaryBusList.
    		   int i =0;
    		   for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
    			        Complex v = subNet.getBus(busId).getVoltage();
    			        subNetEquivTable.get(subNet.getId()).getSource()[i]=v;
    			        i++;
    		   }
		   }
    	 return  subNetEquivTable;
    }
    
    public Hashtable<String, NetworkEquivalent> updateSubNetworkEquivSource(){
   	 
    	if(subNetEquivTable ==null)
    	     throw new Error (" The subnetwork equivalent hastable is not prepared yet");
    	
    	for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
		
    	    // the voltages at the boundary buses are the Thevenin voltages 
    		// the busIds are ordered in an ascending manner
    		 // the matrix and source parts are ordered in the same sequence as defined in BoundaryBusList.
    		   int i =0;
    		   for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
    			        Complex v = subNet.getBus(busId).getVoltage();
    			        subNetEquivTable.get(subNet.getId()).getSource()[i]=v;
    			        i++;
    		   }
		   }
    	 return  subNetEquivTable;
    }
    
    /**
     * update the Thevenin equivalent impedance matrix of all subNetworks
     * @return
     */
    public void updateSubNetworkEquivMatrix(){
    	
    	this.subNetEquivTable = NetworkEquivUtil.calMultiNetPosSeqTheveninEquiv(this.subNetProcessor);
    }
    
    /**
     *  update the Thevenin equivalent impedance matrix of a specific subNetwork
     * @param subNetworkId
     * @return
     */
    public void updateSubNetworkEquivMatrix(String subNetworkId){
    	DStabilityNetwork subNet = this.subNetProcessor.getSubNetwork(subNetworkId);
    	if(subNet!=null){
	    	NetworkEquivalent equiv = NetworkEquivUtil.calPosSeqNetworkTheveninEquiv(subNet,
	    			       this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNetworkId));
	    	
	    	this.subNetEquivTable.put(subNetworkId, equiv);
    	}
    	else
    		throw new Error("No subnetwork is found with the input subNetwork Id!");
    	
    }
    
    
    
    /**
     * Solve the [Zl][Il]=[Eth] dense matrix equations to obtain the currents flowing through the tie-lines 
     * @return  true  if solving without any error, else false.
     */
    public boolean  solveBoundarySubSystem(){
    	
    	boolean flag =true;
    	// as the matrix is only updated when there is a network change, they can be prepared and updated only when necessary 
    	// Note: preparation and update of Zl is done outside this method
    	
    	this.subNetCurrInjTable = new Hashtable<>();
    	if(this.Zl!=null){
    		// use the latest Thevenin equivalent voltage sources
    		FieldVector<Complex> Eth = new ArrayFieldVector<Complex>( ComplexField.getInstance(), this.Zl.getRowDimension());
    	
    		for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    			Complex[] Vth =this.subNetEquivTable.get(subNet.getId()).getSource();
    			FieldVector<Complex>  Eth_k = new ArrayFieldVector<Complex>(Vth);
    			FieldMatrix<Complex> Pk_T = this.subNetIncidenceMatrixTable.get(subNet.getId());
    			Eth_k = Pk_T.operate(Eth_k);//Returns the result of multiplying this matrix by the vector v.
    			Eth = Eth.add(Eth_k);
    			
    			
    		}
    		
    		FieldLUDecomposition<Complex> lu = new FieldLUDecomposition<>(this.Zl);
    		FieldVector<Complex> currVector = lu.getSolver().solve(Eth);
    		
    		for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    			// mapping the branch current into the 
    			FieldMatrix<Complex> Pk_T = this.subNetIncidenceMatrixTable.get(subNet.getId());
    			FieldVector<Complex>  boundaryBusCurrInj = Pk_T.transpose().operate(currVector);
    			
    			Hashtable<String,Complex> busCurrInjTable = new Hashtable<>();
    			int i =0;
    			for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
    				
    				//Please note the direction of current, as fromBus -> toBus is defined as the positive for the interface tie-lie current
    			    busCurrInjTable.put(busId, boundaryBusCurrInj.getEntry(i).multiply(-1.0));
    			    i++;
    			}
    			
    			this.subNetCurrInjTable.put(subNet.getId(),  busCurrInjTable);
    		}
    		
    		
    		
    	}
    	else{
    		 IpssLogger.getLogger().severe("The boundary sub system [Zl] matrix is null");
    		 flag = false;
    	}
    	return flag;
    	
    }
    
    
    
    
    /**
     * prepare the interface branches to boundary buses incident matrix Pk_T
     * (Pk)transpose is the mapping from the interface branches to the boundary buses of each subNetwork;
		
		Thus, if the Thevenin equivalent viewed at the boundary buses is explicitly calculated instead of 
		inverting the whole Yk matrix, then Pk_T can be much smaller, with 
		
		 Pk_T as a m by n integer matrix 
		 m: Num. of interface branches
		 n: Num of boundary buses,
		 For the boundary bus as the interface branch from bus, the corresponding entry is 1;
		 For to bus, it becomes -1; with no connections, it will be zero
     * 
     * @return
     */
    protected Hashtable<String, FieldMatrix<Complex>> prepareInterfaceBranchBusIncidenceMatrix(){
    	
    	this.subNetIncidenceMatrixTable = new Hashtable<>();
    	
    	 for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    		    int m =this.subNetProcessor.getInterfaceBranchIdList().size();
    		    
    		    List<String> busIdList = this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId());
    		    int n = busIdList.size();
    		    FieldMatrix<Complex> Pk_T = new Array2DRowFieldMatrix<Complex>(ComplexField.getInstance(),m,n);
    		    
    		    int i = 0;
    			for(String branchId:this.subNetProcessor.getInterfaceBranchIdList()){
    				DStabBranch branch= net.getBranch(branchId);
    			
    				        
					DStabBus fBus = (DStabBus) branch.getFromBus();
					DStabBus tBus = (DStabBus) branch.getToBus();
					int j =0;
					for(String busId:busIdList){
						if(busId.equals(fBus.getId())){
							Pk_T.setEntry(i, j, new Complex(1,0));
						}
						else if(busId.equals(tBus.getId()))
							  Pk_T.setEntry(i, j, new Complex(-1,0));
						
						j++;
					}
					
					i++;
					
    			} // end of interface branches loop
    			
    			this.subNetIncidenceMatrixTable.put(subNet.getId(), Pk_T);
    	 } // end of subNetwork loop
    	 return  this.subNetIncidenceMatrixTable;
    	
    }
    
   /**
    *  Build the boundary subsystem matrix (also known as the Thevenin impedance matrix [Zl]);
    * @return
    */
    public void prepareBoundarySubSystemMatrix(){
    	int n = subNetProcessor.getInterfaceBranchIdList().size();
      
        this.Zl = new Array2DRowFieldMatrix<Complex>(ComplexField.getInstance(),n,n);
        
    	if(this.subNetIncidenceMatrixTable !=null){
    		
    		 // initialize the Zl matrix
    		  int i=0;
    		  for(String branchId: subNetProcessor.getInterfaceBranchIdList()){
    			  
  				DStabBranch branch= net.getBranch(branchId);
  				this.Zl.setEntry(i, i, branch.getZ());
  				i++;
    		  }
    		    
    		  	
    		 // Connecting the boundary bus Thevenin equivalent to the interface branches according the
    		  // the interface branch to boundary bus incidence matrix
    		  	 for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    		  		  FieldMatrix<Complex>  Pk_T = this.subNetIncidenceMatrixTable.get(subNet.getId());
    				  FieldMatrix<Complex> Zth_k = new Array2DRowFieldMatrix<>(this.subNetEquivTable.get(subNet.getId()).getMatrix());
    	    		  //  Zl_k = Pk_T*Zth_k*Pk
    				  FieldMatrix<Complex> Zl_k = Pk_T.multiply(Zth_k).multiply(Pk_T.transpose());
    				  
    				  // Zl = Zl + sum{Zl_k}|all subsystems
    				   this.Zl = this.Zl.add(Zl_k);
    			  }
    		 
    		  	 
    	}
    	else{
    		throw new Error("The subNetIncidenceMatrixTable is not initialized yet, cannot procede the boundary subsystem matrix calculation!");
    	}
    				
    	
    }
     
    /**
     * 
     *  solve the subNetworks with only current injection at the boundary buses.
     *  
     *  This is the second phase of the subNetwork solution. The first phase is completed with performing 
     *   calcSubNetworkEquivSource() function. Then superpostition method is used to obtain the final network
     *   solution result.
     *  
     *   bus voltage V = Vinternal + Vext_injection
     * @return
     */
     public boolean solveSubNetWithBoundaryCurrInjection(){
	   
		   // need to first reset all customized current injection to be zero
		   for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			   subNet.setCustomBusCurrInjHashtable(null);
			   
			   ISparseEqnComplex subNetY= subNet.getYMatrix();
			   
			   subNetY.setB2Zero();
			   
			   for(Entry<String,Complex> e: this.subNetCurrInjTable.get(subNet.getId()).entrySet()){
				   subNetY.setBi(e.getValue(),subNet.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				    subNetY.solveEqn();
				} catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return false;
				}
			   
			   for(DStabBus b:subNet.getBusList()){
				   //superpostition method
				   //bus voltage V = Vinternal + Vext_injection
				   b.setVoltage(b.getVoltage().add(subNetY.getX(b.getSortNumber())));
			   }
			   
			   
		   }
		   
		   
		   return true;
	   }
     
    


}
