package sample;

import java.io.File;
import java.io.FileNotFoundException;

import org.interpss.CorePluginFactory;
import org.interpss.IpssCorePlugin;
import org.interpss.fadapter.IpssFileAdapter;
import org.interpss.util.AclfNetJsonComparator;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.interpss.common.exp.InterpssException;
import com.interpss.core.aclf.AclfNetwork;

public class Ieee14JSonCompareSample1 {
	public static void main(String[] args) throws InterpssException, JsonIOException, JsonSyntaxException, FileNotFoundException {
		IpssCorePlugin.init();
		
		AclfNetwork aclfNet = CorePluginFactory
				.getFileAdapter(IpssFileAdapter.FileFormat.IEEECDF)
				.load("testdata/adpter/ieee_format/Ieee14Bus.ieee")
				.getAclfNet();	

		new AclfNetJsonComparator("Case1").compareJson(aclfNet, new File("testdata/json/ieee14Bus.json"));
	}
}
