# REGCB inner voltage with zero time constant

REGCB represents the converter as an internal voltage behind its source
impedance. For a positive inner voltage time constant, the internal voltage
is a differential state. A zero-time network solve reads that state without
advancing it. With a zero inner time constant, the same voltage is an
algebraic function of the present terminal voltage and the accepted current
and voltage-filter coordinates.

Previously the algebraic inner voltage was refreshed when the converter took
a differential step. An event-side network solve could reuse the voltage from
the previous topology. The resulting voltage difference across the source
impedance produced an artificial current transient, even though the converter
current and filter coordinates had not advanced.

The network evaluation now reconstructs the inner voltage with the voltage
of the current iterate when the inner lag is bypassed. A live voltage
measurement is used when its filter is also bypassed. Active-power and
reactive-current conventions, limiter priority, converter blocking, the
Norton admittance and differential histories retain their existing roles.
The evaluation reads the states without modifying them.

An independent two-bus reference probe showed that the zero-time-constant
profile holds terminal P/V and Q/V across both fault boundaries. A positive
inner time constant held its internal voltage and changed its current at
the boundary. Both discriminators are covered by tests. The zero-time-
constant test fails against the old model and the existing finite-lag native
trajectory regression passes with the correction.
