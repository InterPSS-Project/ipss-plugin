# AC8B PID and saturation boundary qualification

The 21-CON AC8B now uses directional nonwindup at its PID output limits.
Independent exact-record cold-paired boundary traces supersede the historical
tracking assumption inherited from the ANDES PIDTrackAW equation oracle.
An aggregate PID diagram alone does not determine its antiwindup realization.

The integral rate is `Kir * error`. Hold it only when the unlimited PID is at
or beyond its upper bound with a positive rate, or at/beyond its lower bound
with a negative rate. Inward motion remains allowed while saturated. The
integral is not itself clipped, and the separate VR amplifier limits do not
hold or back-calculate the PID integral. This avoids a derivative kick driving
the integral against the voltage error and suppressing the field long after
voltage recovery.

This correction applies to the exact 21-CON AC8B profile by default. The
15-CON ESAC8B profile shares the runtime but retains its previous tracking
realization pending independent qualification. Rectifier loading, field-current
bases, entered constants, field limits and integration stage/history ownership
are unchanged. The 21-CON profile additionally opts into the independently
qualified saturation cutoff below. Other callers of the shared saturation
helper retain their original behavior.

`Ac8bPidBoundaryTest` covers interior integration, both outward holds, exact
bounds, both inward releases, amplifier-only clipping, staged state ownership,
zero-duration calls, dialect separation and a public synthetic five-state
trajectory with the qualified algebraic (TA=0) amplifier against an adaptive
high-accuracy numerical reference. A separate positive-TA exploratory profile
identified existing VR state-projection error at a limit crossing; it does not
qualify a change to that algorithm, and its measurements remain retained in
private QA. The scoped reference does not widen its error gate to absorb it. The older
ANDES equation test retains its interior equations but uses the qualified PID
boundary instead of claiming agreement with tracking on a saturated branch.
The historical common-profile trajectory tests retain their original inputs
and tolerances; agreement on an inactive limiter is not full nonlinear-model
qualification.

Private source vectors and exact-tool manifests remain exclusively in the
private QA repository. The private qualification checks normalized native
states and prescribed-input trajectories at fine and coarse steps. The native
derivative-lag state is scaled by KDR/TDR relative to the runtime storage.
Endpoint forcing is a component diagnostic, not full coupled trajectory
acceptance. The saturation-initialization difference is addressed by a separate boundary
qualification; neither correction rebases saved states or widens existing
trajectory tolerances.

## Saturation cutoff

The quadratic fit retains its signed intercept A and original normalization.
For 21-CON AC8B, saturation is zero through |A|, including a negative fitted
intercept, then follows the original signed polynomial. Only the cutoff takes
the absolute value. For a negative A this can produce a discontinuous onset;
smoothing it or replacing A inside the polynomial changes the qualified model.
Six cold runs over two distinct synthetic negative-intercept profiles and a
positive-intercept control reproduce this convention with maximum inferred
saturation error below 1e-7 pu. Initialized repeats are exact and entered
constants are unchanged. This is an algebraic boundary qualification, not a
complete coupled-model trajectory acceptance.

Initialization, field feedback and the dynamic field upper limit all use the
same model-specific curve. The 15-CON ESAC8B dialect and the historical
five-argument shared helper retain their signed-A cutoff. Public synthetic
tests cover both curve regions, exact cutoff closure, positive-A bitwise
compatibility, legacy arithmetic, initialized equilibrium and dialect selection.
Private reference files and exact-tool provenance stay in the QA repository.


The shared AC7B/AC8B sensing correction is documented separately in
[compensated voltage boundaries](compensated-exciter-voltage-boundaries.md).
It routes the measurement through the existing machine provider, while keeping
physical supply and ceiling ports separate.
