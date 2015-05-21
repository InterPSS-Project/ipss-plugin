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

import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

public class MultiNetDStabSimuHelper {
	
	private DStabilityNetwork net = null;
	private SubNetworkProcessor subNetProcessor = null;
	private Hashtable<String, NetworkEquivalent> subNetEquivTable = null;
	private Hashtable<String, Hashtable<String, Complex>> subNetCurrInjTable = null;
	private Hashtable<String, FieldMatrix<Complex>> subNetIncidenceMatrixTable = null;  //Pk defined in the MATE paper
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
		
		this.subNetEquivTable = NetworkEquivUtil.calMultiNetworkEquiv(this.subNetProcessor);
	}
	
	
	
public  void processInterfaceBranchEquiv(){
		
		/*
		 *  (1) add the fromShuntY and HShuntY of the tieLine branch to the boundary bus shuntY, if there is no fixed shuntY 
		 *  in the boundary bus, create one first.
		 *  
		 *  step-1: obtain the boundary information from SubNetworkProcessor, iterate over the interface branches
		 *  
		 *  step-2: with the iteration, obtain the two terminal buses, add the fromShuntY and HShuntY of the tie-line to the fixed shuntY of both buses 
		 *  
		 *  (2) equivalent current injection into the boundary buses which are to represent the contribution from the buses on the other end of the tie-line
		 *  
		 *  step-3:  calculate the equivalent current injection and add to the subNetwork as custom current injection
		 *  
		 *  
		 */
		
		//Check the full network power flow convergence status
		
		if(!this.net.isLfConverged()){
			try {
				throw new Exception("The full network is not converged, cannot proceed the pre-processing of Multi-SubNetworks");
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		for(String branchId:this.subNetProcessor.getInterfaceBranchIdList()){
			DStabBranch branch= net.getBranch(branchId);
		
			
					DStabBus fBus = (DStabBus) branch.getFromBus();
					DStabBus tBus = (DStabBus) branch.getToBus();
					
					/*
					 * Step-1, add the tie-line equivalent shuntY1/2/0 to the terminal buses
					 */
					//From bus
					if(fBus.getShuntY()!=null){
						
						fBus.setShuntY(fBus.getShuntY().add(branch.getFromShuntY()).add(branch.getHShuntY()));
						
					}
			        if(fBus.getScFixedShuntY0()==null){
						
						fBus.setScFixedShuntY0(new Complex(0,branch.getHB0()));
						
					}
			        else
			        	fBus.setScFixedShuntY0(fBus.getScFixedShuntY0().add(new Complex(0,branch.getHB0())));
			        
			        
			        
					//To bus
					if(tBus.getShuntY()!=null){
						
						tBus.setShuntY(tBus.getShuntY().add(branch.getToShuntY()).add(branch.getHShuntY()));
						
					}
			        if(tBus.getScFixedShuntY0()==null){
						
						tBus.setScFixedShuntY0(new Complex(0,branch.getHB0()));
						
					}
			        else
			        	tBus.setScFixedShuntY0(tBus.getScFixedShuntY0().add(new Complex(0,branch.getHB0())));
			        
	        
			        /*
					 * Step-2, add the tie-line equivalent curret injection to the terminal buses
					 */
			        //From bus side
			        int fChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(fBus.getId());
			        DStabilityNetwork fChileNet = this.subNetProcessor.getSubNetworkList().get(fChildNetIdx);
			        //HasCurrentInejctionTable 
			        if(fChileNet.getCustomBusCurrInjHashtable()==null){
			           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
			           fChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
			           customBusCurTable.put(fBus.getId(),  branch.getY().multiply((tBus.getVoltage().subtract(fBus.getVoltage()))));
			        }
			        else{
			        	//fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
			        	
			        	Complex currentInj = fChileNet.getCustomBusCurrInjHashtable().get(fBus.getId());
			        	if(currentInj==null) currentInj = new Complex(0,0) ;
			        	currentInj = currentInj.add(branch.getY().multiply((tBus.getVoltage().subtract(fBus.getVoltage()))));
			        	fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), currentInj);
			        }
			        	
			        
			        
			        
			        //To Bus side
			        int tChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(tBus.getId());
			        DStabilityNetwork tChileNet = this.subNetProcessor.getSubNetworkList().get(tChildNetIdx);
			        //HasCurrentInejctionTable 
			        if(tChileNet.getCustomBusCurrInjHashtable()==null){
			           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
			           tChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
			           customBusCurTable.put(tBus.getId(), branch.getY().multiply((fBus.getVoltage().subtract(tBus.getVoltage()))));
			        }
			        else{
			        	Complex currentInj = tChileNet.getCustomBusCurrInjHashtable().get(tBus.getId());
			        	if(currentInj==null) currentInj = new Complex(0,0) ;
			        	currentInj = currentInj.add(branch.getY().multiply((fBus.getVoltage().subtract(tBus.getVoltage()))));
			        	tChileNet.getCustomBusCurrInjHashtable().put(tBus.getId(), currentInj);
			        	
			        }
			   // after mapping the effect of the tie-line branch to both terminal buses, set it to out-of-service     
			  branch.setStatus(false);      	
			}
		
		  // set the power flow convergence status 
		  for( DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){

				  subNet.setLfConverged(true);
			  
		  }
	   
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
    public Hashtable<String, NetworkEquivalent> updateSubNetworkEquivMatrix(){
    	
    	return this.subNetEquivTable = NetworkEquivUtil.calMultiNetworkEquiv(this.subNetProcessor);
    }
    
    /**
     *  update the Thevenin equivalent impedance matrix of a specific subNetwork
     * @param subNetworkId
     * @return
     */
    public Hashtable<String, NetworkEquivalent> updateSubNetworkEquivMatrix(String subNetworkId){
    	DStabilityNetwork subNet = this.subNetProcessor.getSubNetwork(subNetworkId);
    	if(subNet!=null){
	    	NetworkEquivalent equiv = NetworkEquivUtil.calPosSeqNetworkTheveninEquiv(subNet,
	    			       this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNetworkId));
	    	
	    	this.subNetEquivTable.put(subNetworkId, equiv);
    	}
    	else
    		throw new Error("No subnetwork is found with the input subNetwork Id!");
    	return this.subNetEquivTable; 
    }
    
    
    
    /**
     * Solve the [Zl][Il]=[Eth] dense matrix equations to obtain the currents flowing through the tie-lines 
     * @return  the boundary current injection table for all subnetworks
     */
    public Hashtable<String, Hashtable<String,Complex>> solveBoundarySubSystem(){
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
    	return this.subNetCurrInjTable;
    	
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
    private Hashtable<String, FieldMatrix<Complex>> prepareInterfaceBranchBusIncidenceMatrix(){
    	
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
    public FieldMatrix<Complex> prepareBoundarySubSystemMatrix(){
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
    				
      return this.Zl;
    	
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
     
     
     
     
     

	
	/*
	   public boolean updateBoundaryBusEquivCurrentInjection(){
		   
		   // need to first reset all customized current injection to be zero
		   for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			   subNet.setCustomBusCurrInjHashtable(null);
		   }
		  
		   for(String branchId: subNetProcessor.getInterfaceBranchIdList()){
				DStabBranch branch= net.getBranch(branchId);
				
				// at this stage, the statuses of boundary buses  are inactive, so it needs to be turned to active first;
				// as the Ymatrix is already built, this won't affect the V=INV(Y)*I network solution
				branch.setStatus(true);
				
				
				DStabBus fBus = (DStabBus) branch.getFromBus();
				DStabBus tBus = (DStabBus) branch.getToBus();

				 
				 //Step-2, add the tie-line equivalent curret injection to the terminal buses
				 
		        //From bus side
		        int fChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(fBus.getId());
		        DStabilityNetwork fChileNet = this.subNetProcessor.getSubNetworkList().get(fChildNetIdx);
		        //HasCurrentInejctionTable 
		        if(fChileNet.getCustomBusCurrInjHashtable()==null){
		           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
		           fChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
		           customBusCurTable.put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
		        }
		        else{
		        	//fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
		        	
		        	Complex currentInj = fChileNet.getCustomBusCurrInjHashtable().get(fBus.getId());
		        	if(currentInj==null) currentInj = new Complex(0,0) ;
		        	currentInj = currentInj.add(tBus.getVoltage().multiply(branch.yft()).multiply(-1.0d));
		        	fChileNet.getCustomBusCurrInjHashtable().put(fBus.getId(), currentInj);
		        }
		        	
		        
		        
		        
		        //To Bus side
		        int tChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(tBus.getId());
		        DStabilityNetwork tChileNet = this.subNetProcessor.getSubNetworkList().get(tChildNetIdx);
		        //HasCurrentInejctionTable 
		        if(tChileNet.getCustomBusCurrInjHashtable()==null){
		           Hashtable<String, Complex> customBusCurTable = new Hashtable<>();
		           tChileNet.setCustomBusCurrInjHashtable(customBusCurTable);
		           customBusCurTable.put(tBus.getId(), fBus.getVoltage().multiply(branch.ytf()).multiply(-1.0d));
		        }
		        else{
		        	Complex currentInj = tChileNet.getCustomBusCurrInjHashtable().get(tBus.getId());
		        	if(currentInj==null) currentInj = new Complex(0,0) ;
		        	currentInj = currentInj.add(fBus.getVoltage().multiply(branch.ytf()).multiply(-1.0d));
		        	tChileNet.getCustomBusCurrInjHashtable().put(tBus.getId(), currentInj);
		        	
		        }
		        
		        
		        branch.setStatus(false);
		   }
		   
		   for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			   System.out.println(subNet.getId()+"   customCurrentTable:\n"+subNet.getCustomBusCurrInjHashtable());
		   }
		   
		   return true;
		   
	   }
	   */
	   
	   public void predictNextStepBoundaryVoltage(DStabilityNetwork net, SubNetworkProcessor subNetProc){
		   
	   }
	    
	   
	   public SubNetworkProcessor getSubNetworkProcessor(){
		   return this.subNetProcessor;
	   }
	   
	   public Hashtable<String, FieldMatrix<Complex>>  getSubNetIncidenceMatrixTable(){
		   
	      return this.subNetIncidenceMatrixTable;
       }
	   
	   public Hashtable<String, NetworkEquivalent> getSubNetEquivTable(){
		   return this.subNetEquivTable;
	   }
	 

}
