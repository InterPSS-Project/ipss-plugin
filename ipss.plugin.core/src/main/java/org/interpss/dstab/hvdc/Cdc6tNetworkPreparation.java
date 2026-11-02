package org.interpss.dstab.hvdc;

import com.interpss.core.aclf.hvdc.*;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import org.interpss.numeric.datatype.Unit.UnitType;

/** One-time preparation from a solved static link for explicit CDC6T attachment.
 * Supports ordinary single rectifier/inverter equipment with fixed dynamic taps.
 * The caller must establish source eligibility for features not retained by the
 * static importer (including capacitor commutation and constant-gamma selection).
 * This API does not enable automatic source import or mutate the solved network. */
public final class Cdc6tNetworkPreparation {
    private Cdc6tNetworkPreparation() {}

    /** Prepared objects belong exclusively to this link; attach the ports explicitly once. */
    public record Binding(HvdcLine2TLCC<?> owner,Cdc6tParameters.OperatingPoint operatingPoint,
            Cdc6tNormalRuntime runtime,Cdc6tProtectionCoordinator protection,Cdc6tNormalTerminalPorts ports) {}

    /** Resolve the exact source name, rejecting missing or ambiguous static owners.
     * The mode-transfer threshold is supplied explicitly because the static network
     * representation does not retain that source control setting. */
    public static Binding prepare(BaseDStabNetwork<?,?> network,Cdc6tParameters parameters,double modeThresholdKilovolts){
        java.util.Objects.requireNonNull(network);java.util.Objects.requireNonNull(parameters);
        if(!network.isLfConverged())throw new IllegalStateException("Solve load flow before preparing CDC6T");
        HvdcLine2TLCC<?> owner=null;
        for(var branch:network.getSpecialBranchList()){
            if(branch instanceof HvdcLine2TLCC<?> candidate&&parameters.linkName().equals(candidate.getName())){
                if(owner!=null)throw new IllegalArgumentException("Ambiguous static DC-link name: "+parameters.linkName());
                owner=candidate;
            }
        }
        if(owner==null)throw new IllegalArgumentException("No static DC-link owner named "+parameters.linkName());
        if(!owner.isActive()||!owner.isInitialized()||owner.getNetwork()!=network)
            throw new IllegalArgumentException("Active solved DC-link owner required");
        if(owner.getOperationMode()!=HvdcOperationMode.REC1_INV1||owner.getRectifier2()!=null||owner.getInverter2()!=null||owner.isPuBasedPowerFlowAlgo())
            throw new IllegalArgumentException("CDC6T preparation requires a single converter pair solved in physical units");
        if(owner.getRectifierControlMode()!=HvdcControlMode.DC_CURRENT||owner.getInverterControlMode()!=HvdcControlMode.DC_VOLTAGE)
            throw new IllegalArgumentException("Ordinary rectifier-current and inverter-voltage controls required");
        var rectifier=owner.getRectifier();var inverter=owner.getInverter();
        if(rectifier==null||inverter==null||rectifier.getConverterType()!=ConverterType.RECTIFIER||inverter.getConverterType()!=ConverterType.INVERTER)
            throw new IllegalArgumentException("Oriented rectifier/inverter equipment required");
        if(!(rectifier.getBus() instanceof BaseDStabBus<?,?> rb)||!(inverter.getBus() instanceof BaseDStabBus<?,?> ib)
                ||rb==ib||!rb.isActive()||!ib.isActive()||rb.getNetwork()!=network||ib.getNetwork()!=network
                ||owner.getFromBus()!=rb||owner.getToBus()!=ib)
            throw new IllegalArgumentException("Distinct active converter terminals in the owning dynamic network required");
        var mode=owner.getDcLineControlMode();double setpoint;
        Cdc6tCurrentOrder.Mode command;
        if(mode==HvdcControlMode.DC_CURRENT){command=Cdc6tCurrentOrder.Mode.CURRENT;setpoint=owner.getCurrentDemand();}
        else if(mode==HvdcControlMode.DC_POWER){
            command=Cdc6tCurrentOrder.Mode.POWER;
            if(owner.getControlSide()!=HvdcControlSide.RECTIFIER&&owner.getControlSide()!=HvdcControlSide.INVERTER)
                throw new IllegalArgumentException("Explicit power-control terminal required");
            setpoint=owner.getPowerDemand(UnitType.mW)*(owner.getControlSide()==HvdcControlSide.INVERTER?-1:1);
        }else throw new IllegalArgumentException("Power or current control required");
        double current=rectifier.getIdc(),vr=rectifier.getDcVoltage(),vi=inverter.getDcVoltage(),resistance=owner.getRdc(UnitType.Ohm);
        if(!Double.isFinite(current)||!Double.isFinite(vr)||!Double.isFinite(vi)||!Double.isFinite(resistance)||resistance<0
                ||Math.abs(current-inverter.getIdc())>1e-7*Math.max(1,Math.abs(current))
                ||!Double.isFinite(inverter.getIdc())||Math.abs(vr-vi-current*resistance)>1e-7*Math.max(1,Math.max(Math.abs(vr),Math.abs(vi))))
            throw new IllegalArgumentException("Consistent solved DC current and voltage drop required");
        var point=new Cdc6tParameters.OperatingPoint(command,setpoint,owner.getScheduledDCVoltage(UnitType.kV),
                owner.getCompondR(UnitType.Ohm),modeThresholdKilovolts,vi,current,vr);
        var prepared=parameters.prepare(boundary(rectifier,true,network.getBaseKva()*1000),
                boundary(inverter,false,network.getBaseKva()*1000),resistance,point);
        var ports=new Cdc6tNormalTerminalPorts(parameters.linkName(),rb,ib,prepared.runtime(),vi,current,vr,prepared.protection());
        return new Binding(owner,point,prepared.runtime(),prepared.protection(),ports);
    }
    private static Cdc6tConverterBoundary boundary(ThyConverter<?> converter,boolean rectifier,double baseVA){
        if(converter.getCommutingZ()==null||converter.getCommutingZ().getReal()!=0||converter.getCommutingCapacitor()!=0)
            throw new IllegalArgumentException("Converter resistance and capacitor compensation are not qualified");
        double rated=converter.getAcRatedVoltage();
        if(!Double.isFinite(rated)||rated<0)throw new IllegalArgumentException("Nonnegative converter primary voltage base required");
        double baseVolts=converter.getBus().getBaseVoltage()*(rated>0?rated:1);
        return new Cdc6tConverterBoundary(rectifier,converter.getNBridges(),baseVolts,converter.getXformerRatio(),
                converter.getXformerTapSetting(),converter.getCommutingZ().getImaginary(),baseVA);
    }
}
