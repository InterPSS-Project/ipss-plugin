package org.interpss.mapper.odm.impl.aclf;

import static org.interpss.mapper.odm.ODMUnitHelper.ToActivePowerUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToAngleUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToVoltageUnit;
import static org.interpss.mapper.odm.ODMUnitHelper.ToZUnit;

import org.apache.commons.math3.complex.Complex;
import org.ieee.odm.common.ODMLogger;
import org.ieee.odm.schema.BusIDRefXmlType;
import org.ieee.odm.schema.ConverterXmlType;
import org.ieee.odm.schema.DCLineData2TXmlType;
import org.ieee.odm.schema.DcLineControlModeEnumType;
import org.ieee.odm.schema.DcLineMeteredEndEnumType;
import org.interpss.numeric.datatype.LimitType;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.aclf.hvdc.ConverterType;
import com.interpss.core.aclf.hvdc.HvdcControlMode;
import com.interpss.core.aclf.hvdc.HvdcLine2T;
import com.interpss.core.aclf.hvdc.HvdcLine2TCCC;
import com.interpss.core.aclf.hvdc.Inverter;
import com.interpss.core.aclf.hvdc.Rectifier;
import com.interpss.core.aclf.hvdc.impl.HvdcLineFactoryImpl;


public class AclfHvdcDataHelper {
	private AclfNetwork aclfNet = null;
	private HvdcLine2TCCC hvdc2T = null;
	
