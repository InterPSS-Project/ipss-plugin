package org.interpss.mapper.odm.impl.aclf;

import static org.interpss.mapper.odm.ODMUnitHelper.toActivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.toAngleUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.toVoltageUnit;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.ODMLogger;
import org.ieee.odm.schema.BusIDRefXmlType;
import org.ieee.odm.schema.BusXmlType;
import org.ieee.odm.schema.DCLineData2TXmlType;
import org.ieee.odm.schema.DcLineControlModeEnumType;
import org.ieee.odm.schema.DcLineMeteredEndEnumType;
import org.ieee.odm.schema.DcLineOperationModeEnumType;
import org.ieee.odm.schema.ThyristorConverterXmlType;
import org.ieee.odm.schema.VSCACControlModeEnumType;
import org.ieee.odm.schema.VSCConverterXmlType;
import org.ieee.odm.schema.VSCDCControlModeEnumType;
import org.ieee.odm.schema.VSCHVDC2TXmlType;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.ThyConverter;
import com.interpss.core.aclf.hvdc.VSCConverter;
import com.interpss.core.aclf.hvdc.impl.HvdcLineFactoryImpl;


public class AclfHvdcDataHelper {
	private BaseAclfNetwork<?, ?>  aclfNet = null;
	private HvdcLine2TLCC hvdc2T = null;
	private HvdcLine2TVSC vscHvdc2T = null;

	public AclfHvdcDataHelper(BaseAclfNetwork<?, ?>  aclfNet, HvdcLine2TLCC hvdc2T){
		this.aclfNet = aclfNet;
		this.hvdc2T = hvdc2T;
	}
	
