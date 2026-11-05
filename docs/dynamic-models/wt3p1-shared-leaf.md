# WT3P1 shared pitch state

WT3P1 exposes its three existing numerical coordinates to a converter bundle:
blade pitch, speed PI integral and compensation integral. The bundle supplies
generator speed deviation, speed reference deviation and active-power order to
the existing pitch equations. Shared rate evaluation is pure, writes directly
into caller storage and reuses the same equation helper as ordinary modified
Euler. It does not call the ordinary pitch `step` or advance a hidden history.

Projection validates every coordinate before changing the model. It preserves
the existing pitch and compensation bounds, and accepts a zero-duration
projection to restore a bundle checkpoint. A pending ordinary predictor bars
shared access. Reinitialization clears an incomplete ordinary stage. The
entered signed pitch-rate parameter and its explicit converted-model policy
remain unchanged; the shared path uses the already prepared rate magnitude.

The focused test compares 2,000 complete predictor/corrector steps with changing
speed, reference and power inputs under both native positive-rate and explicit
converted signed-rate policies. Predictor and accepted coordinates agree exactly
with the ordinary implementation. Separate controls exercise pure repeated RHS,
invalid-state rejection, upper pitch limit, rollback and stage ownership.
Existing native pitch and independent ordinary SMIB controls remain passing.

This leaf alone does not qualify an attached converter bundle under staggered
integration. The enclosing owner must advance converter, electrical, drive-train
and pitch coordinates together, supply live pitch inputs, preserve event history,
and compare a complete coupled fault against independent reference trajectories.
The converted-suite voltage and power accuracy gates remain open.
