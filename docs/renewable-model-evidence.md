# Approved native renewable-model coverage

Generated from RenewableEvidenceInventory and the visually reviewed May 2026 PDF, pages 3-4. Source SHA-256: `b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.

The source renewable section has 45 rows: 29 approved, 28 with a native PTI mapping, and 27 distinct planned native families because WT1T/WT2T share WT12T1. 20 families currently have loadable runtime paths; seven implementations are genuinely missing. PVD1 has a blank PTI cell and is excluded from native scope. Approval, source mapping, loadability, recorded profile evidence and complete verification are separate claims. No row below is VERIFIED.

Eight families have native recorded-profile links, five have partial native profiles (REGCA1 Iolim, WTDTB1, WTPTB1, WTGWGOA and REECD Paux), six have only the explicitly bounded ANDES coupled comparison, and one has focused tests only. None of the latter seven is established as native simulator conformance. Legacy/retired and manufacturer-package supplemental models need a separate evidence expansion; their source rows are retained below and must not be counted as approved implementations. No new simulator run or complete raw-provenance acceptance is claimed by this inventory.

| Native family | Evidence scope | Focused test | Comparison | Checkpoint | Limitation |
|---|---|---|---|---|---|
| DERA1 | NATIVE_RECORDED_PROFILE | Dera1ModelTest | Dera1NativeSmibConformanceTest | smib-dera1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| HPLNTD | MISSING_IMPLEMENTATION | missing | none | none | No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit. |
| REECA1 | BOUNDED_DIALECT_COMPARISON | PsseReeca1ControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| REECC1 | NATIVE_RECORDED_PROFILE | PsseReecc1ControllerTest | Reecc1NativeConformanceTest | reecc1-bus1062/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| REECD | NATIVE_PARTIAL_PROFILE | PsseReecd1ControllerTest | ReecdAuxiliaryPowerNativeConformanceTest | ReecdAuxiliaryPowerReference | Native 36.7 Paux power-before-voltage-divider and algebraic-update profile only: four cold-paired recorded-input/coupled SMIB profiles at two steps. Both reference-update sides retained. Inactive PI/bypass storage differs; other selectors, limits, lifecycle and complete REECD conformance remain open. This is not WTGIBFFRA implementation evidence. |
| REECE1 | MISSING_IMPLEMENTATION | missing | none | none | No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit. |
| REGCA1 | NATIVE_PARTIAL_PROFILE | PsseRegca1ConverterTest | RegcaCurrentLimitNativeTest | QUARTER,EIGHTH | Native Iolim direction/selected endpoints only; high-voltage boundary, complete fault and instantaneous event-post parity remain open. Inline checkpoint provenance/digests require separate review. |
| REGCB1 | NATIVE_RECORDED_PROFILE | Regcb1ModelTest | Regcb1NativeSmibConformanceTest | smib-regcb1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| REGFMA1 | NATIVE_RECORDED_PROFILE | PsseRegfma1NativeSchemaTest | Regfma1NativeTrajectoryTest | Regfma1NativeReferenceCheckpoints | Fifteen native checkpoint profiles construct DYR in the test; plant-input/lifecycle, lower integral limit and exact event-post gaps remain. Typed/PowerWorld dialect is separate. |
| REGFMB1 | MISSING_IMPLEMENTATION | missing | none | none | No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit. |
| REGFMC1 | MISSING_IMPLEMENTATION | missing | none | none | No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit. |
| REPCA1 | BOUNDED_DIALECT_COMPARISON | PsseRepca1PlantControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| REPCCU | MISSING_IMPLEMENTATION | missing | none | none | No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit. |
| REPCGFMC1 | MISSING_IMPLEMENTATION | missing | none | none | No catalog/parser/runtime implementation. Freeze exact native schema and equations, implement attachment/initialization/limits, then independently verify and commit. |
| WT12A1B | FOCUSED_ONLY | PsseWt12a1bModelTest | none | none | No independent WT12A1U_B trajectory link found; the older WT12A1 reference is a different model. |
| WT12T1 | NATIVE_RECORDED_PROFILE | PsseWt12t1ModelTest | mach.Wt1g1PsseSmibConformanceTest | smib-wt12t1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WT1G1 | NATIVE_RECORDED_PROFILE | PsseWt1g1ModelTest | mach.Wt1g1PsseSmibConformanceTest | smib-wt1g1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WT2E1 | NATIVE_RECORDED_PROFILE | PsseWt2e1ModelTest | mach.Wt2e1PsseSmibConformanceTest | smib-wt2e1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WT2G1 | NATIVE_RECORDED_PROFILE | PsseWt2g1ModelTest | mach.Wt2g1PsseSmibConformanceTest | smib-wt2g1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WTARA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| WTDTA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| WTDTBU1 | NATIVE_PARTIAL_PROFILE | PsseWtdtb1ModelTest | Wtdtb1NativePartialConformanceTest | Wtdtb1NativeReference | Native 36.7 standard/exact legacy wrapper; 14 cold-repeated profiles. Unit-speed five-state playback is bounded; non-unit absolute integrated-coordinate precision and instantaneous coupled-network event-post parity remain open. Published PowerWorld convention is separate. |
| WTGIBFFRA | MISSING_IMPLEMENTATION | missing | none | none | Typed published-equation kernel and seven cold-paired native partial replay profiles exist. No native catalog/parser/host attachment or coupled SMIB path yet; automatic-recovery and callback differences remain explicit. Not loadable or fully verified. |
| WTGWGOAU | NATIVE_PARTIAL_PROFILE | PsseWtgwgoaModelTest | WtgwgoaNativePartialConformanceTest | WtgwgoaNativeReference | Native 36.7 strict standard/legacy import and REEC reference routing; ten recorded-input profiles plus coupled flat/fault/no-plant/trip checks at two steps. Callback/handoff and instantaneous network-post parity remain open; published second-fault restart is a separate explicit convention. PowerWorld export diagnostic is not accepted evidence. |
| WTPTA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| WTPTBU1 | NATIVE_PARTIAL_PROFILE | PsseWtptb1ModelTest | Wtptb1NativeExtendedConformanceTest | Wtptb1NativeExtendedReference | Native 36.7 strict standard/legacy import, zero-time bypass and recorded-input branch matrix. Coupled two-step flat/fault/trip checks; instantaneous network-post, additional lifecycle/base profiles and complete cross-tool acceptance remain open. Typed PowerWorld cold-repeat artifacts are separate evidence. |
| WTTQA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native PSS/E conformance. PowerWorld Type-3 test is finite-error diagnostic only. |

## Source columns and implementation presence

These are frozen source-list statuses, not a claim about today's WECC approval. Planned mappings for absent models do not add parser aliases, guessed field counts or dummy runtimes. Descriptive vendor-package cells are retained as comments rather than invented native model identifiers.

| Source row | PSLF | PTI PSS/E | PowerWorld | Source status | Runtime/planned mapping | Loadable primary mapping |
|---|---|---|---|---|---|---|
| REGFM_A1 | regfm_a1 | REGFMA1 | REGFM_A1 | APPROVED | REGFMA1 | yes (not verified) |
| REGFM_B1 | regfm_b1 | REGFMB1 | REGFM_B1 | APPROVED | REGFMB1 | no |
| REGFM_C1 | regfm_c1 | REGFMC1 | REGFM_C1 | APPROVED | REGFMC1 | no |
| PVD1 | pvd1 | (blank/no concrete identifier) | PVD1 | APPROVED | none | no |
| DER_A | der_a | DERAU1 | DER_A | APPROVED | DERA1 | yes (not verified) |
| REGC_A | regc_a | REGCAU1, REGCA1 | REGC_A | APPROVED | REGCA1 | yes (not verified) |
| REGC_B | regc_b | REGCBU1, REGCB1 | REGC_B | APPROVED | REGCB1 | yes (not verified) |
| WT1G | wt1g | WT1G1 | WT1G and WT1G1 | APPROVED | WT1G1 | yes (not verified) |
| WT2G | wt2g | WT2G1 | WT2G and WT2G1 | APPROVED | WT2G1 | yes (not verified) |
| WT2E | wt2e | WT2E1 | WT2E and WT2E1 | APPROVED | WT2E1 | yes (not verified) |
| REEC_A | reec_a | REECAU1, REECA1 | REEC_A | APPROVED | REECA1 | yes (not verified) |
| REEC_C | reec_c | REECCU1, REECC1 | REEC_C | APPROVED | REECC1 | yes (not verified) |
| REEC_D | reec_d | REECDU1, REECD1 | REEC_D | APPROVED | REECD | yes (not verified) |
| REEC_E | reec_e | REECEU1, REECE1 | REEC_E | APPROVED | REECE1 | no |
| WT1T | wt1t | WT12T1 | WT1T and WT12T1 | APPROVED | WT12T1 | yes (not verified) |
| WT1P_B | wt1p_b | WT12A1U_B | WT1P_B | APPROVED | WT12A1B | yes (not verified) |
| WT2T | wt2t | WT12T1 | WT2T | APPROVED | WT12T1 | yes (not verified) |
| WTGT_A | wtgt_a | WTDTAU1, WTDTA1 | WTGT_A | APPROVED | WTDTA1 | yes (not verified) |
| WTGA_A | wtga_a | WTARAU1, WTARA1 | WTGAR_A | APPROVED | WTARA1 | yes (not verified) |
| WTGP_A | wtgp_a | WTPTAU1, WTPTA1 | WTGPT_A | APPROVED | WTPTA1 | yes (not verified) |
| WTGQ_A | wtgq_a | WTTQAU1, WTTQA1 | WTGTRQ_A | APPROVED | WTTQA1 | yes (not verified) |
| WTGWGO | wtgwgo | WTGWGOAU | WTGWGO_A | APPROVED | WTGWGOAU | yes (not verified) |
| WTGIBFFR_A | wtgibffr_a | WTGIBFFRA | WTGIBFFR_A | APPROVED | WTGIBFFRA | no |
| WTGP_B | wtgp_b | WTPTBU1 | WTGPT_B | APPROVED | WTPTBU1 | yes (not verified) |
| WTGT_B | wtgt_b | WTDTBU1 | WTGT_B | APPROVED | WTDTBU1 | yes (not verified) |
| REPC_A | repc_a | REPCAU1, REPCA1; REPCTAU1, REPCTA1 | REPC_A | APPROVED | REPCA1 | yes (not verified) |
| REPC_B | repc_b | PLNTBU1 | REPC_B | UNAPPROVED | PLNTBU1 | yes (not verified) |
| REPC_C | repc_c | REPCCU | REPC_C | APPROVED | REPCCU | no |
| REPC_D | repc_d | HPLNTD | REPC_D | APPROVED | HPLNTD | no |
| REPCGFM_C1 | repcgfm_c1 | REPCGFMC1 | REPCGFM_C1 | APPROVED | REPCGFMC1 | no |
| GENWRI | genwri | (blank/no concrete identifier) | GENWRI | NEVER_APPROVED | none | no |
| GEWTG | gewtg | (blank/no concrete identifier) | GEWTG | NEVER_APPROVED | none | no |
| WT3G | wt3g | WT3G1 | WT3G and WT3G1 | RETIRED | WT3G1 | yes (not verified) |
| WT4G | wt4g | WT4G1 | WT4G and WT4G1 | RETIRED | WT4G1 | yes (not verified) |
| EXWTG1 | exwtg1 | Not converted (1) | EXWTG1 | NEVER_APPROVED | none | no |
| EXWTGE | exwtge | Not used | EXWTGE | NEVER_APPROVED | none | no |
| WT3E | wt3e | WT3E1 | WT3E and WT3E1 | RETIRED | WT3E1 | yes (not verified) |
| WT4E | wt4e | WT4E1 | WT4E and WT4E1 | RETIRED | WT4E1 | yes (not verified) |
| REEC_B | reec_b | REECBU1, REECB1 | REEC_B | RETIRED | REECB1 | yes (not verified) |
| WT2P | wt2p | WT12A1 | WT2P | RETIRED | WT12A1 | yes (not verified) |
| WT3T | wt3t | WT3T1 | WT3T and WT3T1 | RETIRED | WT3T1 | yes (not verified) |
| WT3P | wt3p | WT3P1 | WT3P and WT3P1 | RETIRED | WT3P1 | yes (not verified) |
| WT4T | wt4t | (blank/no concrete identifier) | WT4T | RETIRED | none | no |
| WNDTGE | wndtge | (blank/no concrete identifier) |  | NEVER_APPROVED | none | no |
| WT1P | wt1p | WT12A1 | WT1P and WT12A1 | RETIRED | WT12A1 | yes (not verified) |
