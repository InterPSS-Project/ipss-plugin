package org.ipss.multiNet.algo;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map.Entry;

import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.complex.ComplexField;
import org.apache.commons.math3.linear.ArrayFieldVector;
import org.apache.commons.math3.linear.FieldLUDecomposition;
import org.apache.commons.math3.linear.FieldMatrix;
import org.apache.commons.math3.linear.FieldVector;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.ipss.multiNet.equivalent.NetworkEquivUtil;
import org.ipss.multiNet.equivalent.NetworkEquivalent;
import org.ipss.sparse.Matrix3x3.SparseEqnComplexMatrix3x3Impl;
import org.ipss.threePhase.basic.Bus3Phase;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

/**
 * 
 * MultiNet3Ph3SeqDStabSimuHelper is specially designed for 3-phase/3-seq mulit-subnetwork TS simulation
 * 
 * The fault is limited to be applied within the three-phase modeling
 * subsystem, while all other subsystems are modeled in three-sequence.
 * 
 * @author Qiuhua Huang
 *
 */
public class MultiNet3Ph3SeqDStabSimuHelper extends AbstractMultiNetDStabSimuHelper{
	
	private Complex3x3[][] ZlAry = null;
	private List<String> threePhModelingSubNetIdList = null; // should be provided after subnetwork creation 
	private Hashtable<String, Hashtable<String, Complex3x1>> subNet3SeqCurrInjTable = null;
	private double negZeroSeqCurrTolerance=1.0E-3;  // in pu;

	
	public MultiNet3Ph3SeqDStabSimuHelper (DStabilityNetwork net, SubNetworkProcessor subNetProc){
		this.net = net;
		this.subNetProcessor = subNetProc;
		
		this.threePhModelingSubNetIdList = subNetProc.getThreePhaseSubNetIdList();
		
		//since the interconnection of the subnetworks are preset and kept constant during the simulation, 
		//the corresponding interface branches to subnetwork boundary buses incidence matrix can be prepared. 
		prepareInterfaceBranchBusIncidenceMatrix();
		
		//consolidate the from/toShuntY and Half charging shuntY of the tie-lies to the terminal buses
		processInterfaceBranchEquiv();
		
		//calculate the subnetwork equivalent
		this.subNetEquivTable = NetworkEquivUtil.calMultiNet3ph3SeqTheveninEquiv(subNetProc, threePhModelingSubNetIdList);
		
		
	}
	
