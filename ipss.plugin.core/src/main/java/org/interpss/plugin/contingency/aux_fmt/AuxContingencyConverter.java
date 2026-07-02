package org.interpss.plugin.contingency.aux_fmt;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Objects;

import org.interpss.plugin.contingency.aux_fmt.mapper.AclfNetworkAuxBranchResolver;
import org.interpss.plugin.contingency.aux_fmt.bean.AuxParsedData;
import org.interpss.plugin.contingency.aux_fmt.mapper.AuxToBranchContingencyMapper;
import org.interpss.plugin.contingency.aux_fmt.parser.AuxContingencyParser;
import org.interpss.plugin.contingency.definition.BranchContingencyRecord;
import org.interpss.plugin.contingency.util.ContingencyFileUtil;
import org.interpss.plugin.contingency.util.DclfMultiOutageContingencyHelper;

import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;
import com.interpss.core.algo.dclf.ContingencyAnalysisAlgorithm;
import com.interpss.core.contingency.dclf.DclfMultiOutage;

public class AuxContingencyConverter {
    private final AclfNetwork network;

    public AuxContingencyConverter(AclfNetwork network) {
        this.network = Objects.requireNonNull(network, "network");
    }

    public AuxConversionReport convert(File inputAuxFile, File outputJsonFile, AuxConversionOptions options)
            throws IOException {
        AuxConversionReport report = new AuxConversionReport();
        List<BranchContingencyRecord> records = importContingencyRecords(inputAuxFile, options, report);
        ContingencyFileUtil.exportContingenciesToJson(outputJsonFile, records);
        return report;
    }

    public List<BranchContingencyRecord> importContingencyRecords(
            File inputAuxFile,
            AuxConversionOptions options)
            throws IOException {
        return importContingencyRecords(inputAuxFile, options, new AuxConversionReport());
    }

    public List<BranchContingencyRecord> importContingencyRecords(
            File inputAuxFile,
            AuxConversionOptions options,
            AuxConversionReport report)
            throws IOException {
        AuxParsedData parsedData = new AuxContingencyParser().parse(inputAuxFile);
        report.setContingencyCount(parsedData.getContingencies().size());
        report.setCtgElementCount(parsedData.getCtgElements().size());

        return new AuxToBranchContingencyMapper(options, new AclfNetworkAuxBranchResolver(network))
                .map(parsedData, report);
    }

    public List<DclfMultiOutage> importDclfMultiOutages(
            File inputAuxFile,
            ContingencyAnalysisAlgorithm dclfAlgo,
            AuxConversionOptions options)
            throws IOException, InterpssException {
        List<BranchContingencyRecord> records = importContingencyRecords(inputAuxFile, options);
        return new DclfMultiOutageContingencyHelper(dclfAlgo).createDclfMultiOutageContList(records);
    }
}
