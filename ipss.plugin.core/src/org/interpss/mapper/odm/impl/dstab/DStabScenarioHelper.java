/*
 * @(#)DStabScenarioHelper.java   
 *
 * Copyright (C) 2008-2010 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Stephen Hau, Mike Zhou
 * @Version 1.0
 * @Date 09/15/2010
 * 
 *   Revision History
 *   ================
 *
 */


package org.interpss.mapper.odm.impl.dstab;

import static com.interpss.common.util.IpssLogger.ipssLogger;
import static org.interpss.CorePluginFunction.MapBranchOutageType;

import org.ieee.odm.model.base.BaseDataSetter;
import org.ieee.odm.model.ext.ipss.IpssScenarioHelper;
import org.ieee.odm.schema.AclfAlgorithmXmlType;
import org.ieee.odm.schema.AcscBaseFaultXmlType;
import org.ieee.odm.schema.AcscBranchFaultXmlType;
import org.ieee.odm.schema.AcscBusFaultXmlType;
import org.ieee.odm.schema.AcscFaultTypeEnumType;
import org.ieee.odm.schema.DStabLoadChangeEnumType;
import org.ieee.odm.schema.DStabLoadChangeXmlType;
import org.ieee.odm.schema.DStabMethodEnumType;
import org.ieee.odm.schema.DStabSetPointChangeXmlType;
import org.ieee.odm.schema.DStabSimuSettingXmlType;
import org.ieee.odm.schema.DStabSimulationXmlType;
import org.ieee.odm.schema.DStabStaticLoadModelEnumType;
import org.ieee.odm.schema.DynamicEventEnumType;
import org.ieee.odm.schema.DynamicEventXmlType;
import org.ieee.odm.schema.FactorUnitType;
import org.ieee.odm.schema.IpssStudyScenarioXmlType;
import org.ieee.odm.schema.MachineControllerEnumType;
import org.ieee.odm.schema.SetPointChangeEnumType;
import org.ieee.odm.schema.StaticLoadModelXmlType;
import org.ieee.odm.schema.TimePeriodUnitType;
import org.ieee.odm.schema.TimePeriodXmlType;
import org.interpss.mapper.odm.impl.aclf.AclfScenarioHelper;
import org.interpss.mapper.odm.impl.acsc.AcscScenarioHelper;

import com.interpss.CoreObjectFactory;
import com.interpss.DStabObjectFactory;
import com.interpss.common.datatype.Constants;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.acsc.AcscBranch;
import com.interpss.core.acsc.AcscBus;
import com.interpss.core.acsc.fault.AcscBusFault;
import com.interpss.dstab.DStabilityNetwork;
import com.interpss.dstab.StaticLoadModel;
import com.interpss.dstab.algo.DynamicSimuAlgorithm;
import com.interpss.dstab.algo.DynamicSimuMethod;
import com.interpss.dstab.devent.BranchOutageEvent;
import com.interpss.dstab.devent.DStabBranchFault;
import com.interpss.dstab.devent.DynamicEvent;
import com.interpss.dstab.devent.DynamicEventType;
import com.interpss.dstab.devent.LoadChangeEvent;
import com.interpss.dstab.devent.LoadChangeEventType;
import com.interpss.dstab.devent.SetPointChangeEvent;
import com.interpss.dstab.mach.Machine;
import com.interpss.dstab.mach.MachineControllerType;

/**
 * Acsc scenario helper functions
 *
 	Key concepts:
		- Acsc Fault : defined using Fault Type (BusFault, BranchFault, BranchOutage) and Fault Category (3P, LG, LL, LLG)
		- Dynamic Event : defined using Dynamic Event Type (Fault, LoadChange, SetPointChange). Dynamic Event Fault is then 
               defined using Acsc Fault.

 * @author mzhou
 *
 */
public class DStabScenarioHelper {
	
	private DStabilityNetwork dstabNet = null;
	private DynamicSimuAlgorithm dstabAlgo = null;	
	
	/**
	 * constructor
	 * 
	 * @param dstabNet
	 * @param algo
	 */
	public DStabScenarioHelper(DStabilityNetwork dstabNet, DynamicSimuAlgorithm algo) {
		this.dstabNet = dstabNet;
		this.dstabAlgo = algo;
	}
	
