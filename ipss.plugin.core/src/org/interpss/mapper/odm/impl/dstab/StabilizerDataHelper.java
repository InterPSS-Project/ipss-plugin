/*
 * @(#)StabilizerDataHelper.java   
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

import org.ieee.odm.schema.PssBPADualInputXmlType;
import org.ieee.odm.schema.PssBpaSgTypeXmlType;
import org.ieee.odm.schema.PssBpaSpTypeXmlType;
import org.ieee.odm.schema.PssBpaSsTypeXmlType;
import org.ieee.odm.schema.PssIEEE1992Type2AXmlType;
import org.ieee.odm.schema.PssIEEE1AXmlType;
import org.ieee.odm.schema.PssSimpleTypeXmlType;
import org.ieee.odm.schema.StabilizerModelXmlType;
import org.interpss.dstab.control.pss.StabilizerObjectFactory;
import org.interpss.dstab.control.pss.bpa.sg.BPASGTypeStabilizer;
import org.interpss.dstab.control.pss.bpa.si.BPASITypeStabilizer;
import org.interpss.dstab.control.pss.bpa.sp.BPASPTypeStabilizer;
import org.interpss.dstab.control.pss.bpa.ss.BPASSTypeStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer;
import org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer;
import org.interpss.dstab.control.pss.simple.SimpleStabilizer;

import com.interpss.common.exp.InterpssException;
import com.interpss.dstab.mach.Machine;

/**
 * Class for map ODM stabilizer xml document to InterPSS Stabilizer model 
 * 
 * @author mzhou
 *
 */
public class StabilizerDataHelper {
	private Machine mach = null;
	
	/**
	 * constructor
	 * 
	 * @param mach
	 */
	public StabilizerDataHelper(Machine mach) {
		this.mach = mach;
	}
	
