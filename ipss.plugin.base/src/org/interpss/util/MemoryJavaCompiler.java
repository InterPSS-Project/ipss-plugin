/*
 * @(#)MemoryJavaCompiler.java   
 *
 * Copyright (C) 2007 www.interpss.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU LESSER GENERAL PUBLIC LICENSE
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * @Author Mike Zhou
 * @Version 1.0
 * @Date 02/15/2007
 * 
 *   Revision History
 *   ================
 *
 */

package org.interpss.util;

import static com.interpss.common.util.IpssLogger.ipssLogger;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardLocation;
import javax.tools.ToolProvider;
import javax.tools.JavaFileObject.Kind;

import junit.framework.AssertionFailedError;

import com.interpss.common.util.IpssLogger;
import com.interpss.spring.CoreCommonSpringFactory;

/**
 * In-memory Java file compiler. The code is based test.net.java.privateer on java.net
 */
public class MemoryJavaCompiler {
	private static Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();
	private static JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();

	/**
	 * When there is any editing of source code, the classMap table needs to be cleared 
	 * using this method.
	 */
	public static void clearClassMap() {
		classMap.clear();
	}

	/**
	 * Compile the java source code in memory, load the class into JVM and create a new instance
	 * 
	 * @param name class name "dsl.controller.MyController" or "dsl/controller/MyController"
	 * @param code source string
	 * @return the created instance
	 */
	public static Object javac(final String name, final String code)
			throws Exception {
		//System.out.println(code);
		MemoryJavaCompiler c = new MemoryJavaCompiler();
		try {
			ipssLogger.info("In memory compile Java code, " + name);
			Class<?> klass = c.compileSource(name, code, c
					.getDefaultClassLoader());
			if (klass != null)
				return klass.newInstance();
			else
				throw new Exception(
						"In memory Java compile problem, please constant InterPSS Support");
		} catch (Exception e) {
			IpssLogger.logErr(e);
			return null;
		}
	}

	/**
	 * Compiles a single source file and loads the class with a
	 * default class loader.  The default class loader is the one used
	 * to load the test case class.
	 * 
	 * @param name the name of the class to compile.
	 * @param code the source code of the class.
	 * 
	 * @return the compiled class.
	 * 
	 * @throws AssertionFailedError if compilation fails.
	 */
	public Class<?> compileSource(final String name, final String code) {
		return (compileSource(name, code, getDefaultClassLoader()));
	}

	/**
	 * Checks that the platform is Java 1.6.  If not throw an error
	 * with a friendlier message than NoClassDefFoundError.
	 * 
	 * @throws Error if not running on Java 1.6 or later.
	 */
	private boolean checkJava16() {
		try {
			Class.forName("javax.tools.ToolProvider");
		} catch (final ClassNotFoundException e) {
			ipssLogger.severe("Require Java 1.6 or later." + e.toString());
			return false;
		}
		return true;
	}

	/**
	 * Compiles a single source file and loads the class.
	 * 
	 * @param name the name of the class to compile.
	 * @param code the source code of the class.
	 * @param parentLoader the parent classloader to use when loading classes.
	 * 
	 * @return the compiled class.
	 * 
	 * @throws AssertionFailedError if compilation fails.
	 */
	protected Class<?> compileSource(final String name, final String code,
			final ClassLoader parentLoader) {
		if (classMap.get(name) == null) {
			Map<String, Class<?>> result = compileSources(Collections
					.singleton(new SourceFile(name, code)), parentLoader);
			if (result != null)
				classMap.put(name, result.get(name));
			else
				return null;
		}
		return classMap.get(name);
	}

	/**
	 * Compiles multiple sources file and loads the classes.
	 *
	 * @param sourceFiles the source files to compile.
	 * 
	 * @return a map of compiled classes.  This maps class names to
	 * 			Class objects.
	 * 
	 * @throws AssertionFailedError if compilation fails.
	 */
	protected Map<String, Class<?>> compileSources(
			final Collection<? extends SourceFile> sourceFiles) {
		return (compileSources(sourceFiles, getDefaultClassLoader()));
	}

