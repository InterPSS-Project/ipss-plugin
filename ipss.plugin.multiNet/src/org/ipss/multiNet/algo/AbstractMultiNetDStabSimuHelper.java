package org.ipss.multiNet.algo;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.linear.Array2DRowFieldMatrix;
import org.apache.commons.math3.linear.FieldMatrix;
import org.ipss.multiNet.equivalent.NetworkEquivUtil;
import org.ipss.multiNet.equivalent.NetworkEquivalent;

import com.interpss.CoreObjectFactory;
import com.interpss.dstab.BaseDStabNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

public abstract class AbstractMultiNetDStabSimuHelper {
	

	protected BaseDStabNetwork<?,?> net = null;
	protected SubNetworkProcessor subNetProcessor = null;
	protected Hashtable<String, NetworkEquivalent> subNetEquivTable = null;
	protected Hashtable<String, Hashtable<String, Complex>> subNetCurrInjTable = null;
	protected Hashtable<String, double[][]> subNetIncidenceAryTable = null;  //Pk defined in the MATE paper
	protected Hashtable<String, FieldMatrix<Complex>> subNetIncidenceMatrixTable = null;  //Pk defined in the MATE paper

	
	public AbstractMultiNetDStabSimuHelper(){
		
	}
	
	/**
	 *  in the MultiNetDStabSimuHelper constructor, subnetwork Y matrices are built, the Thevenin equivalent Zth 
	    // matrices are prepared, the interface tie-line to boundary bus incidence matrix is formed.
	 * @param net
	 * @param subNetProc
	 */
	public  AbstractMultiNetDStabSimuHelper(BaseDStabNetwork<?,?> net, SubNetworkProcessor subNetProc){
		this.net = net;
		this.subNetProcessor = subNetProc;
		
		//since the interconnection of the subnetworks are preset and kept constant during the simulation, 
		//the corresponding interface branches to subnetwork boundary buses incidence matrix can be prepared. 
		prepareInterfaceBranchBusIncidenceMatrix();
		
		//consolidate the from/toShuntY and Half charging shuntY of the tie-lies to the terminal buses
		processInterfaceBranchEquiv();
		
		
	}
	
	
	public void calculateSubNetTheveninEquiv(){
		this.subNetEquivTable = NetworkEquivUtil.calMultiNetPosSeqTheveninEquiv(this.subNetProcessor);
	}
	
	 /**
	  * as the full network are split by the "cutting off" the connection through the tie-lines, the shunt admittances at both ends of the 
	  * branch needs to be added to the boundary buses, at shuntY are not modeled in the boundary subnetwork, which are formed by the tie-lines
	  * connecting the subsystems. 
	  */
      public  void processInterfaceBranchEquiv(){
		
		/*
		 *  Major steps
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
		
			if(branch.isActive()){
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
					 * Step-2, add the tie-line equivalent current injection to the terminal buses
					 */
			        //From bus side
			        int fChildNetIdx =  this.subNetProcessor.getBusId2SubNetworkTable().get(fBus.getId());
			        BaseDStabNetwork<?,?> fChileNet = this.subNetProcessor.getSubNetworkList().get(fChildNetIdx);
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
			        BaseDStabNetwork<?,?> tChileNet = this.subNetProcessor.getSubNetworkList().get(tChildNetIdx);
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
			       
			    }// end of branch-active
			}
		
		  // set the power flow convergence status 
		  for(BaseDStabNetwork<?,?> subNet: this.subNetProcessor.getSubNetworkList()){

				  subNet.setLfConverged(true);
				  
				  // this is necessary because bus number is required for forming the Ymatrix
					if ( !subNet.isBusNumberArranged() ) {
						//subNet.accept(CoreObjectFactory.createBusNoArrangeVisitor());
						subNet.arrangeBusNumber();
					}	
		  }
	   
	  }
      
      
      
      /**
       * prepare the Zl matrix of the interconnection tie-line subsystem, which
       * links the Zth matrices of each subsystems and the tie-line impedances zl, as illustrated below
       * 
       * |Zth_i|-------zl------|Zth_j|
       * 
       */
      public abstract void prepareBoundarySubSystemMatrix();
      
      
      /**
       *  fetch the boundary bus voltages and update the Thevenin equivalent source arrays
       * @return
       */
      public abstract Hashtable<String, NetworkEquivalent> updateSubNetworkEquivSource();
      
      
      
      /**
       * update the Thevenin equivalent impedance matrix of all subNetworks
       * @return
       */
      public abstract void updateSubNetworkEquivMatrix();
      
      /**
       *  update the Thevenin equivalent impedance matrix of a specific subNetwork
       * @param subNetworkId
       * @return
       */
      public abstract void updateSubNetworkEquivMatrix(String subNetworkId);
      	
      
      /**
       * solve each sub system without considering current injections of the tie-lines, 
       * and subsequently update the boundary Thevenin equivalent voltage sources.
       * @return
       */
      public abstract Hashtable<String, NetworkEquivalent> solvSubNetAndUpdateEquivSource();
      
      
      /**
       * Solve the tie-line boundary subsystem to calculate the current flowing through the tie-lines
       * @return
       */
      public abstract boolean  solveBoundarySubSystem();
      
      

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
       public abstract boolean solveSubNetWithBoundaryCurrInjection();
      
      
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
      	this.subNetIncidenceAryTable = new Hashtable<>();
      	
      	 for(BaseDStabNetwork<?,?> subNet: this.subNetProcessor.getSubNetworkList()){
      		    int m =this.subNetProcessor.getInterfaceBranchIdList().size();
      		    
      		    List<String> busIdList = this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId());
      		    int n = busIdList.size();
      		    FieldMatrix<Complex> Pk_TMatrix = new Array2DRowFieldMatrix<Complex>(ComplexField.getInstance(),m,n);
      		    
      		    //TODO convert Pk_T from int to double, to try to solve the numerical drifting issue.
      		    double[][] Pk_T_ary = new double[m][n];
      		    		
      		    int i = 0;
      			for(String branchId:this.subNetProcessor.getInterfaceBranchIdList()){
      				DStabBranch branch= net.getBranch(branchId);
      			
  					DStabBus fBus = (DStabBus) branch.getFromBus();
  					DStabBus tBus = (DStabBus) branch.getToBus();
  					int j =0;
  					for(String busId:busIdList){
  						if(busId.equals(fBus.getId())){
  							Pk_TMatrix .setEntry(i, j, new Complex(1.0d,0.0d));
  							Pk_T_ary[i][j] = 1.0d;
  						}
  						else if(busId.equals(tBus.getId())){
  							  Pk_TMatrix.setEntry(i, j, new Complex(-1.0d,0.0d));
  							  Pk_T_ary[i][j] = -1.0d;
  						}
  						
  						j++;
  					}
  					
  					i++;
  					
      			} // end of interface branches loop
      			
      			this.subNetIncidenceMatrixTable.put(subNet.getId(), Pk_TMatrix);
      			this.subNetIncidenceAryTable.put(subNet.getId(), Pk_T_ary);
      	 } // end of subNetwork loop
      	 return  this.subNetIncidenceMatrixTable;
      	
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
	   
	   public Hashtable<String, Hashtable<String, Complex>> getSubNetCurrInjTable(){
		   return this.subNetCurrInjTable;
	   }

}
