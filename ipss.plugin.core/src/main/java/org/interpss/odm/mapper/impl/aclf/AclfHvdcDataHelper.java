package org.interpss.odm.mapper.impl.aclf;


import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.ODMLogger;
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
import static org.interpss.odm.mapper.base.ODMUnitHelper.toActivePowerUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toAngleUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toVoltageUnit;
import static org.interpss.odm.mapper.base.ODMUnitHelper.toZUnit;

import com.interpss.common.util.IpssLogger;
import com.interpss.core.HvdcObjectFactory;
import com.interpss.core.aclf.AclfBus;
import com.interpss.core.aclf.BaseAclfNetwork;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcControlSide;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.aclf.hvdc.HvdcLine2TLCC;
import com.interpss.core.aclf.hvdc.HvdcLine2TVSC;
import com.interpss.core.aclf.hvdc.HvdcOperationMode;
import com.interpss.core.aclf.hvdc.ThyConverter;
import com.interpss.core.aclf.hvdc.VSCAcControlMode;
import com.interpss.core.aclf.hvdc.VSCConverter;


public class AclfHvdcDataHelper {
	private BaseAclfNetwork<?, ?>  aclfNet = null;
	private HvdcLine2T<?> hvdc2T = null;

	public AclfHvdcDataHelper(BaseAclfNetwork<?, ?>  aclfNet, HvdcLine2T<?> hvdc2T){
		this.aclfNet = aclfNet;
		this.hvdc2T = hvdc2T;
	}

	//TODO Define DCLineDataXmlType as a baseModel for two-terminal HVDC and VSC-HVDC
	// so that in the future, we can handle two kinds of HVDC under the same method here as well
	// as in the core engine;
	
	/*
	 *  LCC Hvdc 2T Mapping part
	 *  ======================== 
	 */
	
