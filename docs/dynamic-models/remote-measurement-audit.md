# Dynamic remote measurement audit

Scope is the native DYR paths in the plugin runtime catalog. The inventory
covers 10 synchronous-machine, 62 exciter, 31 governor, 12 stabilizer, 13
converter-machine, 11 electrical-controller, and 5 plant-controller
descriptors. A descriptor counts once regardless of aliases. The audit
traces model inputs in the parser, network builder and runtime and checks
the available evidence for the nonlocal paths.

Power-flow IREG defines the steady-state operating point and reactive
allocation. Dynamic devices have their own signal sources. In particular,
a synchronous generator with remote IREG still evaluates its stator
equations at the physical terminal. Its AVR can use local voltage,
IEEEVC or machine-specific compensated voltage, or signals supplied by
its attached control stack. The power-flow IREG is not a live AVR
measurement selector.

| Model or family | Runtime routing and evidence |
| --- | --- |
| Synchronous electrical models, exciters, and governors | Machine terminal phasor, current and rotor quantities drive the machine and local controller equations. IEEEVC uses terminal voltage and current compensation. WSIEG1 is a separate cross-machine path that routes mechanical power to the configured secondary shaft and has shaft and coupled tests. |
| IEEEST, PSS2A/B/C, PSS3B/C, PSS6C/7C, ST2CUT | Configured buses supply the model's bus-based voltage, voltage derivative or frequency inputs. Machine power and speed use the owning machine. Missing-bus handling follows individual model conventions. IEEEST has native measurement coverage and PSS2A/B and PSS3B have targeted selector tests. Presence of a bus binding does not establish native parity for every selector in the remaining models. |
| PSS4B, PSS4C, PSS5C | native DYR runtime uses machine input channels. PowerWorld's PSS4C serialization has additional bus slots and is a separate input format. |
| WT3E1 | The outer regulator uses the configured remote magnitude and the inner loop retains terminal voltage. This has a native selector comparison and a distinct-voltage regression. The transformer-selector/XCC combination still requires native coverage when the interconnection transformer is specified. |
| WT4E1 | Fixed a parsed but unused bus selector. The selected magnitude initializes the WindVAR reference and sensor and drives the sensor during integration. The inner loop and current conversion use terminal voltage. A missing configured bus follows the native terminal fallback and emits a warning. |
| GEWTECU1 | Open. Initialization uses terminal voltage for the sensor and voltage reference. Runtime can feed the remote magnitude into the regulator, current-squared calculation, LVPL, and the inner voltage loop, and falls back on a missing or near-zero remote voltage. Native remote-mode evidence is needed to determine which paths must stay local and how to handle the fallback. |
| REECA1, REECB1, REECC1 | The parsed voltage selector binds a live bus at initialization and feeds the electrical controller sensing path. A missing nonzero selector raises an initialization error. REECA1 and REECC1 have direct remote-voltage tests and REECB1 has disturbance evidence for remote sensing. |
| WT2E1, REECD, REECE, converter kernels | Their basic electrical paths use local device measurements. Plant references and auxiliary frequency signals are attached separately where the model defines them. WTGIBFFRA takes frequency from a selected bus with terminal fallback; this path is separate from the electrical controller's terminal voltage. |
| REPCA1, REPCC, HPLNTDU | Explicit regulated voltage and branch flow inputs are connected. REPCC and HPLNTDU also bind an independent frequency bus. Existing boundary tests exercise bus selection, branch orientation, compensation, and fallback. Native evidence is profile-specific. |
| REPCGFMC1U | The measured branch supplies plant flow and determines the remote voltage point. A separate frequency selector is resolved with fallback to that point. The runtime has native partial profiles, including branch fallback. |
| PLNTBU1, REAX3BU1, REAX4BU1, REAXDU | PLNTBU1 binds the regulated bus and monitored branch. REAX3BU1/REAX4BU1 take plant output from the matching PLNTBU1 host. HPLNTDU/REAXDU validates the declared plant and relative-variable routing. These are controller-to-controller signals. |
| CSVGN5, SVSMO1T2, SVSMO3T2 | Bus-attached var devices bind a separately selected voltage sensor. They are outside the machine/IBR paths. |

Selective bus-frequency calculation retains required IEEEST, PSS2A/B,
and supported REPCA1 measurements. Other controller stacks which cannot
declare their dependencies keep the full bus-frequency calculation.

## WT4E1 native check

The native simulator, version 36.7.0, used the public synthetic WT4E1 DYR and a two-bus
network with 0.98 pu at the infinite bus. The three DYR variants changed
only the first ICON (local, bus 2, or absent bus 999). The terminal shunt
was applied at 0.2 s and removed at 0.27 s, with a 0.5 ms step and a 1 s
run. All three initialized and completed without a network convergence
message. The declared remote input and state definitions are in the
[Siemens Model Library, WT4E1](https://www.taipower.com.tw/media/syobrwsh/wind-electrical-control_type2-4.pdf?mediaDL=true).

| Selector | Initial reference (pu) | Minimum sensor state (pu) |
| --- | ---: | ---: |
| 0 | 0.97866738 | 0.81868857 |
| 2 | 0.98000002 | 0.92749530 |
| 999 | 0.97866738 | 0.81868857 |

The local and missing-bus native channel CSVs are byte-identical
(SHA-256 `a97445510c689e9d5db75863770d4069726fcc0b8e54cc4d198d834fa8e7af53`).
The remote CSV SHA-256 is
`05284adaf24365f098aa419e79ac42dccd006704b5166e5e62c491e56dda65e3`.
The plugin regression independently changes remote voltage while holding
terminal voltage fixed and checks sensor initialization, both modified
Euler stages, and the inner loop's terminal input. The test fails on the
prior implementation. These checks qualify the sensing path only.
