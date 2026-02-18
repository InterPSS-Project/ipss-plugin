package org.interpss.plugin.result.dframe;

import java.util.Set;

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
	
	// Define a record for basic bus info
	private static record BusBasicDFrameRec(String id, long number, String name,
							boolean inService, double nomVolt, double voltMag, double voltAng) {}
	
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
       return adapt(aclfNet, null, true); // default to include all buses and detailed bus information   	
    }

	/**
	 * Adapt the AclfNetwork bus data to DataFrame with options for monitored bus IDs and detailed/basic info
	 * @param aclfNet
	 * @param isDetailedMode - true to include all bus information, false to include only basic bus information
	 * @return the adapted DataFrame
	 */
	public DataFrame adapt(AclfNetwork aclfNet, boolean isDetailedMode) {
		return adapt(aclfNet, null, isDetailedMode); // default to include all buses, with option for detailed or basic bus information
    }

	/**
	 * Adapt the AclfNetwork bus data to DataFrame with options for monitored bus IDs and detailed/basic info
	 * @param aclfNet
	 * @param monitoredBusIDs - set of bus IDs to include in the DataFrame (null or empty to include all buses)
	 * @param isDetailedMode - true to include all bus information, false to include only basic bus information
	 * @return the adapted DataFrame
	 */
	public DataFrame adapt(AclfNetwork aclfNet, Set<String> monitoredBusIDs, boolean isDetailedMode) {
		if (isDetailedMode) {
			// Include all bus information
			for (var bus : aclfNet.getBusList()) {
				if (monitoredBusIDs == null || monitoredBusIDs.contains(bus.getId())) {
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
							bus.isGenPV()? "PV" :"PQ", // three types of bus, Swing, PV, PQ {including genPQ and loadPQ, or non-gen non-load bus	}
						bus.getBaseVoltage(), // in volt
						bus.getVoltageMag(),
						bus.getVoltageAng(),
						bus.getGenP(),
						bus.getGenQ(),
						bus.getLoadP(),
						bus.getLoadQ(),
						mis.getReal(),
						mis.getImaginary()));
				}
			}
			// Create the final DataFrame with all columns
			return appender.toDataFrame();
		} else {
			// Include only basic bus information
			DataFrameAppender<BusBasicDFrameRec> basicAppender = DataFrame
					.byRow(
						Extractor.$col(BusBasicDFrameRec::id),
						Extractor.$long(BusBasicDFrameRec::number),
						Extractor.$col(BusBasicDFrameRec::name),
						Extractor.$bool(BusBasicDFrameRec::inService),
						Extractor.$double(BusBasicDFrameRec::nomVolt),
						Extractor.$double(BusBasicDFrameRec::voltMag),
						Extractor.$double(BusBasicDFrameRec::voltAng)
					)
					.columnNames("ID", "Number", "Name", "InService", "NomVolt", "VoltMag", "VoltAng")
					.appender();
			
			for (var bus : aclfNet.getBusList()) {
				if (monitoredBusIDs == null || monitoredBusIDs.contains(bus.getId())) {
					basicAppender.append(new BusBasicDFrameRec(
							bus.getId(),
							bus.getNumber(),
							bus.getName(),
							bus.isActive(),
							bus.getBaseVoltage(), // in volt
							bus.getVoltageMag(),
							bus.getVoltageAng()));
				}
			}
			// Create the final DataFrame with basic columns only
			return basicAppender.toDataFrame();
		}
    }
}
