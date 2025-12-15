package com.framework.Scanners;

import com.framework.annotation.GetMapping;
import com.framework.annotation.HandlePath;
import com.framework.annotation.HttpMethodType;
import com.framework.annotation.PostMapping;
import com.framework.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ScanHandlePath {

	private ScanHandlePath() {
		// Utility class
	}

	/**
	 * Locate all methods that can act as request handlers in the controller class.
	 */
	public static List<Method> findHandleMethods(Class<?> controllerClass) {
		Method[] methods = controllerClass.getDeclaredMethods();
		List<Method> handleMethods = new ArrayList<>();

		for (Method method : methods) {
			if (isHandleMethod(method)) {
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
			String path = resolvePath(method);
			EnumSet<HttpMethodType> httpMethods = resolveHttpMethods(method);
			UrlDetails details = routes.computeIfAbsent(path, key -> new UrlDetails(controllerClass, key));
			details.addHandler(method, httpMethods);
		}

		return routes;
	}

	private static String normalisePath(String value) {
		if (value == null || value.isBlank()) {
			return "/";
		}

		return value.startsWith("/") ? value : "/" + value;
	}

	private static boolean isHandleMethod(Method method) {
		return method.isAnnotationPresent(HandlePath.class)
			|| method.isAnnotationPresent(RequestMapping.class)
			|| method.isAnnotationPresent(GetMapping.class)
			|| method.isAnnotationPresent(PostMapping.class);
	}

	private static String resolvePath(Method method) {
		List<String> candidates = new ArrayList<>();

		HandlePath handlePath = method.getAnnotation(HandlePath.class);
		if (handlePath != null) {
			candidates.add(handlePath.value());
		}

		RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
		if (requestMapping != null) {
			candidates.add(requestMapping.value());
		}

		GetMapping getMapping = method.getAnnotation(GetMapping.class);
		if (getMapping != null) {
			candidates.add(getMapping.value());
		}

		PostMapping postMapping = method.getAnnotation(PostMapping.class);
		if (postMapping != null) {
			candidates.add(postMapping.value());
		}

		String resolved = null;
		for (String candidate : candidates) {
			if (candidate == null) {
				continue;
			}
			String trimmed = candidate.trim();
			if (trimmed.isEmpty()) {
				continue;
			}
			String normalised = normalisePath(trimmed);
			if (resolved == null) {
				resolved = normalised;
			} else if (!resolved.equals(normalised)) {
				throw new IllegalArgumentException("Conflicting path declarations on handler " + method);
			}
		}

		return resolved != null ? resolved : "/";
	}

	private static EnumSet<HttpMethodType> resolveHttpMethods(Method method) {
		EnumSet<HttpMethodType> httpMethods = EnumSet.noneOf(HttpMethodType.class);

		RequestMapping requestMapping = method.getAnnotation(RequestMapping.class);
		if (requestMapping != null) {
			for (HttpMethodType httpMethod : requestMapping.method()) {
				if (httpMethod != null) {
					httpMethods.add(httpMethod);
				}
			}
		}

		if (method.isAnnotationPresent(GetMapping.class)) {
			httpMethods.add(HttpMethodType.GET);
		}

		if (method.isAnnotationPresent(PostMapping.class)) {
			httpMethods.add(HttpMethodType.POST);
		}

		return httpMethods;
	}
}
