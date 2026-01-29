package org.interpss.plugin.result.dframe;

import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.builder.DataFrameAppender;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter to convert AclfGen data to DataFrame
 */
public class AclfGenDFrameAdapter {
	// Define a record to hold the data for each generator
	private static record GenDFrameRec(String busId, long busNumber, String busName,
			String genId, String genName, String genCode,
			boolean inService,
			double vSched, String regBusId,
			double pGen, double pMax, double pMin,
			double qGen, double qMax, double qMin,
			double mbase, double rSource, double xSource,
			long areaNum, String areaName, 
			long zoneNum, String zoneName,
			long ownerNum, String ownerName, 
			double ratedVoltage,
			double mwControlPFactor, double mvarControlPFactor) {}
    
	// Appender to build the DataFrame
    private DataFrameAppender<GenDFrameRec> appender;
    
    /**
     * Constructor to initialize the DataFrame appender
     */
    public AclfGenDFrameAdapter() {
	  	// Define how to pull data from the object into columns
        this.appender = DataFrame
                .byRow(
                	Extractor.$col(GenDFrameRec::busId),	
					Extractor.$long(GenDFrameRec::busNumber),
					Extractor.$col(GenDFrameRec::busName),
					Extractor.$col(GenDFrameRec::genId),
					Extractor.$col(GenDFrameRec::genName),
					Extractor.$col(GenDFrameRec::genCode),
					Extractor.$bool(GenDFrameRec::inService),
					Extractor.$double(GenDFrameRec::vSched),
					Extractor.$col(GenDFrameRec::regBusId),
					Extractor.$double(GenDFrameRec::pGen),
					Extractor.$double(GenDFrameRec::pMax),
					Extractor.$double(GenDFrameRec::pMin),
					Extractor.$double(GenDFrameRec::qGen),
					Extractor.$double(GenDFrameRec::qMax),
					Extractor.$double(GenDFrameRec::qMin),
					Extractor.$double(GenDFrameRec::mbase),
					Extractor.$double(GenDFrameRec::rSource),
					Extractor.$double(GenDFrameRec::xSource),
					Extractor.$long(GenDFrameRec::areaNum),
					Extractor.$col(GenDFrameRec::areaName),
					Extractor.$long(GenDFrameRec::zoneNum),
					Extractor.$col(GenDFrameRec::zoneName),
					Extractor.$long(GenDFrameRec::ownerNum),
					Extractor.$col(GenDFrameRec::ownerName),
					Extractor.$double(GenDFrameRec::ratedVoltage),
					Extractor.$double(GenDFrameRec::mwControlPFactor),
					Extractor.$double(GenDFrameRec::mvarControlPFactor)
                )
                // define the column names
                .columnNames("BusID", "BusNumber", "BusName",
                			"GenID", "GenName", "GenCode",
                			"InService",
                			"VSched", "RegBus",
                			"PGen", "PMax", "PMin",
                			"QGen", "QMax", "QMin",
                			"MBase", "RSource", "XSource",
                			"AreaNum", "AreaName",
                			"ZoneNum", "ZoneName",
                			"OwnerNum", "OwnerName",
                			"RatedVoltage",
                			"MWControlPFactor", "MVarControlPFactor")
                .appender();
    }
    
    /**
	 * Adapt the AclfNetwork generator data to a DataFrame
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @return DataFrame containing generator data
	 */
    public DataFrame adapt(AclfNetwork aclfNet) {
        // Append rows from the AclfNetwork bus object
        for (var bus : aclfNet.getBusList()) {
        	for (var gen : bus.getContributeGenList()) {
	        	appender.append(new GenDFrameRec(
						bus.getId(),
						bus.getNumber(),
						bus.getName(),
						gen.getId(),
						gen.getName(),
						gen.getCode().toString(),
						gen.isActive(),
						gen.getDesiredVoltMag(),
						gen.getRemoteVControlBusId(),
						gen.getGen().getReal(),
						gen.getPGenLimit() != null? gen.getPGenLimit().getMax() : 0.0,
						gen.getPGenLimit() != null? gen.getPGenLimit().getMin() : 0.0,
						gen.getGen().getImaginary(),
						gen.getQGenLimit() != null? gen.getQGenLimit().getMax() : 0.0,
						gen.getQGenLimit() != null? gen.getQGenLimit().getMin() : 0.0,
						gen.getMvaBase(),
						gen.getSourceZ().getReal(),
						gen.getSourceZ().getImaginary(),
						bus.getArea().getNumber(),
						bus.getArea().getName(),
						bus.getZone().getNumber(),
						bus.getZone().getName(),
						bus.getOwner() != null? bus.getOwner().getNumber(): 0,
						bus.getOwner() != null? bus.getOwner().getName(): "",
						gen.getRatedVoltage(),
						gen.getMwControlPFactor(),
						gen.getMvarControlPFactor()));
        	}
        }
        
    	// Create the final DataFrame
    	return appender.toDataFrame();    	
    }
}
