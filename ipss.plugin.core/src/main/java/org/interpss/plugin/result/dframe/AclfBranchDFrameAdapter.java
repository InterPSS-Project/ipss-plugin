package org.interpss.plugin.result.dframe;

import java.util.Set;
import java.util.function.Predicate;

import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.builder.DataFrameAppender;

import com.interpss.core.aclf.AclfBranch;
import com.interpss.core.aclf.AclfBranchCode;
import com.interpss.core.aclf.AclfNetwork;

/**
 * Adapter to convert AclfBranch data to DataFrame
 */
public class AclfBranchDFrameAdapter {
	// Define a record to hold the data for each branch
	private static record  BranchDFrameRec(String id, String name, String circuit, boolean status,
			String fromBusId, long fromBusNumber, String fromBusName,
			String toBusId, long toBusNumber, String toBusName,
			boolean inService,
			AclfBranchCode branchCode,   // AclfBranchCode
			boolean xfmr,         
			double r, double x, double b,
			double limMvaA, double limMvaB, double limMvaC,
			double pFrom2To, double qFrom2To,
			double pTo2From, double qTo2From,
			double flowFromSide, double loading) {}
	
	// Define a record for basic branch info
	private static record BasicBranchDFrameRec(String id, String fromBusId, String toBusId, String circuitNum,
			double pFrom2To, double qFrom2To,
			double pTo2From, double qTo2From,
			double flowFromSide) {}
    
	// Appender to build the DataFrame
    //private DataFrameAppender<BranchDFrameRec> appender;
    
    /**
	 * Constructor to initialize the DataFrame appender
	 */
    public AclfBranchDFrameAdapter() {
    }
    
    private static DataFrameAppender<BranchDFrameRec> createAppender() {
	  	// Define how to pull data from the object into columns
        return DataFrame
                .byRow(
                	Extractor.$col(BranchDFrameRec::id),
                	Extractor.$col(BranchDFrameRec::name),
                	Extractor.$col(BranchDFrameRec::circuit),
                	Extractor.$bool(BranchDFrameRec::status),
                	Extractor.$col(BranchDFrameRec::fromBusId),
                	Extractor.$long(BranchDFrameRec::fromBusNumber),
                	Extractor.$col(BranchDFrameRec::fromBusName),
                	Extractor.$col(BranchDFrameRec::toBusId),
                	Extractor.$long(BranchDFrameRec::toBusNumber),
                	Extractor.$col(BranchDFrameRec::toBusName),
                	Extractor.$bool(BranchDFrameRec::inService),
                	Extractor.$col(BranchDFrameRec::branchCode),
                	Extractor.$bool(BranchDFrameRec::xfmr),
                	Extractor.$double(BranchDFrameRec::r),
                	Extractor.$double(BranchDFrameRec::x),
                	Extractor.$double(BranchDFrameRec::b),
                	Extractor.$double(BranchDFrameRec::limMvaA),
                	Extractor.$double(BranchDFrameRec::limMvaB),
                	Extractor.$double(BranchDFrameRec::limMvaC),
                	Extractor.$double(BranchDFrameRec::pFrom2To),
                	Extractor.$double(BranchDFrameRec::qFrom2To),
                	Extractor.$double(BranchDFrameRec::pTo2From),
                	Extractor.$double(BranchDFrameRec::qTo2From),
                	Extractor.$double(BranchDFrameRec::flowFromSide),
                	Extractor.$double(BranchDFrameRec::loading)
                )
                // define the column names
                .columnNames("ID", "Name", "Circuit", "Status",
                			"FromBusID", "FromBusNumber", "FromBusName",
                			"ToBusID", "ToBusNumber", "ToBusName",
                			"InService", "BranchCode",
                			"IsXfmr",
                			"R", "X", "B",
                			"LimMvaA", "LimMvaB", "LimMvaC",
							"PFrom2To", "QFrom2To",
							"PTo2From", "QTo2From", "Flow@FromSide", "Loading%")
                .appender();
    }
    
    private static DataFrameAppender<BasicBranchDFrameRec> createBasicInfoAppender() {
		return DataFrame
				.byRow(
					Extractor.$col(BasicBranchDFrameRec::id),
					Extractor.$col(BasicBranchDFrameRec::fromBusId),
					Extractor.$col(BasicBranchDFrameRec::toBusId),
					Extractor.$col(BasicBranchDFrameRec::circuitNum),
					Extractor.$double(BasicBranchDFrameRec::pFrom2To),
					Extractor.$double(BasicBranchDFrameRec::qFrom2To),
					Extractor.$double(BasicBranchDFrameRec::pTo2From),
					Extractor.$double(BasicBranchDFrameRec::qTo2From),
					Extractor.$double(BasicBranchDFrameRec::flowFromSide)
				)
				// define the column names
				.columnNames("ID", "FromBusID", "ToBusID", "CircuitNum",
						"PFrom2To", "QFrom2To",
						"PTo2From", "QTo2From", "Flow@FromSide")
				.appender();
    }
    
    /**
	 * Adapt the AclfNetwork branch data to DataFrame 
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @return the DataFrame containing branch data
	 */
    public DataFrame adapt(AclfNetwork aclfNet) {
       return adapt(aclfNet, branch -> true, true); // default to include all branches and detailed branch information   	
    }

	/**
	 * Adapt the AclfNetwork branch data to DataFrame with options for detailed/basic info
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param isDetailedMode flag to indicate whether to include detailed branch information or only basic information (ID, from/to bus info, status)
	 * @return the DataFrame containing branch	 data
	 */	
    public DataFrame adapt(AclfNetwork aclfNet, boolean isDetailedMode) {
		return adapt(aclfNet, branch -> true, isDetailedMode); // default to include all branches, with option for detailed or basic branch information
	}	