	/**
	 * Returns the default classloader to use when compiling sources
	 * and no classloader is explicitly defined.  Defaults to the 
	 * thread context classloader.
	 * 
	 * @return a classloader.
	 */
	protected ClassLoader getDefaultClassLoader() {
		ClassLoader parent = Thread.currentThread().getContextClassLoader();
		if (parent == null)
			parent = getClass().getClassLoader();

		return (parent);
	}

	/**
	 * Compiles multiple sources file and loads the classes.
	 *
	 * @param sourceFiles the source files to compile.
	 * @param parentLoader the parent classloader to use when loading classes.
	 * 
	 * @return a map of compiled classes.  This maps class names to
	 * 			Class objects.
	 * 
	 * @throws AssertionFailedError if compilation fails.
	 */
	protected Map<String, Class<?>> compileSources(
			final Collection<? extends SourceFile> sourceFiles,
			final ClassLoader parentLoader) {
		checkJava16();

		//If not running on JDK, compiler will be null
		if (compiler == null) {
			ipssLogger.severe(
							"Compiler not available.  This may happen if "
									+ "running on JRE instead of JDK.  Please use a full "
									+ "JDK to run tests.  "
									+ "javax.tools.ToolProvider.getSystemJavaCompiler() returned null.");
			return null;
		}

		MemoryOutputJavaFileManager fileManager = new MemoryOutputJavaFileManager(
				compiler.getStandardFileManager(null, null, null));
		for (final URL url : getClassPathUrls()) {
			fileManager.addClassPathUrl(url);
		}

		List<MemorySourceJavaFileObject> compilationUnits = new ArrayList<MemorySourceJavaFileObject>(
				sourceFiles.size());
		for (final SourceFile sourceFile : sourceFiles) {
			compilationUnits.add(new MemorySourceJavaFileObject(sourceFile
					.getClassName()
					+ ".java", sourceFile.getSourceCode()));
		}

		DiagnosticCollector<JavaFileObject> diagnosticCollector = new DiagnosticCollector<JavaFileObject>();

		Boolean result = compiler.getTask(null, fileManager,
				diagnosticCollector, null, null, compilationUnits).call();

		if (!Boolean.TRUE.equals(result)) {
			CoreCommonSpringFactory.getIpssMsgHub().sendErrorMsg(
					"Java compile error, "
							+ diagnosticCollector.getDiagnostics().toString());
			return null;
		}

		ClassLoader loader = new JavaFileManagerClassLoader(fileManager,
				parentLoader);
		Map<String, Class<?>> classMap = new HashMap<String, Class<?>>();
		for (final SourceFile sourceFile : sourceFiles) {
			String className = sourceFile.getClassName();
			try {
				String classDotName = className.replace('/', '.');
				Class<?> clazz = Class.forName(classDotName, true, loader);
				classMap.put(className, clazz);
			} catch (final ClassNotFoundException e) {
				CoreCommonSpringFactory.getIpssMsgHub().sendErrorMsg(
						"Class loading error, " + e.toString());
				return null;
			}
		}

		return (classMap);
	}

	/**
	 * Returns a list of base URLs to add to the classpath when
	 * compiling.
	 * <p>
	 * 
	 * This method returns an empty list.  Subclasses may override to use
	 * a different classpath.
	 * 
	 * @return a list of classpath URLs.
	 */
	protected List<URL> getClassPathUrls() {
		return (Collections.emptyList());
	}

	/**
	 * Represents a source file with source code.
	 * 
	 * @author prunge
	 */
	public static class SourceFile {
		private final String className;
		private final String sourceCode;

		/**
		 * Constructs a <code>SourceFile</code>.
		 *
		 * @param className the name of the class the source is for.
		 * @param sourceCode Java source code.
		 * 
		 * @throws NullPointerException if any parameter is null.
		 */
		public SourceFile(final String className, final String sourceCode) {
			super();

			if (className == null)
				throw new NullPointerException("className == null");
			if (sourceCode == null)
				throw new NullPointerException("sourceCode == null");

			this.className = className;
			this.sourceCode = sourceCode;
		}

		/**
		 * Returns the class name.
		 * 
		 * @return the class name.
		 */
		public String getClassName() {
			return (className);
		}

		/**
		 * Returns the source code.
		 * 
		 * @return the source code.
		 */
		public String getSourceCode() {
			return (sourceCode);
		}

