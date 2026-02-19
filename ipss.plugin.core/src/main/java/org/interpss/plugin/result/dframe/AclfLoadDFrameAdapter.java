package org.interpss.plugin.result.dframe;

import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.builder.DataFrameAppender;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter to convert AclfLoad data to DataFrame
 */
public class AclfLoadDFrameAdapter {
	// Define a record to hold the data for each load
	private static record LoadDFrameRec(String busId, long busNumber,  String busName,
			String loadId, String loadName, String loadCode,
			boolean inService,
			double ploadTotal, double qloadTotal,
			double pload, double qload,
			double ipload, double iqload,
			double zpload, double zqload,
			double distGenP, double distGenQ,
			boolean distGenStatus,
			long areaNum, String areaName,
			long zoneNum, String zoneName,
			long ownerNum, String ownerName) {}
    
	// Appender to build the DataFrame
    //private DataFrameAppender<LoadDFrameRec> appender;
    
    /**
     * Constructor to initialize the DataFrame appender
     */
    public AclfLoadDFrameAdapter() {
    }
    
    private static DataFrameAppender<LoadDFrameRec> createAppender() {
	  	// Define how to pull data from the object into columns
        return DataFrame
                .byRow(
                	Extractor.$col(LoadDFrameRec::busId),
                	Extractor.$long(LoadDFrameRec::busNumber),
                	Extractor.$col(LoadDFrameRec::busName),
                	Extractor.$col(LoadDFrameRec::loadId),
                	Extractor.$col(LoadDFrameRec::loadName),
                	Extractor.$col(LoadDFrameRec::loadCode),
                	Extractor.$bool(LoadDFrameRec::inService),
                	Extractor.$double(LoadDFrameRec::ploadTotal),
                	Extractor.$double(LoadDFrameRec::qloadTotal),
                	Extractor.$double(LoadDFrameRec::pload),
                	Extractor.$double(LoadDFrameRec::qload),
                	Extractor.$double(LoadDFrameRec::ipload),
                	Extractor.$double(LoadDFrameRec::iqload),
                	Extractor.$double(LoadDFrameRec::zpload),
                	Extractor.$double(LoadDFrameRec::zqload),
                	Extractor.$double(LoadDFrameRec::distGenP),
                	Extractor.$double(LoadDFrameRec::distGenQ),
                	Extractor.$bool(LoadDFrameRec::distGenStatus),
                	Extractor.$long(LoadDFrameRec::areaNum),
                	Extractor.$col(LoadDFrameRec::areaName),
                	Extractor.$long(LoadDFrameRec::zoneNum),
                	Extractor.$col(LoadDFrameRec::zoneName),
                	Extractor.$long(LoadDFrameRec::ownerNum),
                	Extractor.$col(LoadDFrameRec::ownerName)
                )
                // define the column names
                .columnNames("BusID", "BusNumber", "BusName",
                			"LoadID", "LoadName", "LoadCode",
                			"InService",
                			"PLoadTotal", "QLoadTotal",
                			"PLoad", "QLoad",
                			"IPLoad", "IQLoad",
                			"ZPLoad", "ZQLoad",
                			"DistGenP", "DistGenQ", "DistGenStatus",
                			"AreaNum", "AreaName",
                			"ZoneNum", "ZoneName",
                			"OwnerNum", "OwnerName")
                .appender();
    }
    
    /**
	 * Adapt the AclfNetwork load data to DataFrame
	 * * @param aclfNet the AclfNetwork object
	 * @return the adapted DataFrame
	 */
    public DataFrame adapt(AclfNetwork aclfNet) {
    	DataFrameAppender<LoadDFrameRec> appender = createAppender();
    	
        // Append rows from the AclfNetwork bus object
        for (var bus : aclfNet.getBusList()) {
        	for (var load : bus.getContributeLoadList()) {
        		double vMag = bus.getVoltageMag();
	        	appender.append(new LoadDFrameRec(
	        			bus.getId(),
	        			bus.getNumber(),
	        			bus.getName(),
	        			load.getId(),
	        			load.getName(),
	        			load.getCode().toString(),
	        			load.isActive(),
	        			load.getLoad(vMag).getReal(),
	        			load.getLoad(vMag).getImaginary(),
	        			load.getLoadCP().getReal(),
	        			load.getLoadCP().getImaginary(),
	        			load.getLoadCI().getReal(),
	        			load.getLoadCI().getImaginary(),
	        			load.getLoadCZ().getReal(),
	        			load.getLoadCZ().getImaginary(),
	        			load.getDistGenPower().getReal(),
	        			load.getDistGenPower().getImaginary(),
	        			load.isDistGenStatus(),
	        			bus.getArea().getNumber(),
	        			bus.getArea().getName(),
	        			bus.getZone().getNumber(),
	        			bus.getZone().getName(),
	        			bus.getOwner() != null? bus.getOwner().getNumber(): 0,
	        			bus.getOwner() != null? bus.getOwner().getName(): ""));
        	}
        }
        
    	// Create the final DataFrame
    	return appender.toDataFrame();    	
    }
}