	@Override
	public  void processInterfaceBranchEquiv(){
	     
		
		 // process the equivalent using sequence Y and current injection
		 super.processInterfaceBranchEquiv();
		 
		 // As the method in the super class using positive sequence current injection for equivalencing
		 // Need to further process the boundary current injection table of the three-phase modeling subnetworks
		 if(this.threePhModelingSubNetIdList !=null){
			 for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
				 if(this.threePhModelingSubNetIdList.contains(subNet.getId())){
					 
					 DStabNetwork3Phase subNet3Ph = (DStabNetwork3Phase)subNet;
					 
					 // fetch the positive sequence current and save it to three-sequence
					 
					 if(subNet3Ph.getCustomBusCurrInjHashtable()!=null){
						 
						 Hashtable<String,Complex3x1> threePhCurrInjTable = new Hashtable<> ();
						 
						 for(Entry<String,Complex> entry: subNet3Ph.getCustomBusCurrInjHashtable().entrySet()){
							 
							 Complex3x1 Iabc = Complex3x1.z12_to_abc(new Complex3x1(new Complex(0,0),entry.getValue(),new Complex(0,0)));
							 threePhCurrInjTable.put(entry.getKey(), Iabc);
						 }
							 
						 subNet3Ph.set3phaseCustomCurrInjTable(threePhCurrInjTable);
					 }
				 }
			 }
		 }
		 
	}

	@Override
	public void prepareBoundarySubSystemMatrix() {
		int n = subNetProcessor.getInterfaceBranchIdList().size();
	      
		 this.ZlAry = MatrixUtil.createComplex3x32DArray(n,n);
        
    	if(this.subNetIncidenceMatrixTable !=null){
    		
    		 // initialize the Zl matrix
    		  int i=0;
    		  for(String branchId: subNetProcessor.getInterfaceBranchIdList()){
    			  
  				DStabBranch branch= net.getBranch(branchId);
  				ZlAry[i][i]= new Complex3x3(branch.getZ(),branch.getZ(),branch.getZ0());
  				i++;
    		  }
    		    
    		  	
    		 // Connecting the boundary bus Thevenin equivalent to the interface branches according the
    		  // the interface branch to boundary bus incidence matrix
    		  	 for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    		  		  int[][] Pk_T = this.subNetIncidenceAryTable.get(subNet.getId());
    				  
    		  		  //TODO All Zth_k are now Complex3x3 block matrix
    		  		  Complex3x3[][] Zth_k = this.subNetEquivTable.get(subNet.getId()).getMatrix3x3();
    	    		  
    		  		  
    		  		 int[][] Pk =MatrixUtil.transpose(Pk_T);
    		  		 
    		  		  //  Zl_k = Pk_T*Zth_k*Pk
    		  		 
    		  		   Complex3x3[][] Zl_k =  MatrixUtil.multiply(MatrixUtil.preMultiply(Pk_T,Zth_k), Pk);
    				  
    				  // Zl = Zl + sum{Zl_k}|all subsystems
    				   this.ZlAry = MatrixUtil.add(this.ZlAry,Zl_k);
    			  }
    		 
    		  	 
    	}
    	else{
    		throw new Error("The subNetIncidenceMatrixTable is not initialized yet, cannot procede the boundary subsystem matrix calculation!");
    	}
		
	}
	
	@Override
	public Hashtable<String, NetworkEquivalent> updateSubNetworkEquivSource() {
		for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			
    	    // the voltages at the boundary buses are the Thevenin voltages 
    		// the busIds are ordered in an ascending manner
    		 // the matrix and source parts are ordered in the same sequence as defined in BoundaryBusList.
    		   int i =0;
    		   for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
    			        
    			        // TODO the only difference from the positive sequence based implementation is that
    			        // three-sequence voltage is used here instead of positive-seq;
    			        Complex3x1 v = subNet.getBus(busId).get3SeqVoltage();
    			        subNetEquivTable.get(subNet.getId()).getSource3x1()[i]=v;
    			        i++;
    		   }
		   }
    	 return  subNetEquivTable;
	}


	@Override
	public void updateSubNetworkEquivMatrix() {
		this.subNetEquivTable = NetworkEquivUtil.calMultiNet3ph3SeqTheveninEquiv(this.subNetProcessor,threePhModelingSubNetIdList);
		
	}

	@Override
	public void updateSubNetworkEquivMatrix(String subNetworkId) {
		DStabilityNetwork subNet = this.subNetProcessor.getSubNetwork(subNetworkId);
    	if(subNet!=null){
	    	NetworkEquivalent equiv = null;
	    	if(this.threePhModelingSubNetIdList!= null && this.threePhModelingSubNetIdList.contains(subNetworkId))
	    		equiv = NetworkEquivUtil.cal3PhaseNetworkTheveninEquiv((DStabNetwork3Phase) subNet,
	    			       this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNetworkId));
	    	else
	    		equiv = NetworkEquivUtil.cal3SeqNetworkTheveninEquiv(subNet,
	    			       this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNetworkId));
	    	
	    	this.subNetEquivTable.put(subNetworkId, equiv);
    	}
    	else
    		throw new Error("No subnetwork is found with the input subNetwork Id!");
		
	}

	@Override
	public Hashtable<String, NetworkEquivalent> solvSubNetAndUpdateEquivSource() {
		if(subNetEquivTable ==null)
   	     throw new Error (" The subnetwork equivalent hastable is not prepared yet");
   	
	   	for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
			
		   	    // In order to obtain the Thevenin voltage source viewed at the boundary 
		   	    // there should  be no current injection from the interface tie-lines
		   		//if(subNet.initDStabNet())
		   	     
		   		// make sure there is no current injection at the boundary
		   		 subNet.setCustomBusCurrInjHashtable(null);
		   		 
		   	    // perform network solution to get the bus voltages
		   	     
		   		 if(this.threePhModelingSubNetIdList!=null && this.threePhModelingSubNetIdList.contains(subNet.getId())){
			   			DStabNetwork3Phase subNet3Ph = (DStabNetwork3Phase)subNet;
			   			subNet3Ph.solveNetEqn();
			   			
			   			// save the result
			   			NetworkEquivalent equiv = subNetEquivTable.get(subNet.getId());
			   			int i =0;
				   		for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
				   			        
				   			     // save the boundary bus voltages in  three-seq, to avoid unnecessary coordinate transformation
				   			     // as the Zth has already be transformed to three-seq, as the boundary subnetwork solution
				   		         // is based on three-seq modeling. 
				   			        Complex3x1 v = subNet3Ph.getBus(busId).get3SeqVoltage();
				   			        equiv.getSource3x1()[i]=v;
				   			        i++;
				   		 }
				   		
		   			
		   		 }
		   		 else{
		   			 
		   			    // solve seq networks assuming only positive-seq network has internal current injections
		   			    //
		   			    DStabNetwork3Phase subNet3Ph = (DStabNetwork3Phase)subNet;
		   			    
		   			    subNet3Ph.solvePosSeqNetEqn();
		   			    
				   		   int i =0;
				   		   for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
				   			        Complex v = subNet.getBus(busId).getVoltage();
				   			        //TODO here assuming the neg/zero sequence votlages are zero, as there is no fault within the subnetwork
				   			        subNetEquivTable.get(subNet.getId()).getSource3x1()[i].b_1=v;
				   			        i++;
				   		  }
		   			 
