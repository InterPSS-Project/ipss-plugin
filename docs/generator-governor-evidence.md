# Generator and governor verification evidence

Generated from GeneratorGovernorEvidenceInventory. Covers the three approved generator models and 24 approved native reference simulator governor mappings in the May 2026 catalog.

Each row links a DYR fixture, focused tests, readable checkpoints and a registered trajectory comparison. It does not establish coverage of all selectors, limits, lifecycle events or Texas2k system acceptance. Simulator versions, cold regeneration and raw provenance require the private archive.

POWERWORLD identifies archived PowerWorld exports; PSSE and PSSE_TGOV3D identify native reference simulator exports; ANDES identifies the separate open-source oracle. A reference's presence alone does not prove its comparison passes.

GENQEC's exact standard input is one selector plus sixteen constants; GENQECU and GENQEJU use the allocation wrapper. The former flat eighteen-field interchange is not a native input. Native nonzero selectors normalize to scaled quadratic, separately from the typed three-way setting. GenqecSchemaNativeReference adds ten complete raw histories from twenty cold runs and GenqecNativeSelectorConformanceTest checks imported three-cycle fault equivalence. These establish the schema/selector boundary, not complete machine or cross-tool trajectory parity. See [the scoped source and evidence report](benchmarks/genqec-native-schema-2026-10-18.md).

| Model | Oracle | DYR fixture | Focused tests | Trajectory comparison | Checkpoints |
|---|---|---|---|---|---|
| DEGOV1D | POWERWORLD | SMIB_v33_genrou_degov1d.dyr | DStabNetworkBuilderDegov1dTest | Degov1dIndependentSmibConformanceTest | smib-genrou-degov1d |
| GAST2AD | POWERWORLD | SMIB_v33_genrou_gast2ad.dyr | DStabNetworkBuilderGast2adTest | Gast2adIndependentSmibConformanceTest | smib-genrou-gast2ad |
| GASTD | POWERWORLD | SMIB_v33_genrou_gastd.dyr | DStabNetworkBuilderGastdTest | GastdIndependentSmibConformanceTest | smib-genrou-gastd |
| GASTWDD | POWERWORLD | SMIB_v33_genrou_gastwdd.dyr | DStabNetworkBuilderGastwddTest | GastwddIndependentSmibConformanceTest | smib-genrou-gastwdd |
| GENQEC | PSSE | SMIB_v33_genqecu.dyr | GenqecNativeSchemaTest | mach.GenqecuPsseSmibConformanceTest | smib-genqecu/psse.csv |
| GENQEJ | PSSE | SMIB_v33_genqej.dyr | GenqecNativeSchemaTest | mach.GenqejPsseSmibConformanceTest | smib-genqej/psse.csv |
| GENROU | ANDES | SMIB_v33_genrou.dyr | DStabNetworkBuilderMachineTest | mach.GenrouAndesSmibConformanceTest | genrou-smib-line-trip.csv |
| GGOV1 | POWERWORLD | SMIB_v33_genrou_ggov1.dyr | PsseGgov1GovernorTest | Ggov1IndependentSmibConformanceTest | smib-genrou-ggov1 |
| GGOV1D | POWERWORLD | SMIB_v33_genrou_ggov1d.dyr | PsseGgov1GovernorTest | Ggov1IndependentSmibConformanceTest | smib-genrou-ggov1d |
| H6E | POWERWORLD | SMIB_v33_genrou_h6e_independent.dyr | PsseH6eGovernorTest | H6eIndependentSmibConformanceTest | smib-genrou-h6e |
| HYG3 | POWERWORLD | SMIB_v33_genrou_hyg3_independent.dyr | PsseHyg3GovernorTest | Hyg3IndependentSmibConformanceTest | smib-genrou-hyg3 |
| HYGOV | POWERWORLD | SMIB_v33_genrou_hygov.dyr | PsseHygovGovernorTest | HygovIndependentSmibConformanceTest | smib-genrou-hygov |
| HYGOV2D | POWERWORLD | SMIB_v33_genrou_hygov2d_independent.dyr | DStabNetworkBuilderHygov2dTest | Hygov2dIndependentSmibConformanceTest | smib-genrou-hygov2d |
| HYGOVD | POWERWORLD | SMIB_v33_genrou_hygovd_independent.dyr | PsseHygovGovernorTest | HygovdIndependentSmibConformanceTest | smib-genrou-hygovd |
| HYGOVR | POWERWORLD | SMIB_v33_genrou_hygovr1_independent.dyr | PsseHygovrGovernorTest | Hygovr1IndependentSmibConformanceTest | smib-genrou-hygovr1 |
| IEEEG1 | POWERWORLD | SMIB_v33_genrou_ieeeg1.dyr | DStabNetworkBuilderGovernorTest | Ieeeg1IndependentSmibConformanceTest | smib-genrou-ieeeg1 |
| IEEEG1D | PSSE | SMIB_v33_genrou_ieeeg1d.dyr | DStabNetworkBuilderIeeeg1dTest | Ieeeg1dPsseSmibConformanceTest | smib-genrou-ieeeg1d/psse.csv |
| IEEEG3 | POWERWORLD | SMIB_v33_genrou_ieeeg3.dyr | DStabNetworkBuilderIeeeg3Test | Ieeeg3dIndependentSmibConformanceTest | smib-genrou-ieeeg3 |
| IEEEG3D | POWERWORLD | SMIB_v33_genrou_ieeeg3d.dyr | DStabNetworkBuilderIeeeg3dTest | Ieeeg3dIndependentSmibConformanceTest | smib-genrou-ieeeg3d |
| IEESGOD | POWERWORLD | SMIB_v33_genrou_ieesgod.dyr | DStabNetworkBuilderIeesgodTest | IeesgodIndependentSmibConformanceTest | smib-genrou-ieesgod |
| LCFB1 | POWERWORLD | SMIB_v33_genrou_tgov1_lcfb1.dyr | Lcfb1PrefControllerTest | Lcfb1IndependentSmibConformanceTest | smib-genrou-tgov1-lcfb1 |
| PIDGOVD | POWERWORLD | SMIB_v33_genrou_pidgovd.dyr | DStabNetworkBuilderPidgovdTest | PidgovdIndependentSmibConformanceTest | smib-genrou-pidgovd |
| TGOV1 | POWERWORLD | SMIB_v33_genrou_tgov1.dyr | DStabNetworkBuilderGovernorTest | Tgov1IndependentSmibConformanceTest | smib-genrou-tgov1 |
| TGOV1D | POWERWORLD | SMIB_v33_genrou_tgov1d.dyr | DStabNetworkBuilderTgov1dTest | Tgov1IndependentSmibConformanceTest | smib-genrou-tgov1d |
| TGOV3D | PSSE_TGOV3D | SMIB_v33_genrou_tgov3d.dyr | DStabNetworkBuilderTgov3dTest | Tgov3dIndependentSmibConformanceTest | TGOV3D native 0.1 ms checkpoints |
| WESGOVD | POWERWORLD | SMIB_v33_genrou_wesgovd.dyr | DStabNetworkBuilderWesgovdTest | WesgovdIndependentSmibConformanceTest | smib-genrou-wesgovd |
| WPIDHYD | POWERWORLD | SMIB_v33_genrou_wpidhyd.dyr | DStabNetworkBuilderWpidhydTest | WpidhydIndependentSmibConformanceTest | smib-genrou-wpidhyd |
