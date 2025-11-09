package com.framework.Scanners;

import com.framework.annotation.Controller;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ScanControllers {

	private static final Set<String> scannedPackages = new HashSet<>();
	private static final List<Class<?>> controllerClasses = new ArrayList<>();
	private static final Map<Class<?>, List<Method>> controllerHandleMethods = new HashMap<>();
	private static final Map<Class<?>, Map<String, Method>> controllerRoutes = new HashMap<>();
	private static final Map<String, Method> routesRegistry = new HashMap<>();

	private ScanControllers() {
		// Utility class
	}

	/**
	 * Discover every class annotated with {@link Controller} within the package.
	 */
	public static synchronized List<Class<?>> findControllerClasses(String packageName) {
		if (packageName == null || packageName.isBlank()) {
			return Collections.unmodifiableList(new ArrayList<>(controllerClasses));
		}

		if (!scannedPackages.contains(packageName)) {
			for (Class<?> candidate : ClassScanner.getClassesInPackage(packageName)) {
				if (!candidate.isAnnotationPresent(Controller.class)) {
					continue;
				}

				if (!controllerClasses.contains(candidate)) {
					controllerClasses.add(candidate);
				}

				controllerHandleMethods.put(candidate, ScanHandlePath.findHandleMethods(candidate));
				controllerRoutes.put(candidate, ScanHandlePath.mapHandlePaths(candidate));
			}

			scannedPackages.add(packageName);
		}

		return Collections.unmodifiableList(new ArrayList<>(controllerClasses));
	}

	/**
	 * Retrieve cached handle methods for a controller. Call {@link #findControllerClasses(String)} first.
	 */
	public static synchronized List<Method> getHandleMethods(Class<?> controllerClass) {
		List<Method> methods = controllerHandleMethods.get(controllerClass);
		if (methods == null) {
			methods = ScanHandlePath.findHandleMethods(controllerClass);
			controllerHandleMethods.put(controllerClass, methods);
			controllerRoutes.put(controllerClass, ScanHandlePath.mapHandlePaths(controllerClass));
			if (!controllerClasses.contains(controllerClass)) {
				controllerClasses.add(controllerClass);
			}
		}

		return Collections.unmodifiableList(new ArrayList<>(methods));
	}

	/**
	 * Build a mapping between paths and methods across all discovered controllers.
	 */
	public static synchronized Map<String, Method> mapHandlePaths(String packageName) {
		findControllerClasses(packageName);

		for (Map.Entry<Class<?>, Map<String, Method>> entry : controllerRoutes.entrySet()) {
			for (Map.Entry<String, Method> route : entry.getValue().entrySet()) {
				Method previous = routesRegistry.putIfAbsent(route.getKey(), route.getValue());
				if (previous != null && !previous.equals(route.getValue())) {
					throw new IllegalStateException("Conflicting handlers found for path: " + route.getKey());
				}
			}
		}

		return Collections.unmodifiableMap(new HashMap<>(routesRegistry));
	}

	public static void printControllers(String packageName) {
		List<Class<?>> controllers = findControllerClasses(packageName);
		System.out.println("Controllers discovered: " + controllers.size());
		for (Class<?> controller : controllers) {
			System.out.println("- " + controller.getName());
		}
	}
}
