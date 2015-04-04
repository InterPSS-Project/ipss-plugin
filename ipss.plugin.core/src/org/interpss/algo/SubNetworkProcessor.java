package org.interpss.algo;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.Bus;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;

public class SubNetworkProcessor {
	
	
	
	/*
	 *  Main idea:
	 *  
	 *  (1) Splitting subnetwork based on i) custom configuration ii) system zone/area information
	 *  
	 *  (2) for custom configuration
	 *   1) use the tie-line to define the subnetwork boundary, no boundary side information is required
	 *   2) save the tie-line info for latter usage, and set them out of service
	 *   3) iteration all buses, for each portion confined by the tielines, create a corresponding subnetwork
	 *   4) subNetwork modification based on different application. A visitor pattern can be used here 
	 *  
	 *  
	 */
	
	
    private String[] boundaryBusIdAry = null;
	private List<String> interfaceBranchIdList = null;
	private List<String> boundaryBusIdList =null;
	private boolean subNetworkSearched = false;
	private Hashtable<String, Integer> busId2SubNetworkTable =  null;
	private int subNetIdx =0;
	
	private BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch> net = null;
	private ChildNetwork<DStabBus, DStabBranch> childNet =null;
	private List<ChildNetwork<? extends AclfBus, ? extends AclfBranch>> subNetworkList =null;
	


	public SubNetworkProcessor(BaseAclfNetwork<?,?> net ){
		this.net = net;

		this.interfaceBranchIdList = new ArrayList();
		this.boundaryBusIdList = new ArrayList();
		this.subNetworkList = new ArrayList();
		this.busId2SubNetworkTable = new Hashtable<>();
	}
	/**
	 * create an interface branch with information of which side is close to the sub-network
	 * @param branchId
	 * @param subNetFromBusSide
	 * @return
	 */
	public void  addSubNetInterfaceBranch(String branchId){
		
		if(this.net.getBranch(branchId)!=null){
			
			// add the branch id to the branch Id list
			this.interfaceBranchIdList.add(branchId);
		
		}
		else{
			IpssLogger.getLogger().severe("The branchId is invalid # " +branchId);
		}
		
	}
	
	/**
	 * search the sub network and determine the boundary buses, internal network buses and branches
	 * set the status of buses and branches in the master (full) network  which  are now within the internal network
	 * to be false/ out-of-service
	 * 
	 * @return
	 */
	public boolean searchSubNetwork(){
		
		//net.bookmark(true);

		if(interfaceBranchIdList.size()>0){
			
			
			/*
			 * Step-1: processed the interface branches and the boundary buses
			 * 
			 */
			
			//get the boundary bus list
			for(String branchId: interfaceBranchIdList){
				Branch bra = this.net.getBranch(branchId);
				
				//TODO avoid a bus belonging to one subNetwork still connects to one in the other subNetwork
				//bra.setStatus(false);
				
				if(!this.boundaryBusIdList.contains(bra.getFromBus().getId())){
				    this.boundaryBusIdList.add(bra.getFromBus().getId());
				    
				}
				
				if(!this.boundaryBusIdList.contains(bra.getToBus().getId())){
				    this.boundaryBusIdList.add(bra.getToBus().getId());
				    
				}
				
				//purposely set the interface branches to be out
				
			}
			
			/*
			 * Step-2: 
			 *
			 * starting from one interface (or cutset) branch to search all the internal network. The
			 * search is bounded by the interface branches
			 * 
			 */
			
	
			
			/*
			 * NOTE: 02/09/2015
			 * this implementation only works under the condition with all the detailed system are directly connected
			 * Need to iterate over all boundary buses 
			 */
			subNetIdx =0;
			for( String busId:this.boundaryBusIdList){
				
				
				Bus source = this.net.getBus(busId);
				if(!source.isVisited()){
					
					// for each iteration back to this layer, it means one subnetwork search is finished; subsequently, it is going to start
					// searching a new subnetwork.
					this.childNet = (ChildNetwork<DStabBus, DStabBranch>) CoreObjectFactory.createChildNet(net, "childNet-"+(subNetIdx+1));
					
					this.childNet.setNetwork(DStabObjectFactory.createDStabilityNetwork());
					subNetworkList.add(childNet);
					
					try {
						this.childNet.getNetwork().addBus((DStabBus) source);
						// save the busId 2 subNetwork index mapping
						this.busId2SubNetworkTable.put(busId, subNetIdx);
						
					} catch (InterpssException e) {
						e.printStackTrace();
					}
					
					
					DFS(busId);
					subNetIdx++;
				}
			}
			
		
		}
		return subNetworkSearched=true;
		
	}
	private boolean DFS(String busId) {
		boolean isToBus = true;
      
		Bus source = this.net.getBus(busId);
		
		source.setVisited(true);
		
		
        //System.out.println("BusId, Name, kV: "+busId+","+source.getName()+","+source.getBaseVoltage()*0.001);
        
		for (Branch bra : source.getBranchList()) {

			if (!this.interfaceBranchIdList.contains(bra.getId()) && !bra.isGroundBranch() 
					&& bra instanceof AclfBranch) {
				isToBus = bra.getFromBus().getId().equals(busId);
				String nextBusId = isToBus ? bra.getToBus().getId() : bra.getFromBus().getId();
				
				if(this.childNet.getNetwork().getBus(nextBusId)==null){
					
					try {
						this.childNet.getNetwork().addBus((DStabBus) this.net.getBus(nextBusId));
						// save the busId 2 subNetwork index mapping
						this.busId2SubNetworkTable.put(nextBusId, subNetIdx);
						
					} catch (InterpssException e) {
						e.printStackTrace();
					}
				}

				if (!bra.isVisited() ) { // fromBusId-->buId
					
					try {
						this.childNet.getNetwork().addBranch((DStabBranch) bra, bra.getFromBus().getId(), bra.getToBus().getId() , bra.getCircuitNumber());
					} catch (InterpssException e) {
	
						e.printStackTrace();
					}
					
					
					bra.setVisited(true);
					
				    //DFS searching
				    DFS(nextBusId);
					
				}
			}
		}

		return true;
	}
	

	public BaseAclfNetwork<?, ?> getNet() {
		return net;
	}

	public void setNet(BaseAclfNetwork<?, ?> net) {
		this.net = net;
	}

	public String[] getBoundaryBusIdAry() {
		return boundaryBusIdAry = boundaryBusIdList.toArray(new String[]{"1"});
	}


	public List<String> getBoundaryBusIdList() {
		return boundaryBusIdList;
	}
	
	public List<ChildNetwork<? extends AclfBus, ? extends AclfBranch>> getSubNetworkList(){
		return this.subNetworkList;
	}
	public List<String> getInterfaceBranchIdList(){
		return this.interfaceBranchIdList;
	}
	
	public void setInterfaceBranchIdList(List<String> interfaceBranchList){
		this.interfaceBranchIdList = interfaceBranchList;
	}
	
	public Hashtable<String, Integer> getBusId2SubNetworkTable() {
		return busId2SubNetworkTable;
	}

}
