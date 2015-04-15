package org.ipss.multiNet.equivalent;

import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.algo.SubNetworkProcessor;
import org.interpss.numeric.exp.IpssNumericException;
import org.interpss.numeric.sparse.ISparseEqnComplex;

import com.interpss.core.acsc.SequenceCode;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

public class NetworkEquivUtil {
	
	
	public static Hashtable<String, NetworkEquivalent> calMultiNetworkEquiv( SubNetworkProcessor subNetProc){
		   
		Hashtable<String,NetworkEquivalent> netEquivTable = new Hashtable<>();
		
		
		for(DStabilityNetwork subNet:subNetProc.getSubNetworkList()){
			NetworkEquivalent equiv = calNetworkEquiv(subNet,subNetProc.getSubNet2BoundaryBusListTable().get(subNet.getId()));
			netEquivTable.put(subNet.getId(), equiv);
		}
		
		return netEquivTable;
		
	}
	
	
	public static  NetworkEquivalent calNetworkEquiv(DStabilityNetwork net, List<String> boundaryBusIdList){
		
		ISparseEqnComplex ymatrix = net.getYMatrix();
		if(ymatrix==null){
			ymatrix = net.formYMatrix(SequenceCode.POSITIVE,false);
			net.setYMatrix(ymatrix);
			net.setYMatrixDirty(true);
		}
		if(net.isYMatrixDirty()){
			try {
				ymatrix.luMatrix(1.0E-10);
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
				    Complex zij= ymatrix.getX(net.getBus(busId2).getSortNumber());
				    netEquiv.getMatrix()[i][j]= zij;
				    j++;
				     
				}
				i++;
			}
			
		}
		else
			throw new Error("no boundary bus defined in the input BusIdList");
		
		
		return netEquiv;
		
	}

}
