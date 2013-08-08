/*
 * @(#)ExciterDataHelper.java   
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

import org.ieee.odm.schema.ExcBPAECXmlType;
import org.ieee.odm.schema.ExcBPAEKXmlType;
import org.ieee.odm.schema.ExcBPAFJXmlType;
import org.ieee.odm.schema.ExcBPAFKXmlType;
import org.ieee.odm.schema.ExcBPAFVXmlType;
import org.ieee.odm.schema.ExcIEEE1968Type1SXmlType;
import org.ieee.odm.schema.ExcIEEE1968Type1XmlType;
import org.ieee.odm.schema.ExcIEEE1968Type2XmlType;
import org.ieee.odm.schema.ExcIEEE1968Type3XmlType;
import org.ieee.odm.schema.ExcIEEE1968Type4XmlType;
import org.ieee.odm.schema.ExcIEEE1981ST1XmlType;
import org.ieee.odm.schema.ExcIEEE1981TypeDC1XmlType;
import org.ieee.odm.schema.ExcSimpleTypeXmlType;
import org.ieee.odm.schema.ExciterModelXmlType;
import org.interpss.dstab.control.exc.ExciterObjectFactory;
import org.interpss.dstab.control.exc.bpa.ec.BpaEcTypeExciter;
import org.interpss.dstab.control.exc.bpa.ek.BpaEkTypeExciter;
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

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.Machine;

/**
 * Class for map ODM exciter xml document to InterPSS exciter model 
 * 
 * @author mzhou
 *
 */
public class ExciterDataHelper {
	private Machine mach = null;
	
	/**
	 * constructor
	 * 
	 * @param mach
	 */
	public ExciterDataHelper(Machine mach) {
		this.mach = mach;
	}

