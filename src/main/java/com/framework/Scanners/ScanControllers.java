package com.framework.Scanners;

import com.framework.annotation.Controller;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ScanControllers {

	private static List<Class<?>> controllerClasses;
	private static Map<Class<?>, List<Method>> controllerHandleMethods;

	private ScanControllers() {
		// Utility class
	}

	/**
	 * Discover every class annotated with {@link Controller} within the package.
	 */
	public static List<Class<?>> findControllerClasses(String packageName) {
		if (controllerClasses != null) {
			return controllerClasses;
		}

		controllerClasses = new ArrayList<>();
		controllerHandleMethods = new HashMap<>();

		for (Class<?> candidate : ClassScanner.getClassesInPackage(packageName)) {
			if (!candidate.isAnnotationPresent(Controller.class)) {
				continue;
			}

			controllerClasses.add(candidate);
			List<Method> handleMethods = ScanHandlePath.findHandleMethods(candidate);
			controllerHandleMethods.put(candidate, handleMethods);
		}

		return Collections.unmodifiableList(controllerClasses);
	}

	/**
	 * Retrieve cached handle methods for a controller. Call {@link #findControllerClasses(String)} first.
	 */
	public static List<Method> getHandleMethods(Class<?> controllerClass) {
		if (controllerHandleMethods == null) {
			throw new IllegalStateException("Controllers have not been scanned yet");
		}

		List<Method> methods = controllerHandleMethods.get(controllerClass);
		if (methods == null) {
			throw new IllegalArgumentException("Unknown controller: " + controllerClass.getName());
		}

		return Collections.unmodifiableList(methods);
	}

	/**
	 * Build a mapping between paths and methods across all discovered controllers.
	 */
	public static Map<String, Method> mapHandlePaths(String packageName) {
		findControllerClasses(packageName);

		Map<String, Method> routes = new HashMap<>();
		for (Class<?> controller : controllerClasses) {
			for (Map.Entry<String, Method> entry : ScanHandlePath.mapHandlePaths(controller).entrySet()) {
				routes.put(entry.getKey(), entry.getValue());
			}
		}

		return routes;
	}

	public static void printControllers(String packageName) {
		List<Class<?>> controllers = findControllerClasses(packageName);
		System.out.println("Controllers discovered: " + controllers.size());
		for (Class<?> controller : controllers) {
			System.out.println("- " + controller.getName());
		}
	}
}
