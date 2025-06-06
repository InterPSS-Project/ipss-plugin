package org.interpss.plugin.equiv;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.math3.complex.Complex;

import com.interpss.algo.subAreaNet.base.BaseCuttingBranch;
import com.interpss.common.exp.InterpssException;
import com.interpss.common.util.IpssLogger;
import com.interpss.core.CoreObjectFactory;
import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.AclfGen;
import com.interpss.core.aclf.AclfGenCode;
import com.interpss.core.aclf.AclfLoad;
import com.interpss.core.aclf.AclfLoadCode;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.BaseAclfBus;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.net.Branch;
import com.interpss.core.net.BranchBusSide;

public class AclfNetworkEquivHelper {

    private AclfNetwork baseNetwork = null;
    private Set<String> boundaryBusSet = null;
    private Set<BaseCuttingBranch<Complex>> boundaryBranchSet = null;
    private Set<String> keptBusSet = null;
    private Set<String> keptBranchSet = null;
    private AclfNetwork equivNet = null;

    public AclfNetworkEquivHelper(AclfNetwork network){
        this.baseNetwork = network;
        this.boundaryBusSet= new HashSet<>();
        this.boundaryBranchSet= new HashSet<>();
        this.keptBusSet = new HashSet<>();
        this.keptBranchSet = new HashSet<>();
    }

    /**
     * 
     * @param keptAreas
     * @return A list of buses to be kept
     */
    public void  defineKeptSubNetworkByAreas(List<String> keptAreas){
        //iterate through all the buses and use the keptAreas to determine which buses to keep
        for(BaseAclfBus<AclfGen, AclfLoad> bus: this.baseNetwork.getBusList()){
            if(bus.isActive() && keptAreas.contains(bus.getArea().getId())){
                this.keptBusSet.add(bus.getId());
            }
        }
        
        for(AclfBranch branch:this.baseNetwork.getBranchList()){
            if(branch.isActive()){
                if(keptAreas.contains(branch.getFromAclfBus().getArea().getId())){
                    if(!keptAreas.contains(branch.getToAclfBus().getArea().getId())){
                        this.boundaryBusSet.add(branch.getFromBusId());
                        this.boundaryBranchSet.add(new BaseCuttingBranch<Complex>(branch.getId(), 1, 0, BranchBusSide.FROM_SIDE));

                    }
                    else{//the branch are within the kept areas 
                        this.keptBranchSet.add(branch.getId());
                    }

                }
                else{
                    if(keptAreas.contains(branch.getToAclfBus().getArea().getId())){

                        this.boundaryBusSet.add(branch.getToBusId());
                        this.boundaryBranchSet.add(new BaseCuttingBranch<Complex>(branch.getId(), 0, 1, BranchBusSide.TO_SIDE));

                    }

                }
            }
        }

        //process specialBranchList like HVDC
        if(this.baseNetwork.getSpecialBranchList()!=null){
            for(Branch bra: this.baseNetwork.getSpecialBranchList()){
                if(bra != null && bra.isActive() && bra instanceof HvdcLine2T){
                    @SuppressWarnings("unchecked")
					HvdcLine2T<AclfBus> branch = (HvdcLine2T<AclfBus>) bra;
                    if(keptAreas.contains(branch.getFromBus().getArea().getId())){
                        if(!keptAreas.contains(branch.getToBus().getArea().getId())){
                                this.boundaryBusSet.add(branch.getFromBusId());
                                this.boundaryBranchSet.add(new BaseCuttingBranch<Complex>(branch.getId(), 1, 0, BranchBusSide.FROM_SIDE));

                        }
                        else{//the branch are within the kept areas
                            this.keptBranchSet.add(branch.getId());
                        }

                    }
                    else{
                        if(keptAreas.contains(branch.getToBus().getArea().getId())){

                            this.boundaryBusSet.add(branch.getToBusId());
                            this.boundaryBranchSet.add(new BaseCuttingBranch<Complex>(branch.getId(), 0, 1, BranchBusSide.TO_SIDE));

                        }

                    }
                    
                }
                    
            }
        }
    }
    

