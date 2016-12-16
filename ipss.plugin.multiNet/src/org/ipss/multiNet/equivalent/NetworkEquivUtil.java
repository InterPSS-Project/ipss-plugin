package org.ipss.multiNet.equivalent;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.numeric.datatype.Complex3x1;
import org.interpss.numeric.datatype.Complex3x3;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.matrix.MatrixUtil;
import org.interpss.numeric.sparse.ISparseEqnComplex;
import org.interpss.numeric.sparse.ISparseEqnComplexMatrix3x3;
import org.ipss.multiNet.algo.SubNetworkProcessor;
import org.ipss.threePhase.dynamic.DStabNetwork3Phase;

import com.interpss.common.datatype.Constants;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.acsc.SequenceCode;
import com.interpss.core.net.NetCoordinate;
import com.interpss.core.net.NetEquivType;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.BaseDStabNetwork;

public class NetworkEquivUtil {
	
	
	public static Hashtable<String, NetworkEquivalent> calMultiNetPosSeqTheveninEquiv( SubNetworkProcessor subNetProc){
		   
		Hashtable<String,NetworkEquivalent> netEquivTable = new Hashtable<>();
		
		
		for(BaseDStabNetwork subNet:subNetProc.getSubNetworkList()){
			NetworkEquivalent equiv = calPosSeqNetworkTheveninEquiv(subNet,subNetProc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
			netEquivTable.put(subNet.getId(), equiv);
		}
		
		return netEquivTable;
		
	}
	
	
	public static Hashtable<String, NetworkEquivalent> calMultiNet3ph3SeqTheveninEquiv( SubNetworkProcessor subNetProc, List<String> threePhaseSubNetIdList ){
		   
		Hashtable<String,NetworkEquivalent> netEquivTable = new Hashtable<>();
		
		NetworkEquivalent equiv = null;
		for(BaseDStabNetwork subNet:subNetProc.getSubNetworkList()){
			if(threePhaseSubNetIdList!= null){
				if(threePhaseSubNetIdList.contains(subNet.getId())){
					if(subNet instanceof DStabNetwork3Phase){
					       equiv = cal3PhaseNetworkTheveninEquiv((DStabNetwork3Phase) subNet,subNetProc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
					       
					       //System.out.println(MatrixUtil.complex3x32DAry2String(equiv.getMatrix3x3()));
					       //this 3phase-to-3seq transformation is only performed on the Zth part
					       equiv = equiv.transformCoordinate(NetCoordinate.THREE_SEQUENCE);
					}
					else
						throw new Error(" The subnetwork for creating 3Phase Network Thevenin Equiv is not a DStabNetwork3Phase object");
				}
				else
					equiv = cal3SeqNetworkTheveninEquiv(subNet,subNetProc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
			}
			else{
				equiv = cal3SeqNetworkTheveninEquiv(subNet,subNetProc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
			}
			netEquivTable.put(subNet.getId(), equiv);
		}
		
		return netEquivTable;
		
	}
	
	
	public static  NetworkEquivalent calPosSeqNetworkTheveninEquiv(BaseDStabNetwork net, List<String> boundaryBusIdList){
		
		ISparseEqnComplex ymatrix = net.getYMatrix();
		if(ymatrix==null){
			ymatrix = net.formScYMatrix(SequenceCode.POSITIVE,false);
			net.setYMatrix(ymatrix);
			net.setYMatrixDirty(true);
		}
		if(net.isYMatrixDirty()){
			try {
				ymatrix.factorization(1.0E-10);
			} catch (IpssNumericException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// solve YV=I with only unit current injection at one boundary bus
		int dim = boundaryBusIdList.size();
		
		NetworkEquivalent netEquiv = null;
		
		if(dim>0){
			netEquiv = new NetworkEquivalent(dim);
			//ymatrix.setB2Zero();
			int i=0;
			for(String busId:boundaryBusIdList){
				DStabBus bus = net.getBus(busId);
				ymatrix.setB2Unity(bus.getSortNumber());
				try {
					ymatrix.solveEqn();
				} catch (IpssNumericException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				int j=0;
				for(String busId2:boundaryBusIdList){
				    Complex zji= ymatrix.getX(net.getBus(busId2).getSortNumber());
				    netEquiv.getComplexEqn().setAij(zji, j, i);  // zji = Vj/Ii
				    j++;
				     
				}
				i++;
			}
			
		}
		else
			throw new Error("no boundary bus defined in the input BusIdList");
		
		
		return netEquiv;
		
	}
	
	public static  NetworkEquivalent cal3SeqNetworkTheveninEquiv(BaseDStabNetwork net, List<String> boundaryBusIdList){
		
		// calculate three seq thevein equivalent impedance matrices
			Complex[][] posSeqZMatrix  = calcInterfaceSeqZMatrix( net,SequenceCode.POSITIVE,boundaryBusIdList);
			
			Complex[][] negSeqZMatrix  = calcInterfaceSeqZMatrix( net,SequenceCode.NEGATIVE,boundaryBusIdList);
			
			Complex[][] zeroSeqZMatrix  = calcInterfaceSeqZMatrix( net,SequenceCode.ZERO,boundaryBusIdList);
		  
		// form the 3-seq Thevenin Equiv impedance matrix as a block matrix, with zij = diag([zij(1) zij(2) zij(0)]) 
		
		 int dim =  boundaryBusIdList.size();
		 
		 
		 NetworkEquivalent netEquiv = null;
		
		 //TODO change to use Complex3x3 to store;
		 if(dim>0){
				netEquiv = new NetworkEquivalent(dim);
				netEquiv.setEquivCoordinate(NetCoordinate.THREE_SEQUENCE);
		 
				 Complex3x3[][] Z120Matrix = MatrixUtil.createComplex3x32DArray(dim,dim);
				    for(int i=0;i<dim;i++){
				    	for(int j=0;j<dim;j++){
				    		Z120Matrix[i][j] = new Complex3x3(posSeqZMatrix[i][j],negSeqZMatrix[i][j],zeroSeqZMatrix[i][j]);   
				    	}     
				  }
				 
				 
				 netEquiv.setMatrix3x3(Z120Matrix); 
		    
		}
		return netEquiv;
		
	}
	
	
public static  NetworkEquivalent cal3PhaseNetworkTheveninEquiv(DStabNetwork3Phase net, List<String> boundaryBusIdList){
		
	 ISparseEqnComplexMatrix3x3 ymatrix = net.getYMatrixABC();
		if(ymatrix==null){
			try {
				ymatrix = net.formYMatrixABC();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		//System.out.println(ymatrix.getSparseEqnComplex());
		
		if(net.isYMatrixDirty()){
			try {
				ymatrix.factorization(1.0E-10);
			} catch (IpssNumericException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		// solve YV=I with only unit current injection at one boundary bus
		int dim = boundaryBusIdList.size()*3;
		
		NetworkEquivalent netEquiv = null;
		
		if(dim>0){
			netEquiv = new NetworkEquivalent(dim);
			netEquiv.setEquivCoordinate(NetCoordinate.THREE_PHASE);
			netEquiv.setType(NetEquivType.THEVENIN);
			
			int i=0;
			for(String busId:boundaryBusIdList){
				DStabBus bus = net.getBus(busId);
				//System.out.println(bus.getId()+","+bus.getSortNumber());
				
				
				// consider all three phases, one time for each
				for(int phaseIdx = 0;phaseIdx<3;phaseIdx++){
						
						Complex3x1 bi =new Complex3x1(new Complex(1,0), new Complex(0,0),new Complex(0,0));
						if(phaseIdx==1)
							  bi =new Complex3x1(new Complex(0,0), new Complex(1,0),new Complex(0,0));
						
						else if(phaseIdx==2)
							  bi =new Complex3x1(new Complex(0,0), new Complex(0,0),new Complex(1,0));
						
						// reset the B vector to zero every time before setting new Bi
						ymatrix.setB2Zero();
						ymatrix.setBi(bi,  bus.getSortNumber());
						try {
							ymatrix.solveEqn();
						} catch (IpssNumericException e) {
							e.printStackTrace();
						}
						
						
						int j=0;
						
						//TODO this part can be changed to use the Complex3x3 as basic storage element.
						for(String busId2:boundaryBusIdList){
						    Complex3x1 zji= ymatrix.getX(net.getBus(busId2).getSortNumber());
						    netEquiv.getComplexEqn().setAij(zji.a_0, 3*j, 3*i+phaseIdx); //zji(a_phase)
						    netEquiv.getComplexEqn().setAij(zji.b_1, 3*j+1, 3*i+phaseIdx);//zji(b_phase)
						    netEquiv.getComplexEqn().setAij(zji.c_2, 3*j+2, 3*i+phaseIdx);//zji(c_phase)
						    j++;
						     
						}
				}
				i++;
			}
			
			// post-processing
			netEquiv.transMatrixTo3x3BlockMatrix();
		
		}
		else
			throw new Error("no boundary bus defined in the input BusIdList");
		
	
		return netEquiv;
		
	}
	
	/**
	 * calculate the sequence impedance matrix related to those boundary buses
	 * 
	 * @param boundaryBusIdAry
	 * @param code
	 * @return  a Complex[][] matrix corresponding to the order in the boundaryBusIdAry
	 * @throws IpssNumericException 
	 */
	public  static Complex[][] calcInterfaceSeqZMatrix(BaseDStabNetwork net,SequenceCode code,List<String> boundaryBusIdList){
		
		Complex[][] seqZMatrix = null;
		ISparseEqnComplex seqYmatrixEqn = null;
		
		if(net.isPositiveSeqDataOnly() && (code.equals(SequenceCode.NEGATIVE) ||code.equals(SequenceCode.ZERO)) ){
            throw new Error ("The network does not include negative or zero sequence");
		}
		else{
			int dim =boundaryBusIdList.size();
			seqZMatrix = new Complex[dim][dim]; 
			
			switch (code) {
			case ZERO:
				if(net.getZeroSeqYMatrix()==null)
				    seqYmatrixEqn = net.formScYMatrix(SequenceCode.ZERO,true);
				else
					seqYmatrixEqn = net.getZeroSeqYMatrix();
				break;
	        
			case NEGATIVE:	
				
				//setBoundaryGenLoadInactive();
				if(net.getNegSeqYMatrix()==null)
				     seqYmatrixEqn =net.formScYMatrix(SequenceCode.NEGATIVE,true);
				else
					 seqYmatrixEqn = net.getNegSeqYMatrix();
				break;
				
			default:
				     if(net.getYMatrix() == null)
					    seqYmatrixEqn = net.formScYMatrix(SequenceCode.POSITIVE,true);
				     else{
				    	 seqYmatrixEqn = net.getYMatrix();
				    	 
				    	 if(net.isYMatrixDirty()){
							try {
								seqYmatrixEqn.factorization(Constants.Matrix_LU_Tolerance);
							} catch (IpssNumericException e) {
								e.printStackTrace();
							}
				    	 }
				    	 
				     }
				    	 
			}
		
		
				for(int i = 0;i<dim;i++){
					String busId =boundaryBusIdList.get(i);
					BaseAclfBus bus = net.getBus(busId);
					int idx = bus.getSortNumber();
					seqYmatrixEqn.setB2Unity(idx); //unit current injection at bus of Idx only, the rest are zero
					try {
						 seqYmatrixEqn.solveEqn();
					} catch (IpssNumericException e) {
						
						e.printStackTrace();
					}
	
				
					for(int j=0;j<dim;j++){
						busId = boundaryBusIdList.get(j);
						BaseAclfBus busj = net.getBus(busId);
						seqZMatrix[j][i]=seqYmatrixEqn.getX(busj.getSortNumber());
					}
						
					
				}
		}
		return seqZMatrix; 
	}

	
	
}
