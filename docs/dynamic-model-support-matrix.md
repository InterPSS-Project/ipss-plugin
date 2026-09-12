# InterPSS PSS/E dynamic-model support matrix

Generated from `DynamicModelCatalog`; do not edit model rows manually.

`LOADABLE` means that a catalog entry, parser path, and runtime class exist. It
does **not** mean that the model has passed equation conformance, a stationary
flat run, disturbance coverage, or independent trajectory comparison.
Verification status is deliberately kept separate from this generated
loadability inventory.

| Model | Category | Aliases | Parameters | Support | Runtime class | Reference |
|---|---|---|---:|---|---|---|
| CIMTR4 | SYNCHRONOUS_MACHINE |  | 13 | LOADABLE | `org.interpss.dstab.mach.Cimtr4Machine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20CIMTR4.htm) |
| GENCLS | SYNCHRONOUS_MACHINE |  | 2 | LOADABLE | `com.interpss.dstab.mach.EConstMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENCLS.htm) |
| GENQEC | SYNCHRONOUS_MACHINE | GENQECU | 18 | LOADABLE | `org.interpss.dstab.mach.GenqecMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEC.htm) |
| GENQEJ | SYNCHRONOUS_MACHINE | GENQEJU | 18 | LOADABLE | `org.interpss.dstab.mach.GenqejMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEJ.htm) |
| GENROU | SYNCHRONOUS_MACHINE | GENROE | 14 | LOADABLE | `com.interpss.dstab.mach.RoundRotorMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENROU.htm) |
| GENSAL | SYNCHRONOUS_MACHINE | GENSAE | 12 | LOADABLE | `com.interpss.dstab.mach.SalientPoleMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENSAL.htm) |
| GENTPJ1 | SYNCHRONOUS_MACHINE |  | 16 | LOADABLE | `org.interpss.dstab.mach.Gentpj1Machine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENTPJ.htm) |
| GENTRA | SYNCHRONOUS_MACHINE |  | 9 | LOADABLE | `org.interpss.dstab.mach.GentraMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENTRA.htm) |
| WT1G1 | SYNCHRONOUS_MACHINE |  | 10 | LOADABLE | `org.interpss.dstab.mach.Wt1g1Machine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20WT1G.htm) |
| WT2G1 | SYNCHRONOUS_MACHINE |  | 19 | LOADABLE | `org.interpss.dstab.mach.Wt2g1Machine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Generator%20WT2G1.htm) |
| CSVGN5 | CONVERTER_MACHINE |  | 15 | LOADABLE | `org.interpss.dstab.svc.Csvgn5Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20CSVGN5.htm) |
| DERA1 | CONVERTER_MACHINE | DERAU1 | 47 | LOADABLE | `org.interpss.dstab.renewable.Dera1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20DER_A.htm) |
| GEWTGCU1 | CONVERTER_MACHINE |  | 26 | LOADABLE | `org.interpss.dstab.mach.Gewtgcu1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Vendor_Specific_Models.pdf) |
| REGCA1 | CONVERTER_MACHINE | REGCAU1 | 15 | LOADABLE | `org.interpss.dstab.renewable.Regca1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm) |
| REGCB1 | CONVERTER_MACHINE | REGCBU1 | 9 | LOADABLE | `org.interpss.dstab.renewable.Regcb1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_B.htm) |
| REGFMA1 | CONVERTER_MACHINE |  | 19 | LOADABLE | `org.interpss.dstab.renewable.Regfma1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGFM_A1.htm) |
| WT3G1 | CONVERTER_MACHINE |  | 6 | LOADABLE | `org.interpss.dstab.mach.Wt3g1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Generator%20WT3G1.htm) |
| WT3G2 | CONVERTER_MACHINE |  | 14 | LOADABLE | `org.interpss.dstab.mach.Wt3g2Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Generator%20WT3G2.htm) |
| WT4G1 | CONVERTER_MACHINE |  | 9 | LOADABLE | `org.interpss.dstab.mach.Wt4g1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Generator%20WT4G1.htm) |
| IEEEVC | COMPENSATOR |  | 2 | LOADABLE | `org.interpss.dstab.mach.IeeeVoltageCompensatedMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Voltage%20Compensator%20IEEEVC.htm) |
| SVSMO1T2 | SWITCHED_SHUNT | SVSMO1T3 | 65 | LOADABLE | `org.interpss.dstab.svc.Svsmo1t2Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Switched%20Shunt%20SVSMO1.htm) |
| AC11C | EXCITER |  | 40 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac11c.Ac11cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC11C.htm) |
| AC1C | EXCITER |  | 23 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac1c.Ac1cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC1C.htm) |
| AC2C | EXCITER |  | 25 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac2c.Ac2cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC2C.htm) |
| AC3C | EXCITER |  | 30 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac3c.Ac3cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC3C.htm) |
| AC4C | EXCITER |  | 12 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac4c.Ac4cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC4C.htm) |
| AC5C | EXCITER |  | 21 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac5c.Ac5cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC5C.htm) |
| AC6C | EXCITER |  | 27 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac6c.Ac6cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC6C.htm) |
| AC7B | EXCITER | ESAC7B | 27 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac7b.Ac7bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC7B%20and%20ESAC7B.htm) |
| AC7C | EXCITER |  | 38 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac7c.Ac7cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC7C.htm) |
| AC8B | EXCITER |  | 21 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac8b.Ac8bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC8B.htm) |
| AC8C | EXCITER | AC8CU1 | 31 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac8c.Ac8cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC8C.htm) |
| AC9C | EXCITER |  | 45 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac9c.Ac9cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC9C.htm) |
| BBSEX1 | EXCITER |  | 11 | LOADABLE | `org.interpss.dstab.control.exc.psse.bbsex1.Bbsex1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20BBSEX1.htm) |
| DC1C | EXCITER |  | 19 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc1c.Dc1cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC1C.htm) |
| DC2C | EXCITER |  | 19 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc2c.Dc2cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC2C.htm) |
| DC3A | EXCITER | ESDC3A | 12 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc3a.Dc3aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC3A%20and%20ESDC3A.htm) |
| DC4B | EXCITER | ESDC4B | 20 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC4B%20and%20ESDC4B.htm) |
| DC4C | EXCITER | DC4CU1 | 28 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc4c.Dc4cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC4C.htm) |
| ESAC1A | EXCITER |  | 19 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac1a.Esac1aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC1A.htm) |
| ESAC2A | EXCITER |  | 22 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac2a.Esac2aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC2A.htm) |
| ESAC3A | EXCITER |  | 22 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac3a.Esac3aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC3A.htm) |
| ESAC4A | EXCITER |  | 10 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac4a.Esac4aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC4A.htm) |
| ESAC5A | EXCITER |  | 15 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac5a.Esac5aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC5A.htm) |
| ESAC6A | EXCITER |  | 23 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac6a.Esac6aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC6A.htm) |
| ESAC8B | EXCITER |  | 15 | LOADABLE | `org.interpss.dstab.control.exc.psse.esac8b.Esac8bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESAC8B_PTI.htm) |
| ESDC1A | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.esdc1a.Esdc1aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESDC1A.htm) |
| ESDC2A | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.esdc2a.Esdc2aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESDC2A.htm) |
| ESST1A | EXCITER |  | 20 | LOADABLE | `org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST1A%20and%20ESST1A_GE.htm) |
| ESST2A | EXCITER |  | 13 | LOADABLE | `org.interpss.dstab.control.exc.psse.esst2a.Esst2aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST2A.htm) |
| ESST3A | EXCITER |  | 21 | LOADABLE | `org.interpss.dstab.control.exc.ieee.y2005.st3a.IEEE2005ST3AExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST3A.htm) |
| ESST4B | EXCITER |  | 17 | LOADABLE | `org.interpss.dstab.control.exc.ieee.y2005.st4b.IEEE2005ST4BExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST4B.htm) |
| ESURRY | EXCITER | EXAC1M | 20 | LOADABLE | `org.interpss.dstab.control.exc.psse.esurry.EsurryExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter.htm) |
| EXAC1 | EXCITER |  | 17 | LOADABLE | `org.interpss.dstab.control.exc.psse.exac1.Exac1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXAC1.htm) |
| EXAC1A | EXCITER |  | 17 | LOADABLE | `org.interpss.dstab.control.exc.psse.exac1a.Exac1aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXAC1A.htm) |
| EXAC2 | EXCITER |  | 23 | LOADABLE | `org.interpss.dstab.control.exc.psse.exac2.Exac2Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXAC2.htm) |
| EXAC4 | EXCITER |  | 10 | LOADABLE | `org.interpss.dstab.control.exc.psse.exac4.Exac4Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXAC4.htm) |
| EXDC2 | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.exdc2.Exdc2Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXDC2_PTI.htm) |
| EXDC2A | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.exdc2a.Exdc2aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXDC2A.htm) |
| EXELI | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.exeli.ExeliExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXELI.htm) |
| EXST1 | EXCITER |  | 12 | LOADABLE | `org.interpss.dstab.control.exc.ieee.y1981.st1.IEEE1981ST1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXST1_PTI.htm) |
| EXST2 | EXCITER |  | 13 | LOADABLE | `org.interpss.dstab.control.exc.psse.exst2.Exst2Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXST2.htm) |
| EXST3 | EXCITER |  | 18 | LOADABLE | `org.interpss.dstab.control.exc.psse.exst3.Exst3Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20EXST3.htm) |
| IEEET1 | EXCITER |  | 14 | LOADABLE | `org.interpss.dstab.control.exc.ieee.y1968.type1.Ieee1968Type1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20IEEET1.htm) |
| IEEET4 | EXCITER | EXDC4 | 11 | LOADABLE | `org.interpss.dstab.control.exc.psse.ieeet4.Ieeet4Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20IEEET4.htm) |
| IEEEX1 | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.ieeex1.Ieeex1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20IEEEX1.htm) |
| IEEEX2 | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.ieeex2.Ieeex2Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20IEEEX2.htm) |
| REXSYS | EXCITER |  | 31 | LOADABLE | `org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REXSY1.htm) |
| SCRX | EXCITER |  | 8 | LOADABLE | `org.interpss.dstab.control.exc.psse.scrx.ScrxExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20SCRX.htm) |
| ST10C | EXCITER |  | 30 | LOADABLE | `org.interpss.dstab.control.exc.psse.st10c.St10cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST10C.htm) |
| ST1C | EXCITER |  | 21 | LOADABLE | `org.interpss.dstab.control.exc.psse.st1c.St1cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST1C.htm) |
| ST2C | EXCITER |  | 25 | LOADABLE | `org.interpss.dstab.control.exc.psse.st2c.St2cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST2C.htm) |
| ST3C | EXCITER |  | 31 | LOADABLE | `org.interpss.dstab.control.exc.psse.st3c.St3cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST3C.htm) |
| ST4C | EXCITER | ST4CU1 | 26 | LOADABLE | `org.interpss.dstab.control.exc.psse.st4c.St4cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST4C.htm) |
| ST5B | EXCITER | ESST5B | 18 | LOADABLE | `org.interpss.dstab.control.exc.psse.st5b.St5bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST5B%20and%20ST5B.htm) |
| ST5C | EXCITER |  | 20 | LOADABLE | `org.interpss.dstab.control.exc.psse.st5c.St5cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST5C.htm) |
| ST6B | EXCITER | ESST6B | 17 | LOADABLE | `org.interpss.dstab.control.exc.psse.st6b.St6bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST6B%20and%20ST6B.htm) |
| ST6C | EXCITER | ST6CU1 | 29 | LOADABLE | `org.interpss.dstab.control.exc.psse.st6c.St6cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST6C.htm) |
| ST7B | EXCITER |  | 16 | LOADABLE | `org.interpss.dstab.control.exc.psse.st7b.St7bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST7B%20and%20ST7B.htm) |
| ST7C | EXCITER |  | 17 | LOADABLE | `org.interpss.dstab.control.exc.psse.st7c.St7cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST7C.htm) |
| ST8C | EXCITER |  | 28 | LOADABLE | `org.interpss.dstab.control.exc.psse.st8c.St8cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST8C.htm) |
| ST9C | EXCITER |  | 22 | LOADABLE | `org.interpss.dstab.control.exc.psse.st9c.St9cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST9C.htm) |
| DEGOV1D | GOVERNOR | DEGOV1DU | 16 | LOADABLE | `org.interpss.dstab.control.gov.psse.degov1.PsseDegov1dGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20DEGOV1%20and%20DEGOV1D.htm) |
| GAST | GOVERNOR |  | 9 | LOADABLE | `org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20GAST_PTI%20and%20GASTD.htm) |
| GAST2AD | GOVERNOR | GAST2ADU | 33 | LOADABLE | `org.interpss.dstab.control.gov.psse.gast2a.PsseGast2adGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20GAST2A.htm) |
| GASTD | GOVERNOR | GASTDU | 12 | LOADABLE | `org.interpss.dstab.control.gov.psse.gast.PsseGASTGasTurGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20GAST_PTI%20and%20GASTD.htm) |
| GASTWDD | GOVERNOR | GASTWDDU | 34 | LOADABLE | `org.interpss.dstab.control.gov.psse.gastwd.PsseGastwddGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20GASTWD%20and%20GASTWDD.htm) |
| GGOV1 | GOVERNOR |  | 35 | LOADABLE | `org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20GGOV1%20and%20GGOV1D.htm) |
| GGOV1D | GOVERNOR | GGOV1DU | 37 | LOADABLE | `org.interpss.dstab.control.gov.psse.ggov1.PsseGgov1Governor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20GGOV1%20and%20GGOV1D.htm) |
| H6E | GOVERNOR | H6EU1 | 63 | LOADABLE | `org.interpss.dstab.control.gov.psse.h6e.PsseH6eGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20H6E.htm) |
| HYG3 | GOVERNOR | HYG3U1 | 37 | LOADABLE | `org.interpss.dstab.control.gov.psse.hyg3.PsseHyg3Governor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20HYG3.htm) |
| HYGOV | GOVERNOR |  | 12 | LOADABLE | `org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20HYGOV%20and%20HYGOVD.htm) |
| HYGOV2D | GOVERNOR | HYGOV2DU | 19 | LOADABLE | `org.interpss.dstab.control.gov.psse.hygov2.PsseHygov2dGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20HYGOV2%20and%20HYGOV2D.htm) |
| HYGOVD | GOVERNOR | HYGOVDU | 15 | LOADABLE | `org.interpss.dstab.control.gov.psse.hygov.PsseHygovGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20HYGOV%20and%20HYGOVD.htm) |
| HYGOVR | GOVERNOR | HYGOVR1, HYGOVRU | 26 | LOADABLE | `org.interpss.dstab.control.gov.psse.hygovr.PsseHygovrGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20HYGOVR.htm) |
| IEEEG1 | GOVERNOR | WSIEG1 | 22 | LOADABLE | `org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEEEG1%2C%20IEEEG1D%20and%20IEEEG1_GE.htm) |
| IEEEG1D | GOVERNOR | IEEEG1SDU | 23 | LOADABLE | `org.interpss.dstab.control.gov.ieee.steamTCDR.IeeeSteamTCDRGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEEEG1%2C%20IEEEG1D%20and%20IEEEG1_GE.htm) |
| IEEEG3 | GOVERNOR |  | 14 | LOADABLE | `org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEEEG3_PTI%20and%20IEEEG3D.htm) |
| IEEEG3D | GOVERNOR | IEEEG3DU | 17 | LOADABLE | `org.interpss.dstab.control.gov.ieee.hydro1981Type3.Ieee1981Type3HydroGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEEEG3_PTI%20and%20IEEEG3D.htm) |
| IEESGO | GOVERNOR |  | 11 | LOADABLE | `org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEESGO%20and%20IEESGOD.htm) |
| IEESGOD | GOVERNOR | IEESGODU | 14 | LOADABLE | `org.interpss.dstab.control.gov.psse.ieesgo.PsseIEESGOSteamTurGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20IEESGO%20and%20IEESGOD.htm) |
| LCFB1 | GOVERNOR | LCFB1_PTI | 9 | LOADABLE | `org.interpss.dstab.control.gov.psse.lcfb1.Lcfb1PrefController` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pref%20Controller%20LCFB1.htm) |
| PIDGOV | GOVERNOR |  | 21 | LOADABLE | `org.interpss.dstab.control.gov.psse.pidgov.PssePidgovdGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20PIDGOV%20and%20PIDGOVD.htm) |
| PIDGOVD | GOVERNOR | PIDGOVDU | 24 | LOADABLE | `org.interpss.dstab.control.gov.psse.pidgov.PssePidgovdGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20PIDGOV%20and%20PIDGOVD.htm) |
| TGOV1 | GOVERNOR |  | 7 | LOADABLE | `org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20TGOV1%20and%20TGOV1D.htm) |
| TGOV1D | GOVERNOR | TGOV1DU | 10 | LOADABLE | `org.interpss.dstab.control.gov.psse.tgov1.PsseTGov1SteamTurGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20TGOV1%20and%20TGOV1D.htm) |
| TGOV3D | GOVERNOR | TGOV3DU | 21 | LOADABLE | `org.interpss.dstab.control.gov.psse.tgov3.PsseTgov3dGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20TGOV3%20and%20TGOV3D.htm) |
| WESGOVD | GOVERNOR | WESGOVDU | 12 | LOADABLE | `org.interpss.dstab.control.gov.psse.wesgov.PsseWesgovdGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WESGOV%20and%20WESGOVD.htm) |
| WPIDHYD | GOVERNOR | WPIDHYDU | 24 | LOADABLE | `org.interpss.dstab.control.gov.psse.wpidhy.PsseWpidhydGovernor` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WPIDHY%20and%20WPIDHYD.htm) |
| IEEEST | STABILIZER |  | 19 | LOADABLE | `org.interpss.dstab.control.pss.psse.ieeest.IeeestStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20IEEEST.htm) |
| PSS1A | STABILIZER |  | 14 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y1992.pss1a.Ieee1992PSS1AStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS1A.htm) |
| PSS2A | STABILIZER |  | 23 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y1992.pss2a.Ieee1992PSS2AStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS2A.htm) |
| PSS2B | STABILIZER |  | 27 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y1992.pss2b.Ieee1992PSS2BStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS2B.htm) |
| PSS2C | STABILIZER | PSS2CU1 | 35 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss2c.Ieee2016PSS2CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS2C.htm) |
| PSS3B | STABILIZER |  | 21 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS3B.htm) |
| PSS3C | STABILIZER |  | 26 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS3C.htm) |
| PSS4B | STABILIZER |  | 75 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS4B.htm) |
| PSS4C | STABILIZER |  | 94 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS4C.htm) |
| PSS5C | STABILIZER |  | 21 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS5C.htm) |
| PSS6C | STABILIZER |  | 34 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS6C.htm) |
| PSS7C | STABILIZER |  | 38 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS7C.htm) |
| PSSSB | STABILIZER |  | 30 | LOADABLE | `org.interpss.dstab.control.pss.psse.psssb.PsssbStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSSSB.htm) |
| ST2CUT | STABILIZER | WSCCST | 20 | LOADABLE | `org.interpss.dstab.control.pss.psse.st2cut.St2cutStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20ST2CUT.htm) |
| UEL1 | UNDER_EXCITATION_LIMITER |  | 15 | LOADABLE | `org.interpss.dstab.control.uel.psse.uel1.Uel1UnderExcitationLimiter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Under%20Excitation%20Limiter%20UEL1.htm) |
| UEL2C | UNDER_EXCITATION_LIMITER | UEL2CU1 | 51 | LOADABLE | `org.interpss.dstab.control.uel.psse.uel2c.Uel2cUnderExcitationLimiter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Under%20Excitation%20Limiter%20UEL2C.htm) |
| OEL2C | OVER_EXCITATION_LIMITER | OEL2CU1 | 43 | LOADABLE | `org.interpss.dstab.control.oel.psse.oel2c.Oel2cOverExcitationLimiter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Over%20Excitation%20Limiter%20OEL2C.htm) |
| GEWTECU1 | ELECTRICAL_CONTROLLER |  | 82 | LOADABLE | `org.interpss.dstab.mach.Gewtecu1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Vendor_Specific_Models.pdf) |
| REECA1 | ELECTRICAL_CONTROLLER | REECAU1 | 51 | LOADABLE | `org.interpss.dstab.renewable.Reeca1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_A.htm) |
| REECB1 | ELECTRICAL_CONTROLLER |  | 30 | LOADABLE | `org.interpss.dstab.renewable.Reecb1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_B.htm) |
| REECC1 | ELECTRICAL_CONTROLLER | REECCU1 | 50 | LOADABLE | `org.interpss.dstab.renewable.Reecc1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_C.htm) |
| REECD | ELECTRICAL_CONTROLLER | REECD1, REECDU1 | 83 | LOADABLE | `org.interpss.dstab.renewable.Reecd1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_D.htm) |
| WT2E1 | ELECTRICAL_CONTROLLER |  | 6 | LOADABLE | `org.interpss.dstab.mach.Wt2e1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Electrical%20Control%20WT2E1.htm) |
| WT3E1 | ELECTRICAL_CONTROLLER |  | 37 | LOADABLE | `org.interpss.dstab.mach.Wt3e1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Electrical%20Control%20WT3E1.htm) |
| WT4E1 | ELECTRICAL_CONTROLLER |  | 27 | LOADABLE | `org.interpss.dstab.mach.Wt4e1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Electrical%20Control%20WT4E1.htm) |
| REPCA1 | PLANT_CONTROLLER | REPCAU1, REPCTA1, REPCTAU1 | 34 | LOADABLE | `org.interpss.dstab.renewable.Repca1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPC_A.htm) |
| GEWT2MU1 | DRIVE_TRAIN |  | 12 | LOADABLE | `org.interpss.dstab.mach.Gewt2mu1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Vendor_Specific_Models.pdf) |
| WT12T1 | DRIVE_TRAIN |  | 5 | LOADABLE | `org.interpss.dstab.mach.Wt12t1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WT12T1.htm) |
| WT3T1 | DRIVE_TRAIN |  | 8 | LOADABLE | `org.interpss.dstab.mach.Wt3t1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WT3T1.htm) |
| WTDTA1 | DRIVE_TRAIN | WTDAT1, WTDTAU1 | 5 | LOADABLE | `org.interpss.dstab.renewable.Wtdta1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WTDTA1.htm) |
| GEWTARU1 | AERODYNAMIC_CONTROLLER |  | 16 | LOADABLE | `org.interpss.dstab.mach.Gewtaru1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Vendor_Specific_Models.pdf) |
| GEWTGDU1 | AERODYNAMIC_CONTROLLER |  | 13 | LOADABLE | `org.interpss.dstab.mach.Gewtgdu1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Vendor_Specific_Models.pdf) |
| WT12A1 | AERODYNAMIC_CONTROLLER |  | 8 | LOADABLE | `org.interpss.dstab.mach.Wt12a1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Aerodynamic%20Model%20WT12A1.htm) |
| WT3P1 | AERODYNAMIC_CONTROLLER |  | 9 | LOADABLE | `org.interpss.dstab.mach.Wt3p1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20WT3P%20and%20WT3P1.htm) |
| WTARA1 | AERODYNAMIC_CONTROLLER | WTARAU1 | 2 | LOADABLE | `org.interpss.dstab.renewable.Wtara1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Aerodynamic%20Model%20WTGA_A.htm) |
| WTPTA1 | PITCH_CONTROLLER | WTPTAU1 | 10 | LOADABLE | `org.interpss.dstab.renewable.Wtpta1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pitch%20Controller%20WTGPT_A.htm) |
| WTTQA1 | TORQUE_CONTROLLER | WTTQAU1 | 16 | LOADABLE | `org.interpss.dstab.renewable.Wttqa1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pref%20Controller%20WTGTRQ_A.htm) |
| CMLDZNU2 | LOAD_CHARACTERISTIC |  | 142 | LOADABLE | `org.interpss.dstab.dynLoad.impl.Cmldznu2Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Load%20Characteristic%20CMPLDW.htm) |
| IEELAR | LOAD_CHARACTERISTIC |  | 14 | LOADABLE | `org.interpss.dstab.dynLoad.impl.IeelLoadModel` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Load%20Characteristic%20IEEL.htm) |
| IEELBL | LOAD_CHARACTERISTIC |  | 14 | LOADABLE | `org.interpss.dstab.dynLoad.impl.IeelLoadModel` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Load%20Characteristic%20IEEL.htm) |
| PERC1 | LOAD_CHARACTERISTIC |  | 30 | LOADABLE | `org.interpss.dstab.dynLoad.impl.Perc1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Load%20Characteristic%20PERC1.htm) |
| LDS3BL | LOAD_PROTECTION |  | 24 | LOADABLE | `org.interpss.dstab.relay.Lds3blRelayModel` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Relay%20Model%20LDS3.htm) |
| LVS3BL | LOAD_PROTECTION |  | 29 | LOADABLE | `org.interpss.dstab.relay.Lvs3blRelayModel` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Relay%20Model%20LVS3.htm) |
| FRQTPAT | GENERATOR_PROTECTION |  | 6 | LOADABLE | `org.interpss.dstab.relay.FrqtpatRelayModel` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Relay%20Model%20FRQDCAT%20and%20FRQTPAT.htm) |
| VTGTPAT | GENERATOR_PROTECTION |  | 6 | LOADABLE | `org.interpss.dstab.relay.VtgtpatRelayModel` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Relay%20Model%20VTGDCAT%20and%20VTGTPAT.htm) |
