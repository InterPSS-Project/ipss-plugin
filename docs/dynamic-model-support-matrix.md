# InterPSS PSS/E dynamic-model support matrix

Generated from `DynamicModelCatalog`; do not edit model rows manually.

`LOADABLE` means that a catalog entry, parser path, and runtime class exist. It
does **not** mean that the model has passed equation conformance, a stationary
flat run, a representative disturbance matrix, or an independent-tool
trajectory comparison. Those acceptance results are tracked in
`dynamic-model-coverage-development-plan.md`. In particular, the six Texas2k
cases are loadable and all pass the strict one-second flat-run gate; the
full-system independent fault-parity milestone remains open.

Current Texas2k audit (2026-09-10): `17/17` PSS/E DYR model names are
loadable, and the standard PSS/E `WTDTA1` drive train now has a parser and
runtime path. Acceptance uses PSS/E RAW/DYR plus `_gnet.idv` and
`_MODREMOVE.idv`. Approved-list rows without a native PSS/E model name are
excluded
from coverage counts and the unsupported-model TODO. GE PSLF `.dyd` files are
not discovered, parsed, inventoried, or tested by this workflow.
`6/6` prepared cases pass the
short flat and common Bus-7159 fault execution smokes, `6/6` pass the production
1/240 s strict one-second flat-run gate, and all `15/15` required
location-specific faults complete as execution/sanity checks. Full-stack independent trajectory
acceptance remains `0/6`. REGFMA1 has a registered public PowerWorld
nine-state trajectory contract, HYGOVD has a repeatable direct four-state
PowerWorld contract that reuses core GENROU and the verified HYGOV runtime,
PERC1 has both short-pulse and six-second multi-ramp native PSS/E `PLBVF1`
contracts including cease/reconnect performance. Its published loading-factor
rule and dynamic-load-change Norton-current behavior are covered directly,
and the public Bus-1062 renewable stack has hash-repeatable native PSS/E flat
and fault artifacts covering seven boundary/command channels plus all 16
REGCA1/REECA1/REPCA1 states. The published REPCA1 zero-branch fallback now uses
generator power. Twelve of 13 active-state trajectory comparisons are within
`0.00113 pu`; the remaining end-to-end REECA1 voltage-PI difference reaches
`0.04363 pu`, so this evidence does not promote any Texas2k case to full-stack
trajectory acceptance. Direct integration of the native PSS/E
`Kvi*(PIQ-Vt_filt)` derivative reproduces its reported K+3 state change within
`1.42e-6 pu`, resolving the state definition and equation without gain or
limiter fitting. An exact-initial-condition PSS/E signal play-in separately
reduces the cross-tool voltage-PI residual to `0.001321 pu` and keeps the other
compared REECA1/REPCA1 states within `1.05e-4 pu`, localizing the larger
end-to-end residual to the weak-mode operating-point sensitivity and exported
network/controller boundary rather than the PI equation.
REECA1/REPCA1 now preserve their initialized measurement boundary against only
sub-`1e-8 pu` solver-partition residuals; a focused `2e-8 pu` change remains
observable. A fresh Case-5 local Q/V analysis still identifies Bus 1062 unit 2
`REECA_V_PI`, `REGCA_IQ`, and `REPCA_LEAD_LAG` as the leading candidate-mode
components. The REECA voltage-PI state is an absolute-voltage coordinate while
the local analyzer uses perturbation coordinates, so its limits are
`VMIN-V0` and `VMAX-V0`; all 34 initial perturbations are interior and the
candidate is a valid two-sided local mode. Moving those inactive anti-windup
limits cannot change this operating-point Jacobian.
GGOV1 has an additional native PSS/E machine-trip lifecycle contract, and
LCFB1 has a hash-repeatable direct two-state PowerWorld contract coupled
to TGOV1. DC4B, ST1C, ESST2A, ST2C, ST3C, ST4C, ST5B, ST5C, and ST6B have hash-repeatable direct PowerWorld contracts for
the approved ESDC4B, ESST1C, ESST2A/EXST2A, ESST2C, ESST3C, ESST4C, ESST5B, ESST5C, and ESST6B rows. The validated PowerWorld inventory is 77 artifacts,
including `50/64` approved exciter rows and `23/24` approved PSS/E governor rows with
direct trajectory evidence. BBSEX1 separately has a hash-repeatable native
PSS/E 36.7 boundary/EFD/three-state trajectory contract. IEEEX1 and EXDC2
likewise have native PSS/E boundary/EFD/five-state contracts, IEEET4 has a
native PSS/E boundary/EFD/two-state contract, and EXELI has a native PSS/E
boundary/EFD/eight-state/stabilizer contract. SCRX adds a native PSS/E
boundary/EFD/two-state contract, and EXST2 adds a native PSS/E
boundary/EFD/four-state contract. ST6C, ST7B, ST7C, ST8C, ST9C, and ST10C add native PSS/E
boundary/EFD/five-, four-, five-, five-, four-, and ten-state contracts. Together they raise
combined exact-tool direct exciter evidence to `63/64` without changing the explicitly
PowerWorld-only inventory. PSS2B adds a direct PowerWorld boundary, machine,
exciter, and 11-signal stabilizer trajectory contract, raising direct
PowerWorld stabilizer evidence to `2/15`. PSS2C adds a native PSS/E 36.7
boundary, machine, three-coordinate, and physical-output contract plus an
independent recorded-signal play-in, raising combined exact-tool stabilizer
evidence to `3/15`. IEEEST adds a native PSS/E full-loop and recorded-speed
play-in contract. The same artifact also verifies PSS1A through PSS/E 36.7's
published exact IEEEST parameter correspondence, raising combined exact-tool
stabilizer evidence to `5/15`. PSS3B adds a native PSS/E full-loop,
nine-state, and recorded-power/speed contract, raising combined exact-tool
stabilizer evidence to `6/15`. Current-schema IEEEG1D adds a native PSS/E boundary
and six-state contract, raising combined exact-tool direct governor evidence to
`24/24`; it remains version-blocked only in the explicitly PowerWorld-only inventory.
GENQEJ is likewise unavailable in both installed exact tools (Simulator 24 and
PSS/E Xplore 36.7); the Case-5 production flat-run gate now passes. See
the plan's release checklist before interpreting any
`LOADABLE` row as completed model validation.

