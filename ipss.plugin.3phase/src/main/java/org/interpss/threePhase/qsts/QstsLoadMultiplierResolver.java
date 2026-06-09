package org.interpss.threePhase.qsts;

public class QstsLoadMultiplierResolver {
	private final QstsProfileRegistry profileRegistry;

	public QstsLoadMultiplierResolver(QstsProfileRegistry profileRegistry) {
		this.profileRegistry = profileRegistry == null ? new QstsProfileRegistry() : profileRegistry;
	}

	public QstsLoadMultiplier resolve(QstsProfileBinding binding, QstsMode mode, int stepIndex, double globalLoadMult) {
		double global = globalLoadMult > 0.0 ? globalLoadMult : 1.0;
		if(binding == null || mode == null || mode == QstsMode.SNAPSHOT) {
			return new QstsLoadMultiplier(global, global, null);
		}
		if(binding.getStatus() == QstsDeviceStatus.FIXED || binding.getStatus() == QstsDeviceStatus.EXEMPT) {
			return new QstsLoadMultiplier(1.0, 1.0, null);
		}
		String profileId = binding.getProfileId(mode.profileTypeKey());
		if(profileId == null || profileId.isEmpty()) {
			return new QstsLoadMultiplier(global, global, null);
		}
		QstsProfile profile = profileRegistry.get(profileId);
		if(profile == null || profile.getPointCount() == 0) {
			return new QstsLoadMultiplier(global, global, profileId);
		}
		int index = Math.floorMod(stepIndex, profile.getPointCount());
		return new QstsLoadMultiplier(profile.getPMultiplierAtIndex(index) * global,
				profile.getQMultiplierAtIndex(index) * global, profileId);
	}
}