	/**
	 * map ODM DStab scenario to InterPSS object model. It only applies to one fault scenario
	 * 
	 * @param sScenarioXml
	 * @throws InterpssException
	 */
	public void mapOneFaultScenario( IpssStudyScenarioXmlType sScenarioXml) throws InterpssException {
		if(	sScenarioXml.getAnalysisCaseList().getAnalysisCase() != null &&
				sScenarioXml.getAnalysisCaseList().getAnalysisCase().size() == 1){
			// first we check if dstab analysis type, scenario is defined and only one scenario 
			// is defined
			
			DStabSimulationXmlType dstabAnalysisXml = new IpssScenarioHelper(sScenarioXml)
														.getDStabSimulation();
			mapDStabSimuAlgo(dstabAnalysisXml);
			
/* not tested yet			
			IpssScenarioXmlType scenario = sScenarioXml.getScenarioList().getScenario().get(0);
			if (scenario.getSimuAlgo() != null && scenario.getSimuAlgo().getDStabAnalysis() != null)
				// then we check if simuAlgo and dstabAnalysis info if defined
				mapDStabSimuAlgo(scenario.getSimuAlgo().getDStabAnalysis());
			else
				throw new InterpssException("DStab Scenario mapping error: data not defined properly");
*/				
		}
		else {
			throw new InterpssException("DStab StudyScenario mapping error: data not defined properly");
		}
	}
	
	private void mapDStabSimuAlgo(DStabSimulationXmlType dstabSimuXml) throws InterpssException {
		// map the simulation setting part
		DStabSimuSettingXmlType settings = dstabSimuXml.getSimulationSetting();
		mapGeneralSettings(settings);
		
		// map the LF algo part
		AclfAlgorithmXmlType lfInit = dstabSimuXml.getAclfInitialization().getAclfAlgo();
		new AclfScenarioHelper(this.dstabAlgo.getAclfAlgorithm())
				.mapAclfAlgorithm(lfInit);		
		
		// map the dynamic event part
		dstabAlgo.setDisableDynamicEvent(dstabSimuXml.isDisableDynEvents());
		if (dstabAlgo.isDisableDynamicEvent()) {
			/*
			when disableDynEvents = true, SetPointChange events might be added. All other dynamic events are ignore. 
			When study SetPointChange dynamic evetns, you must disableDynEvents = true.
			 */
			if (dstabSimuXml.getDynamicEvent().size() == 1) {
				DynamicEventXmlType eventXml = dstabSimuXml.getDynamicEvent().get(0);
				if ( eventXml.getEventType() == DynamicEventEnumType.SET_POINT_CHANGE) {
					DStabSetPointChangeXmlType spcEventXml = eventXml.getSetPointChangeData();
					if (spcEventXml != null) {
						setSetPointChangeDynEvent(spcEventXml, settings);						
					}
				}
			}
		}
		else {
			/*
			 * In this clss file
			 * 
			 *    eventObj - InterPSS DynamicEvent object
			 *    eventXml - ODM DynamicEvent xml record 
			 */
			for (DynamicEventXmlType eventXml : dstabSimuXml.getDynamicEvent()) {			
				// create event name
				String name = eventXml.getName();
				if (name == null) 
					name = "EventAt_" + eventXml.getStartTime().getValue() + "[" +
					       eventXml.getStartTime().getUnit() + "]->" + eventXml.getEventType();
				if (eventXml.getId() == null)
					eventXml.setId(name);
				
				// map event type
				DynamicEventType deType = getDynamicEventType(eventXml.getEventType(), eventXml.getFault().getFaultType());

				// create the DStabEvent
				DynamicEvent eventObj = DStabObjectFactory.createDEvent(eventXml.getId(), name, deType, dstabNet);
			
				setDynamicEventData(eventObj, eventXml);		
			}		
		}
	}
	
