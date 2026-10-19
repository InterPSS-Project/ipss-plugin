# Approved native renewable-model coverage

Generated from RenewableEvidenceInventory and the visually reviewed May 2026 PDF, pages 3-4. Source SHA-256: `b4add4a9d584e69f2a97823f87cb4c40362f1e868b2b9d3c60e3c59ba62af635`.

The source renewable section has 45 rows: 29 approved, 28 with a native PTI mapping, and 27 distinct planned native families because WT1T/WT2T share WT12T1. 26 families currently have loadable runtime paths; HPLNTD remains missing. REPCC has a bounded standard-native runtime and enabled-switching import with fast-band coupled evidence; source REPCCU is not an inferred alias, and slow-band clock precision/other initialization modes remain open. REEC_E uses the proved native reference standard name REECE; the source-listed REECE1/REECEU1 names and wrappers remain unsupported. PVD1 has a blank PTI cell and is excluded from native scope. Approval, source mapping, loadability, recorded profile evidence and complete verification are separate claims. No row below is VERIFIED.

Eight families have native recorded-profile links, eleven have partial native profiles (REGCA1 Iolim, WTDTB1, WTPTB1, WTGWGOA, REECD Paux, WTGIBFFRA kernel, REECE, REGFMB1U, REGFMC1U, REPCGFMC1U and REPCC), six have only the explicitly bounded ANDES coupled comparison, and one has focused tests only. None of the latter seven is established as native simulator conformance. Legacy/retired and manufacturer-package supplemental models need a separate evidence expansion; their source rows are retained below and must not be counted as approved implementations. No new simulator run or complete raw-provenance acceptance is claimed by this inventory.

