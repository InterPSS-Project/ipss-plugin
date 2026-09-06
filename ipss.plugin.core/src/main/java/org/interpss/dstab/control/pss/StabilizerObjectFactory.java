/*
 * @(#)StabilizerObjectFactory.java   
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

package org.interpss.dstab.control.pss;

import org.interpss.dstab.control.pss.bpa.sg.BPASGTypeStabilizer;
import org.interpss.dstab.control.pss.bpa.si.BPASITypeStabilizer;
import org.interpss.dstab.control.pss.bpa.sp.BPASPTypeStabilizer;
import org.interpss.dstab.control.pss.bpa.ss.BPASSTypeStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss2c.Ieee2016PSS2CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer;
import org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizerData;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer;
import org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizerData;
import org.interpss.dstab.control.pss.simple.SimpleStabilizer;

import com.interpss.dstab.mach.Machine;

/**
 * Stabilizer object factory
 * 
 * @author mzhou
 *
 */
public class StabilizerObjectFactory {
	/**
	 * factory method to create a SimpleStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static SimpleStabilizer createSimpleStabilizer(String id, String name, Machine machine) {
		SimpleStabilizer pss = new SimpleStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
  	}

	/**
	 * factory method to create a Ieee1992PSS1AStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static Ieee1992PSS1AStabilizer createIeee1992PSS1AStabilizer(String id, String name, Machine machine) {
		Ieee1992PSS1AStabilizer pss = new Ieee1992PSS1AStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
  	}

	/**
	 * factory method to create a Ieee1992PSS2AStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static Ieee1992PSS2AStabilizer createIeee1992PSS2AStabilizer(
			String id, String name, Machine machine) {
		Ieee1992PSS2AStabilizer pss = new Ieee1992PSS2AStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
	}

	/** Create an IEEE PSS2B stabilizer and attach it to its machine. */
	public static Ieee1992PSS2BStabilizer createIeee1992PSS2BStabilizer(
			String id, String name, Machine machine) {
		Ieee1992PSS2BStabilizer pss = new Ieee1992PSS2BStabilizer(
				id, name, "InterPSS");
		pss.setMachine(machine);
		return pss;
	}

	/** Create an IEEE 421.5-2016 PSS2C stabilizer and attach it to its machine. */
	public static Ieee2016PSS2CStabilizer createIeee2016PSS2CStabilizer(
			String id, String name, Machine machine) {
		Ieee2016PSS2CStabilizer pss = new Ieee2016PSS2CStabilizer(
				id, name, "InterPSS");
		pss.setMachine(machine);
		return pss;
	}

	/** Create an IEEE 421.5-2016 PSS3C stabilizer and attach it to its machine. */
	public static Ieee2016PSS3CStabilizer createIeee2016PSS3CStabilizer(
			String id, Ieee2016PSS3CStabilizerData data, Machine machine) {
		return new Ieee2016PSS3CStabilizer(id, data, machine);
	}

	/** Create an IEEE 421.5-2005 PSS3B stabilizer and attach it to its machine. */
	public static Ieee2005PSS3BStabilizer createIeee2005PSS3BStabilizer(
			String id, Ieee2005PSS3BStabilizerData data, Machine machine) {
		return new Ieee2005PSS3BStabilizer(id, data, machine);
	}

	/** Create an IEEE 421.5-2005 PSS4B stabilizer and attach it to its machine. */
	public static Ieee2005PSS4BStabilizer createIeee2005PSS4BStabilizer(
			String id, Ieee2005PSS4BStabilizerData data, Machine machine) {
		return new Ieee2005PSS4BStabilizer(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 PSS4C stabilizer and attach it to its machine. */
	public static Ieee2016PSS4CStabilizer createIeee2016PSS4CStabilizer(
			String id, Ieee2016PSS4CStabilizerData data, Machine machine) {
		return new Ieee2016PSS4CStabilizer(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 PSS5C stabilizer and attach it to its machine. */
	public static Ieee2016PSS5CStabilizer createIeee2016PSS5CStabilizer(
			String id, Ieee2016PSS5CStabilizerData data, Machine machine) {
		return new Ieee2016PSS5CStabilizer(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 PSS6C stabilizer and attach it to its machine. */
	public static Ieee2016PSS6CStabilizer createIeee2016PSS6CStabilizer(
			String id, Ieee2016PSS6CStabilizerData data, Machine machine) {
		return new Ieee2016PSS6CStabilizer(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 PSS7C stabilizer and attach it to its machine. */
	public static Ieee2016PSS7CStabilizer createIeee2016PSS7CStabilizer(
			String id, Ieee2016PSS7CStabilizerData data, Machine machine) {
		return new Ieee2016PSS7CStabilizer(id, data, machine);
	}

	/**
	 * factory method to create a BPASITypeStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static BPASITypeStabilizer createBpaSITypeStabilizer(String id, String name, Machine machine) {
		BPASITypeStabilizer pss = new BPASITypeStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
  	}

	/**
	 * factory method to create a BPASSTypeStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static BPASSTypeStabilizer createBpaSsTypeStabilizer(String id, String name, Machine machine) {
		BPASSTypeStabilizer pss = new BPASSTypeStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
  	}

	/**
	 * factory method to create a BPASPTypeStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static BPASPTypeStabilizer createBpaSpTypeStabilizer(String id, String name, Machine machine) {
		BPASPTypeStabilizer pss = new BPASPTypeStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
  	}

	/**
	 * factory method to create a BPASGTypeStabilizer object
	 * 
	 * @param id stabilizer id
	 * @param name stabilizer name
	 * @param machine parent machine object
	 * @return
	 */		
	public static BPASGTypeStabilizer createBpaSgTypeStabilizer(String id, String name, Machine machine) {
		BPASGTypeStabilizer pss = new BPASGTypeStabilizer(id, name, "InterPSS");
		pss.setMachine(machine); 
		return pss;
  	}
}