	/**
	 * create the exc model and add to its parent machine object
	 * 
	 * @param excXmlRec ODM exciter model record
	 */
	public void createExciter(ExciterModelXmlType excXmlRec) throws InterpssException {
		if (excXmlRec == null) { throw new InterpssException("Programming error in createExciter()"); }
		
		// we need to put the if statements in the reverse order of the inheritance hierarchy 
		
		////////////////////////////////////////////
		////    BPA           //////////////////////
		////////////////////////////////////////////
		//BPA EC type
		else if (excXmlRec instanceof ExcBPAECXmlType){
			ExcBPAECXmlType excXml =(ExcBPAECXmlType) excXmlRec;
			BpaEcTypeExciter exc=ExciterObjectFactory.createBPAEcTypeExciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTe(excXml.getTE().getValue());
			exc.getData().setKe(excXml.getKE());
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setE1(excXml.getE1());
			exc.getData().setSe_e1(excXml.getSE1());
			exc.getData().setE2(excXml.getE2());
			exc.getData().setSe_e2(excXml.getSE2());
			exc.getData().setVrMax(excXml.getVrmax());
			exc.getData().setVrMin(excXml.getVrmin());	
			exc.getData().setEfdmax(excXml.getEFDMAX());
			exc.getData().setEfdmin(excXml.getEFDMIN());
			
		}
		//BPA EK type
		else if (excXmlRec instanceof ExcBPAEKXmlType){
			ExcBPAEKXmlType excXml =(ExcBPAEKXmlType) excXmlRec;
			BpaEkTypeExciter exc=ExciterObjectFactory.createBPAEKExciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTe(excXml.getTE().getValue());
			exc.getData().setKe(excXml.getKE());
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setE1(excXml.getE1());
			exc.getData().setSe_e1(excXml.getSE1());
			exc.getData().setE2(excXml.getE2());
			exc.getData().setSe_e2(excXml.getSE2());
			exc.getData().setVrMax(excXml.getVrmax());
			exc.getData().setVrMin(excXml.getVrmin());	
			exc.getData().setEfdmax(excXml.getEFDMAX());
			
		}
		
		//BPA FJ Type					
		else if (excXmlRec instanceof ExcBPAFJXmlType){
			ExcBPAFJXmlType excXml =(ExcBPAFJXmlType) excXmlRec;
			BpaFjTypeExciter exc=ExciterObjectFactory.createBPAFJTypeExciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setEfdmax(excXml.getEFDMAX());
			exc.getData().setEfdmin(excXml.getEFDMIN());
			exc.getData().setTc(excXml.getTC().getValue());
			exc.getData().setTb(excXml.getTB().getValue());		
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setKc(excXml.getKC());
			
		} else if (excXmlRec instanceof ExcBPAFKXmlType){
			ExcBPAFKXmlType excXml =(ExcBPAFKXmlType) excXmlRec;
			BpaFkTypeExciter exc=ExciterObjectFactory.createBPAFKTypeExciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setTc(excXml.getTC().getValue());
			exc.getData().setTb(excXml.getTB().getValue());		
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setKc(excXml.getKC());
			exc.getData().setVimax(excXml.getVIMAX());
			exc.getData().setVimin(excXml.getVIMIN());
		} 
		else if (excXmlRec instanceof ExcBPAFVXmlType){
		    ExcBPAFVXmlType excXml =(ExcBPAFVXmlType) excXmlRec;
		    double kv=excXml.getKV();

			if(kv==0)  {//BPA FV(kv=0) Type
				FVkv0Exciter exc = ExciterObjectFactory.createBPAFVKv0TypeExciter(mach.getId()+"_Exc", excXml.getName(), mach);
				exc.getData().setRc(excXml.getLoadRc());
				exc.getData().setXc(excXml.getLoadXc());
				exc.getData().setTr(excXml.getTransTr().getValue());
				exc.getData().setT1(excXml.getT1().getValue());
				exc.getData().setT2(excXml.getT2().getValue());
				exc.getData().setT3(excXml.getT3().getValue());
				exc.getData().setT4(excXml.getT4().getValue());
				exc.getData().setKa(excXml.getKa());
				exc.getData().setTa(excXml.getTa().getValue());
				exc.getData().setVrmax(excXml.getVrmax());
				exc.getData().setVrmin(excXml.getVrmin());
				exc.getData().setKf(excXml.getKF());
				exc.getData().setTf(excXml.getTF().getValue());
				exc.getData().setKc(excXml.getKC());
			}
		    else {//BPA FV(kv=1) Type
		    	FVkv1Exciter exc=ExciterObjectFactory.createBPAFVKv1TypeExciter(mach.getId()+"_Exc", excXml.getName(), mach);			
				exc.getData().setRc(excXml.getLoadRc());
				exc.getData().setXc(excXml.getLoadXc());
				exc.getData().setTr(excXml.getTransTr().getValue());
				exc.getData().setT1(excXml.getT1().getValue());
				exc.getData().setT2(excXml.getT2().getValue());
				exc.getData().setT3(excXml.getT3().getValue());
				exc.getData().setT4(excXml.getT4().getValue());
				exc.getData().setKa(excXml.getKa());
				exc.getData().setTa(excXml.getTa().getValue());
				exc.getData().setVrmax(excXml.getVrmax());
				exc.getData().setVrmin(excXml.getVrmin());
				exc.getData().setKf(excXml.getKF());
				exc.getData().setTf(excXml.getTF().getValue());
				exc.getData().setKc(excXml.getKC());
	      }
		}

		////////////////////////////////////////////
		////    IEEE-1981     //////////////////////
		////////////////////////////////////////////
		
		// IEEE 1981 DC1 Type ,the same as BPA FA Type	
		else if (excXmlRec instanceof ExcIEEE1981TypeDC1XmlType){
			ExcIEEE1981TypeDC1XmlType excXml =(ExcIEEE1981TypeDC1XmlType) excXmlRec;
			IEEE1981DC1Exciter exc=ExciterObjectFactory.createIeee1981DC1Exciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setTc(excXml.getTC().getValue());
			exc.getData().setTb(excXml.getTB().getValue());		
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setE1(excXml.getE1());
			exc.getData().setSe_e1(excXml.getSE1());
			exc.getData().setE2(excXml.getE2());
			exc.getData().setSe_e2(excXml.getSE2());
			exc.getData().setKe(excXml.getKE());
		} 

		//IEEE 1981 ST1 Type 				
		else if (excXmlRec instanceof ExcIEEE1981ST1XmlType){
			ExcIEEE1981ST1XmlType excXml =(ExcIEEE1981ST1XmlType) excXmlRec;
			IEEE1981ST1Exciter exc=ExciterObjectFactory.createIeee1981ST1Exciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setTc(excXml.getTC().getValue());
			exc.getData().setTb(excXml.getTB().getValue());		
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setKc(excXml.getKC());
			exc.getData().setVimax(excXml.getVIMAX());
			exc.getData().setVimin(excXml.getVIMIN());
		} 

		////////////////////////////////////////////
		////    IEEE-1968     //////////////////////
		////////////////////////////////////////////

		else if (excXmlRec instanceof ExcIEEE1968Type1XmlType){
			ExcIEEE1968Type1XmlType excXml =(ExcIEEE1968Type1XmlType) excXmlRec;
			Ieee1968Type1Exciter exc=ExciterObjectFactory.createIeee1968Type1Exciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTa(excXml.getTA().getValue());
			exc.getData().setKa(excXml.getKA());
			exc.getData().setTe(excXml.getTE().getValue());
			exc.getData().setKe(excXml.getKE());
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());
			exc.getData().setE1(excXml.getE1());
			exc.getData().setSeE1(excXml.getSE1());
			exc.getData().setE2(excXml.getE2());
			exc.getData().setSeE2(excXml.getSE2());
			exc.getData().setVrmax(excXml.getVRMAX());
			exc.getData().setVrmin(excXml.getVRMIN());
			exc.getData().setTr(excXml.getTR().getValue());
		}
		else if (excXmlRec instanceof ExcIEEE1968Type1SXmlType){
			ExcIEEE1968Type1SXmlType excXml =(ExcIEEE1968Type1SXmlType) excXmlRec;
			Ieee1968Type1sExciter exc=ExciterObjectFactory.createIeee1968Type1sExciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setKa(excXml.getKa());			
			exc.getData().setKp(excXml.getKP());
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());						
			exc.getData().setVrmin(excXml.getVrmin());	
		}	
		else if (excXmlRec instanceof ExcIEEE1968Type2XmlType){
			ExcIEEE1968Type2XmlType excXml =(ExcIEEE1968Type2XmlType) excXmlRec;
			Ieee1968Type2Exciter exc=ExciterObjectFactory.createIeee1968Type2Exciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setKa(excXml.getKa());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setTe(excXml.getTE().getValue());
			exc.getData().setKe(excXml.getKE());
			exc.getData().setE1(excXml.getE1());
			exc.getData().setSeE1(excXml.getSE1());
			exc.getData().setE2(excXml.getE2());
			exc.getData().setSeE2(excXml.getSE2());			
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf1(excXml.getTF1().getValue());	
			exc.getData().setTf2(excXml.getTF2().getValue());					
		}	
		else if (excXmlRec instanceof ExcIEEE1968Type3XmlType){
			ExcIEEE1968Type3XmlType excXml =(ExcIEEE1968Type3XmlType) excXmlRec;
			Ieee1968Type3Exciter exc=ExciterObjectFactory.createIeee1968Type3Exciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setKa(excXml.getKa());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setTe(excXml.getTE().getValue());
			exc.getData().setKe(excXml.getKE());
			exc.getData().setKp(excXml.getKP());
			exc.getData().setKi(excXml.getKI());
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());	
			exc.getData().setVbmax(excXml.getVBMAX());					
		}
		else if (excXmlRec instanceof ExcIEEE1968Type4XmlType){
			ExcIEEE1968Type4XmlType excXml =(ExcIEEE1968Type4XmlType) excXmlRec;
			Ieee1968Type4Exciter exc=ExciterObjectFactory.createIeee1968Type4Exciter(mach.getId()+"_Exc", excXml.getName(), mach);			
			exc.getData().setTrh(excXml.getTRH().getValue());
			exc.getData().setKv(excXml.getKV());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
			exc.getData().setTe(excXml.getTE().getValue());
			exc.getData().setKe(excXml.getKE());
			exc.getData().setE1(excXml.getE1());
			exc.getData().setSeE1(excXml.getSE1());
			exc.getData().setE2(excXml.getE2());
			exc.getData().setSeE2(excXml.getSE2());			
			exc.getData().setKf(excXml.getKF());			
			exc.getData().setTf(excXml.getTF().getValue());	
		} 

		else if (excXmlRec instanceof ExcSimpleTypeXmlType) {
			ExcSimpleTypeXmlType excXml = (ExcSimpleTypeXmlType)excXmlRec;
			SimpleExciter exc = ExciterObjectFactory.createSimpleExciter(mach.getId()+"_Exc", excXml.getName(), mach);
			exc.getData().setKa(excXml.getKa());
			exc.getData().setTa(excXml.getTa().getValue());
			exc.getData().setVrmax(excXml.getVrmax());
			exc.getData().setVrmin(excXml.getVrmin());
		}
		
		else {
			throw new InterpssException("Exciter type invalid or not implemented, type " + excXmlRec.getClass().getSimpleName());
		}
	}
}