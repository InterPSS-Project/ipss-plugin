package org.interpss.plugin.result.dframe;

import java.util.Set;

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
							boolean inService, String busType,
							double nomVolt, double voltMag, double voltAng, 
							double genP, double genQ, double loadP, double loadQ,
							double busInjP, double busInjQ) {}
	
	// Define a record for basic bus info
	private static record BusBasicDFrameRec(String id, long number, String name,String areaName,
							boolean inService, double nomVolt, double voltMag, double voltAng, double busInjP, double busInjQ) {}
	
    // Appender to build the DataFrame
    //private DataFrameAppender<BusDFrameRec> appender;
    
    /**
	 * Constructor to initialize the DataFrame appender
	 */
    public AclfBusDFrameAdapter() {
    }
    
    private static DataFrameAppender<BusDFrameRec> createAppender() {
	  	// Define how to pull data from the object into columns
        return DataFrame
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
                    Extractor.$double(BusDFrameRec::busInjP),
                    Extractor.$double(BusDFrameRec::busInjQ)
                )
                // define the column names
                .columnNames("ID", "Number", "Name", 
							"AreaName", "AreaNum", "ZoneName", "ZoneNum", "OwnerName", "OwnerNum", 
							"InService", "BusType", "NomVolt", "VoltMag", "VoltAng", 
							"GenP", "GenQ", "LoadP", "LoadQ",
							"BusInjP", "BusInjQ")
                .appender();
    }
    
    private static DataFrameAppender<BusBasicDFrameRec> createBasicAppender() {
		// Include only basic bus information
		return DataFrame
				.byRow(
					Extractor.$col(BusBasicDFrameRec::id),
					Extractor.$long(BusBasicDFrameRec::number),
					Extractor.$col(BusBasicDFrameRec::areaName),
					Extractor.$bool(BusBasicDFrameRec::inService),
					Extractor.$double(BusBasicDFrameRec::nomVolt),
					Extractor.$double(BusBasicDFrameRec::voltMag),
					Extractor.$double(BusBasicDFrameRec::voltAng)
				)
				.columnNames("ID", "Number", "Name", "InService", "NomVolt", "VoltMag", "VoltAng")
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
			DataFrameAppender<BusDFrameRec> appender = createAppender();
			// Include all bus information
			for (var bus : aclfNet.getBusList()) {
				if (monitoredBusIDs == null || monitoredBusIDs.contains(bus.getId())) {
					//Complex mis = bus.mismatch(AclfMethodType.NR); 
					appender.append(new BusDFrameRec(
						bus.getId(),
						bus.getNumber(),
						bus.getName(),
						bus.getArea() != null ? bus.getArea().getName() : "",
						bus.getArea() != null ? bus.getArea().getNumber() : 0,
						bus.getZone() != null ? bus.getZone().getName() : "",
						bus.getZone() != null ? bus.getZone().getNumber() : 0,
						bus.getOwner() != null? bus.getOwner().getName(): "",
						bus.getOwner() != null? bus.getOwner().getNumber(): 0,
						bus.isActive(),		
						bus.isSwing()? "Swing" :
							bus.isGenPV()? "PV" :"PQ", // three types of bus, Swing, PV, PQ {including genPQ and loadPQ, or non-gen non-load bus	}
						bus.getBaseVoltage(), // in volt
						bus.getVoltageMag(),
						bus.getVoltageAng(),
						bus.getGenP(),
						bus.calNetGenResults().getImaginary(), // use calNetGenResults to get the genQ value after load flow calculation, as getGenQ() may not be updated with the latest load flow results
						bus.getLoadP(),
						bus.getLoadQ(),
						bus.powerIntoNet().getReal(),
						bus.powerIntoNet().getImaginary()));
				}
			}
			// Create the final DataFrame with all columns
			return appender.toDataFrame();
		} else {
			// Include only basic bus information
			DataFrameAppender<BusBasicDFrameRec> basicAppender = createBasicAppender();
			
			for (var bus : aclfNet.getBusList()) {
				if (monitoredBusIDs == null || monitoredBusIDs.contains(bus.getId())) {
					basicAppender.append(new BusBasicDFrameRec(
							bus.getId(),
							bus.getNumber(),
							bus.getName(),
							bus.getArea() != null ? bus.getArea().getName() : "",
							bus.isActive(),
							bus.getBaseVoltage(), // in volt
							bus.getVoltageMag(),
							bus.getVoltageAng(),
							bus.powerIntoNet().getReal(),
							bus.powerIntoNet().getImaginary()));
				}
			}
			// Create the final DataFrame with basic columns only
			return basicAppender.toDataFrame();
		}
    }
}
