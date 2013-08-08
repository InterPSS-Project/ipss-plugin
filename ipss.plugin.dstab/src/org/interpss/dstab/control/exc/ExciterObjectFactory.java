/*
 * @(#)ExciterObjectFactory.java   
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

package org.interpss.dstab.control.exc;

import org.interpss.dstab.control.exc.bpa.ea.BpaEaTypeExciter;
import org.interpss.dstab.control.exc.bpa.ec.BpaEcTypeExciter;
import org.interpss.dstab.control.exc.bpa.ek.BpaEkTypeExciter;
import org.interpss.dstab.control.exc.bpa.fa.BpaFaTypeExciter;
import org.interpss.dstab.control.exc.bpa.fj.BpaFjTypeExciter;
import org.interpss.dstab.control.exc.bpa.fk.BpaFkTypeExciter;
import org.interpss.dstab.control.exc.bpa.fvkv0.FVkv0Exciter;
import org.interpss.dstab.control.exc.bpa.fvkv1.FVkv1Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type1s.Ieee1968Type1sExciter;
import org.interpss.dstab.control.exc.ieee.y1968.type2.Ieee1968Type2Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type3.Ieee1968Type3Exciter;
import org.interpss.dstab.control.exc.ieee.y1968.type4.Ieee1968Type4Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.simple.SimpleExciter;

import com.interpss.dstab.mach.Machine;

/**
 * Exciter object factory
 * 
 * @author mzhou
 *
 */
public class ExciterObjectFactory {
	/**
	 * factory method to create a SimpleExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static SimpleExciter createSimpleExciter(String id, String name, Machine machine) {
		SimpleExciter exc = new SimpleExciter(id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}

	/*
	 * IEEE 1968 Exciter set
	 */
	
	/**
	 * factory method to create a Ieee1968Type1Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static Ieee1968Type1Exciter createIeee1968Type1Exciter(String id, String name, Machine machine) {
		Ieee1968Type1Exciter exc = new Ieee1968Type1Exciter(id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a Ieee1968Type1sExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static Ieee1968Type1sExciter createIeee1968Type1sExciter(String id, String name, Machine machine) {
		Ieee1968Type1sExciter exc = new Ieee1968Type1sExciter(id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a Ieee1968Type2Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static Ieee1968Type2Exciter createIeee1968Type2Exciter(String id, String name, Machine machine) {
		Ieee1968Type2Exciter exc = new Ieee1968Type2Exciter(id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a Ieee1968Type3Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static Ieee1968Type3Exciter createIeee1968Type3Exciter(String id, String name, Machine machine) {
		Ieee1968Type3Exciter exc = new Ieee1968Type3Exciter(id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a Ieee1968Type4Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static Ieee1968Type4Exciter createIeee1968Type4Exciter(String id, String name, Machine machine) {
		Ieee1968Type4Exciter exc = new Ieee1968Type4Exciter(id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/*
	 * IEEE 1981 Exciter set
	 */
	
	/**
	 * factory method to create a IEEE1981DC1Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static IEEE1981DC1Exciter createIeee1981DC1Exciter(String id, String name, Machine machine) {
		IEEE1981DC1Exciter  exc = new IEEE1981DC1Exciter (id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a IEEE1981ST1Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static IEEE1981ST1Exciter createIeee1981ST1Exciter(String id, String name, Machine machine) {
		IEEE1981ST1Exciter exc = new IEEE1981ST1Exciter (id, name, "InterPSS");
		exc.setMachine(machine); 
		return exc;
  	}
	
	
	/*
	 * IEEE 1992 Exciter set
	 */
	
	
	/*
	 * BPA Exciter set
	 */
	/**
	 * factory method to create a BpaEaTypeExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  BpaEaTypeExciter createBPAEATypeExciter(String id, String name, Machine machine) {
		BpaEaTypeExciter exc = new BpaEaTypeExciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}

	/**
	 * factory method to create a BpaEcTypeExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  BpaEcTypeExciter createBPAEcTypeExciter(String id, String name, Machine machine) {
		BpaEcTypeExciter exc = new BpaEcTypeExciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}

	/**
	 * factory method to create a BpaEkTypeExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  BpaEkTypeExciter createBPAEKExciter(String id, String name, Machine machine) {
		BpaEkTypeExciter exc = new BpaEkTypeExciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}

	/**
	 * factory method to create a BpaFaTypeExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  BpaFaTypeExciter createBPAFATypeExciter(String id, String name, Machine machine) {
		BpaFaTypeExciter exc = new BpaFaTypeExciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}

	/**
	 * factory method to create a SimpleExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  BpaFjTypeExciter createBPAFJTypeExciter(String id, String name, Machine machine) {
		BpaFjTypeExciter exc = new BpaFjTypeExciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}

	/**
	 * factory method to create a BpaFkTypeExciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  BpaFkTypeExciter createBPAFKTypeExciter(String id, String name, Machine machine) {
		BpaFkTypeExciter exc = new BpaFkTypeExciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}

	/**
	 * factory method to create a FVkv0Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  FVkv0Exciter createBPAFVKv0TypeExciter(String id, String name, Machine machine) {
		FVkv0Exciter exc = new FVkv0Exciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a FVkv1Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static  FVkv1Exciter createBPAFVKv1TypeExciter(String id, String name, Machine machine) {
		FVkv1Exciter exc = new FVkv1Exciter(id, name, "BPA");
		exc.setMachine(machine); 
		return exc;
  	}
}
