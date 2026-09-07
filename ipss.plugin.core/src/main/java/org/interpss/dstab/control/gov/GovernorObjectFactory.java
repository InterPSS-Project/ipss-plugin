/*
 * @(#)GovernorObjectFactory.java   
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

package org.interpss.dstab.control.gov;

import org.interpss.dstab.control.gov.bpa.giGaTbcombinedType.BpaGIGATBCombinedGovernor;
import org.interpss.dstab.control.gov.bpa.gsTb.BpaGsTbCombineGovernor;
import org.interpss.dstab.control.gov.bpa.hydro.BpaGHTypeHydroGovernor;
import org.interpss.dstab.control.gov.ieee.hydro1981Type2.Ieee1981Type2HydroGovernor;
import org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor;
import org.interpss.dstab.control.gov.ieee.ieeeST1.IeeeST1Governor;
import org.interpss.dstab.control.gov.ieee.ieeeST2.IeeeST2Governor;
import org.interpss.dstab.control.gov.ieee.steamNR.IeeeSteamNRGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor;
import org.interpss.dstab.control.gov.ieee.steamTCSR.IeeeSteamTCSRGovernor;
import org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor;
import org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernor;
import org.interpss.dstab.control.gov.psse.degov1.PsseDegov1dGovernor;
import org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor;
import org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernor;
import org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor;
import org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor;
import org.interpss.dstab.control.gov.psse.hygov2.PsseHygov2dGovernor;
import org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernor;
import org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor;
import org.interpss.dstab.control.gov.psse.pidgov.PssePidgovdGovernor;
import org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor;
import org.interpss.dstab.control.gov.psse.tgov3.PsseTgov3dGovernor;
import org.interpss.dstab.control.gov.psse.wesgov.PsseWesgovdGovernor;
import org.interpss.dstab.control.gov.psse.wpidhy.PsseWpidhydGovernor;
import org.interpss.dstab.control.gov.simple.SimpleGovernor;

import com.interpss.dstab.mach.Machine;

/**
 * Governor object factory
 * 
 * @author mzhou
 *
 */
public class GovernorObjectFactory {
	/**
	 * factory method to create a SimpleGovernor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static SimpleGovernor createSimpleGovernor(String id, String name, Machine machine) {
		SimpleGovernor gov = new SimpleGovernor(id, name, "InterPSS");
		gov.setMachine(machine);
		return gov;
	}

	/**
	 * factory method to create an Ieee Hydro Turbine Governor
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static Ieee1981Type2HydroGovernor createIeeeHTurbineGovernor(String id, String name, Machine machine) {
		Ieee1981Type2HydroGovernor gov = new Ieee1981Type2HydroGovernor(id, name, "InterPSS");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create a IeeeST1Governor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static IeeeST1Governor createIeeeST1Governor(String id, String name, Machine machine) {
		IeeeST1Governor gov = new IeeeST1Governor(id, name, "InterPSS");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create a IeeeST2Governor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static IeeeST2Governor createIeeeST2Governor(String id, String name, Machine machine) {
		IeeeST2Governor gov = new IeeeST2Governor(id, name, "InterPSS");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create an Ieee Steam Non-reheat turbine Governor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static IeeeSteamNRGovernor createIeeeSteamNRGovernor(String id, String name, Machine machine) {
		IeeeSteamNRGovernor gov = new IeeeSteamNRGovernor(id, name, "InterPSS");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create an Ieee Steam single-reheat turbine Governor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static IeeeSteamTCSRGovernor createIeeeSteamTCSRGovernor(String id, String name, Machine machine) {
		IeeeSteamTCSRGovernor gov = new IeeeSteamTCSRGovernor(id, name, "InterPSS");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create an Ieee Steam double-reheat turbine Governor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static IeeeSteamTCDRGovernor createIeeeSteamTCDRGovernor(String id, String name, Machine machine) {
		IeeeSteamTCDRGovernor gov = new IeeeSteamTCDRGovernor(id, name, "InterPSS");
		gov.setMachine(machine); 
		return gov;
  	}
	 /**
	  * IEEE Type 2 speed-governing model for hybro generator
	  * corresponding to the IEEEG2 model in PSS/E
	  * @param id
	  * @param name
	  * @param machine
	  * @return
	  */
	 public static Ieee1981Type2HydroGovernor createIeee1981Type2HydroGovernor(String id, String name, Machine machine) {
		 Ieee1981Type2HydroGovernor gov = new Ieee1981Type2HydroGovernor(id, name, "InterPSS");
			gov.setMachine(machine); 
			return gov;
	  }
	 