	public AclfHvdcDataHelper(AclfNetwork aclfNet, HvdcLine2TCCC hvdc2T){
		this.aclfNet = aclfNet;
		this.hvdc2T = hvdc2T;
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
		this.hvdc2T.setId(hvdc2TXml.getId());
		
		//Control Mode
		DcLineControlModeEnumType mode =hvdc2TXml.getControlMode();
		
		//TODO No "blocked" enum type
		this.hvdc2T.setControlMode(mode==DcLineControlModeEnumType.POWER? HvdcControlMode.POWER: 
			mode==DcLineControlModeEnumType.CURRENT?HvdcControlMode.CURRENT:HvdcControlMode.BLOCKED);
		
		//RDC
		//TODO 
		//this.hvdc2T.setLineR()
		
		//SETVL
		if(this.hvdc2T.getControlMode()==HvdcControlMode.CURRENT){
		//	this.hvdc2T.setCurrentDemand(hvdc2TXml.getCurrentDemand().getValue());
		}else if(this.hvdc2T.getControlMode()==HvdcControlMode.POWER)
			this.hvdc2T.setPowerDemand(hvdc2TXml.getPowerDemand().getValue(), ToActivePowerUnit.f(hvdc2TXml.getPowerDemand().getUnit()));
		else //HVDC Line is Blocked
			this.hvdc2T.setStatus(false);
		
		
		//Scheduled compound dc voltage, kV by default
		this.hvdc2T.setScheduledDCVoltage(hvdc2TXml.getScheduledDCVoltage().getValue(), ToVoltageUnit.f(hvdc2TXml.getScheduledDCVoltage().getUnit()));
		
		//TODO VCMOD mode switch dc voltage
		//this.hvdc2T.setSwitchModeVoltage()
		
		
		//RCOMP
		/* Gamma and/or TAPI is used to attempt 
		to hold the compounded voltage (VDCI + DCCUR∗RCOMP) at VSCHD. To control the 
		inverter end dc voltage VDCI, set RCOMP to zero; to control the rectifier 
		end dc voltage VDCR, set RCOMP to the dc line resistance, RDC; otherwise, set 
		RCOMP to the appropriate fraction of RDC.
		*/
		this.hvdc2T.setCompondR(hvdc2TXml.getCompoundingR().getR(),ToZUnit.f(hvdc2TXml.getCompoundingR().getUnit()));
		
		
		
		//DELTI  DC power or current margin when ALPHA is at minimum and inverter is controlling line current
		//TODO
		//this.hvdc2T.setPowerCurrentMargin();
		
		
		//Meter end
		this.hvdc2T.setMeterEnd(hvdc2TXml.getMeteredEnd()==DcLineMeteredEndEnumType.RECTIFIER? ConverterType.RECTIFIER:ConverterType.INVERTER);
		
		//DCVMIN - Minimum compounded dc voltage
		//TODO
		//this.hvdc2T.setMinCompVdc()
		
		//set Rectifier data
		if(hvdc2TXml.getRectifier()!=null)
		    setRectifierData(hvdc2TXml.getRectifier());
		else{ 
			ODMLogger.getLogger().severe("Rectifier data is Null, or not defined in ODM/XML!");
			return false;
		}
			
		//set Inverter data
		
		if(hvdc2TXml.getInverter()!=null)
		    setInverterData(hvdc2TXml.getRectifier());
		else{
			ODMLogger.getLogger().severe("Inverter data is Null, or not defined in ODM/XML!");
			return false;
		}
		
		
		return success;
		
	}
	
	
	private void setRectifierData(ConverterXmlType rectifierXml){
		
		Rectifier rectifier = null;
		if(this.hvdc2T.getRectifier()==null){
		   rectifier = HvdcLineFactoryImpl.init().createRectifier();
		   this.hvdc2T.setRectifier(rectifier);
		}
		
		rectifier = this.hvdc2T.getRectifier();
		
		//TODO interface bus id
		//rectifier.setRefBusId(BusXmlRef2BusId.fx(rectifierXml.getBusId()));
		
		//Num of bridges
		rectifier.setNBridges(rectifierXml.getNumberofBridges());
		
		rectifier.setFiringAngLimit(new LimitType(rectifierXml.getMaxFiringAngle().getValue(), 
				rectifierXml.getMinFiringAngle().getValue()),
				ToAngleUnit.f(rectifierXml.getMaxFiringAngle().getUnit()));	
		
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
		
		//IFR, ITR,IDR for specifying a two winding transformer to control a converter to keep 
		//ALPHA/GARMER within limit
		
		// IFR = ITR=0, IDR =1.0 by default
		if(rectifierXml.getRefXfrFromBusId()!=null && rectifierXml.getRefXfrToBusId()!=null)
		//BusIDRefXmlType fbRef=((BusIDRefXmlType)rectifierXml.getRefXfrFromBusId());
		
		//TODO to add the following methods
		//rectifier.setXfrFromBus()
		//rectifier.setXfrToBus()
		//rectifier.setXfrCirId();
		
		//XCAPR , =0 by default
		rectifier.setCommutingCapacitor(rectifierXml.getCommutatingCapacitor());
		
	}
	
    private void setInverterData(ConverterXmlType inverterXml){
		
    	Inverter inverter = null;
    	if(this.hvdc2T.getInverter()==null){
    		inverter = HvdcLineFactoryImpl.init().createInverter();
 		   this.hvdc2T.setInverter(inverter);
 		}
    	
    	inverter = this.hvdc2T.getInverter();
    	
    	//Num of bridges
		inverter.setNBridges(inverterXml.getNumberofBridges());

		inverter.setFiringAngLimit(new LimitType(inverterXml
				.getMaxFiringAngle().getValue(), inverterXml
				.getMinFiringAngle().getValue()), ToAngleUnit.f(inverterXml
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
		inverter.setXformerTapSetting(inverterXml.getXformerTapSetting()
				.getValue());

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
		BusIDRefXmlType fbRef = ((BusIDRefXmlType) inverterXml
				.getRefXfrFromBusId());

		// TODO
		// inverter.setXfrFromBus()
		// inverter.setXfrToBus()
		// inverter.setXfrCirId();

		// XCAPR , =0 by default
		inverter.setCommutingCapacitor(inverterXml.getCommutatingCapacitor());
    	
	}
    
    /*
    private boolean setVSCHvdcData(DCLineDataVSCXmlType hvdc2TXml){
		
	}
	*/
}
