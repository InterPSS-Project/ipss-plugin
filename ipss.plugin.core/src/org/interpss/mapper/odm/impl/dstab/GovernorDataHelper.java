/*
 * @(#)GovernorDataHelper.java   
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
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 08/15/2010
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.mapper.odm.impl.dstab;

import org.ieee.odm.schema.GovBPAGsTbCombinedModelXmlType;
import org.ieee.odm.schema.GovBPAHydroTurbineGHXmlType;
import org.ieee.odm.schema.GovHydroTurbineXmlType;
import org.ieee.odm.schema.GovSimpleTypeXmlType;
import org.ieee.odm.schema.GovSteamNRXmlType;
import org.ieee.odm.schema.GovSteamTCDRXmlType;
import org.ieee.odm.schema.GovSteamTCSRXmlType;
import org.ieee.odm.schema.GovernorModelXmlType;
import org.ieee.odm.schema.SpeedGovBPAGSModelXmlType;
import org.ieee.odm.schema.SteamTurbineBPATBModelXmlType;
import org.interpss.dstab.control.gov.GovernorObjectFactory;
import org.interpss.dstab.control.gov.bpa.gsTb.BpaGsTbCombineGovernor;
import org.interpss.dstab.control.gov.bpa.hydro.BpaGHTypeHydroGovernor;
import org.interpss.dstab.control.gov.ieee.hturbine.IeeeHTurbineGovernor;
import org.interpss.dstab.control.gov.ieee.steamNR.IeeeSteamNRGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCSR.IeeeSteamTCSRGovernor;
import org.interpss.dstab.control.gov.ieee.steamTDSR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.Machine;

/**
 * Class for map ODM governor xml document to InterPSS governor model 
 * 
 * @author mzhou
 *
 */
public class GovernorDataHelper {
	private Machine mach = null;

	/**
	 * constructor
	 * 
	 * @param mach
	 */
	public GovernorDataHelper(Machine mach) {
		this.mach = mach;
	}
	
