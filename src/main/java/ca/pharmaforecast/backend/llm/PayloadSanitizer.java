package ca.pharmaforecast.backend.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.Temporal;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class PayloadSanitizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PayloadSanitizer.class);
    private static final Set<String> FORBIDDEN_FIELDS = Set.of(
            normalize("patient_id"),
            normalize("patient_name"),
            normalize("prescriber_id"),
            normalize("patient_dob")
    );

    public void sanitize(Object payload) {
        if (payload == null) {
            return;
        }
        walk(payload, "payload", new IdentityHashMap<>());
    }

    private void walk(Object value, String path, IdentityHashMap<Object, Boolean> seen) {
        if (value == null || isTerminal(value)) {
            return;
        }
        if (seen.putIfAbsent(value, Boolean.TRUE) != null) {
            return;
        }

        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey());
                String nextPath = path + "." + key;
                checkForbidden(key, nextPath);
                walk(entry.getValue(), nextPath, seen);
            }
            return;
        }

        if (value instanceof Collection<?> collection) {
            int index = 0;
            for (Object item : collection) {
                walk(item, path + "[" + index + "]", seen);
                index++;
            }
            return;
        }

        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            for (int index = 0; index < length; index++) {
                walk(Array.get(value, index), path + "[" + index + "]", seen);
            }
            return;
        }

        ReflectionUtils.doWithFields(value.getClass(), field -> {
            if (Modifier.isStatic(field.getModifiers())) {
                return;
            }
            inspectField(value, field, path, seen);
        });
    }

    private void inspectField(Object target, Field field, String path, IdentityHashMap<Object, Boolean> seen) {
        ReflectionUtils.makeAccessible(field);
        String fieldName = field.getName();
        String nextPath = path + "." + fieldName;
        checkForbidden(fieldName, nextPath);
        Object fieldValue = ReflectionUtils.getField(field, target);
        walk(fieldValue, nextPath, seen);
    }

    private void checkForbidden(String fieldName, String path) {
        if (FORBIDDEN_FIELDS.contains(normalize(fieldName))) {
            LOGGER.error("Forbidden LLM payload field detected at path={}", path);
            throw new IllegalStateException("Forbidden LLM payload field detected at path " + path);
        }
    }

    private boolean isTerminal(Object value) {
        Class<?> type = value.getClass();
        return type.isPrimitive()
                || type.isEnum()
                || value instanceof String
                || value instanceof Number
                || value instanceof Boolean
                || value instanceof Character
                || value instanceof UUID
                || value instanceof BigDecimal
                || value instanceof BigInteger
                || value instanceof Temporal
                || (type.getPackageName().startsWith("java.")
                && !(value instanceof Map<?, ?>)
                && !(value instanceof Collection<?>)
                && !type.isArray());
    }

    private static String normalize(String value) {
        return value == null ? "" : value.replaceAll("[^A-Za-z0-9]", "").toLowerCase(Locale.ROOT);
    }
}
