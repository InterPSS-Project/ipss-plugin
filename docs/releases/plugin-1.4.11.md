# Plugin 1.4.11

CDC6T named DYR records now attach to an exact static LCC owner before load flow.
The default dynamic solver prepares the existing terminal runtime from the solved
DC operating point before device initialization. Private pre-initialization
network copies retain the original source constants and explicit owner identity.
The ordinary fixed-tap, non-capacitor-commutated equipment guards remain active.
Malformed records, missing or ambiguous owners, duplicate attachment and
unsupported equipment fail explicitly.

CSVGN5, SVSMO1T2/T3 and the exact named SVSMO3T2 record are accepted by strict
import when their existing parser, parameter and ownership checks succeed.
SVSMO3T2 retains its partial qualification label. Successful attachment is
separate from complete model trajectory acceptance.

The existing CDC6T and reactive-device equations, integration histories and
trajectory tolerances are unchanged. The documented CDC6T finite-resistance
modified-Euler terminal-voltage gate and broader reactive-device qualification
remain open. Import parity tests compare flat and fault runs with the explicitly
prepared runtime under both integration methods.