	/**
	 * Adapt the AclfNetwork branch data to DataFrame with options for monitored branch IDs, including all detailed branch information
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param monitoredBranchIDs the set of branch IDs to be included in the DataFrame
	 * @return the DataFrame containing branch data
	 */
	public DataFrame adapt(AclfNetwork aclfNet, Set<String> monitoredBranchIDs) {
       return adapt(aclfNet, monitoredBranchIDs, true); // default to include all branches and detailed branch information   	
    }

	/**
	 * Adapt the AclfNetwork branch data to DataFrame with options for monitored branch IDs and detailed/basic info
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param monitoredBranchIDs the set of branch IDs to be included in the DataFrame
	 * @param isDetailedMode flag to indicate whether to include detailed branch information or only basic information (ID, from/to bus info, status)
	 * @return the DataFrame containing branch data
	 */
	public DataFrame adapt(AclfNetwork aclfNet, Set<String> monitoredBranchIDs, boolean isDetailedMode) {
		   return adapt(aclfNet, 
				        branch -> monitoredBranchIDs == null || monitoredBranchIDs.contains(branch.getId()), 
				        isDetailedMode); // use a predicate to filter branches based on monitoredBranchIDs, with option for detailed or basic branch information
	}
	  
	/**
	* Adapt the AclfNetwork branch data to DataFrame with options for monitored branch IDs and detailed/basic info
	* 
	* @param aclfNet the AclfNetwork object
	 * @param predicate - a predicate to filter which branches to include in the DataFrame (e.g., based on monitored branch IDs)
	 * @param isDetailedMode flag to indicate whether to include detailed branch information or only basic information (ID, from/to bus info, status)
	 * @return the DataFrame containing branch data
	 */	  
	public DataFrame adapt(AclfNetwork aclfNet, Predicate<AclfBranch> predicate, boolean isDetailedMode) {
        // Append rows from the AclfNetwork bus object
		if (isDetailedMode) {
			DataFrameAppender<BranchDFrameRec> appender = createAppender();
			
			double baseMva = aclfNet.getBaseMva();
			for (var branch : aclfNet.getBranchList()) {
				if (predicate.test(branch)) {
					double flowFromSide = branch.powerFrom2To().abs();
					
					double powerFlowMW = flowFromSide * baseMva;
		            double ratingMVA = branch.getRatingMva1();
		            
		            double loadingPercent = 0.0;
		            if (ratingMVA > 0.0) {
		                loadingPercent = Math.abs(powerFlowMW) / ratingMVA * 100.0;
		            }
		            
					appender.append(new BranchDFrameRec(
							branch.getId(),
							branch.getName(),
							branch.getCircuitNumber(),
							branch.isActive(),
							branch.getFromBus().getId(),
							branch.getFromBus().getNumber(),
							branch.getFromBus().getName(),
							branch.getToBus().getId(),
							branch.getToBus().getNumber(),
							branch.getToBus().getName(),
							branch.isActive(),
							branch.getBranchCode(),
							branch.getBranchCode() == AclfBranchCode.XFORMER || branch.getBranchCode() == AclfBranchCode.PS_XFORMER,
							branch.getZ().getReal(),
							branch.getZ().getImaginary(),
							branch.getHShuntY().getImaginary() * 2.0,
							branch.getRatingMvaA(),
							branch.getRatingMvaB(),
							branch.getRatingMvaC(),
							branch.powerFrom2To().getReal(),
							branch.powerFrom2To().getImaginary(),
							branch.powerTo2From().getReal(),
							branch.powerTo2From().getImaginary(),
							flowFromSide,
							loadingPercent)
						);
				}
			}
			// Create the final DataFrame with all columns
			return appender.toDataFrame();
		} else {
			// Basic branch information
			DataFrameAppender<BasicBranchDFrameRec> basicAppender = createBasicInfoAppender();
			
			for (var branch : aclfNet.getBranchList()) {
				if ( predicate.test(branch)) {
					basicAppender.append(new BasicBranchDFrameRec(
							branch.getId(),
							branch.getFromBusId(),
							branch.getToBusId(),
							branch.getCircuitNumber(),
							branch.powerFrom2To().getReal(),
							branch.powerFrom2To().getImaginary(),
							branch.powerTo2From().getReal(),
							branch.powerTo2From().getImaginary(),
							branch.powerFrom2To().abs()));
				}
			}
			// skip the special branches for basic branch info DataFrame, as they may not have the same attributes as regular AC branches
			/* 
			if(aclfNet.getSpecialBranchList() != null) {
				for (var branch : aclfNet.getSpecialBranchList()) {
					if (monitoredBranchIDs == null || monitoredBranchIDs.contains(branch.getId())) {
						if (branch instanceof HvdcLine2T) {
							HvdcLine2T<AclfBus> hvdcBranch = (HvdcLine2T<AclfBus>)branch;
						
							Complex sFrom = hvdcBranch.powerIntoConverter(hvdcBranch.getFromBusId());
							
							Complex sTo = hvdcBranch.powerIntoConverter(hvdcBranch.getToBusId());

							basicAppender.append(new BasicBranchDFrameRec(
									branch.getId(),
									hvdcBranch.getFromBus().getNumber(),
									hvdcBranch.getToBus().getNumber(),
									hvdcBranch.getCircuitNumber(),
									sFrom.getReal(),
									sFrom.getImaginary(),
									sTo.getReal(),
									sTo.getImaginary(),
									sFrom.abs()));
						}
						
					}
				}
			}
			*/

			// Create the final DataFrame with basic columns only
			return basicAppender.toDataFrame();
		}
	}
    
}
