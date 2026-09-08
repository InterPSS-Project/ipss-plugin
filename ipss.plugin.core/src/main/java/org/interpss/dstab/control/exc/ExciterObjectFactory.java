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
import org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Data;
import org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.ac1.IEEE1981AC1Exciter;
import org.interpss.dstab.control.exc.ieee.y1981.dc1.IEEE1981DC1Exciter;
import org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter;
import org.interpss.dstab.control.exc.psse.exdc2.Exdc2Exciter;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysData;
import org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter;
import org.interpss.dstab.control.exc.psse.esac6a.Esac6aData;
import org.interpss.dstab.control.exc.psse.esac6a.Esac6aExciter;
import org.interpss.dstab.control.exc.psse.exdc2a.Exdc2aExciter;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bData;
import org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bData;
import org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter;
import org.interpss.dstab.control.exc.psse.ac8c.Ac8cData;
import org.interpss.dstab.control.exc.psse.ac8c.Ac8cExciter;
import org.interpss.dstab.control.exc.psse.ac9c.Ac9cData;
import org.interpss.dstab.control.exc.psse.ac9c.Ac9cExciter;
import org.interpss.dstab.control.exc.psse.ac11c.Ac11cData;
import org.interpss.dstab.control.exc.psse.ac11c.Ac11cExciter;
import org.interpss.dstab.control.exc.psse.bbsex1.Bbsex1Data;
import org.interpss.dstab.control.exc.psse.bbsex1.Bbsex1Exciter;
import org.interpss.dstab.control.exc.psse.esac4a.Esac4aData;
import org.interpss.dstab.control.exc.psse.esac4a.Esac4aExciter;
import org.interpss.dstab.control.exc.psse.exac4.Exac4Data;
import org.interpss.dstab.control.exc.psse.exac4.Exac4Exciter;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bData;
import org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter;
import org.interpss.dstab.control.exc.psse.dc4c.Dc4cData;
import org.interpss.dstab.control.exc.psse.dc4c.Dc4cExciter;
import org.interpss.dstab.control.exc.psse.st1c.St1cData;
import org.interpss.dstab.control.exc.psse.st1c.St1cExciter;
import org.interpss.dstab.control.exc.psse.st2c.St2cData;
import org.interpss.dstab.control.exc.psse.st2c.St2cExciter;
import org.interpss.dstab.control.exc.psse.st3c.St3cData;
import org.interpss.dstab.control.exc.psse.st3c.St3cExciter;
import org.interpss.dstab.control.exc.psse.st4c.St4cData;
import org.interpss.dstab.control.exc.psse.st4c.St4cExciter;
import org.interpss.dstab.control.exc.psse.st5c.St5cData;
import org.interpss.dstab.control.exc.psse.st5c.St5cExciter;
import org.interpss.dstab.control.exc.psse.dc3a.Dc3aData;
import org.interpss.dstab.control.exc.psse.dc3a.Dc3aExciter;
import org.interpss.dstab.control.exc.psse.st6b.St6bData;
import org.interpss.dstab.control.exc.psse.st6b.St6bExciter;
import org.interpss.dstab.control.exc.psse.st6c.St6cData;
import org.interpss.dstab.control.exc.psse.st6c.St6cExciter;
import org.interpss.dstab.control.exc.psse.exeli.ExeliData;
import org.interpss.dstab.control.exc.psse.exeli.ExeliExciter;
import org.interpss.dstab.control.exc.psse.st7b.St7bData;
import org.interpss.dstab.control.exc.psse.st7b.St7bExciter;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aData;
import org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Data;
import org.interpss.dstab.control.exc.psse.exst2.Exst2Exciter;
import org.interpss.dstab.control.exc.psse.st5b.St5bData;
import org.interpss.dstab.control.exc.psse.st5b.St5bExciter;
import org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter;
import org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter;
import org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter;
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
	 * factory method to create a IEEE1981AC1Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static IEEE1981AC1Exciter createIeee1981AC1Exciter(String id, String name, Machine machine) {
		IEEE1981AC1Exciter exc = new IEEE1981AC1Exciter (id, name, "IEEE");
		exc.setMachine(machine); 
		return exc;
  	}
	/**
	 * factory method to create a IEEE1981DC1Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static IEEE1981DC1Exciter createIeee1981DC1Exciter(String id, String name, Machine machine) {
		IEEE1981DC1Exciter  exc = new IEEE1981DC1Exciter (id, name, "IEEE");
		exc.setMachine(machine); 
		return exc;
	}

	/** Create a PSS/E IEEET4 / WECC EXDC4 exciter. */
	public static Ieeet4Exciter createIeeet4Exciter(String id, String modelName,
			Ieeet4Data data, Machine machine) {
		return new Ieeet4Exciter(id, modelName, data, machine);
	}

	/** Create a PSS/E IEEEX1 exciter while retaining IEEE DC1 compatibility. */
	public static Ieeex1Exciter createIeeex1Exciter(String id, Machine machine) {
		return new Ieeex1Exciter(id, machine);
	}

	/** Create a PSS/E EXDC2 / PowerWorld EXDC2_PTI exciter. */
	public static Exdc2Exciter createExdc2Exciter(String id, Machine machine) {
		return new Exdc2Exciter(id, machine);
	}

	/** Create a PSLF/PowerWorld EXDC2A exciter with the additional Tf2 lag. */
	public static Exdc2aExciter createExdc2aExciter(String id, Machine machine) {
		return new Exdc2aExciter(id, machine);
	}

	/** Create an IEEE 421.5-2005 / PSS/E AC8B rotating exciter. */
	public static Ac8bExciter createAc8bExciter(String id, Ac8bData data, Machine machine) {
		return new Ac8bExciter(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 / PSS/E AC8C controlled-rectifier exciter. */
	public static Ac8cExciter createAc8cExciter(String id, Ac8cData data, Machine machine) {
		return new Ac8cExciter(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 / PSS/E AC9C cascaded regulator exciter. */
	public static Ac9cExciter createAc9cExciter(String id, Ac9cData data, Machine machine) {
		return new Ac9cExciter(id, data, machine);
	}

	/** Create an IEEE 421.5-2016 / PSS/E AC11C brushless exciter. */
	public static Ac11cExciter createAc11cExciter(String id, Ac11cData data, Machine machine) {
		return new Ac11cExciter(id, data, machine);
	}

	/** Create a native PSS/E BBSEX1 transformer-fed static exciter. */
	public static Bbsex1Exciter createBbsex1Exciter(String id, Bbsex1Data data, Machine machine) {
		return new Bbsex1Exciter(id, data, machine);
	}

	/** Create a PSS/E ESAC4A (IEEE Type AC4A) excitation system. */
	public static Esac4aExciter createEsac4aExciter(String id, Esac4aData data,
			Machine machine) {
		return new Esac4aExciter(id, data, machine);
	}

	/** Create a PSS/E EXAC4 (IEEE Type AC4) excitation system. */
	public static Exac4Exciter createExac4Exciter(String id, Exac4Data data,
			Machine machine) {
		return new Exac4Exciter(id, data, machine);
	}

	/** Create an IEEE 421.5 DC4B or PSS/E ESDC4B excitation system. */
	public static Dc4bExciter createDc4bExciter(String id, String modelName,
			Dc4bData data, Machine machine) {
		return new Dc4bExciter(id, modelName, data, machine);
	}

	/** Create an IEEE 421.5-2016 / native PSS/E DC4C excitation system. */
	public static Dc4cExciter createDc4cExciter(String id, Dc4cData data, Machine machine) {
		return new Dc4cExciter(id, data, machine);
	}

	/** Create an IEEE DC3A or PSLF ESDC3A excitation system. */
	public static Dc3aExciter createDc3aExciter(String id, String modelName,
			Dc3aData data, Machine machine) {
		return new Dc3aExciter(id, modelName, data, machine);
	}

	/** Create an IEEE ST6B or PSLF ESST6B excitation system. */
	public static St6bExciter createSt6bExciter(String id,String modelName,
			St6bData data,Machine machine){return new St6bExciter(id,modelName,data,machine);}

	/** Create a native PSS/E IEEE 421.5-2016 ST6C excitation system. */
	public static St6cExciter createSt6cExciter(String id,St6cData data,Machine machine){
		return new St6cExciter(id,data,machine);
	}

	/** Create a native PSS/E EXELI excitation system. */
	public static ExeliExciter createExeliExciter(String id, ExeliData data, Machine machine) {
		return new ExeliExciter(id, data, machine);
	}

	/** Create a native PSS/E IEEE 421.5-2016 ST1C excitation system. */
	public static St1cExciter createSt1cExciter(String id, St1cData data, Machine machine) {
		return new St1cExciter(id, data, machine);
	}

	/** Create a native PSS/E IEEE 421.5-2016 ST2C excitation system. */
	public static St2cExciter createSt2cExciter(String id, St2cData data, Machine machine) {
		return new St2cExciter(id, data, machine);
	}

	/** Create a native PSS/E IEEE 421.5-2016 ST3C excitation system. */
	public static St3cExciter createSt3cExciter(String id, St3cData data, Machine machine) {
		return new St3cExciter(id, data, machine);
	}

	/** Create a native PSS/E IEEE 421.5-2016 ST4C excitation system. */
	public static St4cExciter createSt4cExciter(String id, St4cData data, Machine machine) {
		return new St4cExciter(id, data, machine);
	}

	/** Create an IEEE ST7B or PSLF ESST7B excitation system. */
	public static St7bExciter createSt7bExciter(String id,String modelName,
			St7bData data,Machine machine){return new St7bExciter(id,modelName,data,machine);}

	/** Create an IEEE 421.5-2005 / PSS/E ESST2A excitation system. */
	public static Esst2aExciter createEsst2aExciter(String id, Esst2aData data,
			Machine machine) {
		return new Esst2aExciter(id, data, machine);
	}

	/** Create a PSS/E EXST2 additive compound-source excitation system. */
	public static Exst2Exciter createExst2Exciter(String id, Exst2Data data,
			Machine machine) {
		return new Exst2Exciter(id, data, machine);
	}

	/** Create an IEEE ST5B or PSLF ESST5B excitation system. */
	public static St5bExciter createSt5bExciter(String id, String modelName,
			St5bData data, Machine machine) {
		return new St5bExciter(id, modelName, data, machine);
	}

	/** Create a native PSS/E IEEE 421.5-2016 ST5C excitation system. */
	public static St5cExciter createSt5cExciter(String id, St5cData data,
			Machine machine) {
		return new St5cExciter(id, data, machine);
	}

	/** Create an IEEE 421.5-2005 AC7B or PSS/E ESAC7B rotating exciter. */
	public static Ac7bExciter createAc7bExciter(String id, String modelName,
			Ac7bData data, Machine machine) {
		return new Ac7bExciter(id, modelName, data, machine);
	}

	/** Create a PSS/E REXSYS general-purpose rotating exciter. */
	public static RexsysExciter createRexsysExciter(String id, RexsysData data, Machine machine) {
		return new RexsysExciter(id, data, machine);
	}

	/** Create a PSS/E ESAC6A / IEEE AC6A rotating exciter. */
	public static Esac6aExciter createEsac6aExciter(String id, Esac6aData data, Machine machine) {
		return new Esac6aExciter(id, data, machine);
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
		IEEE1981ST1Exciter exc = new IEEE1981ST1Exciter (id, name, "IEEE");
		exc.setMachine(machine); 
		return exc;
  	}
	
	
	/*
	 * IEEE 1992/2005 Exciter set
	 */
	/**
	 * factory method to create a IEEE2005ST3A Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static IEEE2005ST3AExciter createIeee2005ST3AExciter(String id, String name, Machine machine) {
		IEEE2005ST3AExciter exc = new IEEE2005ST3AExciter (id, name, "IEEE");
		exc.setMachine(machine); 
		return exc;
  	}
	
	/**
	 * factory method to create a IEEE2005ST4B Exciter object
	 * 
	 * @param id exciter id
	 * @param name exciter name
	 * @param machine parent machine object
	 * @return
	 */
	public static IEEE2005ST4BExciter createIeee2005ST4BExciter(String id, String name, Machine machine) {
		IEEE2005ST4BExciter exc = new IEEE2005ST4BExciter (id, name, "IEEE");
		exc.setMachine(machine); 
		return exc;
  	}
	
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
