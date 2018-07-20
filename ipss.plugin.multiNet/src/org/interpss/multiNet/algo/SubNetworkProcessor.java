package org.interpss.multiNet.algo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Hashtable;
import java.util.List;

import org.apache.commons.math3.complex.Complex;
import org.interpss.threePhase.dynamic.DStabNetwork3Phase;
import org.interpss.threePhase.util.ThreePhaseObjectFactory;

import com.interpss.DStabObjectFactory;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.net.Branch;
import com.interpss.core.net.BranchBusSide;
import com.interpss.core.net.Bus;
import com.interpss.core.net.childnet.ChildNetInterfaceBranch;
import com.interpss.core.net.childnet.ChildNetworkFactory;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
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
	private List<String> internalboundaryBusIdList =null;  // for hybrid simulation use only
	private List<ChildNetInterfaceBranch> cutSetList = null;
	private boolean subNetworkSearched = false;
	private Hashtable<String, Integer> busId2SubNetworkTable =  null;
	private Hashtable<String, List<String>> subNet2BoundaryBusListTable =  null;
	private int subNetIdx =0;
	
	private BaseAclfNetwork<?, ?> net = null;

	//TODO There is shortcoming in the existing childNet model, which did not allow the same bus 
	//to be co-existing in the childNet and parent network;
	private BaseDStabNetwork subNet =null;
	private List<BaseDStabNetwork> subNetworkList = null;
	private List<String> threePhaseSubNetIdList = null; // should be provided after subnetwork creation 

	public SubNetworkProcessor(BaseAclfNetwork<?,?> net ){
		this.net = (BaseAclfNetwork<? extends BaseAclfBus, ? extends AclfBranch>) net;
		this.defInterfaceBranchIdList = new ArrayList();
		this.boundaryBusIdList = new ArrayList();
		this.subNetworkList = new ArrayList();
		this.busId2SubNetworkTable = new Hashtable<>();
		this.subNet2BoundaryBusListTable = new Hashtable<>();
		
		cutSetList =new ArrayList<>();
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
    	
    	if( !(this.net instanceof BaseDStabNetwork))
    		throw new Error ("This method is only applicable to DStab network");
    		
    	// if bus splitting is used, need to add dummy buses and create the "internally used" artificial interface tie-lies
    	if(busSplitting){
    	  // initialized
    	  this.internalInterfaceBranchIdList  = new ArrayList<>();
    	  
		   try {
				addDummyBusToFullNet();
			} catch (InterpssException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
    	 }
         
    	// it calls  createSubNetworks() to split the original full network into at least two subsystems.
    	
		/*
		 * To consider both the bus splitting approach and the normal tie-line splitting approach in the same 
		 * splitting subnetwork function,internalInterfaceBranchIdList is always considered first.However,
		 * it is null by default, and only when no "internal used" interface branch is defined, will the input 
		 * definition interface branches be used.
		 */

		if(this.internalInterfaceBranchIdList == null || this.internalInterfaceBranchIdList.size()==0)
			this.internalInterfaceBranchIdList = defInterfaceBranchIdList;
		
         boolean flag = createSubNetworks(this.net,this.internalInterfaceBranchIdList,this.boundaryBusIdList);
    	 
    	 
    	 return flag;
    }
    
    
    public boolean splitSubsystem(String subsystemId, String[] tieLineIdAry, boolean[] boundarySideAry, boolean boundaryBusSplitting){
    	
    	boolean flag = true;
    	
    	
    	// 1. re-initialize the tie-line and cutset list
    	
    	List<String> subNetDefInterfaceBranchIdList = new ArrayList<>();
    	List<ChildNetInterfaceBranch> subNetCutSetList = new ArrayList<>();
    	
    	BaseDStabNetwork<?,?> subNet = this.getSubNetwork(subsystemId);
    	
    	if(subNet==null){
    		throw new Error("The input subsystem Id is not valid");
    		
    	}
    	
    	// 2. process tie line and boundary information
    
    		
		int k = 0;
		
		if(subNet.getBranch(tieLineIdAry[k])!=null){
			
			// 2.1 check whether the boundary side information is available
			if( boundarySideAry != null){
    			ChildNetInterfaceBranch intBranch = ChildNetworkFactory.eINSTANCE.createChildNetInterfaceBranch();
    			intBranch.setBranchId(tieLineIdAry[k]);
    			intBranch.setChildNetSide(boundarySideAry[k]? BranchBusSide.FROM_SIDE: BranchBusSide.TO_SIDE);
    			
    			
    			subNetCutSetList.add(intBranch);
			}
			
		   	
	    	// 2.2 add tieLine to InterfaceBranchIdList and/or cutsetList depending on whether the boundary side info is available
	    	
			subNetDefInterfaceBranchIdList.add(tieLineIdAry[k]);
			
			k++;
		}
		else{
			IpssLogger.getLogger().severe("The branchId is invalid # " + tieLineIdAry[k]);
		}
		
    	
    	// 3. call creatSubNetworks(), it should be modified to refer to the target system to be split
    	
    	
    	
		return flag;
    	
    	
    	
    }
    
    
    /**
	  *  further split the transmission system into at least two subsystems, and the subsystem where the fault is applied
	  *  is modeled in three-phase. While others are still modeled in three-sequence detail.
	  *   
	  * @param unbalanceFaultBusId
	  * @return
	  */
	 public boolean splitTransmissionNetwork(String unbalanceFaultBusId){
		 boolean flag = true;

		 BaseDStabNetwork<?,?> transNet = this.getExternalSubNetwork();
		 
		 if(transNet==null)
			 return flag = false;
			 
		 
		 boolean validBusId = false;
		 boolean isFaultBusLoadOrGen = false;

		 BaseDStabBus<?,?> faultBus = transNet.getBus(unbalanceFaultBusId);

		 // 1. check whether the bus of <unbalanceFaultBusId> is belonging to the transmission network
		 
		 if(faultBus ==null){
			 return flag = false;
		 }
		 else if(!faultBus.isActive()){
			 IpssLogger.getLogger().severe("The input fault bus is off-line");
			 return flag = false;
		 }
		 else{
			 
			 if(faultBus.isLoad() || faultBus.isGen()){
				 isFaultBusLoadOrGen = true;
			 }
		 
			 // 2. get the connected branches and boundary buses
			 //TODO : should consider the local connected subtransmission and distribution systems of  <unbalanceFaultBus>
			 
			 List<Branch> connectedBranchList = faultBus.getBranchList();
			 
			 String[] connectedBranchIdAry = new String[connectedBranchList.size()];
			 
			 boolean[] boundarySideAry = new boolean[connectedBranchList.size()];
			 
			 int k = 0;
			 for(Branch bra:connectedBranchList){
				 if(bra.isActive()){
					 connectedBranchIdAry[k] = bra.getId();
					
					 boolean boundaryAtFromSide = bra.getFromBus().getId().equals(unbalanceFaultBusId)? false:true;
					 
					 // if the faulted bus itself has load(s) or Generator(s) connected to it, it can be 
					 // modeled as a single subsystem, without violating the MATE based dynamic simulation requirement (calculating Zth based on Yii)
                     // which means the boundary does NOT need to be extended to the neighboring buses.
					 if(isFaultBusLoadOrGen ){
                    	 boundaryAtFromSide = !boundaryAtFromSide;
					 }
                     
                     boundarySideAry[k++] =   boundaryAtFromSide; 
				 }
			 }
			 
			 
			 
			 // 3. call splitSubsystem(String subsystemId, String[] tieLineIdAry, boolean[] boundarySideAry, boolean boundaryBusSplitting)
			 //    to split the transmission system into subsystems
			 
			 
			 flag = splitSubsystem(transNet.getId(), connectedBranchIdAry, boundarySideAry, true);
			 
			 
			 // 4. set the subsystem associated with the bus of <unbalanceFaultBusId> to be modeled in three-phase 
			 
			 threePhaseSubNetIdList.add(this.getSubNetworkByBusId(unbalanceFaultBusId).getId());
		 
		 }
		 
		 return flag;
		 
	 
	 }
    
    
    
	
    /**
     * This process relies on inputing the boundary tie-line data with the information of internal/detailed subnetwork side
     * @throws InterpssException
     */
    private void addDummyBusToFullNet() throws InterpssException{
    	
    	
    		externalboundaryBusIdList = new ArrayList<>();
    		internalboundaryBusIdList = new ArrayList<>();
        
    	// obtain the boundary buses
        if(cutSetList.size()>0){
			
			//get the boundary bus list
			for(ChildNetInterfaceBranch interfaceBra: cutSetList){
				boolean isFromSide = interfaceBra.getChildNetSide()==BranchBusSide.FROM_SIDE;
				AclfBranch bra = this.net.getBranch(interfaceBra.getBranchId());

				BaseAclfBus boundaryBus = isFromSide? bra.getFromAclfBus():bra.getToAclfBus();

					if(!internalboundaryBusIdList.contains(boundaryBus.getId()))
						internalboundaryBusIdList.add(boundaryBus.getId());
						
			         // dummy bus id
					String dummyBusId =boundaryBus.getId()+"Dummy";
					
					BaseDStabBus<?,?> dummyBus = (DStabBus) this.net.getBus(dummyBusId);

					if( dummyBus ==null){
						
						if(this.net instanceof DStabNetwork3Phase)
							dummyBus = ThreePhaseObjectFactory.create3PDStabBus(dummyBusId, (DStabNetwork3Phase) this.net);
							
					    else if(this.net instanceof BaseDStabNetwork)
						    dummyBus = DStabObjectFactory.createDStabBus(dummyBusId, (BaseDStabNetwork<?,?>) this.net);
						
						
					    //basic info copy
						dummyBus.setBaseVoltage(boundaryBus.getBaseVoltage());
						dummyBus.setVoltage(boundaryBus.getVoltage());
					    
						
					    // add the zero-impedance line to connect the dummy bus to the original bus
					    DStabBranch dummyBranch = this.net instanceof DStabNetwork3Phase? ThreePhaseObjectFactory.create3PBranch(dummyBusId, boundaryBus.getId(), "0",(DStabNetwork3Phase) net):
					    		DStabObjectFactory.createDStabBranch(dummyBusId, boundaryBus.getId(), (DStabilityNetwork) net);
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

	private boolean createSubNetworks(BaseAclfNetwork<?, ?>  _net,

			List<String> _internalInterfaceBranchIdList, List<String> _boundaryBusIdList){
		


		if(_internalInterfaceBranchIdList.size()>0){
			
			
			/*
			 * Step-1: processed the interface branches and the boundary buses
			 * 
			 */
			
			//get the boundary bus list
			for(String branchId: _internalInterfaceBranchIdList){
				Branch bra = _net.getBranch(branchId);
				
				//TODO avoid a bus belonging to one subNetwork still connects to one in the other subNetwork
				//bra.setStatus(false);
				
				if(!_boundaryBusIdList.contains(bra.getFromBus().getId())){
				    _boundaryBusIdList.add(bra.getFromBus().getId());
				    
				}
				
				if(!_boundaryBusIdList.contains(bra.getToBus().getId())){
				    _boundaryBusIdList.add(bra.getToBus().getId());
				    
				}
				
				//purposely set the interface branches to be out
				
			}
			
			Collections.sort(_boundaryBusIdList);
			
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
			for( String busId:_boundaryBusIdList){
				
				
			    BaseAclfBus source = _net.getBus(busId);
				if(!source.isBooleanFlag()){
					
					// for each iteration back to this layer, it means one subnetwork search is finished; subsequently, it is going to start
					// searching a new subnetwork. Thus, a new subnetwork object needs to be created first.
					
					if(_net instanceof DStabNetwork3Phase)
						this.subNet = (BaseDStabNetwork) ThreePhaseObjectFactory.create3PhaseDStabNetwork();
					else if(_net instanceof DStabilityNetwork)
					     this.subNet = (BaseDStabNetwork) DStabObjectFactory.createDStabilityNetwork(); //(ChildNetwork<DStabBus, DStabBranch>) CoreObjectFactory.createChildNet(net, "childNet-"+(subNetIdx+1));
					else
						throw new UnsupportedOperationException("The network should be either  DStabNetwork3Phase or DStabilityNetwork type!");
					this.subNet.setId("SubNet-"+(subNetIdx+1));
					
					subNetworkList.add((BaseDStabNetwork<?, ?>) subNet);
					
					try {

						this.subNet.addBus((BaseDStabBus<?,?>) source);
						// save the busId 2 subNetwork index mapping
						this.busId2SubNetworkTable.put(busId, subNetIdx);
						
					} catch (InterpssException e) {
						e.printStackTrace();
					}
					
					
					DFS(_net, this.subNet,_internalInterfaceBranchIdList,busId);
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

	private boolean DFS(BaseAclfNetwork _net, BaseDStabNetwork _subNet,
			            List<String> _internalInterfaceBranchIdList,String busId) {
		boolean isToBus = true;
      
		Bus source = _net.getBus(busId);
		
		source.setBooleanFlag(true);
		
		
        //System.out.println("BusId, Name, kV: "+busId+","+source.getName()+","+source.getBaseVoltage()*0.001);
        
		for (Branch bra : source.getBranchList()) {

			if (!_internalInterfaceBranchIdList.contains(bra.getId()) && !bra.isGroundBranch() 
					&& bra instanceof AclfBranch) {
				isToBus = bra.getFromBus().getId().equals(busId);
				String nextBusId = isToBus ? bra.getToBus().getId() : bra.getFromBus().getId();
				
				if(_subNet.getBus(nextBusId)==null){
					
					try {

						int nextBusIdx = getBusIdx(_net,nextBusId);
						_subNet.addBus((BaseDStabBus<?,?>)_net.getBusList().remove(nextBusIdx));

						// save the busId 2 subNetwork index mapping
						this.busId2SubNetworkTable.put(nextBusId, subNetIdx);
						
					} catch (InterpssException e) {
						e.printStackTrace();
					}
				}

				if (!bra.isBooleanFlag() ) { // fromBusId-->buId
					
					try {

						_subNet.addBranch((DStabBranch)bra, bra.getFromBus().getId(), bra.getToBus().getId() , bra.getCircuitNumber());

					} catch (InterpssException e) {
	
						e.printStackTrace();
					}
					
					
					bra.setBooleanFlag(true);
					
				    //DFS searching
				    DFS(_net,_subNet,_internalInterfaceBranchIdList,nextBusId);
					
				}
			}
		}

		return true;
	}
	

	private int getBusIdx(BaseAclfNetwork<?, ?> _net, String busId){

		int idx = -1;
		for(int i = 0; i<_net.getBusList().size(); i++){
			if(_net.getBusList().get(i).getId().equals(busId)){
				idx = i;
			}
		}
		return idx;
	}

	public BaseAclfNetwork<?,?> getNet() {
		return net;
	}

	public void setNet(BaseDStabNetwork<?,?> net) {
		this.net = net;
	}

	/**
	 * return the internal or detailed susbsytem boundary busId array
	 * @return
	 */
	public String[] getBoundaryBusIdAry() {
		return boundaryBusIdAry = boundaryBusIdList.toArray(new String[]{"1"});
	}
	/**
	 * return the internal or detailed susbsytem boundary busId list
	 * @return
	 */
	public List<String> getBoundaryBusIdList() {
		return boundaryBusIdList;
	}
	
	public List<BaseDStabNetwork> getSubNetworkList(){

		return this.subNetworkList;
	}
	
	/**
	 * If no bus splitting is performed, then return the tie-line definition Id list.
	 * else, return the "virtual breakers" connecting the boundary bus and the dummy bus.
	 * 
	 * @return
	 */
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
	
	public BaseDStabNetwork<?,?> getSubNetwork(String subNetworkId){
		for( BaseDStabNetwork<?,?> subnet: this.subNetworkList){
			if(subnet.getId().equals(subNetworkId)){
				return subnet;
			}
		}
		return null;
	}
	
	

	public BaseDStabNetwork<?,?> getSubNetworkByBusId(String busId){
		Integer index = getBusId2SubNetworkTable().get(busId);
		if(index == null)
			try {
				throw new Exception (" the input busId is not valid");
			} catch (Exception e) {
				e.printStackTrace();
			}
		return this.subNetworkList.get(index);
	}
	
	public List<String>  getExternalSubNetBoundaryBusIdList(){
		return this.externalboundaryBusIdList;
	}
	
	public List<String>  getInternalSubNetBoundaryBusIdList(){
		return this.internalboundaryBusIdList;
	}
	
	public BaseDStabNetwork<?,?> getExternalSubNetwork(){
		   String busid = this.externalboundaryBusIdList.get(0);
		   int subNetIdx = busId2SubNetworkTable.get(busid);
		   return  (BaseDStabNetwork<?, ?>) this.subNetworkList.get(subNetIdx);
	}
	
	public BaseDStabNetwork<?,?> getInternalSubNetwork(){
		    String busid = "";
		   for(String id:this.boundaryBusIdList){
			   if(!externalboundaryBusIdList.contains(id)){
		              busid = id;
		              break;
			   }
		   }
		   int subNetIdx = busId2SubNetworkTable.get(busid);
		   return  this.subNetworkList.get(subNetIdx);
	}
	
	
	
	public void setInternalTieLineStatus(boolean status){
		for(String id:getInterfaceBranchIdList()){
			this.net.getBranch(id).setStatus(status);
		}
	}
	
	public List<String> getThreePhaseSubNetIdList(){
		return this.threePhaseSubNetIdList;
	}
	
	public void set3PhaseSubNetByBusId(String threePhaseBusId){
		if(this.busId2SubNetworkTable.containsKey(threePhaseBusId)){
			
			int idx = this.busId2SubNetworkTable.get(threePhaseBusId);
			
			if(this.threePhaseSubNetIdList ==null)
			     this.threePhaseSubNetIdList = new ArrayList<>();
			
			this.threePhaseSubNetIdList.add(this.subNetworkList.get(idx).getId());
		}
		else
			IpssLogger.getLogger().severe("The input busId is not valid");
	}
}