	 /**
	  * IEEE Type 3 speed-governing model for hybro generator
	  * corresponding to the IEEEG3 model in PSS/E
	  * @param id
	  * @param name
	  * @param machine
	  * @return
	  */
	 public static Ieee1981Type3HydroGovernor createIeee1981Type3HydroGovernor(String id, String name, Machine machine) {
		 Ieee1981Type3HydroGovernor gov = new Ieee1981Type3HydroGovernor(id, name, "PSS/E");
		 gov.setMachine(machine);
		 return gov;
	 }
	 
    //////////////////////////////////////////////////////////////
	///      PSS/E Tur-Gov model
	/////////////////////////////////////////////////////////////
	
	 /**
	  * PSS/E GSAT Type gas turbine-governor model
	  * @param id
	  * @param name
	  * @param machine
	  * @return
	  */
	public static PsseGASTGasTurGovernor createPsseGASTGasTurGovernor(String id, String name, Machine machine) {
		PsseGASTGasTurGovernor gov = new PsseGASTGasTurGovernor(id, name, "PSS/E");
		gov.setMachine(machine); 
		return gov;
  	}
	
	public static PsseTGov1SteamTurGovernor createPsseTGOV1SteamTurGovernor(String id, String name, Machine machine) {
		PsseTGov1SteamTurGovernor gov = new PsseTGov1SteamTurGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseGastwddGovernor createPsseGASTWDDGovernor(String id, String name, Machine machine) {
		PsseGastwddGovernor gov = new PsseGastwddGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseGgov1Governor createPsseGGOV1Governor(String id, String name, Machine machine) {
		PsseGgov1Governor gov = new PsseGgov1Governor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseH6eGovernor createPsseH6EGovernor(String id, String name, Machine machine) {
		PsseH6eGovernor gov = new PsseH6eGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseHygovGovernor createPsseHYGOVGovernor(String id, String name, Machine machine) {
		PsseHygovGovernor gov = new PsseHygovGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseHygovrGovernor createPsseHYGOVR1Governor(String id, String name, Machine machine) {
		PsseHygovrGovernor gov = new PsseHygovrGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseHyg3Governor createPsseHYG3Governor(String id, String name, Machine machine) {
		PsseHyg3Governor gov = new PsseHyg3Governor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseWesgovdGovernor createPsseWESGOVDGovernor(
			String id, String name, Machine machine) {
		PsseWesgovdGovernor gov = new PsseWesgovdGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseDegov1dGovernor createPsseDEGOV1DGovernor(
			String id, String name, Machine machine) {
		PsseDegov1dGovernor gov = new PsseDegov1dGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PssePidgovdGovernor createPssePIDGOVDGovernor(
			String id, String name, Machine machine) {
		PssePidgovdGovernor gov = new PssePidgovdGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseTgov3dGovernor createPsseTGOV3DGovernor(
			String id, String name, Machine machine) {
		PsseTgov3dGovernor gov = new PsseTgov3dGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseHygov2dGovernor createPsseHYGOV2DGovernor(
			String id, String name, Machine machine) {
		PsseHygov2dGovernor gov = new PsseHygov2dGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}

	public static PsseWpidhydGovernor createPsseWPIDHYDGovernor(
			String id, String name, Machine machine) {
		PsseWpidhydGovernor gov = new PsseWpidhydGovernor(id, name, "PSS/E");
		gov.setMachine(machine);
		return gov;
	}
	
	public static PsseIEESGOSteamTurGovernor createPsseIEESGOSteamTurGovernor(String id, String name, Machine machine) {
		PsseIEESGOSteamTurGovernor gov = new PsseIEESGOSteamTurGovernor(id, name, "PSS/E");
		gov.setMachine(machine); 
		return gov;
  	}
	
	//////////////////////////////////////////////////////////////
	///      PSD-BPA Tur-Gov model
	/////////////////////////////////////////////////////////////
	/**
	 * factory method to create a BpaGHTypeHydroGovernor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static BpaGHTypeHydroGovernor createBPAGHTypeGovernor(String id, String name, Machine machine) {
		BpaGHTypeHydroGovernor gov = new BpaGHTypeHydroGovernor(id, name, "BPA");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create a BpaGsTbCombineGovernor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static BpaGsTbCombineGovernor createBPAGsTbCombineGovernor(String id, String name, Machine machine) {
		BpaGsTbCombineGovernor gov = new BpaGsTbCombineGovernor(id, name, "BPA");
		gov.setMachine(machine); 
		return gov;
  	}

	/**
	 * factory method to create a BpaGIGATBCombinedGovernor object
	 * 
	 * @param id governor id
	 * @param name governor name
	 * @param machine parent machine object
	 * @return
	 */	
	public static BpaGIGATBCombinedGovernor createBpaGIGATBCombinedGovernor(String id, String name, Machine machine) {
		BpaGIGATBCombinedGovernor gov = new BpaGIGATBCombinedGovernor(id, name, "BPA");
		gov.setMachine(machine); 
		return gov;
  	}
}