	/**
	 * Transfer data stored in the ODM record eventXml to the Ipss Dynamic Event object eventObj
	 * 
	 * @param eventObj
	 * @param eventXml
	 * @throws InterpssException
	 */
	private void setDynamicEventData(DynamicEvent eventObj,
					DynamicEventXmlType eventXml) throws InterpssException {
		ipssLogger.info("Dynamic Event Type: " + eventXml.getEventType().toString());

		if (eventXml.getEventType() == DynamicEventEnumType.LOAD_CHANGE) {
			initLoadChange(eventObj, eventXml, this.dstabAlgo.getTotalSimuTimeSec());
		}

		double startTime = BaseDataSetter.convertTime2Sec(eventXml.getStartTime(), this.dstabNet.getFrequency());
		eventObj.setStartTimeSec(startTime);
		eventObj.setPermanent(eventXml.isPermanentFault());
		if (eventObj.isPermanent()) {
			eventObj.setDurationSec(this.dstabAlgo.getTotalSimuTimeSec());
		} else {
			double duration = BaseDataSetter.convertTime2Sec(eventXml.getDuration(), this.dstabNet.getFrequency());
			eventObj.setDurationSec(duration);
		}

		// SetPointChange has been already processed
		if (eventXml.getEventType() == DynamicEventEnumType.LOAD_CHANGE) {
			setLoadChangeData(eventObj, eventXml);
		} 
		else if (eventXml.getEventType() == DynamicEventEnumType.FAULT) {
			AcscBaseFaultXmlType faultXml = eventXml.getFault();
			if (eventXml.getFault().getFaultType() == AcscFaultTypeEnumType.BRANCH_OUTAGE) {
				AcscBranchFaultXmlType braFaultXml = (AcscBranchFaultXmlType)faultXml;
				eventObj.setType(DynamicEventType.BRANCH_OUTAGE);
				String faultBranchId = braFaultXml.getRefBranch().getBranchId();
				BranchOutageEvent bOutageEvent = DStabObjectFactory.createBranchOutageEvent(faultBranchId, dstabNet);
				bOutageEvent.setOutageType(MapBranchOutageType.f(faultXml.getFaultCategory()));
				eventObj.setBranchDynamicEvent(bOutageEvent);
			} 
			else if (eventXml.getFault().getFaultType() == AcscFaultTypeEnumType.BUS_FAULT) {
				AcscBusFaultXmlType busFaultXml = (AcscBusFaultXmlType)faultXml;
				eventObj.setType(DynamicEventType.BUS_FAULT);
				String faultBusId = busFaultXml.getRefBus().getBusId();
				AcscBusFault busFault = CoreObjectFactory.createAcscBusFault(Constants.Token_BusFaultId+faultBusId, dstabNet);
				AcscBus bus = this.dstabNet.getAcscBus(faultBusId);
				busFault.setFaultBus(bus);
				
				double baseV=bus.getBaseVoltage();
				double baseKVA= bus.getNetwork().getBaseKva();				
				AcscScenarioHelper.setBusFaultInfo(busFaultXml, busFault, baseV, baseKVA);
				
				eventObj.setBusFault(busFault);
			} 
			else if (eventXml.getFault().getFaultType() == AcscFaultTypeEnumType.BRANCH_FAULT) {
				AcscBranchFaultXmlType braFaultXml = (AcscBranchFaultXmlType)faultXml;
				eventObj.setType(DynamicEventType.BRANCH_FAULT);
				DStabBranchFault fault = createDStabBranchFault(braFaultXml);
				eventObj.setBranchFault(fault);
				if (fault.isReclosure()) {
					String name = "EventAt_" + eventXml.getStartTime()	+ eventXml.getEventType();
					DynamicEvent reclosureEvent = DStabObjectFactory.createDEvent(eventObj.getId() + "-Reclosure", name,
							DynamicEventType.BRANCH_RECLOSURE, dstabNet);
					reclosureEvent.setStartTimeSec(fault.getReclosureTime());
					reclosureEvent.setDurationSec(this.dstabAlgo.getTotalSimuTimeSec());
					reclosureEvent.setPermanent(true);
					reclosureEvent.setBranchFault(createDStabBranchFault(braFaultXml));
				}
			}
		}
	}
	
	private DStabBranchFault createDStabBranchFault(AcscBranchFaultXmlType faultXml) throws InterpssException {
		String faultBranchId = faultXml.getRefBranch().getBranchId();
		DStabBranchFault branchFault = DStabObjectFactory.createDStabBranchFault(Constants.Token_BranchFaultId + faultBranchId);
		
		AcscBranch branch = this.dstabNet.getAcscBranch(faultBranchId);
		double baseV = branch.getFromAclfBus().getBaseVoltage();
		double baseKVA= branch.getNetwork().getBaseKva();			
		AcscScenarioHelper.setBranchFaultInfo(faultXml, branchFault, baseV, baseKVA);

		branchFault.setReclosure(faultXml.isBranchReclosure());
		branchFault.setReclosureTime(BaseDataSetter.convertTime2Sec(faultXml.getReclosureTime(), this.dstabNet.getFrequency()));
		return branchFault;
	}	

