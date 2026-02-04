package org.interpss.result;

import org.dflib.DataFrame;
import org.dflib.builder.DataFrameArrayAppender;
import org.interpss.plugin.result.AclfResultAdapter;
import org.interpss.plugin.result.AclfResultContainer;
import org.interpss.plugin.result.bean.AclfBusInfo;
import org.interpss.plugin.result.bean.AclfBranchInfo;
import org.interpss.plugin.result.bean.AclfGenInfo;
import org.interpss.plugin.result.bean.AclfNetInfo;
import org.interpss.util.FileUtil;

import com.interpss.core.CoreObjectFactory;
import com.interpss.core.LoadflowAlgoObjectFactory;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.LoadflowAlgorithm;
import com.interpss.simu.util.sample.SampleTestingCases;

import java.io.FileOutputStream;
import java.io.OutputStream;

public class Bus5_Result_Sample {
    public static void main(String args[]) throws Exception {
        AclfNetwork net = CoreObjectFactory.createAclfNetwork();
        SampleTestingCases.load_LF_5BusSystem(net);

        LoadflowAlgorithm algo = LoadflowAlgoObjectFactory.createLoadflowAlgorithm(net);
        algo.loadflow();

        AclfResultContainer results = new AclfResultAdapter().accept(net);

        // turn the results into a string
        String resultStr = results.toString();
        //System.out.println(resultStr);

        // write the results to a file
        FileUtil.writeText2File("bus5_result.json", resultStr);

        // Convert AclfResultContainer to DataFrames
        DataFrame netDf = toNetInfoDataFrame(results);
        DataFrame busDf = toBusInfoDataFrame(results);
        DataFrame branchDf = toBranchInfoDataFrame(results);
        DataFrame genDf = toGenInfoDataFrame(results);

        // Print DataFrames
        StringBuilder df = new StringBuilder();
        df.append("=== Net Info ===").append(System.lineSeparator())
                .append(netDf).append(System.lineSeparator())
                .append("=== Bus Info ===").append(System.lineSeparator())
                .append(busDf).append(System.lineSeparator())
                .append("=== Branch Info ===").append(System.lineSeparator())
                .append(branchDf).append(System.lineSeparator())
                .append("=== Gen Info ===").append(System.lineSeparator())
                .append(genDf).append(System.lineSeparator());
        FileUtil.writeText2File("DataFrame.txt", df.toString());
    }

    /**
     * Convert AclfNetInfo to DataFrame
     */
    private static DataFrame toNetInfoDataFrame(AclfResultContainer results) {
        AclfNetInfo netInfo = results.getNetResults();
        return DataFrame.byArrayRow("networkId", "networkName", "caseDescription", "numberOfBuses", "numberOfBranches",
                        "loadflowConverged", "maxMismatchReal", "maxMismatchImag",
                        "totalGenReal", "totalGenImag", "totalLoadReal", "totalLoadImag")
                .appender()
                .append(netInfo.getNetworkId(),
                        netInfo.getNetworkName(),
                        netInfo.getCaseDescription(),
                        netInfo.getNumberOfBuses(),
                        netInfo.getNumberOfBranches(),
                        netInfo.isLoadflowConverged(),
                        netInfo.getMaxMismatch().getReal(),
                        netInfo.getMaxMismatch().getImaginary(),
                        netInfo.getTotalGeneration().getReal(),
                        netInfo.getTotalGeneration().getImaginary(),
                        netInfo.getTotalLoad().getReal(),
                        netInfo.getTotalLoad().getImaginary())
                .toDataFrame();
    }

    /**
     * Convert AclfBusInfo list to DataFrame
     */
    private static DataFrame toBusInfoDataFrame(AclfResultContainer results) {
        DataFrameArrayAppender appender = DataFrame.byArrayRow("areaNo", "areaName", "zoneNo", "zoneName",
                        "busId", "busName", "busType", "voltageMag", "voltageAngle",
                        "genP", "genQ", "loadP", "loadQ")
                .appender();

        for (AclfBusInfo bus : results.getBusResults()) {
            appender.append(
                    bus.getAreaNo(),
                    bus.getAreaName(),
                    bus.getZoneNo(),
                    bus.getZoneName(),
                    bus.getBusId(),
                    bus.getBusName(),
                    bus.getBusType(),
                    bus.getBusVoltageMagnitude(),
                    bus.getBusVoltageAnlgle(),
                    bus.getBusGeneration().getReal(),
                    bus.getBusGeneration().getImaginary(),
                    bus.getBusLoad().getReal(),
                    bus.getBusLoad().getImaginary()
            );
        }
        return appender.toDataFrame();
    }

    /**
     * Convert AclfBranchInfo list to DataFrame
     */
    private static DataFrame toBranchInfoDataFrame(AclfResultContainer results) {
        DataFrameArrayAppender appender = DataFrame.byArrayRow("branchId", "branchName", "branchType",
                        "flowFrom2To_P", "flowFrom2To_Q",
                        "flowTo2From_P", "flowTo2From_Q")
                .appender();

        for (AclfBranchInfo branch : results.getBranchResults()) {
            appender.append(
                    branch.getBranchId(),
                    branch.getBranchName(),
                    branch.getBranchType(),
                    branch.getBranchPowerFlowFrom2To().getReal(),
                    branch.getBranchPowerFlowFrom2To().getImaginary(),
                    branch.getBranchPowerFlowTo2From().getReal(),
                    branch.getBranchPowerFlowTo2From().getImaginary()
            );
        }
        return appender.toDataFrame();
    }

    /**
     * Convert AclfGenInfo list to DataFrame
     */
    private static DataFrame toGenInfoDataFrame(AclfResultContainer results) {
        DataFrameArrayAppender appender = DataFrame.byArrayRow("areaNo", "areaName", "zoneNo", "zoneName",
                        "busId", "genId", "genName", "genType", "genP", "genQ")
                .appender();

        for (AclfGenInfo gen : results.getGenResults()) {
            appender.append(
                    gen.getAreaNo(),
                    gen.getAreaName(),
                    gen.getZoneNo(),
                    gen.getZoneName(),
                    gen.getBusId(),
                    gen.getGenId(),
                    gen.getGenName(),
                    gen.getGenType(),
                    gen.getGen().getReal(),
                    gen.getGen().getImaginary()
            );
        }
        return appender.toDataFrame();
    }
}