| Native family | Evidence scope | Focused test | Comparison | Checkpoint | Limitation |
|---|---|---|---|---|---|
| DERA1 | NATIVE_RECORDED_PROFILE | Dera1ModelTest | Dera1NativeSmibConformanceTest | smib-dera1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| HPLNTD | MISSING_IMPLEMENTATION | missing | none | none | Native HPLNTDU/REAXDU data, weighted dispatch and two-/thirteen-state kernels have bounded recorded-input evidence. Exact wrapper decoding, immutable multi-unit link planning and plant-base/physical-Mvar measurement APIs exist separately. A bounded typed REGCA1/REECA1 shared runtime now has flat, command, lifecycle and three-cycle fault checks. Main import, alternate hosts and complete selector/lifecycle acceptance remain missing; cross-method slew residuals are explicit and default modified Euler is unchanged. |
| REECA1 | BOUNDED_DIALECT_COMPARISON | PsseReeca1ControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native reference simulator conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| REECC1 | NATIVE_RECORDED_PROFILE | PsseReecc1ControllerTest | Reecc1NativeConformanceTest | reecc1-bus1062/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| REECD | NATIVE_PARTIAL_PROFILE | PsseReecd1ControllerTest | ReecdAuxiliaryPowerNativeConformanceTest | ReecdAuxiliaryPowerReference | Native reference Paux power-before-voltage-divider and algebraic-update profile only: four cold-paired recorded-input/coupled SMIB profiles at two steps. Both reference-update sides retained. Inactive PI/bypass storage differs; other selectors, limits, lifecycle and complete REECD conformance remain open. This is not WTGIBFFRA implementation evidence. |
| REECE | NATIVE_PARTIAL_PROFILE | PsseReeceModelTest | ReeceRuntimeNativePartialConformanceTest | ReeceBranchNativeReference + ReeceLocalPiNativeReference | Native reference standard REECE eight-ICON/79-CON import and deferred active REGCA/REGCB attachment; twelve recorded-input replay datasets, two cold-paired REGCB hardware-blocking boundary profiles and two-step flat/fault/trip checks. Source-listed REECE1/REECEU1 names/wrappers remain unsupported. Prepared-reference/current-limit callbacks, remaining active-limit branches and independent coupled native/PowerWorld trajectories remain open; not complete conformance. |
| REGCA1 | NATIVE_PARTIAL_PROFILE | PsseRegca1ConverterTest | RegcaCurrentLimitNativeTest | QUARTER,EIGHTH | Native Iolim direction/selected endpoints only; high-voltage boundary, complete fault and instantaneous event-post parity remain open. Inline checkpoint provenance/digests require separate review. |
| REGCB1 | NATIVE_RECORDED_PROFILE | Regcb1ModelTest | Regcb1NativeSmibConformanceTest | smib-regcb1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| REGFMA1 | NATIVE_RECORDED_PROFILE | PsseRegfma1NativeSchemaTest | Regfma1NativeTrajectoryTest | Regfma1NativeReferenceCheckpoints | Fifteen native checkpoint profiles construct DYR in the test; plant-input/lifecycle, lower integral limit and exact event-post gaps remain. Typed/PowerWorld dialect is separate. |
| REGFMB1U | NATIVE_PARTIAL_PROFILE | PsseRegfmb1ModelTest | Regfmb1NativeRuntimePartialConformanceTest | Regfmb1NativeContractReference + Regfmb1SelectorNativeReference | Native reference exact USRMDL 101 1 6 25 14 17 import and network adapter; thirteen fourteen-state replays and two-step flat/fault/trip checks. Source REGFMB1 is not an inferred alias. Modified-/staggered-Euler, active limits, further selectors/lifecycle and complete coupled/PowerWorld acceptance remain open. |
| REGFMC1U | NATIVE_PARTIAL_PROFILE | PsseRegfmc1ModelTest | Regfmc1NativeRuntimePartialConformanceTest | Regfmc1NativeContractReference + Regfmc1SelectorNativeReference | Native reference exact USRMDL 101 1 3 34 11 27 import and hybrid network adapter; twelve eleven-state replays and bounded two-step flat/fault/trip checks. REGFMC1 is not an inferred alias. Dedicated REPCGFMC1U routing exists; precision/history/callback and complete lifecycle/PowerWorld acceptance remain open. |
| REPCA1 | BOUNDED_DIALECT_COMPARISON | PsseRepca1PlantControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native reference simulator conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| REPCC | NATIVE_PARTIAL_PROFILE | PsseRepccModelTest | RepccRuntimeNativeSmibTest | RepccRuntimeNativeReference | Exact 35-ICON/57-CON standard import, dedicated REGCA1/REECA1 reference port, eight typed equilibrium modes and cold-paired flat/three-cycle fault comparisons at two steps. Every history/event-side row and all thirteen plant states are compared. Enabled switching retains per-device G+jB, samples the accepted origin and has four bounded fast-band coupled native comparisons at two steps. Four slow-band profiles retain an explicit clock-only discrepancy, not transient acceptance. REPCCU is not an inferred alias; disabled loops, further initialization/limits, lifecycle, integration/precision floors and independent PowerWorld acceptance remain open. |
| REPCGFMC1U | NATIVE_PARTIAL_PROFILE | PsseRepcgfmc1ModelTest | Repcgfmc1NativeImportSmibTest | Repcgfmc1NativeContractReference + Repcgfmc1KernelNativeReference + Repcgfmc1ImportNativeReference | Exact seven-ICON/54-CON wrapper, deferred REGFMC1U attachment, four-output/base routing and bounded coupled flat/fault/trip profiles. Signed measurements follow documented network-flow directions; native negative-bus inputs fall back and are rejected as direction oracles. Precision/history/callback, further lifecycle and independent PowerWorld acceptance remain open. |
| WT12A1B | FOCUSED_ONLY | PsseWt12a1bModelTest | none | none | No independent WT12A1U_B trajectory link found; the older WT12A1 reference is a different model. |
| WT12T1 | NATIVE_RECORDED_PROFILE | PsseWt12t1ModelTest | mach.Wt1g1PsseSmibConformanceTest | smib-wt12t1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WT1G1 | NATIVE_RECORDED_PROFILE | PsseWt1g1ModelTest | mach.Wt1g1PsseSmibConformanceTest | smib-wt1g1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WT2E1 | NATIVE_RECORDED_PROFILE | PsseWt2e1ModelTest | mach.Wt2e1PsseSmibConformanceTest | smib-wt2e1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WT2G1 | NATIVE_RECORDED_PROFILE | PsseWt2g1ModelTest | mach.Wt2g1PsseSmibConformanceTest | smib-wt2g1/psse.csv | Recorded profile link only; all selectors/limits, cold provenance, clock/event exclusions and channel tolerances still need profile-by-profile review. |
| WTARA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native reference simulator conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| WTDTA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native reference simulator conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| WTDTBU1 | NATIVE_PARTIAL_PROFILE | PsseWtdtb1ModelTest | Wtdtb1NativePartialConformanceTest | Wtdtb1NativeReference | Native reference standard/exact legacy wrapper; 14 cold-repeated profiles. Unit-speed five-state playback is bounded; non-unit absolute integrated-coordinate precision and instantaneous coupled-network event-post parity remain open. Published PowerWorld convention is separate. |
| WTGIBFFRA | NATIVE_PARTIAL_PROFILE | PsseWtgibffraModelTest | WtgibffraNativePartialConformanceTest | WtgibffraNativeReference | Strict native one-ICON/45-CON import and REGCA/REGCB + REECD Paux attachment; seven cold-paired kernel replay profiles, two cold-paired governed-SMIB trajectories and two-step flat/frequency-step/fault/trip tests. Published automatic recovery differs from native reference. Callback parity, nonconvergent frequency/voltage floors, alternate hosts/bases and complete PowerWorld verification remain open. |
| WTGWGOAU | NATIVE_PARTIAL_PROFILE | PsseWtgwgoaModelTest | WtgwgoaNativePartialConformanceTest | WtgwgoaNativeReference | Native reference strict standard/legacy import and REEC reference routing; ten recorded-input profiles plus coupled flat/fault/no-plant/trip checks at two steps. Callback/handoff and instantaneous network-post parity remain open; published second-fault restart is a separate explicit convention. PowerWorld export diagnostic is not accepted evidence. |
| WTPTA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native reference simulator conformance. PowerWorld Type-3 test is finite-error diagnostic only. |
| WTPTBU1 | NATIVE_PARTIAL_PROFILE | PsseWtptb1ModelTest | Wtptb1NativeExtendedConformanceTest | Wtptb1NativeExtendedReference | Native reference strict standard/legacy import, zero-time bypass and recorded-input branch matrix. Coupled two-step flat/fault/trip checks; instantaneous network-post, additional lifecycle/base profiles and complete cross-tool acceptance remain open. Typed PowerWorld cold-repeat artifacts are separate evidence. |
| WTTQA1 | BOUNDED_DIALECT_COMPARISON | PsseType3WindControllerTest | Type3WindAndesTrajectoryTest | type3-wind-bus1062-fault.csv | ANDES-specific WTTQA speed convention and documented REECA VFLAG/REPCA differences: bounded coupled comparison, not native reference simulator conformance. PowerWorld Type-3 test is finite-error diagnostic only. |

