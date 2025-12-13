package com.framework.Scanners;

import com.framework.annotation.HandlePath;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScanHandlePath {

	private ScanHandlePath() {
		// Utility class
	}

	/**
	 * Locate all {@link HandlePath} methods declared in the controller class.
	 */
	public static List<Method> findHandleMethods(Class<?> controllerClass) {
		Method[] methods = controllerClass.getDeclaredMethods();
		List<Method> handleMethods = new ArrayList<>();

		for (Method method : methods) {
			if (method.isAnnotationPresent(HandlePath.class)) {
				handleMethods.add(method);
			}
		}

		return handleMethods;
	}

	/**
	 * Build a path-to-method map for quick lookups at runtime.
	 */
	public static Map<String, UrlDetails> mapHandlePaths(Class<?> controllerClass) {
		Map<String, UrlDetails> routes = new LinkedHashMap<>();

		for (Method method : findHandleMethods(controllerClass)) {
			HandlePath annotation = method.getAnnotation(HandlePath.class);
			String rawPath = annotation.value();
			String path = normalisePath(rawPath);
			UrlDetails details = routes.computeIfAbsent(path, key -> new UrlDetails(controllerClass, key));
			details.addMethod(method);
		}

		return routes;
	}

	private static String normalisePath(String value) {
		if (value == null || value.isBlank()) {
			return "/";
		}

		return value.startsWith("/") ? value : "/" + value;
	}
}