	/**
	 * create the pss model and add to its parent machine object
	 * 
	 * @param pssXmlRec ODM stabilizer model record
	 */
	public void createStabilizer(StabilizerModelXmlType pssXmlRec) throws InterpssException {
		if (pssXmlRec == null) { throw new InterpssException("Programming error in createStabilizer()"); }

		// we need to put the if statements in the reverse order of the inheritance hierarchy 
		
		////////////////////////////////////////////
		////    BPA           //////////////////////
		////////////////////////////////////////////
		
		else if (pssXmlRec instanceof PssBPADualInputXmlType) {
			PssBPADualInputXmlType pssXml = (PssBPADualInputXmlType)pssXmlRec;
			BPASITypeStabilizer pss = StabilizerObjectFactory.createBpaSITypeStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKr(pssXml.getKr());	
			pss.getData().setTrp(pssXml.getTrp().getValue());
			pss.getData().setTrw(pssXml.getTrw().getValue());
			pss.getData().setTw(pssXml.getTW().getValue());
			pss.getData().setTw1(pssXml.getTW1().getValue());
			pss.getData().setTw2(pssXml.getTW2().getValue());
			pss.getData().setKs(pssXml.getKS());
			pss.getData().setKp(pssXml.getKp());
			pss.getData().setT1(pssXml.getT1().getValue());
			pss.getData().setT2(pssXml.getT2().getValue());
			pss.getData().setT3(pssXml.getT3().getValue());
			pss.getData().setT4(pssXml.getT4().getValue());
			pss.getData().setT5(pssXml.getT5().getValue());
			pss.getData().setT6(pssXml.getT6().getValue());	
			pss.getData().setT7(pssXml.getT7().getValue());
			pss.getData().setT9(pssXml.getT9().getValue());
			pss.getData().setT10(pssXml.getT10().getValue());
			pss.getData().setT12(pssXml.getT12().getValue());
			pss.getData().setT13(pssXml.getT13().getValue());
			pss.getData().setT14(pssXml.getT14().getValue());
			pss.getData().setVsMax(pssXml.getVSMAX());
			pss.getData().setVsMin(pssXml.getVSMIN());
					
		}
		else if (pssXmlRec instanceof PssBpaSgTypeXmlType) {
			PssBpaSgTypeXmlType pssXml = (PssBpaSgTypeXmlType)pssXmlRec;
			BPASGTypeStabilizer pss = StabilizerObjectFactory.createBpaSgTypeStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKqs(pssXml.getKQS());	
			pss.getData().setTqs(pssXml.getTQS().getValue());
			pss.getData().setKqs(pssXml.getKQS());	
			pss.getData().setTq(pssXml.getTQ().getValue());
			pss.getData().setTq1(pssXml.getTQ1().getValue());
			pss.getData().setTq11(pssXml.getT1Q1().getValue());
			pss.getData().setTq2(pssXml.getTQ2().getValue());
			pss.getData().setTq21(pssXml.getT1Q2().getValue());
			pss.getData().setTq3(pssXml.getTQ3().getValue());
			pss.getData().setTq31(pssXml.getT1Q3().getValue());
			pss.getData().setVsmax(pssXml.getVSMAX());
			pss.getData().setVsmin(pssXml.getVSMIN());
					
		}
		else if (pssXmlRec instanceof PssBpaSpTypeXmlType) {
			PssBpaSpTypeXmlType pssXml = (PssBpaSpTypeXmlType)pssXmlRec;
			BPASPTypeStabilizer pss = StabilizerObjectFactory.createBpaSpTypeStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKqs(pssXml.getKQS());	
			pss.getData().setTqs(pssXml.getTQS().getValue());
			pss.getData().setKqs(pssXml.getKQS());	
			pss.getData().setTq(pssXml.getTQ().getValue());
			pss.getData().setTq1(pssXml.getTQ1().getValue());
			pss.getData().setTq11(pssXml.getT1Q1().getValue());
			pss.getData().setTq2(pssXml.getTQ2().getValue());
			pss.getData().setTq21(pssXml.getT1Q2().getValue());
			pss.getData().setTq3(pssXml.getTQ3().getValue());
			pss.getData().setTq31(pssXml.getT1Q3().getValue());
			pss.getData().setVsmax(pssXml.getVSMAX());
			pss.getData().setVsmin(pssXml.getVSMIN());
					
		}
		else if (pssXmlRec instanceof PssBpaSsTypeXmlType) {
			PssBpaSsTypeXmlType pssXml = (PssBpaSsTypeXmlType)pssXmlRec;
			BPASSTypeStabilizer pss = StabilizerObjectFactory.createBpaSsTypeStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKqs(pssXml.getKQS());	
			pss.getData().setTqs(pssXml.getTQS().getValue());
			pss.getData().setKqs(pssXml.getKQS());	
			pss.getData().setTq(pssXml.getTQ().getValue());
			pss.getData().setTq1(pssXml.getTQ1().getValue());
			pss.getData().setTq11(pssXml.getT1Q1().getValue());
			pss.getData().setTq2(pssXml.getTQ2().getValue());
			pss.getData().setTq21(pssXml.getT1Q2().getValue());
			pss.getData().setTq3(pssXml.getTQ3().getValue());
			pss.getData().setTq31(pssXml.getT1Q3().getValue());
			pss.getData().setVsmax(pssXml.getVSMAX());
			pss.getData().setVsmin(pssXml.getVSMIN());
					
		}
		
		////////////////////////////////////////////
		////    IEEE-1992     //////////////////////
		////////////////////////////////////////////
		
		else if (pssXmlRec instanceof PssIEEE1992Type2AXmlType) {
			PssIEEE1992Type2AXmlType pssXml = (PssIEEE1992Type2AXmlType)pssXmlRec;
			Ieee1992PSS2AStabilizer pss = StabilizerObjectFactory.createIeee1992PSS2AStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKs1(pssXml.getKS1());			
			pss.getData().setT1(pssXml.getT1().getValue());
			pss.getData().setT2(pssXml.getT2().getValue());
			pss.getData().setT3(pssXml.getT3().getValue());
			pss.getData().setT4(pssXml.getT4().getValue());
			pss.getData().setT6(pssXml.getT1().getValue());
			pss.getData().setT7(pssXml.getT2().getValue());
			pss.getData().setT8(pssXml.getT3().getValue());
			pss.getData().setT9(pssXml.getT4().getValue());
			pss.getData().setN(pssXml.getN());
			pss.getData().setM(pssXml.getM());			
			pss.getData().setVstmax(pssXml.getVSTMAX());
			pss.getData().setVstmin(pssXml.getVSTMIN());
			pss.getData().setKs2(pssXml.getKS2());	
			pss.getData().setKs3(pssXml.getKS3());	
			pss.getData().setTw1(pssXml.getTW1().getValue());
			pss.getData().setTw2(pssXml.getTW2().getValue());
			pss.getData().setTw3(pssXml.getTW3().getValue());
			pss.getData().setTw4(pssXml.getTW4().getValue());
		}
		else if (pssXmlRec instanceof PssIEEE1AXmlType) {
			PssIEEE1AXmlType pssXml = (PssIEEE1AXmlType)pssXmlRec;
			Ieee1992PSS1AStabilizer pss = StabilizerObjectFactory.createIeee1992PSS1AStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKs(pssXml.getKS());			
			pss.getData().setT1(pssXml.getT1().getValue());
			pss.getData().setT2(pssXml.getT2().getValue());
			pss.getData().setT3(pssXml.getT3().getValue());
			pss.getData().setT4(pssXml.getT4().getValue());
			pss.getData().setT6(pssXml.getT1().getValue());			
			pss.getData().setVstmax(pssXml.getVSTMAX());
			pss.getData().setVstmin(pssXml.getVSTMIN());
			pss.getData().setA1(pssXml.getA1());	
			pss.getData().setA2(pssXml.getA2());				
		}
		
		else if (pssXmlRec instanceof PssSimpleTypeXmlType) {
			PssSimpleTypeXmlType pssXml = (PssSimpleTypeXmlType)pssXmlRec;
			SimpleStabilizer pss = StabilizerObjectFactory.createSimpleStabilizer(mach.getId()+"_Pss", pssXml.getName(), mach);
			pss.getData().setKs(pssXml.getKs());
			pss.getData().setT1(pssXml.getT1().getValue());
			pss.getData().setT2(pssXml.getT2().getValue());
			pss.getData().setT3(pssXml.getT3().getValue());
			pss.getData().setT4(pssXml.getT4().getValue());
			pss.getData().setVsmax(pssXml.getVsmax());
			pss.getData().setVsmin(pssXml.getVsmin());
		}

		else {
			throw new InterpssException("Stabilizer type invalid or not implemented, type " + pssXmlRec.getClass().getSimpleName());
		}
		
	}
}