//				   		 subNet.solveNetEqn();
//				   		 
//				   	     // the voltages at the boundary buses are the Thevenin voltages 
//				   		 // the busIds are ordered in an ascending manner
//				   		 // the matrix and source parts are ordered in the same sequence as defined in BoundaryBusList.
//				   		   int i =0;
//				   		   for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
//				   			        Complex v = subNet.getBus(busId).getVoltage();
//				   			        subNetEquivTable.get(subNet.getId()).getSource()[i]=v;
//				   			        i++;
//				   		  }
		   		 }
		 }
	   	 return  subNetEquivTable;
	}

	@Override
	public boolean solveBoundarySubSystem() {
		boolean flag =true;
    	// as the matrix is only updated when there is a network change, they can be prepared and updated only when necessary 
    	// Note: preparation and update of Zl is done outside this method
    	
    	this.subNet3SeqCurrInjTable = new Hashtable<>();
    	
    	if(this.ZlAry!=null){
    		int dim = this.ZlAry.length;
    		// use the latest Thevenin equivalent voltage sources
    		Complex3x1[] Eth = MatrixUtil.createComplex3x1DArray(dim);
    	
    		for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    			
    			Complex3x1[] Eth_k = this.subNetEquivTable.get(subNet.getId()).getSource3x1();
    			int[][] Pk_T = this.subNetIncidenceAryTable.get(subNet.getId());
    			Eth_k = MatrixUtil.preMultiply(Pk_T,Eth_k);//Returns the result of multiplying this matrix by the vector v.
    			Eth = MatrixUtil.add(Eth,Eth_k);
    			
    			
    		}
    		
    		//create SparseMatrix3x3 with the ZlAry;
    		
    		 ISparseEqnComplexMatrix3x3 ZlMatrix = new SparseEqnComplexMatrix3x3Impl(dim);
    		 
    		 for(int i=0; i<dim;i++){
    			 for(int j=0;j<dim;j++){
    				 if(this.ZlAry[i][j].abs()>0)
    				    ZlMatrix.setA(this.ZlAry[i][j], i, j);
    			 }
    		 }
    		 
    		 // set the B vector
    		 for(int i=0; i<dim;i++){
    		    ZlMatrix.setBi(Eth[i], i);
    		 }
    		
    		 try {
				ZlMatrix.luMatrixAndSolveEqn(1.0E-9);
			} catch (IpssNumericException e) {
				
				e.printStackTrace();
			}
    		 
    		 // retrieve the results
    		 Complex3x1[] currVector = MatrixUtil.createComplex3x1DArray(dim);
    		 for(int i=0; i<dim;i++){
    		         currVector[i] = ZlMatrix.getX(i);
    		 }
    		 
//    		FieldLUDecomposition<Complex> lu = new FieldLUDecomposition<>(this.Zl);
//    		FieldVector<Complex> currVector = lu.getSolver().solve(Eth);
    		
    		for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
    			// mapping the branch current into the subnet current injection table
//    			FieldMatrix<Complex> Pk_T = this.subNetIncidenceMatrixTable.get(subNet.getId());
//    			FieldVector<Complex>  boundaryBusCurrInj = Pk_T.transpose().operate(currVector);
    			
    			int[][] Pk_T = this.subNetIncidenceAryTable.get(subNet.getId());
    			int[][] Pk = MatrixUtil.transpose(Pk_T);
    			 Complex3x1[] boundaryBusCurrInj = MatrixUtil.preMultiply(Pk,currVector);
    			
    			Hashtable<String,Complex3x1> busCurrInjTable = new Hashtable<>();
    			int i =0;
    			for(String busId: this.subNetProcessor.getSubNet2BoundaryBusListTable().get(subNet.getId())){
    				
    				//Please note the direction of current, as fromBus -> toBus is defined as the positive for the interface tie-lie current
    			    busCurrInjTable.put(busId, boundaryBusCurrInj[i].multiply(-1.0));
    			    i++;
    			}
    			
    			this.subNet3SeqCurrInjTable.put(subNet.getId(),  busCurrInjTable);
    		}
    		
    		
    		
    	}
    	else{
    		 IpssLogger.getLogger().severe("The boundary sub system [Zl] matrix is null");
    		 flag = false;
    	}
    	return flag;
	}

	@Override
	public boolean solveSubNetWithBoundaryCurrInjection() {
		  
		  // need to first reset all customized current injection to be zero
//		   for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
//			   subNet.setCustomBusCurrInjHashtable(null);
//			   
//			   ISparseEqnComplex subNetY= subNet.getYMatrix();
//			   
//			   subNetY.setB2Zero();
//			   
//			   for(Entry<String,Complex> e: this.subNetCurrInjTable.get(subNet.getId()).entrySet()){
//				   subNetY.setBi(e.getValue(),subNet.getBus(e.getKey()).getSortNumber());
//			   }
//			   try {
//				   // solve network to obtain Vext_injection
//				    subNetY.solveEqn();
//				} catch (IpssNumericException e1) {
//					
//					e1.printStackTrace();
//					return false;
//				}
//			   
//			   for(DStabBus b:subNet.getBusList()){
//				   //superpostition method
//				   //bus voltage V = Vinternal + Vext_injection
//				   b.setVoltage(b.getVoltage().add(subNetY.getX(b.getSortNumber())));
//			   }
			   
			   
			   // need to separately process the three-seq subnetwork and three-phase subnetwork
				for(DStabilityNetwork subNet: this.subNetProcessor.getSubNetworkList()){
				
			   	     
			   		// make sure there is no current injection at the boundary
			   		 subNet.setCustomBusCurrInjHashtable(null);
			   		 
			   	    // perform network solution to get the bus voltages
			   	     
			   		 if(this.threePhModelingSubNetIdList!=null && this.threePhModelingSubNetIdList.contains(subNet.getId())){
			   			 
			   		   // for the three-phase modeling subnetworks, they are directly solved in a way similar to the positive-seq base approach
			   			 
				   			DStabNetwork3Phase subNet3Ph = (DStabNetwork3Phase)subNet;
				   			
				   			ISparseEqnComplexMatrix3x3  subNetYabc = subNet3Ph.getYMatrixABC();
				   			subNetYabc.setB2Zero();
				   			for(Entry<String,Complex3x1> e: this.subNet3SeqCurrInjTable.get(subNet.getId()).entrySet()){
				   				   Complex3x1 IinjAbc = Complex3x1.z12_to_abc(e.getValue());
				   				   subNetYabc.setBi(IinjAbc,subNet.getBus(e.getKey()).getSortNumber());
							   }
				   			
				   			try {
								subNetYabc.solveEqn();
							} catch (IpssNumericException e1) {
							
								e1.printStackTrace();
								return false;
							}
				   			
				   			// save the result
				   			
				   			for(DStabBus b:subNet.getBusList()){
				   				
				   				   Bus3Phase bus = (Bus3Phase) b;
				   				   
								   //superpostition method
								   //bus voltage V = Vinternal + Vext_injection
								   bus.set3PhaseVoltages(bus.get3PhaseVotlages().add(subNetYabc.getX(b.getSortNumber())));
							 }
			   			
			   		 }
			   		 else{
			   			 
			   		     //TODO for the three-sequence subnetworks
			   			 
			   			 Hashtable<String, Complex> posCurTable = getSeqCurInjTable(this.subNet3SeqCurrInjTable.get(subNet.getId()), SequenceCode.POSITIVE);
			   	    	 Hashtable<String, Complex> negCurTable = getSeqCurInjTable(this.subNet3SeqCurrInjTable.get(subNet.getId()), SequenceCode.NEGATIVE);
			   	    	 Hashtable<String, Complex> zeroCurTable = getSeqCurInjTable(this.subNet3SeqCurrInjTable.get(subNet.getId()), SequenceCode.ZERO);
			   		
			   	    	 
			   	    	Hashtable<String,Complex> posVoltTable = this.solveSeqNetwork(SequenceCode.POSITIVE, posCurTable); 
			   	    	 for(DStabBus b:this.net.getBusList()){
			  			   //superpostition method
			  			   //bus voltage V = Vinternal + Vext_injection
			  			   b.setVoltage(b.getVoltage().add(posVoltTable.get(b.getId())));
			  			   
			  			  // also save the result to the bus threeSeqBusVolt
			  			   Complex3x1 v120 = new Complex3x1( new Complex(0,0),b.getVoltage(),new Complex(0,0));
			  			   b.set3SeqVoltage(v120);
			  		   }
			      	 
			  	
			          // negative sequence
			   	       if(this.getMaxCurMag(negCurTable)>this.negZeroSeqCurrTolerance){
				  		   Hashtable<String,Complex> negVoltTable = this.solveSeqNetwork(SequenceCode.NEGATIVE, negCurTable);
				  		   
				  		   for(Entry<String,Complex> e: negVoltTable.entrySet()){
				  			   this.net.getBus(e.getKey()).get3SeqVoltage().c_2 =e.getValue();
				  		   }
			   	       }
			      	
			      	// zero sequence
			  		   
			   	    if(this.getMaxCurMag(zeroCurTable)>this.negZeroSeqCurrTolerance){
			            Hashtable<String,Complex> zeroVoltTable = this.solveSeqNetwork(SequenceCode.ZERO, zeroCurTable);
			  		   
			  		    for(Entry<String,Complex> e: zeroVoltTable.entrySet()){
			  			   this.net.getBus(e.getKey()).get3SeqVoltage().a_0 =e.getValue();
			  		    }
			   		} // end of zeroImax > tol
			   	}// end of else
			   		 
			 }  // end of for subnetwork loop
			   
		   return true;
	}
	
	
	private Hashtable<String, Complex> solveSeqNetwork(SequenceCode seq,Hashtable<String, Complex> seqCurInjTable){
		
		 Hashtable<String, Complex>  busVoltResults = new  Hashtable<>();
		// solve the Ymatrix
		switch (seq){
		
	  	// Positive sequence
		case POSITIVE:
		    this.net.setCustomBusCurrInjHashtable(null);
		   
		    ISparseEqnComplex subNetY= this.net.getYMatrix();
		   
		    subNetY.setB2Zero();
		    
		       for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				   subNetY.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				    subNetY.solveEqn();
				   
				   
				} catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return null;
				}
			   for(DStabBus bus:this.net.getBusList()){
			    	  busVoltResults.put(bus.getId(), subNetY.getX(bus.getSortNumber()));
			   }
			   
		    
		    //TODO extract the current and map them to the buses
		    
		    break;
		case NEGATIVE:
			   ISparseEqnComplex negSeqYMatrix = this.net.getNegSeqYMatrix();
			
			   negSeqYMatrix.setB2Zero();
			   
			   for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				 negSeqYMatrix.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				   negSeqYMatrix.solveEqn();
				
			   } catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return null;
			   }
			   
			   for(DStabBus bus:this.net.getBusList()){
			    	  busVoltResults.put(bus.getId(), negSeqYMatrix.getX(bus.getSortNumber()));
			   }
			
			
			break;
		case ZERO:
			   ISparseEqnComplex zeroSeqYMatrix = this.net.getNegSeqYMatrix();
				
			   zeroSeqYMatrix.setB2Zero();
			   
			   for(Entry<String,Complex> e: seqCurInjTable.entrySet()){
				   zeroSeqYMatrix.setBi(e.getValue(),this.net.getBus(e.getKey()).getSortNumber());
			   }
			   try {
				   // solve network to obtain Vext_injection
				   zeroSeqYMatrix.solveEqn();
				
			   } catch (IpssNumericException e1) {
					
					e1.printStackTrace();
					return null;
			   }
			   
			   for(DStabBus bus:this.net.getBusList()){
			    	  busVoltResults.put(bus.getId(), zeroSeqYMatrix.getX(bus.getSortNumber()));
			   }
			
			break;
	    }
		
		// save the seq bus voltage result;
		
		return busVoltResults;
	}
	
	private Hashtable<String, Complex> getSeqCurInjTable(Hashtable<String, Complex3x1> curInjTable, SequenceCode seq){
		
		Hashtable<String, Complex> seqCurInjTable = new Hashtable<>();
		  if(curInjTable !=null){
			  switch(seq){
			  case POSITIVE:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().b_1);
				  }
				  break;
			  case NEGATIVE:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().c_2);
				  }
				  break;
			  case ZERO:
				  for(Entry<String, Complex3x1> nvpair :curInjTable.entrySet()){
					  seqCurInjTable.put(nvpair.getKey(), nvpair.getValue().a_0);
				  }
				  break;
			  
			  }
			  
			  return seqCurInjTable;
		  }
		  else 
			  return null;
	}
	
	private double getMaxCurMag(Hashtable<String, Complex> seqCurInjTable){
		double imax = 0;
		for(Complex i :seqCurInjTable.values()){
			  if(imax <i.abs())
				  imax = i.abs();
		  }
	    return imax;
	}


}