		/**
		 * Returns the class name.
		 * 
		 * @return the class name.
		 */
		@Override
		public String toString() {
			return (getClassName());
		}
	}
}

/**
 * A class loader that loads classes generated from a Java file manager.
 * This can be used in conjunction with the compiler API to compile and run
 * classes on the fly.
 * 
 * @author prunge
 */
class JavaFileManagerClassLoader extends ClassLoader {
	private final JavaFileManager fileManager;

	/**
	 * Constructs a <code>ClassDataClassLoader</code>.
	 * 
	 * @param fileManager the file manager to read classes from.
	 * 
	 * @throws NullPointerException if <code>fileManager</code>
	 * 			is null.
	 */
	public JavaFileManagerClassLoader(final JavaFileManager fileManager) {
		super();

		if (fileManager == null)
			throw new NullPointerException("fileManager == null");

		this.fileManager = fileManager;
	}

	/**
	 * Constructs a <code>ClassDataClassLoader</code>.
	 *
	 * @param fileManager the file manager to read classes from.
	 * @param parent the parent classloader to delegate to if a class
	 * 			is not found in the file manager.
	 * 
	 * @throws NullPointerException if <code>fileManager</code>
	 * 			is null.
	 */
	public JavaFileManagerClassLoader(final JavaFileManager fileManager,
			final ClassLoader parent) {
		super(parent);

		if (fileManager == null)
			throw new NullPointerException("fileManager == null");

		this.fileManager = fileManager;
	}

	@Override
	protected Class<?> findClass(final String name)
			throws ClassNotFoundException {
		try {
			JavaFileObject classFile = fileManager.getJavaFileForInput(
					StandardLocation.CLASS_OUTPUT, name, Kind.CLASS);

			if (classFile != null) {
				byte[] classData = readClassData(classFile);
				Class<?> clazz = defineClass(name, classData, 0,
						classData.length);

				return (clazz);
			} else {
				return (super.findClass(name));
			}
		} catch (final IOException e) {
			throw new ClassNotFoundException(name, e);
		}
	}

	/**
	 * Reads all class file data into a byte array from the given file
	 * object.
	 * 
	 * @param classFile the class file to read.
	 * 
	 * @return the class data.
	 * 
	 * @throws IOException if an I/O error occurs.
	 */
	private byte[] readClassData(final JavaFileObject classFile)
			throws IOException {
		InputStream classStream = classFile.openInputStream();
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		int n;
		byte[] buf = new byte[4096];
		do {
			n = classStream.read(buf);
			if (n >= 0)
				bos.write(buf, 0, n);
		} while (n > 0);

		return (bos.toByteArray());
	}

}

/**
 * A file object that retains contents in memory and does not write
 * out to disk.
 * 
 * @author prunge
 */
class MemoryOutputJavaFileObject extends SimpleJavaFileObject {
	private ByteArrayOutputStream outputStream;

	/**
	 * Constructs a <code>MemoryOutputJavaFileObject</code>.
	 *
	 * @param uri the URI of the output file.
	 * @param kind the file type.
	 */
	public MemoryOutputJavaFileObject(final URI uri, final Kind kind) {
		super(uri, kind);
	}

	/**
	 * Opens an output stream to write to the file.  This writes to 
	 * memory.  This clears any existing output in the file.
	 * 
	 * @return an output stream.
	 * 
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public OutputStream openOutputStream() throws IOException {
		outputStream = new ByteArrayOutputStream();
		return (outputStream);
	}

	/**
	 * Opens an input stream to the file data.  If the file has never
	 * been written the input stream will contain no data (i.e. length=0).
	 * 
	 * @return an input stream.
	 * 
	 * @throws IOException if an I/O error occurs.
	 */
	@Override
	public InputStream openInputStream() throws IOException {
		if (outputStream != null)
			return (new ByteArrayInputStream(outputStream.toByteArray()));
		else
			return (new ByteArrayInputStream(new byte[0]));
	}
}

/**
 * A java file manager that stores output in memory, delegating all other
 * functions to another file manager.
 * 
 * @author prunge
 */
