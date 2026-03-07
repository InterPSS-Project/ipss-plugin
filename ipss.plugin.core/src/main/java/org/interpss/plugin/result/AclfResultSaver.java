package org.interpss.plugin.result;

import java.util.Set;

import org.dflib.DataFrame;
import org.dflib.csv.Csv;
import org.dflib.parquet.Parquet;
import org.interpss.plugin.result.dframe.AclfBranchDFrameAdapter;
import org.interpss.plugin.result.dframe.AclfBusDFrameAdapter;

import com.interpss.core.aclf.AclfNetwork;

/**
 * Utility class for saving ACLF results to various file formats.
 * Provides convenience methods for saving bus and branch records to Parquet and CSV formats.
 * Can accept either pre-created DataFrames or AclfNetwork objects (will create records internally).
 * 
 * @author InterPSS Team
 */
public class AclfResultSaver {
	
	// ========== Bus Record Methods with Network Input ==========
	
	/**
	 * Create bus records from AclfNetwork and save to Parquet format.
	 * Uses detailed bus information by default.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/bus_results.parquet")
	 */
	public static void saveBusRecordToParquet(AclfNetwork aclfNet, String filePath) {
		saveBusRecordToParquet(aclfNet, filePath, true);
	}
	
	/**
	 * Create bus records from AclfNetwork and save to Parquet format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/bus_results.parquet")
	 * @param detailed true for detailed bus information, false for basic information only
	 */
	public static void saveBusRecordToParquet(AclfNetwork aclfNet, String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBusDFrameAdapter().adapt(aclfNet, !detailed);
		saveToParquet(df, filePath);
	}
	
	/**
	 * Create bus records from AclfNetwork with monitored bus set and save to Parquet format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param monitoredBusSet set of bus IDs to include (null or empty for all buses)
	 * @param filePath the output file path (e.g., "output/bus_results.parquet")
	 * @param detailed true for detailed bus information, false for basic information only
	 */
	public static void saveBusRecordToParquet(AclfNetwork aclfNet, Set<String> monitoredBusSet, 
			String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBusDFrameAdapter().adapt(aclfNet, monitoredBusSet, detailed);
		saveToParquet(df, filePath);
	}
	
	/**
	 * Create bus records from AclfNetwork and save to CSV format.
	 * Uses detailed bus information by default.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/bus_results.csv")
	 */
	public static void saveBusRecordToCsv(AclfNetwork aclfNet, String filePath) {
		saveBusRecordToCsv(aclfNet, filePath, true);
	}
	
	/**
	 * Create bus records from AclfNetwork and save to CSV format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/bus_results.csv")
	 * @param detailed true for detailed bus information, false for basic information only
	 */
	public static void saveBusRecordToCsv(AclfNetwork aclfNet, String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBusDFrameAdapter().adapt(aclfNet, !detailed);
		saveToCsv(df, filePath);
	}
	
	/**
	 * Create bus records from AclfNetwork with monitored bus set and save to CSV format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param monitoredBusSet set of bus IDs to include (null or empty for all buses)
	 * @param filePath the output file path (e.g., "output/bus_results.csv")
	 * @param detailed true for detailed bus information, false for basic information only
	 */
	public static void saveBusRecordToCsv(AclfNetwork aclfNet, Set<String> monitoredBusSet, 
			String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBusDFrameAdapter().adapt(aclfNet, monitoredBusSet, detailed);
		saveToCsv(df, filePath);
	}
	
	// ========== Branch Record Methods with Network Input ==========
	
	/**
	 * Create branch records from AclfNetwork and save to Parquet format.
	 * Uses detailed branch information by default.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/branch_results.parquet")
	 */
	public static void saveBranchRecordToParquet(AclfNetwork aclfNet, String filePath) {
		saveBranchRecordToParquet(aclfNet, filePath, true);
	}
	
	/**
	 * Create branch records from AclfNetwork and save to Parquet format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/branch_results.parquet")
	 * @param detailed true for detailed branch information, false for basic information only
	 */
	public static void saveBranchRecordToParquet(AclfNetwork aclfNet, String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBranchDFrameAdapter().adapt(aclfNet, !detailed);
		saveToParquet(df, filePath);
	}
	
	/**
	 * Create branch records from AclfNetwork with monitored branch set and save to Parquet format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param monitoredBranchSet set of branch IDs to include (null or empty for all branches)
	 * @param filePath the output file path (e.g., "output/branch_results.parquet")
	 * @param detailed true for detailed branch information, false for basic information only
	 */
	public static void saveBranchRecordToParquet(AclfNetwork aclfNet, Set<String> monitoredBranchSet, 
			String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBranchDFrameAdapter().adapt(aclfNet, monitoredBranchSet, detailed);
		saveToParquet(df, filePath);
	}
	
	/**
	 * Create branch records from AclfNetwork and save to CSV format.
	 * Uses detailed branch information by default.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/branch_results.csv")
	 */
	public static void saveBranchRecordToCsv(AclfNetwork aclfNet, String filePath) {
		saveBranchRecordToCsv(aclfNet, filePath, true);
	}
	
	/**
	 * Create branch records from AclfNetwork and save to CSV format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param filePath the output file path (e.g., "output/branch_results.csv")
	 * @param detailed true for detailed branch information, false for basic information only
	 */
	public static void saveBranchRecordToCsv(AclfNetwork aclfNet, String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBranchDFrameAdapter().adapt(aclfNet, !detailed);
		saveToCsv(df, filePath);
	}
	
	/**
	 * Create branch records from AclfNetwork with monitored branch set and save to CSV format.
	 * 
	 * @param aclfNet the AclfNetwork object
	 * @param monitoredBranchSet set of branch IDs to include (null or empty for all branches)
	 * @param filePath the output file path (e.g., "output/branch_results.csv")
	 * @param detailed true for detailed branch information, false for basic information only
	 */
	public static void saveBranchRecordToCsv(AclfNetwork aclfNet, Set<String> monitoredBranchSet, 
			String filePath, boolean detailed) {
		if (aclfNet == null) {
			throw new IllegalArgumentException("AclfNetwork cannot be null");
		}
		DataFrame df = new AclfBranchDFrameAdapter().adapt(aclfNet, monitoredBranchSet, detailed);
		saveToCsv(df, filePath);
	}
	
	// ========== Generic DataFrame Save Methods ==========
	
	/**
	 * Save any DataFrame to Parquet format
	 * 
	 * @param df the DataFrame to save
	 * @param filePath the output file path (e.g., "output/results.parquet")
	 */
	public static void saveToParquet(DataFrame df, String filePath) {
		if (df == null) {
			throw new IllegalArgumentException("DataFrame cannot be null");
		}
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("File path cannot be null or empty");
		}
		Parquet.saver().save(df, filePath);
	}
	
	/**
	 * Save any DataFrame to CSV format
	 * 
	 * @param df the DataFrame to save
	 * @param filePath the output file path (e.g., "output/results.csv")
	 */
	public static void saveToCsv(DataFrame df, String filePath) {
		if (df == null) {
			throw new IllegalArgumentException("DataFrame cannot be null");
		}
		if (filePath == null || filePath.trim().isEmpty()) {
			throw new IllegalArgumentException("File path cannot be null or empty");
		}
		Csv.saver().save(df, filePath);
	}
}