	public boolean setLCCHvdcData(DCLineData2TXmlType hvdc2TXml){
		boolean success = true;
		//set DCLine Id
		//this.hvdc2T.setId(hvdc2TXml.getId());
		
		HvdcLine2TLCC<AclfBus> lccHvdc2T = (HvdcLine2TLCC<AclfBus>)hvdc2T; 
				 
		//Control Mode
		DcLineControlModeEnumType mode =hvdc2TXml.getControlMode();
		
		//TODO No "blocked" enum type
		lccHvdc2T.setDcLineControlMode(mode==DcLineControlModeEnumType.POWER? 
									HvdcControlMode.DC_POWER: 
										mode==DcLineControlModeEnumType.CURRENT?
												HvdcControlMode.DC_CURRENT:
													HvdcControlMode.BLOCKED);
		lccHvdc2T.setOperationMode(hvdc2TXml.getOperationMode()==DcLineOperationModeEnumType.DOUBLE?
									HvdcOperationMode.REC2_INV2:
										HvdcOperationMode.REC1_INV1);

		//RDC
		lccHvdc2T.setRdc(hvdc2TXml.getLineR().getR(), toZUnit.apply(hvdc2TXml.getLineR().getUnit())); // Rdc in ohm


		lccHvdc2T.setStatus(hvdc2TXml.isOffLine()?false:true);
		
		//SETVL
		if(lccHvdc2T.getDcLineControlMode()==HvdcControlMode.DC_CURRENT){
			lccHvdc2T.setCurrentDemand(hvdc2TXml.getCurrentDemand().getValue()); // Set current demand
		}
		else if(lccHvdc2T.getDcLineControlMode()==HvdcControlMode.DC_POWER){
			double powerDemand = hvdc2TXml.getPowerDemand().getValue();
			//TODO: we move the specific of control side to the adapter level, so this becomes more generic for different formats
			lccHvdc2T.setControlSide(hvdc2TXml.isControlOnRectifierSide()?HvdcControlSide.RECTIFIER:HvdcControlSide.INVERTER);
			lccHvdc2T.setPowerDemand(Math.abs(powerDemand), toActivePowerUnit.apply(hvdc2TXml.getPowerDemand().getUnit()));
			if(hvdc2TXml.getOperationMode()==DcLineOperationModeEnumType.DOUBLE){
				lccHvdc2T.setPowerDemand2(hvdc2TXml.getPowerDemand2().getValue(), toActivePowerUnit.apply(hvdc2TXml.getPowerDemand2().getUnit()));
			}
		}
		else{ //HVDC Line is Blocked
			lccHvdc2T.setStatus(false);
		}

		//set default RectifierControlMode and InverterControlMode based on PSS/E default settings
		lccHvdc2T.setRectifierControlMode(HvdcControlMode.DC_CURRENT);
		lccHvdc2T.setInverterControlMode(HvdcControlMode.DC_VOLTAGE);
		
		//Scheduled compound dc voltage, kV by default
		lccHvdc2T.setScheduledDCVoltage(hvdc2TXml.getScheduledDCVoltage().getValue(), toVoltageUnit.apply(hvdc2TXml.getScheduledDCVoltage().getUnit()));
		
		//TODO VCMOD mode switch dc voltage
		//this.hvdc2T.setSwitchModeVoltage(hvdc2TXml.getModeSwitchDCVoltage().getValue(), toVoltageUnit.apply(hvdc2TXml.getModeSwitchDCVoltage().getUnit()));
		
		
		//RCOMP
		/* Gamma and/or TAPI is used to attempt 
		to hold the compounded voltage (VDCI + DCCUR∗RCOMP) at VSCHD. To control the 
		inverter end dc voltage VDCI, set RCOMP to zero; to control the rectifier 
		end dc voltage VDCR, set RCOMP to the dc line resistance, RDC; otherwise, set 
		RCOMP to the appropriate fraction of RDC.
		*/
		lccHvdc2T.setCompondR(hvdc2TXml.getCompoundingR().getR(),toZUnit.apply(hvdc2TXml.getCompoundingR().getUnit()));
		
		
		
		//DELTI  DC power or current margin when ALPHA is at minimum and inverter is controlling line current
		double deltaI = hvdc2TXml.getPowerOrCurrentMarginPU();
		lccHvdc2T.setPowerCurrentMargin(deltaI);
		
		
		//Meter end
		lccHvdc2T.setMeterEnd(hvdc2TXml.getMeteredEnd()==DcLineMeteredEndEnumType.RECTIFIER? ConverterType.RECTIFIER:ConverterType.INVERTER);
		
		//DCVMIN - Minimum compounded dc voltage
		//TODO
		//this.hvdc2T.setMinCompVdc()
		
		//set Rectifier data
		if (hvdc2TXml.getRectifier() != null) {
			ThyConverter<AclfBus> rectifier = HvdcObjectFactory.createThyConverter((AclfBus)this.hvdc2T.getFromBus());
			//TODO: It is better to rename the setRectifier method to setConverterType or something like that
			rectifier.setConverterType(ConverterType.RECTIFIER);
			lccHvdc2T.setRectifier(rectifier);
			setThyRectifierData(rectifier, hvdc2TXml.getRectifier(), 1);
			
			// double
			if (hvdc2TXml.getOperationMode() == DcLineOperationModeEnumType.DOUBLE) {
				ThyConverter<AclfBus> rectifier2 = HvdcObjectFactory.createThyConverter((AclfBus)this.hvdc2T.getFromBus());
				rectifier2.setConverterType(ConverterType.RECTIFIER);
				lccHvdc2T.setRectifier2(rectifier2);
				setThyRectifierData(rectifier2, hvdc2TXml.getRectifier(), 2);
			}
		}
		else{ 
			ODMLogger.getLogger().severe("Rectifier data is Null, or not defined in ODM/XML!");
			return false;
		}
			
		//set Inverter data
		
		if (hvdc2TXml.getInverter() != null) {
			ThyConverter<AclfBus> inverter = HvdcObjectFactory.createThyConverter((AclfBus)this.hvdc2T.getToBus());
			lccHvdc2T.setInverter(inverter);
			//It is better to rename the setInverter method to setConverterType or something like that 
			inverter.setConverterType(ConverterType.INVERTER);
			setThyInverterData(inverter, hvdc2TXml.getInverter(), 1);
			// double
			if (hvdc2TXml.getOperationMode() == DcLineOperationModeEnumType.DOUBLE) {
				ThyConverter<AclfBus> inverter2 = HvdcObjectFactory.createThyConverter((AclfBus)this.hvdc2T.getToBus());
				lccHvdc2T.setInverter2(inverter2);
				inverter2.setConverterType(ConverterType.INVERTER);
				setThyInverterData(inverter2, hvdc2TXml.getInverter(), 2);
			}
		}
		
		else{
			ODMLogger.getLogger().severe("Inverter data is Null, or not defined in ODM/XML!");
			return false;
		}
		
		
		return success;
		
	}
	
