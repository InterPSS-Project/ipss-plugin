package org.interpss.threePhase.qsts.opendss;

import org.interpss.threePhase.dataParser.opendss.OpenDSSDataParser;
import org.interpss.threePhase.dataParser.opendss.OpenDSSStaticDataParser;
import org.interpss.threePhase.dataParser.opendss.timeseries.OpenDSSTimeSeriesData;
import org.interpss.threePhase.qsts.QstsStateApplier;
import org.interpss.threePhase.qsts.QstsStudy;

import com.interpss.core.threephase.INetwork3Phase;

public final class OpenDSSQstsStudyFactory {
	private OpenDSSQstsStudyFactory() {
	}

	public static QstsStudy from(OpenDSSDataParser parser) {
		if(parser == null) {
			throw new IllegalArgumentException("OpenDSS QSTS study requires a parser");
		}
		if(!parser.isStaticNetworkMode()) {
			throw new IllegalArgumentException("OpenDSS QSTS studies require OpenDSSStaticDataParser");
		}
		return from(parser.getStaticNetwork(), parser.getTimeSeriesData())
				.setRegulatorControls(parser.getRegulatorControls())
				.setCapacitorControls(parser.getCapacitorControls())
				.setInverterControls(parser.getInverterControls());
	}

	public static QstsStudy from(OpenDSSStaticDataParser parser) {
		return from((OpenDSSDataParser) parser);
	}

	public static QstsStudy from(INetwork3Phase network, OpenDSSTimeSeriesData timeSeriesData) {
		if(timeSeriesData == null) {
			return QstsStudy.from(network, null);
		}
		QstsStateApplier applier = new QstsStateApplier(timeSeriesData.toQstsScheduleData(),
				timeSeriesData.getLoadStateStore(), timeSeriesData.getGeneratorStateStore(),
				timeSeriesData.getStorageStateStore());
		QstsStateApplier.registerNetworkDevices(network, applier.getLoadStateStore(),
				applier.getGeneratorStateStore());
		return QstsStudy.from(network, timeSeriesData.toQstsScheduleData()).setStateApplier(applier)
				.setInverterAdapterStore(timeSeriesData.getInverterAdapterStore());
	}
}
