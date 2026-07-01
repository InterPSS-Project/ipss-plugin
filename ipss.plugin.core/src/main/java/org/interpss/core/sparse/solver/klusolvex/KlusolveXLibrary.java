package org.interpss.core.sparse.solver.klusolvex;

import org.interpss.core.sparse.solver.KlusolveXAvailability;
import com.sun.jna.Library;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.ptr.IntByReference;

/**
 * JNA mapping for the KLUSolveX C API.
 */
public interface KlusolveXLibrary extends Library {
	long REUSE_SYMBOLIC_FACTORIZATION = 2L;

	static KlusolveXLibrary load() {
		String libraryPath = System.getProperty(KlusolveXAvailability.LIBRARY_PATH_PROPERTY);
		if(libraryPath != null && !libraryPath.isBlank()) {
			return Native.load(libraryPath, KlusolveXLibrary.class);
		}
		String libraryName = System.getProperty(KlusolveXAvailability.LIBRARY_NAME_PROPERTY, "klusolvex");
		return Native.load(libraryName, KlusolveXLibrary.class);
	}

	void SetOptions(Pointer id, long options);
	Pointer NewSparseSet(int nBus);
	int DeleteSparseSet(Pointer id);
	int ZeroSparseSet(Pointer id);
	int FactorSparseMatrix(Pointer id);
	int SolveSparseSet(Pointer id, Memory x, Memory b);
	int AddMatrixElement(Pointer id, int i, int j, Memory value);
	int AddPrimitiveMatrix(Pointer id, int nOrder, Memory nodes, Memory values);
	int SetMatrixElement(Pointer id, int i, int j, Memory value);
	int IncrementMatrixElement(Pointer id, int i, int j, double re, double im);
	int ZeroiseMatrixElement(Pointer id, int i, int j);
	int GetSingularCol(Pointer id, IntByReference result);
}