	/**
	 * Transfer ODM schema dynamic event type to InterPSS dynamic event type 
	 * 
	 * @param eventTypeXml
	 * @param faultType
	 * @return
	 * @throws InterpssException
	 */
	private DynamicEventType getDynamicEventType(DynamicEventEnumType eventTypeXml,
			AcscFaultTypeEnumType faultType) throws InterpssException {
		if (eventTypeXml == DynamicEventEnumType.FAULT) {
			if (faultType == AcscFaultTypeEnumType.BUS_FAULT)
				return DynamicEventType.BUS_FAULT;
			else if (faultType == AcscFaultTypeEnumType.BRANCH_FAULT)
				return DynamicEventType.BRANCH_FAULT;
			else if (faultType == AcscFaultTypeEnumType.BRANCH_OUTAGE)
				return DynamicEventType.BRANCH_OUTAGE;

		} 
		else {
			if (eventTypeXml == DynamicEventEnumType.LOAD_CHANGE)
				return DynamicEventType.LOAD_CHANGE;
			else if (eventTypeXml == DynamicEventEnumType.SET_POINT_CHANGE)
				return DynamicEventType.SET_POINT_CHANGE;
		}
		throw new InterpssException("Programming error, eventDataType: " + eventTypeXml);
	}	

	/*
	 *   Load change functions
	 *   =====================
	 */
	private void initLoadChange(DynamicEvent eventObj, DynamicEventXmlType eventXml, double toltalSimuTime) {
		// for LoadChange
		// LowFreq and LowVolt startTime will set by system
		// FixedTime startTime = threshhold
		// always permanent
		eventObj.setPermanent(true);
		eventXml.setDuration(BaseDataSetter.createTimePeriodValue(0.0, TimePeriodUnitType.SEC));
		if (eventXml.getLoadChangeData().getLoadChangeType() == DStabLoadChangeEnumType.FIXED_TIME)
			eventXml.setStartTime(BaseDataSetter.createTimePeriodValue(eventXml.getLoadChangeData().getThreshhold(), TimePeriodUnitType.SEC));
		else
			eventXml.setStartTime(BaseDataSetter.createTimePeriodValue(toltalSimuTime, TimePeriodUnitType.SEC));
	}

	private void setLoadChangeData(DynamicEvent eventObj, DynamicEventXmlType eventXml) {
		eventObj.setType(DynamicEventType.LOAD_CHANGE);
		DStabLoadChangeXmlType ldata = eventXml.getLoadChangeData();
		String busId = ldata.getRefBus().getBusId();
		LoadChangeEvent eLoad = DStabObjectFactory.createLoadChangeEvent(busId, dstabNet);
		eLoad.setType(ldata.getLoadChangeType() == 
			DStabLoadChangeEnumType.LOW_FREQUENCY ? LoadChangeEventType.LOW_FREQUENCY
				: (ldata.getLoadChangeType() == DStabLoadChangeEnumType.LOW_VOLTAGE) ? LoadChangeEventType.LOW_VOLTAGE
								: LoadChangeEventType.FIXED_TIME);
		if (ldata.getChangeFactor() != null && ldata.getChangeFactor().getFactor() != 0.0) {
			double f = 1.0;
			if (ldata.getChangeFactor().getUnit() == FactorUnitType.PERCENT)
				f = 0.01;
			eLoad.setChangeFactor(ldata.getChangeFactor().getFactor()*f);
		}
		if (ldata.getThreshhold() != null && ldata.getThreshhold() != 0.0)
			eLoad.setThreshhold(ldata.getThreshhold());
		if (ldata.getDelayTime() != null && ldata.getDelayTime() != 0.0)
			eLoad.setDelaySec(ldata.getDelayTime());
		eventObj.setBusDynamicEvent(eLoad);
	}
	
