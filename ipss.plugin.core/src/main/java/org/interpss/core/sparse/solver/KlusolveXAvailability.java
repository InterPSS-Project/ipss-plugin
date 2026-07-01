package org.interpss.core.sparse.solver;

import org.interpss.core.sparse.solver.klusolvex.KlusolveXLibrary;

/**
 * Runtime availability checks for the native KLUSolveX backend.
 */
public final class KlusolveXAvailability {
	public static final String LIBRARY_PATH_PROPERTY = "ipss.klusolvex.library.path";
	public static final String LIBRARY_NAME_PROPERTY = "ipss.klusolvex.library.name";

	private KlusolveXAvailability() {
	}

	public static boolean isAdapterImplemented() {
		return true;
	}

	public static String unavailableReason() {
		if(!isAdapterImplemented()) {
			return "KLUSolveX native adapter is not implemented yet";
		}
		return "KLUSolveX native library is not loadable; set " + LIBRARY_PATH_PROPERTY
				+ " or " + LIBRARY_NAME_PROPERTY;
	}

	public static boolean isNativeLibraryLoadable() {
		try {
			KlusolveXLibrary.load();
			return true;
		}
		catch(UnsatisfiedLinkError | RuntimeException e) {
			return false;
		}
	}
}