	private void setThyRectifierData(ThyConverter<AclfBus> rectifier, ThyristorConverterXmlType rectifierXml,int n){
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
		rectifier.setXformerTapSetting(rectifierXml.getXformerTapSetting().getValue());
		
		// TAP Setting limit
		rectifier.setXformerTapLimit(new LimitType(rectifierXml.getXformerTapLimit().getMax(),
				                           rectifierXml.getXformerTapLimit().getMin()));
		
		//STPR tap step; must be positive. STPR = 0.00625 by default.
		rectifier.setXformerTapStepSize(rectifierXml.getXformerTapStepSize());
		
		//IFR, ITR,IDR for specifying a two winding transformer to control a converter to keep ALPHA/GARMER within limit,
		//if no transformer is used, set IFR=ITR=0 and IDR=1.0, TAPR tap setting is adjusted to control 
		//the Alpa/GAMMA within limit.
		
		// IFR = ITR=0, IDR =1.0 by default
		if(rectifierXml.getRefXfrFromBusId()!=null && rectifierXml.getRefXfrToBusId()!=null){
			IpssLogger.getLogger().severe("IFR, ITR for specifying a two winding transformer to control a converter is not supported in the LCC HVDC rectifier: "+ rectifier.getId());
		}
		//BusIDRefXmlType fbRef=((BusIDRefXmlType)rectifierXml.getRefXfrFromBusId());
		
		//TODO to add the following methods
		//rectifier.setXfrFromBus()
		//rectifier.setXfrToBus()
		//rectifier.setXfrCirId();
		
		//XCAPR , =0 by default
		rectifier.setCommutingCapacitor(rectifierXml.getCommutatingCapacitor());
		
		//Firing angle
		// NOTE PSS/E data do not provie the fire angle
		if(n==1 && rectifierXml.getFiringAngle() != null){
		    rectifier.setFiringAng(rectifierXml.getFiringAngle().getValue());
		}else if(n==2 && rectifierXml.getFiringAngle2() != null){
			rectifier.setFiringAng(rectifierXml.getFiringAngle2().getValue());
			//throw new IllegalArgumentException("Firing angle 2 is not supported in the current model");
		}
			
	}
	
    private void setThyInverterData(ThyConverter<AclfBus> inverter, ThyristorConverterXmlType inverterXml,int n){
	
    	
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
		if (inverterXml.getXformerTapSetting()!=null)
			inverter.setXformerTapSetting(inverterXml.getXformerTapSetting().getValue());

		// TAP Setting limit
		inverter.setXformerTapLimit(new LimitType(inverterXml
				.getXformerTapLimit().getMax(), inverterXml
				.getXformerTapLimit().getMin()));

		// STPR tap step; must be positive. STPR = 0.00625 by default.
		inverter.setXformerTapStepSize(inverterXml.getXformerTapStepSize());

		// IFR, ITR,IDR for specifying a two winding transformer to control a
		// converter to keep
		// ALPHA/GARMER within limit

		// IFR = ITR=0, IDR =1.0 by default
		if(inverterXml.getRefXfrFromBusId()!=null && inverterXml.getRefXfrToBusId()!=null){
			IpssLogger.getLogger().severe("IFR, ITR for specifying a two winding transformer to control a converter is not supported in the LCC HVDC inverter: " + inverter.getId());
		}

		// TODO
		// inverter.setXfrFromBus()
		// inverter.setXfrToBus()
		// inverter.setXfrCirId();

		// XCAPR , =0 by default
		inverter.setCommutingCapacitor(inverterXml.getCommutatingCapacitor());

		if(n==1 && inverterXml.getFiringAngle() != null){
		    inverter.setFiringAng(inverterXml.getFiringAngle().getValue());
		}else if(n==2 && inverterXml.getFiringAngle2() != null	){
			inverter.setFiringAng(inverterXml.getFiringAngle2().getValue());
			//throw new IllegalArgumentException("Firing angle 2 is not supported in the current model");
		}
	}
    
	
	/*
	 *  VSC Hvdc 2T Mapping part
	 *  ======================== 
	 */
	
