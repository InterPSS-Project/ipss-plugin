package org.interpss.plugin.exchange.dframe;

import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.builder.DataFrameAppender;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter to convert AclfBus data to DataFrame
 */
public class AclfBusDFrameAdapter {
	// Define a record to hold the data for each bus
	private static record BusDFrameRec(String id, long number, String name, 
							String areaName, long areaNum, 
							String zoneName, long zoneNum, 
							String ownerName, long ownerNum, 
							double nomVolt, double voltMag, double voltAng, 
							double genP, double genQ, double loadP, double loadQ) {}
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
                    Extractor.$double(BusDFrameRec::nomVolt),
                    Extractor.$double(BusDFrameRec::voltMag),
                    Extractor.$double(BusDFrameRec::voltAng),
                    Extractor.$double(BusDFrameRec::genP),
                    Extractor.$double(BusDFrameRec::genQ),
                    Extractor.$double(BusDFrameRec::loadP),
                    Extractor.$double(BusDFrameRec::loadQ)
                )
                // define the column names
                .columnNames("ID", "Number", "Name", 
							"AreaName", "AreaNum", "ZoneName", "ZoneNum", "OwnerName", "OwnerNum", 
							"NomVolt", "VoltMsg", "VoltAng", 
							"GenP", "GenQ", "LoadP", "LoadQ")
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
					bus.getBaseVoltage(), // in pu
					bus.getVoltageMag(),
					bus.getVoltageAng(),
					bus.getGenP(),
					bus.getGenQ(),
					bus.getLoadP(),
					bus.getLoadQ()));
        }
        
    	// Create the final DataFrame
    	return appender.toDataFrame();    	
    }
}