	public AclfHvdcDataHelper(BaseAclfNetwork<?, ?>  aclfNet, HvdcLine2TVSC vscHvdc2T){
		this.aclfNet = aclfNet;
		this.vscHvdc2T = vscHvdc2T;
	}
	//TODO Define DCLineDataXmlType as a baseModel for two-terminal HVDC and VSC-HVDC
	// so that in the future, we can handle two kinds of HVDC under the same method here as well
	// as in the core engine;
	/*
	 * 
	public boolean setHvdcData(DCLineDataXmlType hvdcXml){
		if(hvdcXml instanceof DCLineData2TXmlType){
			setHvdc2TData((DCLineData2TXmlType)hvdcXml)
		}
		else
           setVSCHvdcData(DCLineDataVSCXmlType)hvdcXml)
	}
	*/
	public boolean setHvdc2TData(DCLineData2TXmlType hvdc2TXml){
		boolean success = true;
		//set DCLine Id
		//this.hvdc2T.setId(hvdc2TXml.getId());
		
		//Control Mode
		DcLineControlModeEnumType mode =hvdc2TXml.getControlMode();
		
		//TODO No "blocked" enum type
		this.hvdc2T.setControlMode(mode==DcLineControlModeEnumType.POWER? HvdcControlMode.DC_POWER: 
			mode==DcLineControlModeEnumType.CURRENT?HvdcControlMode.DC_CURRENT:HvdcControlMode.BLOCKED);
		this.hvdc2T.setOperationMode(hvdc2TXml.getOperationMode()==DcLineOperationModeEnumType.DOUBLE?HvdcOperationMode.REC1_INV2:
			HvdcOperationMode.REC1_INV1);
		//RDC
		//TODO 
		//this.hvdc2T.setLineR()
		this.hvdc2T.setStatus(hvdc2TXml.isOffLine()?false:true);
		//SETVL
		if(this.hvdc2T.getControlMode()==HvdcControlMode.DC_CURRENT){
		//	this.hvdc2T.setCurrentDemand(hvdc2TXml.getCurrentDemand().getValue());
		}else if(this.hvdc2T.getControlMode()==HvdcControlMode.DC_POWER){
			this.hvdc2T.setPowerDemand(hvdc2TXml.getPowerDemand().getValue(), toActivePowerUnit.apply(hvdc2TXml.getPowerDemand().getUnit()));
			if(hvdc2TXml.getOperationMode()==DcLineOperationModeEnumType.DOUBLE){
				this.hvdc2T.setPowerDemand2(hvdc2TXml.getPowerDemand2().getValue(), toActivePowerUnit.apply(hvdc2TXml.getPowerDemand().getUnit()));
			}
		}
		
		else //HVDC Line is Blocked
			this.hvdc2T.setStatus(false);
		
		
		//Scheduled compound dc voltage, kV by default
		this.hvdc2T.setScheduledDCVoltage(hvdc2TXml.getScheduledDCVoltage().getValue(), toVoltageUnit.apply(hvdc2TXml.getScheduledDCVoltage().getUnit()));
		
		//TODO VCMOD mode switch dc voltage
		//this.hvdc2T.setSwitchModeVoltage()
		
		
		//RCOMP
		/* Gamma and/or TAPI is used to attempt 
		to hold the compounded voltage (VDCI + DCCUR∗RCOMP) at VSCHD. To control the 
		inverter end dc voltage VDCI, set RCOMP to zero; to control the rectifier 
		end dc voltage VDCR, set RCOMP to the dc line resistance, RDC; otherwise, set 
		RCOMP to the appropriate fraction of RDC.
		*/
		//this.hvdc2T.setCompondR(hvdc2TXml.getCompoundingR().getR(),toZUnit.apply(hvdc2TXml.getCompoundingR().getUnit()));
		this.hvdc2T.setLineR(hvdc2TXml.getLineR().getR());
		
		
		//DELTI  DC power or current margin when ALPHA is at minimum and inverter is controlling line current
		//TODO
		//this.hvdc2T.setPowerCurrentMargin();
		
		
		//Meter end
		this.hvdc2T.setMeterEnd(hvdc2TXml.getMeteredEnd()==DcLineMeteredEndEnumType.RECTIFIER? ConverterType.RECTIFIER:ConverterType.INVERTER);
		
		//DCVMIN - Minimum compounded dc voltage
		//TODO
		//this.hvdc2T.setMinCompVdc()
		
		//set Rectifier data
		if (hvdc2TXml.getRectifier() != null) {
			ThyConverter rectifier = HvdcLineFactoryImpl.init().createThyConverter();
			this.hvdc2T.setRectifier(rectifier);
			setRectifierData(rectifier, hvdc2TXml.getRectifier(), 1);
			// double
			if (hvdc2TXml.getOperationMode() == DcLineOperationModeEnumType.DOUBLE) {
				ThyConverter rectifier2 = HvdcLineFactoryImpl.init().createThyConverter();
				this.hvdc2T.setRectifier2(rectifier2);
				setRectifierData(rectifier2, hvdc2TXml.getRectifier(), 2);
			}
		}
		else{ 
			ODMLogger.getLogger().severe("Rectifier data is Null, or not defined in ODM/XML!");
			return false;
		}
			
		//set Inverter data
		
		if (hvdc2TXml.getInverter() != null) {
			ThyConverter inverter = HvdcLineFactoryImpl.init().createThyConverter();
			this.hvdc2T.setInverter(inverter);
			setInverterData(inverter, hvdc2TXml.getInverter(), 1);
			// double
			if (hvdc2TXml.getOperationMode() == DcLineOperationModeEnumType.DOUBLE) {
				ThyConverter inverter2 = HvdcLineFactoryImpl.init().createThyConverter();
				this.hvdc2T.setInverter2(inverter2);
				setInverterData(inverter2, hvdc2TXml.getInverter(), 2);
			}
		}
		
		else{
			ODMLogger.getLogger().severe("Inverter data is Null, or not defined in ODM/XML!");
			return false;
		}
		
		
		return success;
		
	}
	
    
    public boolean setVSCHvdcData(VSCHVDC2TXmlType vschvdc2TXml){
    	boolean success = false;
    	
    	this.vscHvdc2T.setId(vschvdc2TXml.getId());
    	this.vscHvdc2T.setName(vschvdc2TXml.getName());
    	
    	// Rdc and rating
    	this.vscHvdc2T.setRdc(vschvdc2TXml.getRdc().getR());
    	
    	if(vschvdc2TXml.getMVARating()!=null)
    	      this.vscHvdc2T.setMvaRating(vschvdc2TXml.getMVARating().getValue());
    	
    	
    	// set the vsc converter data
    	 success = this.setVSCInverterData(vschvdc2TXml.getInverter());
    	if(!success) {
    		IpssLogger.getLogger().severe("Error in setting the vsc inverter data");
    		return success;
    	}
    	
    	 success = this.setVSCRectifierData(vschvdc2TXml.getRectifier());
    	 if(!success) {
     		IpssLogger.getLogger().severe("Error in setting the vsc rectifier data");
     		return success;
     	}
    	
    	return success;
    	
		
	}
	
	
	private void setRectifierData(ThyConverter rectifier, ThyristorConverterXmlType rectifierXml,int n){
		
		
		//TODO interface bus id
		//rectifier.setRefBusId(BusXmlRef2BusId.fx(rectifierXml.getBusId()));
		
		//Num of bridges
		rectifier.setNBridges(rectifierXml.getNumberofBridges());
		
		rectifier.setFiringAngLimit(new LimitType(rectifierXml.getMaxFiringAngle().getValue(), 
				rectifierXml.getMinFiringAngle().getValue()),
				toAngleUnit.apply(rectifierXml.getMaxFiringAngle().getUnit()));	
		
		//RCR and XCR in ohm
		rectifier.setCommutingZ(new Complex(rectifierXml.getCommutatingZ().getRe(),rectifierXml.getCommutatingZ().getIm()));
		
		//Rectifier primary base ac voltage; entered in kV
		rectifier.setAcRatedVoltage(rectifierXml.getAcSideRatedVoltage().getValue());
		
		//TRR  transformer ratio ,1.0 by default
		rectifier.setXformerRatio(rectifierXml.getXformerTurnRatio());
		
		//TAPR tap setting 
		//rectifier.setXformerTapSetting(rectifierXml.getXformerTapSetting().getValue());
		
		// TAP Setting limit
		rectifier.setXformerTapLimit(new LimitType(rectifierXml.getXformerTapLimit().getMax(),
				                           rectifierXml.getXformerTapLimit().getMin()));
		
		//STPR tap step; must be positive. STPR = 0.00625 by default.
		//rectifier.setXformerTapStepSize(rectifierXml.getXformerTapStepSize());
		
		//IFR, ITR,IDR for specifying a two winding transformer to control a converter to keep 
		//ALPHA/GARMER within limit
		
		// IFR = ITR=0, IDR =1.0 by default
		//if(rectifierXml.getRefXfrFromBusId()!=null && rectifierXml.getRefXfrToBusId()!=null)
		//BusIDRefXmlType fbRef=((BusIDRefXmlType)rectifierXml.getRefXfrFromBusId());
		
		//TODO to add the following methods
		//rectifier.setXfrFromBus()
		//rectifier.setXfrToBus()
		//rectifier.setXfrCirId();
		
		//XCAPR , =0 by default
		rectifier.setCommutingCapacitor(rectifierXml.getCommutatingCapacitor());
		if(n==1){
		    rectifier.setFiringAng(rectifierXml.getFiringAngle().getValue());
		}else if(n==2){
			rectifier.setFiringAng(rectifierXml.getFiringAngle2().getValue());
		}
			
	}
	