    //List<String>  defineKeptBuses(List<String> keptBuses);
    //defineBoundary(String insideBusId, List<BaseCuttingBranch> cutsets);

    /**
     * 
     * @return equivalent network object
     */
    public AclfNetwork createEquivNetwork(boolean copySubNet){
        try {
        //use subnetwork copy function to create a new network object
        if(copySubNet)
            equivNet = this.baseNetwork.createSubNet(this.keptBusSet, this.keptBranchSet, false /*equivHvdc*/);
        else{
            equivNet = this.baseNetwork;
        }
        
        // post-process the equivalent network to equivalent loads at the boundary buses based on the power flow of the boundary branches
        for(BaseCuttingBranch<Complex> cuttingBranch: this.boundaryBranchSet){
            AclfBranch branch = this.baseNetwork.getBranch(cuttingBranch.getBranchId());
            if(branch != null&& branch.isActive()){
                //check if the branch is a zero impedance branch, if so, this could lead to large power mismatch at the boundary bus
                if(branch.getZ().abs()< this.baseNetwork.getZeroZBranchThreshold()) {
                    IpssLogger.getLogger().severe("Branch " + branch.getId() + " is a zero impedance branch, which may lead to large power mismatch at the boundary bus. Consider selecting another boundary branch instead.");
                }

                if(cuttingBranch.getSplitSide() == BranchBusSide.FROM_SIDE){
                    
                    Complex s = branch.powerFrom2To();
                    AclfBus bus = equivNet.getBus(branch.getFromBusId());
                    if(bus != null && bus.isActive()){
                        // create a load object with the total power at the boundary bus
                        AclfLoad load = CoreObjectFactory.createAclfLoad(branch.getId() + "_" + branch.getFromBusId());
                        bus.getContributeLoadList().add(load);
                        load.setLoadCP(s);
                        load.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
                        bus.setLoadCode(AclfLoadCode.CONST_P);
                        bus.initContributeLoad(false);
                        // log the load creation and the power value
                        IpssLogger.getLogger().info("Boundary load created at bus " + branch.getFromBusId() + " with power: " + s.toString());
                
                    }
                    else{
                        // throw errors
                        IpssLogger.getLogger().severe("Boundary bus " + branch.getFromBusId() + " is not found or not active in the equivalent network.");
                        throw new IllegalStateException("Boundary bus " + branch.getFromBusId() + " is not found or not active in the equivalent network.");
                    }
                }
                else if(cuttingBranch.getSplitSide() == BranchBusSide.TO_SIDE){
                    Complex s = branch.powerTo2From();
                    AclfBus bus = equivNet.getBus(branch.getToBusId());
                    if(bus != null && bus.isActive()){
                        // create a load object with the total power at the boundary bus
                        AclfLoad load = CoreObjectFactory.createAclfLoad(branch.getId() + "_" + branch.getToBusId());
                        bus.getContributeLoadList().add(load);
                        load.setLoadCP(s);
                        load.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
                        bus.setLoadCode(AclfLoadCode.CONST_P);
                        bus.initContributeLoad(false);
                         // log the load creation and the power value
                        IpssLogger.getLogger().info("Boundary load created at bus " + branch.getToBusId() + " with power: " + s.toString());

                    }
                    else{
                        // throw errors
                        IpssLogger.getLogger().severe("Boundary bus " + branch.getToBusId() + " is not found or not active in the equivalent network.");
                        throw new IllegalStateException("Boundary bus " + branch.getToBusId() + " is not found or not active in the equivalent network.");
                    }
                }
            }

            //check if the branch is a special branch (HVDC), if so, we need to handle it differently
            if(this.baseNetwork.getSpecialBranchList()!=null){
                for(Branch bra: this.baseNetwork.getSpecialBranchList()){
                    if(bra != null && bra.isActive() && bra instanceof HvdcLine2T){
                        @SuppressWarnings("unchecked")
						HvdcLine2T<AclfBus> hvdcBranch = (HvdcLine2T<AclfBus>) bra;
                        if(hvdcBranch.getId().equals(cuttingBranch.getBranchId())){
                            // handle HVDC branch
                                if(cuttingBranch.getSplitSide() == BranchBusSide.FROM_SIDE){
                                    Complex s = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
                                    AclfBus bus = equivNet.getBus(hvdcBranch.getFromBusId());
                                    if(bus != null && bus.isActive()){
                                        // create a load object with the total power at the boundary bus
                                        AclfLoad load = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getFromBusId());
                                        bus.getContributeLoadList().add(load);
                                        load.setLoadCP(s);
                                        load.setCode(AclfLoadCode.CONST_P); // set load code to CONST_P
                                        bus.setLoadCode(AclfLoadCode.CONST_P);
                                    }
                                }
                                else if(cuttingBranch.getSplitSide() == BranchBusSide.TO_SIDE){
                                    Complex s = hvdcBranch.powerIntoConverter(hvdcBranch.getToBusId());
                                    AclfBus toBus = equivNet.getBus(hvdcBranch.getToBusId());
                                    AclfLoad load = CoreObjectFactory.createAclfLoad(hvdcBranch.getId() + "_" + hvdcBranch.getToBusId());
                                    toBus.getContributeLoadList().add(load);
                                    load.setLoadCP(s.negate()); // negative load for the converter
                                    toBus.setLoadCode(AclfLoadCode.CONST_P);
                                }
                            }
                        }
                    }
                }
            }
        
        // post-process the equivalent network to turn off any buses and branches that are not in the kept set
        if(!copySubNet){
            //iterate through all the buses and turn off buses not in the kept set keptBusSet
            for(AclfBus bus : equivNet.getBusList()) {
                if(!keptBusSet.contains(bus.getId())) {
                    bus.setStatus(false);
                    //IpssLogger.getLogger().info("Turning off bus: " + bus.getId());
                }
            }
            
            //iterate through all the branches and turn off branches not in the kept set keptBranchSet
            for(AclfBranch branch : equivNet.getBranchList()) {
                if(!keptBranchSet.contains(branch.getId())) {
                    branch.setStatus(false);
                   //IpssLogger.getLogger().info("Turning off branch: " + branch.getId());
                }
            }
            
            //process special branches like HVDC
            if(equivNet.getSpecialBranchList() != null) {
                for(Branch bra : equivNet.getSpecialBranchList()) {
                    if(bra != null && !keptBranchSet.contains(bra.getId())) {
                        bra.setStatus(false);
                        //IpssLogger.getLogger().info("Turning off special branch: " + bra.getId());
                    }
                }
            }
            //
        }

        // check if the swing bus is in the kept bus set, if not, set the bus with the largest generator as the swing bus
        AclfBus swingBus = null;
        for(AclfBus bus: equivNet.getBusList()){
            if(bus.isActive() && bus.isSwing()){
                swingBus = bus;
                break;
            }
        }
        if(swingBus == null){
            // find the bus with the largest generator
            double maxGen = Double.NEGATIVE_INFINITY;
            for(AclfBus bus: equivNet.getBusList()){
                if(bus.isActive() && bus.getContributeGenList().size() > 0){
                    double genP = bus.getContributeGenList().stream().filter(prdct -> prdct.isActive()).mapToDouble(gen -> gen.getPGenLimit().getMax()).sum();
                    if(genP > maxGen){
                        maxGen = genP;
                        swingBus = bus;
                    }
                }
            }
            if(swingBus != null){
                System.out.println("Setting bus " + swingBus.getId() + " as the swing bus in the equivalent network.");
                swingBus.setGenCode(AclfGenCode.SWING);
                swingBus.setDesiredVoltMag(swingBus.getVoltageMag());
                swingBus.setDesiredVoltAng(swingBus.getVoltageAng());
                swingBus.initContributeGen(false);
            }
        }   

        return equivNet;
    } catch (InterpssException e) {
        // TODO Auto-generated catch block
        e.printStackTrace();
    }
        return null;
    }

    public Set<BaseCuttingBranch<Complex>> getBoundaryBranches(){
        return this.boundaryBranchSet;
    }
    
    public Set<String> getBoundaryBuses(){
        return this.boundaryBusSet;
    }
}