    public boolean setVSCHvdcData(VSCHVDC2TXmlType vschvdc2TXml){
    	boolean success = false;
    	
		HvdcLine2TVSC<?> vscHvdc2T = (HvdcLine2TVSC<?>)hvdc2T; 
		
    	vscHvdc2T.setId(vschvdc2TXml.getId());
    	vscHvdc2T.setName(vschvdc2TXml.getName());
    	
    	// Rdc and rating
    	vscHvdc2T.setRdc(vschvdc2TXml.getRdc().getR(),toZUnit.apply(vschvdc2TXml.getRdc().getUnit()));
    	
    	if(vschvdc2TXml.getMVARating()!=null)
    	      vscHvdc2T.setMvaRating(vschvdc2TXml.getMVARating().getValue());
    	
    	
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
	    
    private boolean setVSCInverterData(VSCConverterXmlType vscConvXml){
    	boolean success = true;
    	
		HvdcLine2TVSC<?> vscHvdc2T = (HvdcLine2TVSC<?>)hvdc2T; 
		
    	VSCConverter vscInv = (VSCConverter)vscHvdc2T.getInvConverter();
    	
    	// connection bus
    	BusXmlType busXml = (BusXmlType) vscConvXml.getBusId().getIdRef();
    	
    	vscInv.setBus(this.aclfNet.getBus(busXml.getId()));

		//TODO: setId otherwise the id is null, and causes error in jsoncopy
		vscInv.setId("VSC Inv_" + busXml.getId());
    	
    	// DC Control mode
    	HvdcControlMode dcMode = 
    	vscConvXml.getDcControlMode() == VSCDCControlModeEnumType.BLOCKED? HvdcControlMode.BLOCKED:
    			vscConvXml.getDcControlMode() == VSCDCControlModeEnumType.REAL_POWER? 
    					HvdcControlMode.DC_POWER : HvdcControlMode.DC_VOLTAGE;
    	
    	 vscInv.setDcControlMode(dcMode);
    	 
    	 vscInv.setDcSetPoint(vscConvXml.getDcSetPoint());
    	
    	// AC Control mode
    	VSCAcControlMode acMode = 
    			vscConvXml.getAcControlMode() == VSCACControlModeEnumType.REACTIVE_POWER?VSCAcControlMode.AC_REACTIVE_POWER:
    				(vscConvXml.getAcControlMode() == VSCACControlModeEnumType.VOLTAGE)?
    						VSCAcControlMode.AC_VOLTAGE:VSCAcControlMode.AC_POWER_FACTOR;
    	 vscInv.setAcControlMode(acMode);
    	
    	 vscInv.setAcSetPoint( vscConvXml.getAcSetPoint());
    	 
    	 //TODO Power Loss is not considered in the VSCConveter model yet
    	 
    	 
    	 //Rating, assuming to be based on MVA unit
    	 vscInv.setMvaRating( vscConvXml.getMVARating().getValue());
    	 
    	 
    	 // Q limit
    	 vscInv.setQMvarLimit(new LimitType(vscConvXml.getQMax().getValue(), vscConvXml.getQMin().getValue()));
    	 
    	 // remote Voltage control
    	
    	 
    	 if(vscConvXml.getRemoteCtrlBusId()!=null){
    	         vscInv.setRemoteControlBusId(((BusXmlType)vscConvXml.getRemoteCtrlBusId().getIdRef()).getId());
    	         // only when there is a remote control bus, this value is useful
    	         vscInv.setRemoteControlPercent(vscConvXml.getRemoteCtrlPercent());
    	 }
    	
    	
    	return success;
    	
    }
    
    private boolean setVSCRectifierData(VSCConverterXmlType vscConvXml){
    	
		boolean success = true;
		
		HvdcLine2TVSC<?> vscHvdc2T = (HvdcLine2TVSC<?>)hvdc2T; 
		
		VSCConverter vscRec = (VSCConverter)vscHvdc2T.getRecConverter();
	 	
	 	// connection bus
	 	BusXmlType busXml = (BusXmlType) vscConvXml.getBusId().getIdRef();
	 	
	 	vscRec.setBus(this.aclfNet.getBus(busXml.getId()));

		//TODO: setId otherwise the id is null, and causes error in jsoncopy
		vscRec.setId("VSC Rec_" + busXml.getId());
	 	
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
	 	 vscRec.setQMvarLimit(new LimitType(vscConvXml.getQMax().getValue(), vscConvXml.getQMin().getValue()));
	 	 
	 	 // remote Voltage control
	 
	 	 
	 	if(vscConvXml.getRemoteCtrlBusId()!=null){
	         vscRec.setRemoteControlBusId(((BusXmlType) vscConvXml.getRemoteCtrlBusId().getIdRef()).getId());
	    	 vscRec.setRemoteControlPercent(vscConvXml.getRemoteCtrlPercent());
	 	}
	 	 
    	return success;
    	
    }
	
}