    private void setInverterData(ThyConverter inverter, ThyristorConverterXmlType inverterXml,int n){
	
    	
    	//Num of bridges
		inverter.setNBridges(inverterXml.getNumberofBridges());

		inverter.setFiringAngLimit(new LimitType(inverterXml
				.getMaxFiringAngle().getValue(), inverterXml
				.getMinFiringAngle().getValue()), toAngleUnit.apply(inverterXml
				.getMaxFiringAngle().getUnit()));

		// RCR and XCR in ohm
		inverter.setCommutingZ(new Complex(inverterXml.getCommutatingZ()
				.getRe(), inverterXml.getCommutatingZ().getIm()));

		// inverter primary base ac voltage; entered in kV
		inverter.setAcRatedVoltage(inverterXml.getAcSideRatedVoltage()
				.getValue());

		// TRR transformer ratio ,1.0 by default
		inverter.setXformerRatio(inverterXml.getXformerTurnRatio());

		// TAPR tap setting
		//inverter.setXformerTapSetting(inverterXml.getXformerTapSetting().getValue());

		// TAP Setting limit
		inverter.setXformerTapLimit(new LimitType(inverterXml
				.getXformerTapLimit().getMax(), inverterXml
				.getXformerTapLimit().getMin()));

		// STPR tap step; must be positive. STPR = 0.00625 by default.
		//inverter.setXformerTapStepSize(inverterXml.getXformerTapStepSize());

		// IFR, ITR,IDR for specifying a two winding transformer to control a
		// converter to keep
		// ALPHA/GARMER within limit

		// IFR = ITR=0, IDR =1.0 by default
		BusIDRefXmlType fbRef = ((BusIDRefXmlType) inverterXml
				.getRefXfrFromBusId());

		// TODO
		// inverter.setXfrFromBus()
		// inverter.setXfrToBus()
		// inverter.setXfrCirId();

		// XCAPR , =0 by default
		inverter.setCommutingCapacitor(inverterXml.getCommutatingCapacitor());
		if(n==1){
		    inverter.setFiringAng(inverterXml.getFiringAngle().getValue());
		}else if(n==2){
			inverter.setFiringAng(inverterXml.getFiringAngle2().getValue());
		}
	}
    

    
    private boolean setVSCInverterData(VSCConverterXmlType vscConvXml){
    	
    	boolean success = true;
    	VSCConverter vscInv = (VSCConverter)this.vscHvdc2T.getInvConverter();
    	
    	// connection bus
    	BusXmlType busXml = (BusXmlType) vscConvXml.getBusId().getIdRef();
    	
    	vscInv.setBus(this.aclfNet.getBus(busXml.getId()));
    	
    	// DC Control mode
    	HvdcControlMode dcMode = 
    	vscConvXml.getDcControlMode() == VSCDCControlModeEnumType.BLOCKED?HvdcControlMode.BLOCKED:
    		vscConvXml.getDcControlMode() == VSCDCControlModeEnumType.REAL_POWER? HvdcControlMode.DC_POWER : HvdcControlMode.DC_VOLTAGE;
    	
    	 vscInv.setDcControlMode(dcMode);
    	 
    	 vscInv.setDcSetPoint(vscConvXml.getDcSetPoint());
    	
    	// AC Control mode
    	VSCAcControlMode acMode = 
    			vscConvXml.getAcControlMode() == VSCACControlModeEnumType.REACTIVE_POWER?VSCAcControlMode.AC_REACTIVE_POWER:
    				(vscConvXml.getAcControlMode() == VSCACControlModeEnumType.VOLTAGE)?VSCAcControlMode.AC_VOLTAGE:VSCAcControlMode.AC_POWER_FACTOR;
    	 vscInv.setAcControlMode(acMode);
    	
    	 vscInv.setAcSetPoint( vscConvXml.getAcSetPoint());
    	 
    	 //TODO Power Loss is not considered in the VSCConveter model yet
    	 
    	 
    	 //Rating, assuming to be based on MVA unit
    	 vscInv.setMvaRating( vscConvXml.getMVARating().getValue());
    	 
    	 
    	 // Q limit
    	 vscInv.setQMaxMvar(vscConvXml.getQMax().getValue());
    	 vscInv.setQMinMvar(vscConvXml.getQMin().getValue());
    	 
    	 // remote Voltage control
    	
    	 
    	 if(vscConvXml.getRemoteCtrlBusId()!=null){
    	         vscInv.setRemoteControlBusId((String) vscConvXml.getRemoteCtrlBusId().getIdRef());
    	         // only when there is a remote control bus, this value is useful
    	         vscInv.setRemoteControlPercent(vscConvXml.getRemoteCtrlPercent());
    	 }
    	
    	
    	return success;
    	
    }
    