class MemoryOutputJavaFileManager extends
		ForwardingJavaFileManager<JavaFileManager> {
	/**
	 * Maps class names to file objects.
	 */
	private final Map<String, MemoryOutputJavaFileObject> outputMap;

	private final List<URL> classPathUrls;

	/**
	 * Constructs a <code>MemoryOutputJavaFileManager</code>.
	 *
	 * @param fileManager the underlying file manager to use.
	 */
	public MemoryOutputJavaFileManager(final JavaFileManager fileManager) {
		super(fileManager);

		outputMap = new HashMap<String, MemoryOutputJavaFileObject>();
		classPathUrls = new ArrayList<URL>();
	}

	/**
	 * Adds a URL that classes may be loaded from.  All classes from this
	 * URL will be added to the classpath.
	 * 
	 * @param url the URL to add.
	 * 
	 * @throws NullPointerException if <code>url</code> is null.
	 */
	public void addClassPathUrl(final URL url) {
		if (url == null)
			throw new NullPointerException("url == null");

		classPathUrls.add(url);
	}

	/**
	 * Returns the base URL of the specified class.
	 * <p>
	 * 
	 * For example, if <code>java.lang.String</code> exists at 
	 * http://base.net/parent/java/lang/String.class, the base URL
	 * is http://base.net/parent/.
	 * 
	 * @param clazz the class.
	 * 
	 * @return a base URL where the class is located.
	 * 
	 * @throws IllegalArgumentException if a URL cannot be obtained.
	 */
	public static URL baseUrlOfClass(final Class<?> clazz) {
		try {
			String name = clazz.getName();
			URL url = clazz
					.getResource("/" + name.replace('.', '/') + ".class");
			int curPos = 0;
			do {
				curPos = name.indexOf('.', curPos + 1);
				if (curPos >= 0)
					url = new URL(url, "..");
			} while (curPos >= 0);

			return (url);
		} catch (final MalformedURLException e) {
			throw new IllegalArgumentException("Invalid URL for class "
					+ clazz.getName(), e);
		}
	}

	@Override
	public JavaFileObject getJavaFileForOutput(final Location location,
			final String className, final Kind kind, final FileObject sibling)
			throws IOException {
		if (kind != Kind.CLASS)
			throw new IOException("Only class output supported, kind=" + kind);

		try {
			MemoryOutputJavaFileObject output = new MemoryOutputJavaFileObject(
					new URI(className), kind);

			outputMap.put(className, output);

			return (output);
		} catch (final URISyntaxException e) {
			throw new IOException(e);
		}
	}

	@Override
	public JavaFileObject getJavaFileForInput(final Location location,
			final String className, final Kind kind) throws IOException {
		JavaFileObject result;
		if (StandardLocation.CLASS_OUTPUT == location && Kind.CLASS == kind) {
			result = outputMap.get(className);
			if (result == null)
				result = super.getJavaFileForInput(location, className, kind);
		} else
			result = super.getJavaFileForInput(location, className, kind);

		return (result);
	}

	@Override
	public String inferBinaryName(final Location location,
			final JavaFileObject file) {
		if (file instanceof UrlJavaFileObject) {
			UrlJavaFileObject urlFile = (UrlJavaFileObject) file;
			return (urlFile.getBinaryName());
		} else
			return (super.inferBinaryName(location, file));
	}

	@Override
	public Iterable<JavaFileObject> list(final Location location,
			final String packageName, final Set<Kind> kinds,
			final boolean recurse) throws IOException {
		//Special handling for Privateer classes when building with Maven
		//Maven does not set the classpath but instead uses a custom
		//classloader to load test classes which means the compiler
		//tool cannot normally see standard Privateer classes so 
		//we put in a workaround here
		if (StandardLocation.CLASS_PATH == location
				&& kinds.contains(Kind.CLASS)) {
			List<JavaFileObject> results = new ArrayList<JavaFileObject>();
			Iterable<JavaFileObject> superResults = super.list(location,
					packageName, kinds, recurse);
			for (final JavaFileObject superResult : superResults) {
				results.add(superResult);
			}

			//Now process classpath URLs
			for (final URL curClassPathUrl : classPathUrls) {
				String directory = packageName.replace('.', '/') + '/';
				URL loadUrl = new URL(curClassPathUrl, directory);
				try {
					List<JavaFileObject> additionalClasses = listClassesFromUrl(
							loadUrl, packageName);
					results.addAll(additionalClasses);
				} catch (final IOException e) {
					//This happens if the file does not exist
					//Move onto next one
				}
			}

			return (results);
		} else {
			Iterable<JavaFileObject> results = super.list(location,
					packageName, kinds, recurse);
			return (results);
		}
	}

	/**
	 * Lists all files at a specified URL.
	 * 
	 * @param base the URL.
	 * @param packageName the package name of classes to list.
	 * 
	 * @return a list of class files.
	 * 
	 * @throws IOException if an I/O error occurs.
	 */
	protected List<JavaFileObject> listClassesFromUrl(final URL base,
			final String packageName) throws IOException {
		if (base == null)
			throw new NullPointerException("base == null");

		List<JavaFileObject> list = new ArrayList<JavaFileObject>();

		URLConnection connection = base.openConnection();
		connection.connect();
		String encoding = connection.getContentEncoding();
		if (encoding == null)
			encoding = "UTF-8";
		BufferedReader reader = new BufferedReader(new InputStreamReader(
				connection.getInputStream(), encoding));
		try {
			String curLine;
			do {
				curLine = reader.readLine();
				if (curLine != null) {
					//Only class files
					if (curLine.endsWith(".class")) {
						URL url = new URL(base, curLine);

						try {
							String curSimpleName = curLine.substring(0, curLine
									.length()
									- ".class".length());
							String binaryName;
							if (packageName == null)
								binaryName = curSimpleName;
							else
								binaryName = packageName + "." + curSimpleName;

							UrlJavaFileObject cur = new UrlJavaFileObject(
									curLine, url, Kind.CLASS, binaryName);
							list.add(cur);
						} catch (final URISyntaxException e) {
							throw new IOException("Error parsing URL "
									+ curLine + ".", e);
						}
					}
				}
			} while (curLine != null);
		} finally {
			reader.close();
		}

		return (list);
	}

}

