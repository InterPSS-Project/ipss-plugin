package org.interpss.plugin.result.dframe.ca;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import org.dflib.DataFrame;
import org.dflib.Extractor;
import org.dflib.builder.DataFrameAppender;

import com.interpss.algo.parallel.BranchCAResultRec;
import com.interpss.core.aclf.AclfBranchCode;

/**
 * Adapter to convert BranchCAResultRec data to DataFrame
 */
public class DclfContingencyDFrameAdapter {
	// Define a record to hold the data for each record
	private static record ContDFrameRec(String branchId, String branchName,
							AclfBranchCode branchCode,   // AclfBranchCode
							boolean xfmr, 
							String contingencyName, String outageBranchId, String outageBranchName,
							double postFlowMW, double lineRatingMW, double loadingPercent) {}
    
    /**
     * Constructor to initialize the DataFrame appender
     */
    public DclfContingencyDFrameAdapter() {
    }
    
    private static DataFrameAppender<ContDFrameRec> createAppender() {
	  	// Define how to pull data from the object into columns
        return DataFrame
                .byRow(
                	Extractor.$col(ContDFrameRec::branchId),
                	Extractor.$col(ContDFrameRec::branchName),
                	Extractor.$col(ContDFrameRec::branchCode),
                	Extractor.$bool(ContDFrameRec::xfmr),
                	Extractor.$col(ContDFrameRec::contingencyName),
                	Extractor.$col(ContDFrameRec::outageBranchId),
                	Extractor.$col(ContDFrameRec::outageBranchName),
                	Extractor.$double(ContDFrameRec::postFlowMW),
                	Extractor.$double(ContDFrameRec::lineRatingMW),
                	Extractor.$double(ContDFrameRec::loadingPercent)
                )
                // define the column names
                .columnNames("BranchID", "BranchName",
                			"BranchCode", "IsXfmr",
                			"ContingencyName", "OutageBranchId", "OutageBranchName",
                			"PostFlowMW", "LineRatingMW", "LoadingPercent")
                .appender();
    }
    
    /**
	 * Adapt the BranchCAResultRec data to a DataFrame
	 * 
	 * @param caResultRecords  ConcurrentLinkedQueue of BranchCAResultRec objects to adapt
	 * @return DataFrame containing BranchCAResultRec data
	 */
    public DataFrame adapt(ConcurrentLinkedQueue<BranchCAResultRec> caResultRecords) {
    	return adapt(caResultRecords, caRec -> true); // Default to include all generators
    }
    
    /**
	 * Adapt the BranchCAResultRec data to a DataFrame
	 * 
	 * @param caResultRecords  ConcurrentLinkedQueue of BranchCAResultRec objects to adapt
	 * @param predicate        Predicate to filter which records to include in the DataFrame
	 * @return DataFrame containing generator data
	 */
    public DataFrame adapt(ConcurrentLinkedQueue<BranchCAResultRec> caResultRecords, Predicate<BranchCAResultRec> predicate) {
    	DataFrameAppender<ContDFrameRec> appender = createAppender();
        // Append rows from the AclfNetwork bus object
    	caResultRecords.forEach( rec -> {
    		if (predicate.test(rec)) {
				appender.append(new ContDFrameRec(
						rec.aclfBranch.getId(), 
						rec.aclfBranch.getName(),
						rec.aclfBranch.getBranchCode(),
						rec.aclfBranch.getBranchCode() == AclfBranchCode.XFORMER || rec.aclfBranch.getBranchCode() == AclfBranchCode.PS_XFORMER,
						rec.contingency.getId(), 
						rec.contingency.getOutageEquip().getBranch().getId(), 
						rec.contingency.getOutageEquip().getBranch().getName(),
						rec.getPostFlowMW(), 
						rec.calBranchRateB(), 
						rec.calLoadingPercent()
				));
    		}
        });

    	// Create the final DataFrame
    	return appender.toDataFrame();    	
    }
}
