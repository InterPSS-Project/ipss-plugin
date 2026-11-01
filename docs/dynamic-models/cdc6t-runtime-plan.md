# CDC6T implementation contract

The source record starts with a quoted DC-link name, followed by the model name
and 32 constants. It has no generator identifier or bus-number prefix. The
record reader now preserves that name and the constant order, including names
containing commas, escaped quotes and slashes. A quoted numeric name remains a
link name. Adjacent machine records are retained.

This is record-decoding support only. Strict import continues to report CDC6T
as unsupported, rather than allowing an incomplete dynamic network to appear
fully supported. No controller, network injection or integration support is
claimed by this change.

## Shared-runtime work

- Resolve the exact named two-terminal DC link and its rectifier/inverter
  terminals. Reject missing or ambiguous ownership before initialization.
- Implement the measured inverter voltage/current and rectifier voltage as
  three differential states using shared equations and reusable storage for
  modified-Euler and staggered integration.
- Keep blocking, bypass, mode-switch timers and recovery history separate from
  trial state evaluation. Preserve the auxiliary power-command interface.
- Implement current-demand limits, voltage/current recovery ramps and converter
  electrical coupling with explicit physical units and network-base conversion.
- Initialize from the solved DC operating point. Publish named states,
  derivatives, timer histories and limiter status without advancing history.
- Qualify ordinary operation, blocking/unblocking, bypass/unbypass, mode changes
  and recovery against independent small-network captures, retaining every
  event-side sample. A static DC load-flow result is not dynamic verification.

The exact source-library identity is retained in private evidence. The source
data sheet establishes layout and state roles; detailed controller equations and
event sequencing still require authoritative evidence and independent probes.

## Retained independent boundary evidence

`tools/cdc6t_reference_probe.py` now produces cold flat and rectifier-fault
captures on the existing small LCC network fixture. The staged RAW marks its
disconnected bus out of service; the original RAW is retained alongside it.
The fixture uses fixed classical machine sources to isolate the DC boundary.
This is a preliminary small-network discriminator, not final SMIB qualification.

Both cold pairs match exactly. The reference confirms three differential states,
18 diagnostic variables and three internal control flags. The flat state values
are 500,000 V, 1,000 A and 505,000 V. The all-sample contract verifies the
five-ohm DC voltage drop, rectifier/inverter power signs and 100 MVA power base,
including startup and duplicate segment/event samples. Flat states are constant;
the fault produces nonzero transducer and terminal-power response.

These tests validate the reference contract only. CDC6T remains unsupported by
the Java dynamic runtime. Implement and compare the controller, state/history
integration and network coupling before changing that status.
