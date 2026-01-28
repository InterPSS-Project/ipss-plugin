package org.interpss.plugin.result.dframe;

import org.apache.commons.math3.complex.Complex;
import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.builder.DataFrameAppender;

import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.AclfMethodType;

/**
 * Adapter to convert AclfBus data to DataFrame
 */
public class AclfBusDFrameAdapter {
	// Define a record to hold the data for each bus
	private static record BusDFrameRec(String id, long number, String name, 
							String areaName, long areaNum, 
							String zoneName, long zoneNum, 
							String ownerName, long ownerNum, 
							boolean inService, String busType,
							double nomVolt, double voltMag, double voltAng, 
							double genP, double genQ, double loadP, double loadQ,
							double misP, double misQ) {}
    // Appender to build the DataFrame
    private DataFrameAppender<BusDFrameRec> appender;
    
    /**
	 * Constructor to initialize the DataFrame appender
	 */
    public AclfBusDFrameAdapter() {
	  	// Define how to pull data from the object into columns
        this.appender = DataFrame
                .byRow(
                	Extractor.$col(BusDFrameRec::id),	
                    Extractor.$long(BusDFrameRec::number),
                    Extractor.$col(BusDFrameRec::name),
                    Extractor.$col(BusDFrameRec::areaName),
                    Extractor.$long(BusDFrameRec::areaNum),
                    Extractor.$col(BusDFrameRec::zoneName),
                    Extractor.$long(BusDFrameRec::zoneNum),
                    Extractor.$col(BusDFrameRec::ownerName),
                    Extractor.$long(BusDFrameRec::ownerNum),
                    Extractor.$bool(BusDFrameRec::inService),
                    Extractor.$col(BusDFrameRec::busType),
                    Extractor.$double(BusDFrameRec::nomVolt),
                    Extractor.$double(BusDFrameRec::voltMag),
                    Extractor.$double(BusDFrameRec::voltAng),
                    Extractor.$double(BusDFrameRec::genP),
                    Extractor.$double(BusDFrameRec::genQ),
                    Extractor.$double(BusDFrameRec::loadP),
                    Extractor.$double(BusDFrameRec::loadQ),
                    Extractor.$double(BusDFrameRec::misP),
                    Extractor.$double(BusDFrameRec::misQ)
                )
                // define the column names
                .columnNames("ID", "Number", "Name", 
							"AreaName", "AreaNum", "ZoneName", "ZoneNum", "OwnerName", "OwnerNum", 
							"InService", "BusType", "NomVolt", "VoltMag", "VoltAng", 
							"GenP", "GenQ", "LoadP", "LoadQ",
							"MismatchP", "MismatchQ")
                .appender();
    }
    
    /**
	 * Adapt the AclfNetwork bus data to DataFrame
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @return the adapted DataFrame
	 */
    public DataFrame adapt(AclfNetwork aclfNet) {
        // Append rows from the AclfNetwork bus object
        for (var bus : aclfNet.getBusList()) {
        	Complex mis = bus.mismatch(AclfMethodType.NR); 
        	appender.append(new BusDFrameRec(
					bus.getId(),
					bus.getNumber(),
					bus.getName(),
					bus.getArea().getName(),
					bus.getArea().getNumber(),
					bus.getZone().getName(),
					bus.getZone().getNumber(),
					bus.getOwner() != null? bus.getOwner().getName(): "",
					bus.getOwner() != null? bus.getOwner().getNumber(): 0,
					bus.isActive(),		
					bus.isSwing()? "Swing" :
						bus.isGenPV()? "PV" :
							bus.isGen()? "PQ" : "Load",
					bus.getBaseVoltage(), // in pu
					bus.getVoltageMag(),
					bus.getVoltageAng(),
					bus.getGenP(),
					bus.getGenQ(),
					bus.getLoadP(),
					bus.getLoadQ(),
					mis.getReal(),
					mis.getImaginary()));
        }
        
    	// Create the final DataFrame
    	return appender.toDataFrame();    	
    }
}
