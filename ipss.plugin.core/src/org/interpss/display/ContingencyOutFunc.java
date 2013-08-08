/*
 * @(#)ContingencyOutFunc.java   
 *
 * Copyright (C) 2006 www.interpss.org
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
 * @Date 10/27/2009
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.display;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.interpss.simu.multicase.StudyCase;
import com.interpss.simu.multicase.aclf.AclfStudyCase;
import com.interpss.simu.multicase.aclf.ContingencyAnalysis;
import com.interpss.simu.multicase.result.AclfBranchResultRec;
import com.interpss.simu.multicase.result.AclfBusResultRec;

/**
 * Contingency analysis output functions
 * 
 * @author mzhou
 *
 */
public class ContingencyOutFunc {
	/**
	 * Contingency analysus output configuration interface
	 * 
	 * @author mzhou
	 *
	 */
	public static interface IConfigure {
		/**
		 * set bus limit, for example, voltage limit
		 */
		void setBusLimit();
		/**
		 * set branch rating limit
		 */
		void setBranchRating();
	}
	
	/**
	 * output security margin analysis results
	 * 
	 * @param mcase
	 * @return
	 */
	public static StringBuffer securityMargin(ContingencyAnalysis mcase) {
		return securityMargin(mcase, null);
	}
	
	public static StringBuffer securityMargin(ContingencyAnalysis mcase, IConfigure config) {
		StringBuffer buf = new StringBuffer();
		
		if (config != null) {
			config.setBusLimit();
			config.setBranchRating();
		}

		buf.append("\n");
		buf.append("                     Security Margin Report\n");
		buf.append("\n");

		buf.append("\n");
		buf.append(String.format("                   Non-converged loadflow case [ tolerance: %5.4f pu ]\n", 
				          mcase.getLfTolerance()));
		buf.append("\n");
		buf.append("                 Case                                          Max Error Info\n");
		buf.append("       ====================================================================================================\n");
		for (StudyCase scase : mcase.getStudyCaseList()) {
			AclfStudyCase aclfCase = (AclfStudyCase)scase;
			if (!aclfCase.isAclfConverged()) {
				buf.append(String.format("%25s   %s\n", aclfCase.getId(), aclfCase.getDesc()));
			}
		}
		
		//double max = mcase.getBusVoltageUpperLimitPU(),    // need to be at the bus level
		//       min = mcase.getBusVoltageLowerLimitPU();
		buf.append("\n");
		//buf.append(String.format("              Bus Voltage Limit: [%4.2f, %4.2f]\n", max, min));
		//buf.append("\n");

		buf.append("         Bus Id       max   min    LowVolt LowMargin           Description\n");
		buf.append("       ====================================================================\n");
		Enumeration<String> keys = mcase.getBusResultSummary().keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			AclfBusResultRec r = mcase.getBusResultSummary().get(key);
			double max = r.getLimit().getUpperVoltLimit(),
			       min = r.getLimit().getLowerVoltLimit();
			buf.append(String.format("     %12s    %4.2f, %4.2f   %8.4f   %5.1f%s          %s%n", key, 
					max, min,
					r.getLowVoltMagPU(), (r.getLowVoltMagPU()-min)*100.0, "%", 
					r.getDescLowVoltage()));
		}
		buf.append("\n");
		
		buf.append("          Branch Id        MvaFlow   MvaRating        P + jQ        Margin        Description\n");
		buf.append("  ==============================================================+=================================\n");
		keys = mcase.getBranchResultSummary().keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			AclfBranchResultRec r = mcase.getBranchResultSummary().get(key);
			double rating = r.getRating().getThermalMvaRating();
			if (rating <= 0.0 && mcase.isUseDefaultBranchMvaRating())
				rating = mcase.getDefaultBranchMvaRating();
			String str = "    ";
			if (rating > 0.0) {
				double margin = (rating - r.getMvaFlow()) / rating;
				str = String.format("%5.0f%s", margin*100.0, "%");
			}	
			buf.append(String.format("  %22s  %8.1f  %8.1f    (%7.1f%s%7.1f) %s      %s%n", 
					key, r.getMvaFlow(), rating, r.getPFlow(), "+j", r.getQFlow(), str, r.getDesc()));
		}
		
		return buf;
	}

	/**
	 * output branch violation analysis results
	 * 
	 * @param mcase
	 * @return
	 */
	public static StringBuffer branchMvaRatingViolation(ContingencyAnalysis mcase) {
		StringBuffer buf = new StringBuffer();

		List<Record> elemList = new ArrayList<Record>();
		
		// calculate violation
		Enumeration<String> keys = mcase.getBranchResultSummary().keys();
		while (keys.hasMoreElements()) {
			String key = keys.nextElement();
			AclfBranchResultRec r = mcase.getBranchResultSummary().get(key);
			if (r.getRating().getThermalMvaRating() > 0.0 && r.getMvaLoadingPercent() > 100.0) {
				elemList.add(new Record(key, r));
			}	
		}
		
		// sort violation
		boolean done = false;
		while (!done) {
			done = true;
			for (int i = 0; i < elemList.size()-1; i++) {
				if (elemList.get(i).resultRec.getMvaLoadingPercent() < elemList.get(i+1).resultRec.getMvaLoadingPercent()) {
					Record temp = elemList.get(i);
					elemList.set(i, elemList.get(i+1));
					elemList.set(i+1, temp);
					done = false;
				}
			}
		}

		// display violation
		buf.append("\n");
		buf.append("                    Branch MVA Rating Violation Report\n");
		buf.append("\n");

		buf.append("       Branch Id     MvaFlow   MvaRating   Violation    Description\n");
		buf.append("  ===========================================================================\n");
		for (Record rec : elemList) {
			String str = String.format("%3.0f%s", rec.resultRec.getMvaLoadingPercent()-100.0, "%");
			buf.append(String.format("  %16s  %8.1f  %8.1f       %s      %s%n", 
						rec.key, rec.resultRec.getMvaFlow(), rec.resultRec.getRating().getThermalMvaRating(), 
						str, rec.resultRec.getDesc()));
		}
		
		return buf;
	}
	
	private static class Record {
		public String key = "";
		public AclfBranchResultRec resultRec = null;
		
		public Record(String key, AclfBranchResultRec value) {
			this.key = key;
			this.resultRec = value;
		}
	}	
}
