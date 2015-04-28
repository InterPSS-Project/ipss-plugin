package org.interpss.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.Bus;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetwork;
import com.interpss.core.net.childnet.ChildNetworkFactory;
import com.interpss.dstab.DStabBranch;
import com.interpss.dstab.DStabBus;
import com.interpss.dstab.DStabilityNetwork;

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
	private List<String> defInterfaceBranchIdList = null;  // for storing tie-line definition in data input 
	private List<String> internalInterfaceBranchIdList = null; // for internally used
	private List<String> boundaryBusIdList =null;
	private List<String> externalboundaryBusIdList =null;  // for hybrid simulation use only
	private List<ChildNetInterfaceBranch> cutSetList = null;
	private boolean subNetworkSearched = false;
	private Hashtable<String, Integer> busId2SubNetworkTable =  null;
	private Hashtable<String, List<String>> subNet2BoundaryBusListTable =  null;
	private int subNetIdx =0;
	
	private BaseAclfNetwork<? extends AclfBus, ? extends AclfBranch> net = null;
	//private ChildNetwork<DStabBus, DStabBranch> childNet =null;
	//private List<ChildNetwork<? extends AclfBus, ? extends AclfBranch>> subNetworkList =null;
	
	//TODO There is shortcoming in the existing childNet model, which did not allow the same bus to be co-existing in the childNet and parent network
	private DStabilityNetwork subNet =null;
	private List<DStabilityNetwork> subNetworkList = null;

	public SubNetworkProcessor(BaseAclfNetwork<?,?> net ){
		this.net = net;
		this.defInterfaceBranchIdList = new ArrayList();
		this.boundaryBusIdList = new ArrayList();
		this.subNetworkList = new ArrayList();
		this.busId2SubNetworkTable = new Hashtable<>();
		this.subNet2BoundaryBusListTable = new Hashtable<>();
		
		cutSetList =new ArrayList();
	}
	/**
	 * add an interface branch to define the boundary of subnetworks 
	 * @param branchId
	 * @param subNetFromBusSide
	 * @return
	 */
	public void  addSubNetInterfaceBranch(String branchId){
		
		if(this.net.getBranch(branchId)!=null){
			
			// add the branch id to the branch Id list
			this.defInterfaceBranchIdList.add(branchId);
		
		}
		else{
			IpssLogger.getLogger().severe("The branchId is invalid # " +branchId);
		}
		
	}
	
	/**
	 * create an interface branch with information of which side is close to the child-subnetwork
	 * @param branchId
	 * @param subNetFromBusSide
	 * @return
	 */
    public void  addSubNetInterfaceBranch(String branchId, boolean childNetAtFromBusSide){
    	
    	
		
		if(this.net.getBranch(branchId)!=null){
			ChildNetInterfaceBranch intBranch = ChildNetworkFactory.eINSTANCE.createChildNetInterfaceBranch();
			intBranch.setBranchId(branchId);
			intBranch.setChildNetSide(childNetAtFromBusSide? BranchBusSide.FROM_SIDE: BranchBusSide.TO_SIDE);
			
			
			this.cutSetList.add(intBranch);
			// add the branch id to the branch Id list
			this.defInterfaceBranchIdList.add(branchId);
			
			
		}
		else{
			IpssLogger.getLogger().severe("The branchId is invalid # " +branchId);
		}
		
		
	}
    
    /**
     * If busSplitting is set to true, then a boundary  bus are split into the original and a dummy bus,
     * and both are connected by a newly created zero-impedance branch. Subsequently the full system is split by 
     * the cutset formed by these artificially created tie-lines.
     * 
     * 
     * Otherwise if busSplitting is set to false, the full system is split by the cutset formed by the original input tie-lines.
     * 
     * @param busSplitting
     * @return
     */
    public boolean splitFullSystemIntoSubsystems(boolean busSplitting){
    	
    	if( !(this.net instanceof DStabilityNetwork))
    		throw new Error ("This method is only applicable to DStab network");
    		
    	// if bus splitting is used, need to add dummy buses and create the "internally used" artificial interface tie-lies
    	if(busSplitting){
    	  // initialized
    	  this.internalInterfaceBranchIdList  = new ArrayList();
    	  
		   try {
				addDummyBusToFullNet();
			} catch (InterpssException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	 }
         
    	// it calls  createSubNetworks() to split the original full network into at least two subsystems.
         boolean flag = createSubNetworks();
    	 
    	 
    	 return flag;
    }
	
    
    private void addDummyBusToFullNet() throws InterpssException{
    	
    	
    		externalboundaryBusIdList = new ArrayList();
        
    	// obtain the boundary buses
        if(cutSetList.size()>0){
			
			//get the boundary bus list
			for(ChildNetInterfaceBranch interfaceBra: cutSetList){
				boolean isFromSide = interfaceBra.getChildNetSide()==BranchBusSide.FROM_SIDE;
				AclfBranch bra = this.net.getBranch(interfaceBra.getBranchId());
				AclfBus boundaryBus = isFromSide? bra.getFromAclfBus():bra.getToAclfBus();
						
						
			         // dummy bus id
					String dummyBusId =boundaryBus.getId()+"Dummy";
					
					DStabBus dummyBus = (DStabBus) this.net.getBus(dummyBusId);
					if( dummyBus ==null){
						
						dummyBus = DStabObjectFactory.createDStabBus(dummyBusId, (DStabilityNetwork) this.net);
					    //basic info copy
						dummyBus.setBaseVoltage(boundaryBus.getBaseVoltage());
						dummyBus.setVoltage(boundaryBus.getVoltage());
					    
						
					    // add the zero-impedance line to connect the dummy bus to the original bus
					    DStabBranch dummyBranch = DStabObjectFactory.createDStabBranch(dummyBusId, boundaryBus.getId(), (DStabilityNetwork) net);
					    dummyBranch.setZ(new Complex(0,1.0E-4));
					    
					    //set the branch status to false in order to isolate the subsystems 
					    //dummyBranch.setStatus(false);
					    
					    // add the new branch to the internal tie-line list
					    this.internalInterfaceBranchIdList.add(dummyBranch.getId());
					    
					    // the dummy bus is a boundary bus of the external system
					    externalboundaryBusIdList.add(dummyBusId);
					}
					
					
					
					// update the existing tie-line from/to buses
			        if(isFromSide){
			    	    bra.setFromBus(dummyBus);
			    	}else {
			    		bra.setToBus(dummyBus);
			    		
			    	}
			        // update the interface branch id after a dummy bus is added to replace its parent bus.
			        String newId =bra.getId().replaceAll(boundaryBus.getId(), dummyBusId);
		    		bra.setId(newId);
					    
					 
			     } // end of for
				    
			} // end of cutset size if 
		}
		
    	
    
	
	/**
	 * split the full network system into subsystems based on the interface branches and
	 * create one subNetwork model for each isolated subsystems.
	 * 
	 * DFS algorithm is used to search all the buses and branches within one subNetwork
	 * 
	 * @return
	 */
	private boolean createSubNetworks(){
		
		/*
		 * To consider both the bus splitting approach and the normal tie-line splitting approach in the same 
		 * splitting subnetwork function,internalInterfaceBranchIdList is always considered first.However,
		 * it is null by default, and only when no "internal used" interface branch is defined, will the input 
		 * definition interface branches be used.
		 */

		if(this.internalInterfaceBranchIdList == null || this.internalInterfaceBranchIdList.size()==0)
			this.internalInterfaceBranchIdList = defInterfaceBranchIdList;

		if(this.internalInterfaceBranchIdList.size()>0){
			
			
			/*
			 * Step-1: processed the interface branches and the boundary buses
			 * 
			 */
			
			//get the boundary bus list
			for(String branchId: this.internalInterfaceBranchIdList){
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
			
			Collections.sort(this.boundaryBusIdList);
			
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
					this.subNet = DStabObjectFactory.createDStabilityNetwork(); //(ChildNetwork<DStabBus, DStabBranch>) CoreObjectFactory.createChildNet(net, "childNet-"+(subNetIdx+1));
					this.subNet.setId("SubNet-"+(subNetIdx+1));
					
					subNetworkList.add(subNet);
					
					try {
						this.subNet.addBus((DStabBus) source);
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
		
		// build the subNetwork Id to boundary bus list mapping
		for(String busId:this.boundaryBusIdList){
			String networkId = subNetworkList.get(this.busId2SubNetworkTable.get(busId)).getId();
			if(this.subNet2BoundaryBusListTable.containsKey(networkId))
			   this.subNet2BoundaryBusListTable.get(networkId).add(busId);
			else{
				List<String> busList = new ArrayList<>();
				busList.add(busId);
				this.subNet2BoundaryBusListTable.put(networkId, busList);
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

			if (!this.internalInterfaceBranchIdList.contains(bra.getId()) && !bra.isGroundBranch() 
					&& bra instanceof AclfBranch) {
				isToBus = bra.getFromBus().getId().equals(busId);
				String nextBusId = isToBus ? bra.getToBus().getId() : bra.getFromBus().getId();
				
				if(this.subNet.getBus(nextBusId)==null){
					
					try {
						this.subNet.addBus((DStabBus) this.net.getBus(nextBusId));
						// save the busId 2 subNetwork index mapping
						this.busId2SubNetworkTable.put(nextBusId, subNetIdx);
						
					} catch (InterpssException e) {
						e.printStackTrace();
					}
				}

				if (!bra.isVisited() ) { // fromBusId-->buId
					
					try {
						this.subNet.addBranch((DStabBranch) bra, bra.getFromBus().getId(), bra.getToBus().getId() , bra.getCircuitNumber());
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
	
	public List<DStabilityNetwork> getSubNetworkList(){
		return this.subNetworkList;
	}
	public List<String> getInterfaceBranchIdList(){
		if(this.internalInterfaceBranchIdList !=null)
			return  this.internalInterfaceBranchIdList;
		return this.defInterfaceBranchIdList;
	}
	
	public void setInterfaceBranchIdList(List<String> interfaceBranchList){
		this.defInterfaceBranchIdList = interfaceBranchList;
	}
	
	public Hashtable<String, Integer> getBusId2SubNetworkTable() {
		return busId2SubNetworkTable;
	}
	
	public Hashtable<String, List<String>> getSubNet2BoundaryBusListTable(){
		return this.subNet2BoundaryBusListTable;
	}
	
	public DStabilityNetwork getSubNetwork(String subNetworkId){
		for( DStabilityNetwork subnet: this.subNetworkList){
			if(subnet.getId().equals(subNetworkId)){
				return subnet;
			}
		}
		return null;
	}
	
	public List<String>  getExternalSubNetBoundaryBusIdList(){
		return this.externalboundaryBusIdList;
	}
	
	public DStabilityNetwork getExternalSubNetwork(){
		   String busid = this.externalboundaryBusIdList.get(0);
		   int subNetIdx = busId2SubNetworkTable.get(busid);
		   return  this.subNetworkList.get(subNetIdx);
	}
	
	public void setInternalTieLineStatus(boolean status){
		for(String id:getInterfaceBranchIdList()){
			this.net.getBranch(id).setStatus(status);
		}
	}
}
