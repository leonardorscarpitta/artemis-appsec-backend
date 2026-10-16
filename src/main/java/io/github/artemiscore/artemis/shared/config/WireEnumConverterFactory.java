package io.github.artemiscore.artemis.shared.config;

import java.lang.reflect.Method;
import java.util.Arrays;

import org.springframework.core.convert.converter.Converter;
import org.springframework.core.convert.converter.ConverterFactory;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Lets request parameters use the same enum values as the JSON API (the {@link JsonValue} value, e.g.
 * {@code falsePositive}), falling back to the constant name.
 */
@SuppressWarnings({ "rawtypes", "unchecked" })
class WireEnumConverterFactory implements ConverterFactory<String, Enum> {

	@Override
	public <T extends Enum> Converter<String, T> getConverter(Class<T> targetType) {
		Method wireValue = Arrays.stream(targetType.getMethods())
			.filter(m -> m.isAnnotationPresent(JsonValue.class) && m.getParameterCount() == 0)
			.findFirst()
			.orElse(null);
		return source -> {
			String value = source.trim();
			if (value.isEmpty()) {
				return null;
			}
			for (T constant : targetType.getEnumConstants()) {
				if (constant.name().equalsIgnoreCase(value) || value.equals(wireValueOf(constant, wireValue))) {
					return constant;
				}
			}
			throw new IllegalArgumentException("Invalid value '%s' for %s".formatted(source, targetType.getSimpleName()));
		};
	}

	private static String wireValueOf(Enum<?> constant, Method wireValue) {
		if (wireValue == null) {
			return null;
		}
		try {
			return String.valueOf(wireValue.invoke(constant));
		}
		catch (ReflectiveOperationException ex) {
			return null;
		}
	}
}