	/**
	 * create the gov model and add to its parent machine object
	 * 
	 * @param govXmlRec ODM governor model record
	 */
	public void createGovernor(GovernorModelXmlType govXmlRec) throws InterpssException  {
		if (govXmlRec == null) { throw new InterpssException("Programming error in createGovernor()"); }

		// we need to put the if statements in the reverse order of the inheritance hierarchy 
		
		////////////////////////////////////////////
		////    BPA           //////////////////////
		////////////////////////////////////////////
		
		else if (govXmlRec instanceof GovBPAHydroTurbineGHXmlType) {
			GovBPAHydroTurbineGHXmlType govXml = (GovBPAHydroTurbineGHXmlType)govXmlRec;
			BpaGHTypeHydroGovernor gov = GovernorObjectFactory.createBPAGHTypeGovernor(mach.getId()+"_Gov", govXml.getName(), mach);						
			gov.getData().setPmax(govXml.getPMAX());
			
			gov.getData().setR(govXml.getR());
			gov.getData().setTg(govXml.getTG().getValue());
			gov.getData().setTp(govXml.getTP().getValue());
			gov.getData().setTd(govXml.getTd().getValue());			
			gov.getData().setTw(govXml.getTwHalf().getValue()*2);
			gov.getData().setVelClose(govXml.getVClose());
			gov.getData().setVelOpen(govXml.getVOpen());
			gov.getData().setDelta(govXml.getDd());
			gov.getData().setEpsilon(govXml.getEpsilon());
		}
		else if (govXmlRec instanceof GovBPAGsTbCombinedModelXmlType) {
			GovBPAGsTbCombinedModelXmlType govXml = (GovBPAGsTbCombinedModelXmlType)govXmlRec;
			BpaGsTbCombineGovernor gov = GovernorObjectFactory.createBPAGsTbCombineGovernor(mach.getId()+"_Gov", govXml.getName(), mach);						
			SpeedGovBPAGSModelXmlType spdGov =(SpeedGovBPAGSModelXmlType) govXml.getSpeedGov().getValue();
			SteamTurbineBPATBModelXmlType tb=(SteamTurbineBPATBModelXmlType) govXml.getTurbine().getValue();
			gov.getData().getGsData().setPmax(spdGov.getPmax());
			gov.getData().getGsData().setPmin(spdGov.getPmin());
			gov.getData().getGsData().setR(spdGov.getR());
			gov.getData().getGsData().setT1(spdGov.getT1().getValue());
			gov.getData().getGsData().setT2(spdGov.getT2().getValue());
			gov.getData().getGsData().setT3(spdGov.getT3().getValue());
			gov.getData().getGsData().setVelClose(spdGov.getVELCLOSE());
			gov.getData().getGsData().setVelOpen(spdGov.getVELOPEN());
			gov.getData().getGsData().setEpsilon(spdGov.getEpsilon());
			gov.getData().getTbData().setFhp(tb.getFHP());
			gov.getData().getTbData().setFip(tb.getFIP());
			gov.getData().getTbData().setFlp(tb.getFLP());
			gov.getData().getTbData().setTch(tb.getTCH().getValue());
			gov.getData().getTbData().setTrh(tb.getTRH().getValue());
			gov.getData().getTbData().setTco(tb.getTCO().getValue());
			gov.getData().getTbData().setLambda(tb.getLambda());
		}
		
		////////////////////////////////////////////
		////    IEEE          //////////////////////
		////////////////////////////////////////////
		
		else if (govXmlRec instanceof GovSteamNRXmlType) {
			GovSteamNRXmlType govXml = (GovSteamNRXmlType)govXmlRec;
			IeeeSteamNRGovernor gov = GovernorObjectFactory.createIeeeSteamNRGovernor(mach.getId()+"_Gov", govXml.getName(), mach);
			gov.getData().setK(govXml.getK());
			gov.getData().setT1(govXml.getT1().getValue());
			gov.getData().setT2(govXml.getT2().getValue());
			gov.getData().setT3(govXml.getT3().getValue());
			gov.getData().setPmax(govXml.getPMAX());
			gov.getData().setPmin(govXml.getPMIN());
			gov.getData().setPup(govXml.getPup());
			gov.getData().setPdown(govXml.getPdown());
			gov.getData().setTch(govXml.getTCH().getValue());
		}
		else if (govXmlRec instanceof GovSteamTCSRXmlType) {
			GovSteamTCSRXmlType govXml = (GovSteamTCSRXmlType)govXmlRec;
			IeeeSteamTCSRGovernor gov = GovernorObjectFactory.createIeeeSteamTCSRGovernor(mach.getId()+"_Gov", govXml.getName(), mach);
			gov.getData().setK(govXml.getK());
			gov.getData().setT1(govXml.getT1().getValue());
			gov.getData().setT2(govXml.getT2().getValue());
			gov.getData().setT3(govXml.getT3().getValue());
			gov.getData().setPmax(govXml.getPMAX());
			gov.getData().setPmin(govXml.getPMIN());
			gov.getData().setPup(govXml.getPup());
			gov.getData().setPdown(govXml.getPdown());
			gov.getData().setTch(govXml.getTCH().getValue());
			gov.getData().setTrh(govXml.getTRH().getValue());
			gov.getData().setTco(govXml.getTCO().getValue());
			gov.getData().setFhp(govXml.getFHP());
			gov.getData().setFip(govXml.getFIP());
			gov.getData().setFlp(govXml.getFLP());
		}
		else if (govXmlRec instanceof GovSteamTCDRXmlType) {
			GovSteamTCDRXmlType govXml = (GovSteamTCDRXmlType)govXmlRec;
			IeeeSteamTCDRGovernor gov = GovernorObjectFactory.createIeeeSteamTCDRGovernor(mach.getId()+"_Gov", govXml.getName(), mach);
			gov.getData().setK(govXml.getK());
			gov.getData().setT1(govXml.getT1().getValue());
			gov.getData().setT2(govXml.getT2().getValue());
			gov.getData().setT3(govXml.getT3().getValue());
			gov.getData().setPmax(govXml.getPMAX());
			gov.getData().setPmin(govXml.getPMIN());
			gov.getData().setPup(govXml.getPup());
			gov.getData().setPdown(govXml.getPdown());
			gov.getData().setTch(govXml.getTCH().getValue());
			gov.getData().setTrh1(govXml.getTRH1().getValue());
			gov.getData().setTrh2(govXml.getTRH2().getValue());			
			gov.getData().setTco(govXml.getTCO().getValue());
			gov.getData().setFhp(govXml.getFHP());
			gov.getData().setFip(govXml.getFIP());
			gov.getData().setFlp(govXml.getFLP());
		}
		else if (govXmlRec instanceof GovHydroTurbineXmlType) {
			GovHydroTurbineXmlType govXml = (GovHydroTurbineXmlType)govXmlRec;
			IeeeHTurbineGovernor gov = GovernorObjectFactory.createIeeeHTurbineGovernor(mach.getId()+"_Gov", govXml.getName(), mach);						
			gov.getData().setK(govXml.getK());
			gov.getData().setT1(govXml.getT1().getValue());
			gov.getData().setT2(govXml.getT2().getValue());
			gov.getData().setT3(govXml.getT3().getValue());
			gov.getData().setPmax(govXml.getPMAX());
			gov.getData().setPmin(govXml.getPMIN());			
			gov.getData().setTw(govXml.getTWhalf().getValue());			
		}
		
		else if (govXmlRec instanceof GovSimpleTypeXmlType) {
			GovSimpleTypeXmlType govXml = (GovSimpleTypeXmlType)govXmlRec;
			SimpleGovernor gov = GovernorObjectFactory.createSimpleGovernor(mach.getId()+"_Gov", govXml.getName(), mach);
			gov.getData().setK(govXml.getK());
			gov.getData().setT1(govXml.getT1().getValue());
			gov.getData().setPmax(govXml.getPmax());
			gov.getData().setPmin(govXml.getPmin());
		}

		else {
			throw new InterpssException("Governor type invalid or not implemented, type " + govXmlRec.getClass().getSimpleName());
		}
	}
}