## Source columns and implementation presence

These are frozen source-list statuses, not a claim about today's WECC approval. Planned mappings for absent models do not add parser aliases, guessed field counts or dummy runtimes. Descriptive vendor-package cells are retained as comments rather than invented native model identifiers.

| Source row | PSLF | PTI reference simulator | PowerWorld | Source status | Runtime/planned mapping | Loadable primary mapping |
|---|---|---|---|---|---|---|
| REGFM_A1 | regfm_a1 | REGFMA1 | REGFM_A1 | APPROVED | REGFMA1 | yes (not verified) |
| REGFM_B1 | regfm_b1 | REGFMB1 | REGFM_B1 | APPROVED | REGFMB1U | yes (not verified) |
| REGFM_C1 | regfm_c1 | REGFMC1 | REGFM_C1 | APPROVED | REGFMC1U | yes (not verified) |
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
| REEC_E | reec_e | REECEU1, REECE1 | REEC_E | APPROVED | REECE | yes (not verified) |
| WT1T | wt1t | WT12T1 | WT1T and WT12T1 | APPROVED | WT12T1 | yes (not verified) |
| WT1P_B | wt1p_b | WT12A1U_B | WT1P_B | APPROVED | WT12A1B | yes (not verified) |
| WT2T | wt2t | WT12T1 | WT2T | APPROVED | WT12T1 | yes (not verified) |
| WTGT_A | wtgt_a | WTDTAU1, WTDTA1 | WTGT_A | APPROVED | WTDTA1 | yes (not verified) |
| WTGA_A | wtga_a | WTARAU1, WTARA1 | WTGAR_A | APPROVED | WTARA1 | yes (not verified) |
| WTGP_A | wtgp_a | WTPTAU1, WTPTA1 | WTGPT_A | APPROVED | WTPTA1 | yes (not verified) |
| WTGQ_A | wtgq_a | WTTQAU1, WTTQA1 | WTGTRQ_A | APPROVED | WTTQA1 | yes (not verified) |
| WTGWGO | wtgwgo | WTGWGOAU | WTGWGO_A | APPROVED | WTGWGOAU | yes (not verified) |
| WTGIBFFR_A | wtgibffr_a | WTGIBFFRA | WTGIBFFR_A | APPROVED | WTGIBFFRA | yes (not verified) |
| WTGP_B | wtgp_b | WTPTBU1 | WTGPT_B | APPROVED | WTPTBU1 | yes (not verified) |
| WTGT_B | wtgt_b | WTDTBU1 | WTGT_B | APPROVED | WTDTBU1 | yes (not verified) |
| REPC_A | repc_a | REPCAU1, REPCA1; REPCTAU1, REPCTA1 | REPC_A | APPROVED | REPCA1 | yes (not verified) |
| REPC_B | repc_b | PLNTBU1 | REPC_B | UNAPPROVED | PLNTBU1 | yes (not verified) |
| REPC_C | repc_c | REPCCU | REPC_C | APPROVED | REPCC | yes (not verified) |
| REPC_D | repc_d | HPLNTD | REPC_D | APPROVED | HPLNTD | no |
| REPCGFM_C1 | repcgfm_c1 | REPCGFMC1 | REPCGFM_C1 | APPROVED | REPCGFMC1U | yes (not verified) |
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
