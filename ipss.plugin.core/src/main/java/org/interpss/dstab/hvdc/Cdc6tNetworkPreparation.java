package org.interpss.dstab.hvdc;

import com.interpss.core.aclf.hvdc.*;
import com.interpss.dstab.BaseDStabBus;
import com.interpss.dstab.BaseDStabNetwork;
import org.interpss.numeric.datatype.Unit.UnitType;

/** One-time preparation from a solved static link for explicit CDC6T attachment.
 * Supports ordinary single rectifier/inverter equipment with fixed dynamic taps.
 * Source controls select the voltage threshold and reject unqualified capacitor
 * commutation; equal inverter angle limits select prepared constant-gamma control.
 * This API does not enable automatic source import or mutate the solved network. */
public final class Cdc6tNetworkPreparation {
    private Cdc6tNetworkPreparation() {}

    /** Prepared objects belong exclusively to this link; attach the ports explicitly once. */
    public record Binding(HvdcLine2TLCC<?> owner,Cdc6tParameters.OperatingPoint operatingPoint,
            Cdc6tNormalRuntime runtime,Cdc6tProtectionCoordinator protection,Cdc6tNormalTerminalPorts ports) {}

    /** Use retained source controls; missing metadata must not silently disable a control. */
    public static Binding prepare(BaseDStabNetwork<?,?> network,Cdc6tParameters parameters){
        return prepareInternal(network,parameters,null);
    }
    /** Explicit threshold override for programmatic equipment and controlled experiments.
     * Any retained capacitor-commutation selectors are still checked. */
    public static Binding prepare(BaseDStabNetwork<?,?> network,Cdc6tParameters parameters,double modeThresholdKilovolts){
        return prepareInternal(network,parameters,modeThresholdKilovolts);
    }
    private static Binding prepareInternal(BaseDStabNetwork<?,?> network,Cdc6tParameters parameters,Double explicitThreshold){
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
        var source=owner.getExtensionObject() instanceof LccSourceControls controls?controls:null;
        if(source!=null){
            if(source.modeTransferKilovolts()<0)throw new IllegalArgumentException("Nonnegative mode-transfer voltage required");
            if(source.rectifierCapacitorOhms()!=0||source.inverterCapacitorOhms()!=0)
                throw new IllegalArgumentException("Capacitor-commutated dynamic converters are not yet qualified");
        }
        if(source==null&&explicitThreshold==null)throw new IllegalArgumentException("Retained LCC source controls or explicit programmatic threshold required");
        double modeThresholdKilovolts=explicitThreshold!=null?explicitThreshold:source.modeTransferKilovolts();
        if(!owner.isActive()||!owner.isInitialized()||owner.getNetwork()!=network)
            throw new IllegalArgumentException("Active solved DC-link owner required");
        if(owner.getOperationMode()!=HvdcOperationMode.REC1_INV1||owner.getRectifier2()!=null||owner.getInverter2()!=null||owner.isPuBasedPowerFlowAlgo())
            throw new IllegalArgumentException("CDC6T preparation requires a single converter pair solved in physical units");
        var rectifier=owner.getRectifier();var inverter=owner.getInverter();
        if(rectifier==null||inverter==null||rectifier.getConverterType()!=ConverterType.RECTIFIER||inverter.getConverterType()!=ConverterType.INVERTER)
            throw new IllegalArgumentException("Oriented rectifier/inverter equipment required");
        if(inverter.getFiringAngLimit()==null)throw new IllegalArgumentException("Explicit inverter angle limits required");
        boolean constantGamma=inverter.getFiringAngLimit().getMin()==inverter.getFiringAngLimit().getMax();
        boolean transferredGamma=constantGamma
                &&owner.getRectifierControlMode()==HvdcControlMode.FIRING_ANGLE
                &&owner.getInverterControlMode()==HvdcControlMode.DC_CURRENT
                &&Double.isFinite(owner.getPowerCurrentMargin())&&owner.getPowerCurrentMargin()>=0&&owner.getPowerCurrentMargin()<1
                &&(owner.getDcLineControlMode()==HvdcControlMode.DC_CURRENT||owner.getPowerCurrentMargin()==0);
        if(owner.getRectifierControlMode()!=HvdcControlMode.DC_CURRENT&&!transferredGamma)
            throw new IllegalArgumentException("Rectifier-current control or qualified constant-gamma current transfer required");
        if(!transferredGamma&&owner.getInverterControlMode()!=(constantGamma?HvdcControlMode.FIRING_ANGLE:HvdcControlMode.DC_VOLTAGE))
            throw new IllegalArgumentException("Solved inverter mode must agree with the source angle limits");
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
        if(transferredGamma&&(rectifier.getFiringAngLimit()==null
                ||!Double.isFinite(rectifier.getFiringAng())
                ||!Double.isFinite(rectifier.getFiringAngLimit().getMin())
                ||Math.abs(rectifier.getFiringAng()-rectifier.getFiringAngLimit().getMin())>1e-7
                ||command==Cdc6tCurrentOrder.Mode.CURRENT
                    &&Math.abs(current-owner.getCurrentDemand()*(1-owner.getPowerCurrentMargin()))>1e-7*Math.max(1,Math.abs(current))))
            throw new IllegalArgumentException("Constant-gamma transfer requires the solved rectifier minimum and consistent static current order");
        if(!Double.isFinite(current)||!Double.isFinite(vr)||!Double.isFinite(vi)||!Double.isFinite(resistance)||resistance<0
                ||Math.abs(current-inverter.getIdc())>1e-7*Math.max(1,Math.abs(current))
                ||!Double.isFinite(inverter.getIdc())||Math.abs(vr-vi-current*resistance)>1e-7*Math.max(1,Math.max(Math.abs(vr),Math.abs(vi))))
            throw new IllegalArgumentException("Consistent solved DC current and voltage drop required");
        if(constantGamma&&command==Cdc6tCurrentOrder.Mode.POWER){
            double scheduledWatts=Math.abs(setpoint)*1e6;
            double solvedWatts=current*(setpoint<0?vi:vr);
            if(Math.abs(solvedWatts-scheduledWatts)>1e-6*Math.max(1,scheduledWatts))
                throw new IllegalArgumentException("Solved constant-gamma power must match the DC power schedule before dynamic preparation");
        }
        var point=new Cdc6tParameters.OperatingPoint(command,setpoint,owner.getScheduledDCVoltage(UnitType.kV),
                owner.getCompondR(UnitType.Ohm),modeThresholdKilovolts,vi,current,vr);
        var rectifierBoundary=boundary(rectifier,true,network.getBaseKva()*1000);
        var inverterBoundary=boundary(inverter,false,network.getBaseKva()*1000);
        var prepared=constantGamma?parameters.prepareConstantGamma(rectifierBoundary,inverterBoundary,resistance,point,inverter.getFiringAngLimit().getMin(),owner.getPowerCurrentMargin())
                :parameters.prepare(rectifierBoundary,inverterBoundary,resistance,point);
        var ports=new Cdc6tNormalTerminalPorts(parameters.linkName(),rb,ib,prepared.runtime(),vi,current,vr,prepared.protection());
        if(transferredGamma||parameters.initiallyLimitsCurrent(point))
            ports.prepareInitialControlTransfer(rectifier.powerIntoConverter(),inverter.powerIntoConverter());
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