/**
 * A Java file object that reads from a URL.
 * 
 * @author prunge
 */
class UrlJavaFileObject extends SimpleJavaFileObject {
	private final URL url;
	private final String binaryName;

	/**
	 * Constructs a <code>URLJavaFileObject</code>.
	 *
	 * @param name the file name.
	 * @param url the URL of the file.
	 * @param kind the kind of file.
	 * @param binaryName the binary name of the file.
	 * 
	 * @throws URISyntaxException if an error occurs converting <code>name</code>
	 * 			to a URI.
	 */
	public UrlJavaFileObject(final String name, final URL url, final Kind kind,
			final String binaryName) throws URISyntaxException {
		super(new URI(name), kind);

		this.url = url;
		this.binaryName = binaryName;
	}

	@Override
	public InputStream openInputStream() throws IOException {
		return (url.openStream());
	}

	public String getBinaryName() {
		return (binaryName);
	}
}

/**
 * A Java source file that exists in memory.
 * 
 * @author prunge
 */
class MemorySourceJavaFileObject extends SimpleJavaFileObject {
	private final String code;

	/**
	 * Constructs a <code>MemoryJavaFileObject</code>.
	 * 
	 * @param name the name of the source file.
	 * @param code the source code.
	 * 
	 * @throws IllegalArgumentException if <code>name</code> is not valid.
	 * @throws NullPointerException if any parameter is null.
	 */
	public MemorySourceJavaFileObject(final String name, final String code) {
		super(createUriFromName(name), Kind.SOURCE);

		if (code == null)
			throw new NullPointerException("code == null");

		this.code = code;
	}

	/**
	 * Creates a URI from a source file name.
	 * 
	 * @param name the source file name.
	 * 
	 * @return the URI.
	 * 
	 * @throws NullPointerException if <code>name</code>
	 * 			is null.
	 * @throws IllegalArgumentException if <code>name</code>
	 * 			is invalid.
	 */
	private static URI createUriFromName(final String name) {
		if (name == null)
			throw new NullPointerException("name == null");

		try {
			return (new URI(name));
		} catch (final URISyntaxException e) {
			throw new IllegalArgumentException("Invalid name: " + name, e);
		}
	}

	@Override
	public CharSequence getCharContent(boolean ignoreEncodingErrors)
			throws IOException {
		return (code);
	}
}