| Model | Category | Aliases | Parameters | Support | Runtime class | Reference |
|---|---|---|---:|---|---|---|
| GENCLS | SYNCHRONOUS_MACHINE |  | 2 | LOADABLE | `com.interpss.dstab.mach.EConstMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENCLS.htm) |
| GENQEC | SYNCHRONOUS_MACHINE |  | 18 | LOADABLE | `org.interpss.dstab.mach.GenqecMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEC.htm) |
| GENQEJ | SYNCHRONOUS_MACHINE | GENQEJU | 18 | LOADABLE | `org.interpss.dstab.mach.GenqejMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENQEJ.htm) |
| GENROU | SYNCHRONOUS_MACHINE | GENROE | 14 | LOADABLE | `com.interpss.dstab.mach.RoundRotorMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENROU.htm) |
| GENSAL | SYNCHRONOUS_MACHINE | GENSAE | 12 | LOADABLE | `com.interpss.dstab.mach.SalientPoleMachine` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20GENSAL.htm) |
| REGCA1 | CONVERTER_MACHINE | REGCAU1 | 15 | LOADABLE | `org.interpss.dstab.renewable.Regca1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGC_A.htm) |
| REGFMA1 | CONVERTER_MACHINE |  | 19 | LOADABLE | `org.interpss.dstab.renewable.Regfma1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Machine%20Model%20REGFM_A1.htm) |
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
| AC8C | EXCITER |  | 31 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac8c.Ac8cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC8C.htm) |
| AC9C | EXCITER |  | 45 | LOADABLE | `org.interpss.dstab.control.exc.psse.ac9c.Ac9cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20AC9C.htm) |
| BBSEX1 | EXCITER |  | 11 | LOADABLE | `org.interpss.dstab.control.exc.psse.bbsex1.Bbsex1Exciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20BBSEX1.htm) |
| DC1C | EXCITER |  | 19 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc1c.Dc1cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC1C.htm) |
| DC2C | EXCITER |  | 19 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc2c.Dc2cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC2C.htm) |
| DC3A | EXCITER | ESDC3A | 12 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc3a.Dc3aExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC3A%20and%20ESDC3A.htm) |
| DC4B | EXCITER | ESDC4B | 20 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc4b.Dc4bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC4B%20and%20ESDC4B.htm) |
| DC4C | EXCITER |  | 28 | LOADABLE | `org.interpss.dstab.control.exc.psse.dc4c.Dc4cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20DC4C.htm) |
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
| REXSYS | EXCITER |  | 31 | LOADABLE | `org.interpss.dstab.control.exc.psse.rexsys.RexsysExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REXSY1.htm) |
| SCRX | EXCITER |  | 8 | LOADABLE | `org.interpss.dstab.control.exc.psse.scrx.ScrxExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20SCRX.htm) |
| ST10C | EXCITER |  | 30 | LOADABLE | `org.interpss.dstab.control.exc.psse.st10c.St10cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST10C.htm) |
| ST1C | EXCITER |  | 21 | LOADABLE | `org.interpss.dstab.control.exc.psse.st1c.St1cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST1C.htm) |
| ST2C | EXCITER |  | 25 | LOADABLE | `org.interpss.dstab.control.exc.psse.st2c.St2cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST2C.htm) |
| ST3C | EXCITER |  | 31 | LOADABLE | `org.interpss.dstab.control.exc.psse.st3c.St3cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST3C.htm) |
| ST4C | EXCITER |  | 26 | LOADABLE | `org.interpss.dstab.control.exc.psse.st4c.St4cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST4C.htm) |
| ST5B | EXCITER | ESST5B | 18 | LOADABLE | `org.interpss.dstab.control.exc.psse.st5b.St5bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST5B%20and%20ST5B.htm) |
| ST5C | EXCITER |  | 20 | LOADABLE | `org.interpss.dstab.control.exc.psse.st5c.St5cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST5C.htm) |
| ST6B | EXCITER | ESST6B | 17 | LOADABLE | `org.interpss.dstab.control.exc.psse.st6b.St6bExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ESST6B%20and%20ST6B.htm) |
| ST6C | EXCITER |  | 29 | LOADABLE | `org.interpss.dstab.control.exc.psse.st6c.St6cExciter` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20ST6C.htm) |
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
| PSS2C | STABILIZER |  | 35 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss2c.Ieee2016PSS2CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS2C.htm) |
| PSS3B | STABILIZER |  | 21 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2005.pss3b.Ieee2005PSS3BStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS3B.htm) |
| PSS3C | STABILIZER |  | 24 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss3c.Ieee2016PSS3CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS3C.htm) |
| PSS4B | STABILIZER |  | 75 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2005.pss4b.Ieee2005PSS4BStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS4B.htm) |
| PSS4C | STABILIZER |  | 94 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss4c.Ieee2016PSS4CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS4C.htm) |
| PSS5C | STABILIZER |  | 21 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss5c.Ieee2016PSS5CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS5C.htm) |
| PSS6C | STABILIZER |  | 34 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss6c.Ieee2016PSS6CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS6C.htm) |
| PSS7C | STABILIZER |  | 38 | LOADABLE | `org.interpss.dstab.control.pss.ieee.y2016.pss7c.Ieee2016PSS7CStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20PSS7C.htm) |
| ST2CUT | STABILIZER | WSCCST | 20 | LOADABLE | `org.interpss.dstab.control.pss.psse.st2cut.St2cutStabilizer` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Stabilizer%20ST2CUT.htm) |
| REECA1 | ELECTRICAL_CONTROLLER | REECAU1 | 51 | LOADABLE | `org.interpss.dstab.renewable.Reeca1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_A.htm) |
| REECB1 | ELECTRICAL_CONTROLLER |  | 30 | LOADABLE | `org.interpss.dstab.renewable.Reecb1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Exciter%20REEC_B.htm) |
| REPCA1 | PLANT_CONTROLLER | REPCAU1, REPCTA1, REPCTAU1 | 34 | LOADABLE | `org.interpss.dstab.renewable.Repca1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Plant%20Controller%20REPC_A.htm) |
| WTDTA1 | DRIVE_TRAIN | WTDAT1, WTDTAU1 | 5 | LOADABLE | `org.interpss.dstab.renewable.Wtdta1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Governor%20WTDTA1.htm) |
| WTARA1 | AERODYNAMIC_CONTROLLER | WTARAU1 | 2 | LOADABLE | `org.interpss.dstab.renewable.Wtara1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Aerodynamic%20Model%20WTGA_A.htm) |
| WTPTA1 | PITCH_CONTROLLER | WTPTAU1 | 10 | LOADABLE | `org.interpss.dstab.renewable.Wtpta1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pitch%20Controller%20WTGPT_A.htm) |
| WTTQA1 | TORQUE_CONTROLLER | WTTQAU1 | 16 | LOADABLE | `org.interpss.dstab.renewable.Wttqa1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Pref%20Controller%20WTGTRQ_A.htm) |
| PERC1 | LOAD_CHARACTERISTIC |  | 30 | LOADABLE | `org.interpss.dstab.dynLoad.impl.Perc1Model` | [PowerWorld](https://www.powerworld.com/WebHelp/Content/TransientModels_HTML/Load%20Characteristic%20PERC1.htm) |
