# GEWTE2 source contract and implementation gate

GEWTE2 remains unsupported. Existing GEWTECU1 support is a different model
contract; it must not be registered as a GEWTE2 alias. No exact legacy runtime,
attachment or independent SMIB acceptance is claimed.

## Established evidence

The [published utility model report](https://www.oasis.oati.com/woa/docs/EES/EESdocs/SISR_PID260.pdf)
contains a legacy GEWTE2 allocation with twelve integer slots, sixty-seven
constants, eighteen states and nine output slots. A separate
[published dynamic record](https://opsportal.spp.org/documents/studies/files/2016_Generation_Studies/REP522_MISO%20AFS%20SPP-DISIS-2016-002_rev2%20-%20Public.pdf)
confirms the `USRMDL` allocation `4 0 12 67 18 9`, followed by twelve integers
and sixty-seven real constants. `Gewte2NativeRecord` now decodes precisely this
wrapper and preserves each value in source order. It is data validation only:
GEWTE2 remains absent from the runtime model catalog.

The utility report prints the constant labels in the following order. Positions
are one-based within the sixty-seven real constants:

| Positions | Published labels in order |
| --- | --- |
| 1–7 | TFV, KPV, KIV, RC, XC, TFP, KPP |
| 8–14 | KIP, PMX, PMN, QMX, QMN, IPMAX, TRV |
| 15–21 | RPMX, RPMN, T_POWER, KQi, VMINCL, VMAXCL, KVi |
| 22–27 | XIQmin, XIQmax, Tv, Tp, Fn, TPav |
| 28–31 | FRa, FRb, FRc, FRd |
| 32–35 | PFRa, PFRb, PFRc, PFRd |
| 36–40 | PFRmax, PFRmin, TW, T_LVPL, V_LVPL |
| 41–45 | SPDW1, SPDWMX, SPDWMN, SPD_LOW, WTTHRES |
| 46–48 | EBST, KDBR, Pdbr_MAX |
| 49–55 | ImaxTD, Iphl, Iqhl, TIpqd, Kqd, Xqd, Kwi |
| 56–62 | dbwi, Tipwi, Twowi, urIwi, drIwi, Pmxwi, Pmnwi |
| 63–67 | Vermx, Vermn, Vfrz, QmxZP, QmnZP |

The report separately prints a remote-control bus, `VARFLG`, `PFAFLG`,
`APCFLG`, `FRFLG`, `POFLAG`, a wind-inertia enabling bit and reactive-droop
branch identifiers. It does **not** label all twelve integer slots by position.
These fields therefore cannot safely be assigned integer indexes from the
report alone. Nor does the report establish state coordinates, output indexes,
controller equations, initialization, or the exact linked model-library
revision. A published library filename is insufficient without its executable
and behavioral checks.

The current GEWTECU1 reader instead requires nine integer slots and sixteen
output slots, alongside sixty-seven constants and eighteen states. Consequently,
equal constant and state counts cannot justify passing a GEWTE2 record through
the newer decoder or reusing its initialization and output coordinates unchanged.

The [conversion vendor release notes](https://www.powerworld.com/patch/october-21-2015)
describe conversion of the legacy suite to generic WT3 models. The
[independent importer support document](https://www.spp.org/documents/67445/tsat%20support%20for%20data%20in%20psse%20format.pdf)
also describes internal conversion. Successful imports through those paths would
qualify the converted models, not exact GEWTE2 equation or lifecycle parity.

## Implementation work

1. Obtain the matching legacy model data sheet and runnable model library. Keep
   their version and hashes in private evidence. Establish the integer order,
   constant bases, state coordinates and output map against that executable.
2. Resolve each selector independently, particularly frequency response versus
   active-power control, converter current priority, remote voltage regulation,
   reactive droop, zero-power operation and optional inertia response. Preserve
   inactive branches rather than assuming the newer selector arrangement.
3. Establish the GEWTG2 converter and mechanical-controller signal contract:
   measured voltage, P/Q, rotor speed, available power, external commands, bases
   and command publication. Validate attachment ownership before initialization.
4. Implement shared equations and reusable state/rate buffers. Reuse numerical
   kernels from GEWTECU1 only where exact equation, limit, initialization and
   history equivalence has been demonstrated. Keep separate model identities.
5. Expose named numerical states, derivatives, published state coordinates,
   limiter status and trip history. Separate trial evaluation from accepted
   history and physical command publication under both modified Euler and
   staggered integration; include rollback and repeated observation checks.
6. Run independent synthetic SMIB equilibrium and disturbances at production and
   refined intervals. Cover voltage/reactive and active/frequency branches,
   current priority, droop, inertia, low-voltage freezing, rate limits, zero-power
   operation and trips. Retain all channels and event-side duplicate samples.

Full implementation awaits the exact legacy schema/equation and executable-host
contracts. Generic conversion and newer-model tests are useful controls but do
not close these requirements.