	/*
	 *   Set point change functions
	 *   ==========================
	 */
	private void setSetPointChangeDynEvent(DStabSetPointChangeXmlType spcEventXml, DStabSimuSettingXmlType settings) throws InterpssException {
		// find the machine from the dtabNet using the machId
		ipssLogger.info("Dynamic Event Type: SetPointChange");
		String machId = spcEventXml.getRefGenBus().getBusId();
		Machine mach = this.dstabNet.getMachine(machId);
		if (mach == null)
			throw new InterpssException("Machine for Set Point Change not found");
		ipssLogger.info("SetPointChange mach id : " + mach.getId());

		// create a dynamic event of type SetPointChange. The event is added into the net during the event creation
		DynamicEvent eventObj = DStabObjectFactory.createDEvent(
				Constants.Token_SetPointChangeId + machId,
				"SetPointChange", DynamicEventType.SET_POINT_CHANGE,
				dstabNet);
		
		// set event starting and duration info
		eventObj.setStartTimeSec(0.0);
		eventObj.setDurationSec(this.dstabAlgo.getTotalSimuTimeSec());
		
		// create a SetPointChange event object and set the dynamic event object 
		SetPointChangeEvent eSetPoint = DStabObjectFactory.createSetPointChangeEvent(machId, dstabNet);
		eventObj.setBusDynamicEvent(eSetPoint);		

		eSetPoint.setControllerType(
				spcEventXml.getControllerType() == MachineControllerEnumType.EXCITER ? MachineControllerType.EXCITER
						: spcEventXml.getControllerType() == MachineControllerEnumType.GOVERNOR ? MachineControllerType.GOVERNOR
								: MachineControllerType.STABILIZER);
		eSetPoint.setChangeValue(spcEventXml.getChangeValue());
		eSetPoint.setAbusoluteChange(spcEventXml.getValueChangeType() == SetPointChangeEnumType.ABSOLUTE);
	}
	
	/*
	 *   Simulation setting functions
	 *   ============================
	 */	
	private void mapGeneralSettings(DStabSimuSettingXmlType settings){
		// map numerical iteration method
		DStabMethodEnumType method =  settings.getDstabMethod();
		if(method == DStabMethodEnumType.RUNGER_KUTTA){
			dstabAlgo.setSimuMethod(DynamicSimuMethod.RUNGE_KUTTA);
		}
		else {
			dstabAlgo.setSimuMethod(DynamicSimuMethod.MODIFIED_EULER);
		}
		
		// map total time, unit is sec
		TimePeriodXmlType tolTime = settings.getTotalTime();		
		double tolTimeInSec = BaseDataSetter.convertTime2Sec(tolTime, this.dstabNet.getFrequency());
		dstabAlgo.setTotalSimuTimeSec(tolTimeInSec);
		
		// map time step,  unit is sec
		TimePeriodXmlType stepTime = settings.getStep();		
		double stepTimeInSec = BaseDataSetter.convertTime2Sec(stepTime,this.dstabNet.getFrequency());
		dstabAlgo.setSimuStepSec(stepTimeInSec);
		
		if (settings.isAbsMachineAngle() != null) {
			if(!settings.isAbsMachineAngle()){
				String refMachId = settings.getRefMachineBus().getBusId();
				Machine mach = dstabNet.getMachine(refMachId); 
				dstabAlgo.setRefMachine(mach);			
			} 
		}
	
		//set net equn interation
		if (settings.getNetEqnSolveConfig() != null) {
			int intNoEvent = settings.getNetEqnSolveConfig().getNetEqnItrNoEvent();
			int intWEvent = settings.getNetEqnSolveConfig().getNetEqnItrWithEvent();
			if(intNoEvent != 0){
				dstabNet.setNetEqnIterationNoEvent(intNoEvent);
			}
	        if(intWEvent != 0){
			    dstabNet.setNetEqnIterationWithEvent(intWEvent);	
			}
		}
        
        StaticLoadModelXmlType statLoad = settings.getStaticLoadModel();
        if(statLoad.getStaticLoadType() == DStabStaticLoadModelEnumType.CONSTANT_Z){
        	dstabNet.setStaticLoadModel(StaticLoadModel.CONST_Z);
        }
        else {
        	// set switch vol and dead zone for constant-P static load
        	dstabNet.setStaticLoadModel(StaticLoadModel.CONST_P);
        
        	if (settings.getStaticLoadModel().getSwitchDeadZone() != null) {
        		double deadZone = settings.getStaticLoadModel().getSwitchDeadZone();
            	dstabNet.setStaticLoadSwitchDeadZone(deadZone);
        	}

        	if (settings.getStaticLoadModel().getSwitchVolt() != null)  {
            	double switchVolt = settings.getStaticLoadModel().getSwitchVolt();        	
            	dstabNet.setStaticLoadSwitchVolt(switchVolt);
        	}
        }
	}
}
