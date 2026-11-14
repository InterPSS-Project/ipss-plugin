qInjectBus = 'Bus14';
vMeasureBus = 'Bus13';
dv_dq = senAlgo.calBusSensitivity(SenAnalysisType.QVOLTAGE, qInjectBus, vMeasureBus);
return dv_dq;