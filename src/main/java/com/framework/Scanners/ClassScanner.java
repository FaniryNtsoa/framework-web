package com.framework.Scanners;

import java.io.File;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ClassScanner {

	private ClassScanner() {
		// Utility class
	}

	/**
	 * Locate every concrete class under the provided package.
	 */
	public static List<Class<?>> getClassesInPackage(String packageName) {
		if (packageName == null || packageName.isEmpty()) {
			return Collections.emptyList();
		}

		List<Class<?>> classes = new ArrayList<>();
		ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
		String path = packageName.replace('.', '/');
		URL resource = classLoader.getResource(path);

		if (resource == null) {
			return Collections.emptyList();
		}

		try {
			File directory = new File(resource.toURI());
			if (directory.exists()) {
				scanDirectory(directory, packageName, classes);
			}
		} catch (URISyntaxException e) {
			return Collections.emptyList();
		}

		return classes;
	}

	private static void scanDirectory(File directory, String packageName, List<Class<?>> classes) {
		File[] files = directory.listFiles();
		if (files == null) {
			return;
		}

		for (File file : files) {
			if (file.isDirectory()) {
				scanDirectory(file, packageName + "." + file.getName(), classes);
				continue;
			}

			if (!file.getName().endsWith(".class")) {
				continue;
			}

			String className = packageName + '.' + file.getName().substring(0, file.getName().length() - 6);
			try {
				classes.add(Class.forName(className));
			} catch (ClassNotFoundException ignored) {
				// Skip classes that cannot be loaded
			}
		}
	}
}