 private boolean setVSCRectifierData(VSCConverterXmlType vscConvXml){
    	
		boolean success = true;
		
		VSCConverter vscRec = (VSCConverter)this.vscHvdc2T.getRecConverter();
	 	
	 	// connection bus
	 	BusXmlType busXml = (BusXmlType) vscConvXml.getBusId().getIdRef();
	 	
	 	vscRec.setBus(this.aclfNet.getBus(busXml.getId()));
	 	
	 	// DC Control mode
	 	HvdcControlMode dcMode = 
	 	vscConvXml.getDcControlMode() == VSCDCControlModeEnumType.BLOCKED?HvdcControlMode.BLOCKED:
	 		vscConvXml.getDcControlMode() == VSCDCControlModeEnumType.REAL_POWER? HvdcControlMode.DC_POWER:HvdcControlMode.DC_VOLTAGE;
	 	
	 	 vscRec.setDcControlMode(dcMode);
	 	 
	 	 vscRec.setDcSetPoint(vscConvXml.getDcSetPoint());
	 	
	 	// AC Control mode
	 	VSCAcControlMode acMode = 
	 			vscConvXml.getAcControlMode() == VSCACControlModeEnumType.REACTIVE_POWER?VSCAcControlMode.AC_REACTIVE_POWER:
	 				(vscConvXml.getAcControlMode() == VSCACControlModeEnumType.VOLTAGE)?VSCAcControlMode.AC_VOLTAGE:VSCAcControlMode.AC_POWER_FACTOR;
	 	 vscRec.setAcControlMode(acMode);
	 	
	 	 vscRec.setAcSetPoint( vscConvXml.getAcSetPoint());
	 	 
	 	 //TODO Power Loss is not considered in the VSCConveter model yet
	 	 
	 	 
	 	 //Rating, assuming to be based on MVA unit
	 	 vscRec.setMvaRating( vscConvXml.getMVARating().getValue());
	 	 
	 	 
	 	 // Q limit
	 	 vscRec.setQMaxMvar(vscConvXml.getQMax().getValue());
	 	 vscRec.setQMinMvar(vscConvXml.getQMin().getValue());
	 	 
	 	 // remote Voltage control
	 
	 	 
	 	if(vscConvXml.getRemoteCtrlBusId()!=null){
	         vscRec.setRemoteControlBusId((String) vscConvXml.getRemoteCtrlBusId().getIdRef());
	    	 vscRec.setRemoteControlPercent(vscConvXml.getRemoteCtrlPercent());
	 	}
	 	 
    	return success;
    	
    }
	
}
