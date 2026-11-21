"""Reject incompatible events before calculating trajectory errors."""

import math


def validate_reference_identity(event, *, case_number, event_type, target_bus,
                                reference_event_times, reference_step_s,
                                timestamp_slack_s=1e-4):
    """Check independently identified reference metadata and stored event edges.

    Reference bus/case/type must come from the supplied dataset's provenance,
    never from the simulation plan being checked. Event timestamps may reflect
    the reference integration grid and its accumulated timestamp roundoff.
    This check does not establish matching device data or preparation settings.
    """
    for field, expected in (("case_number", case_number), ("event_type", event_type),
                            ("target_bus", target_bus)):
        if event.get(field) != expected:
            raise ValueError(f"Reference event mismatch: {field}={event.get(field)!r}; expected {expected!r}")
    if event_type != "bus_fault":
        raise ValueError("Only bus-fault event identity is supported")
    if not math.isfinite(reference_step_s) or reference_step_s <= 0:
        raise ValueError("Reference step must be positive and finite")
    if not math.isfinite(timestamp_slack_s) or timestamp_slack_s < 0:
        raise ValueError("Timestamp slack must be nonnegative and finite")
    if len(reference_event_times) != 2:
        raise ValueError("Expected exactly two reference event boundaries")
    if not reference_event_times[0] < reference_event_times[1]:
        raise ValueError("Reference event boundaries must be ordered")
    for field, observed in zip(("start_s", "clear_s"), reference_event_times):
        requested = event[field]
        if not math.isfinite(requested) or not math.isfinite(observed):
            raise ValueError("Event times must be finite")
        if not requested - timestamp_slack_s <= observed <= requested + reference_step_s + timestamp_slack_s:
            raise ValueError(f"Reference event mismatch: {field}={requested}; recorded boundary={observed}")

