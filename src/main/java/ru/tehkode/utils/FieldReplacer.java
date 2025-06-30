package ru.tehkode.utils;

import java.lang.reflect.Field;

/**
 * Utility class for replacing or accessing private fields using reflection.
 *
 * @param <Instance> The type of the class containing the field.
 * @param <Type>     The type of the field value.
 */
public class FieldReplacer<Instance, Type> {

    private final Class<Type> requiredType;
    private final Field field;
    private static boolean verboseLogging;

    static {
        String verboseFlag = System.getProperty("verboseLogging");
        verboseLogging = "true".equalsIgnoreCase(verboseFlag);
    }

    private static boolean isTypeCompatible(Class<?> fieldType, Class<?> requiredType) {
        if (requiredType.isAssignableFrom(fieldType)) {
            return true;
        }
        if (fieldType.isPrimitive()) {
            if (fieldType == boolean.class && requiredType == Boolean.class) return true;
            if (fieldType == byte.class && requiredType == Byte.class) return true;
            if (fieldType == short.class && requiredType == Short.class) return true;
            if (fieldType == int.class && requiredType == Integer.class) return true;
            if (fieldType == long.class && requiredType == Long.class) return true;
            if (fieldType == float.class && requiredType == Float.class) return true;
            if (fieldType == double.class && requiredType == Double.class) return true;
            if (fieldType == char.class && requiredType == Character.class) return true;
        }
        return false;
    }

    /**
     * Constructs a FieldReplacer to access a declared field of a class.
     *
     * @param clazz         Class containing the field.
     * @param fieldName     Name of the field to access.
     * @param requiredType  Expected type of the field.
     */
    public FieldReplacer(Class<? extends Instance> clazz, String fieldName, Class<Type> requiredType) {
        this.requiredType = requiredType;

        try {
            logVerbose("Looking for field '" + fieldName + "' in class " + clazz.getName());
            this.field = clazz.getDeclaredField(fieldName);
            this.field.setAccessible(true);

            if (!isTypeCompatible(field.getType(), requiredType)) {
                logVerbose("Field '" + fieldName + "' is of type " + field.getType().getName() +
                        ", expected " + requiredType.getName());
                throw new ExceptionInInitializerError("Field '" + fieldName + "' has incorrect type.");
            }

            logVerbose("Field '" + fieldName + "' successfully bound to " + field.getType().getName());

        } catch (NoSuchFieldException e) {
            logVerbose("Field '" + fieldName + "' not found in class " + clazz.getName());
            throw new ExceptionInInitializerError(e);
        }
    }

    /**
     * Gets the value of the field from the given instance.
     *
     * @param instance The instance from which to get the field.
     * @return The value of the field.
     */
    public Type get(Instance instance) {
        try {
            Type value = requiredType.cast(field.get(instance));
            logVerbose("Retrieved value from field '" + field.getName() + "': " + value);
            return value;
        } catch (IllegalAccessException e) {
            logVerbose("Failed to access field '" + field.getName() + "' for getting value.");
            throw new Error(e);
        }
    }

    /**
     * Sets the value of the field on the given instance.
     *
     * @param instance The instance to modify.
     * @param newValue The new value to set.
     */
    public void set(Instance instance, Type newValue) {
        try {
            logVerbose("Setting field '" + field.getName() + "' to value: " + newValue);
            field.set(instance, newValue);
        } catch (IllegalAccessException e) {
            logVerbose("Failed to access field '" + field.getName() + "' for setting value.");
            throw new Error(e);
        }
    }

    /**
     * Logs verbose messages with a [PermissionsEx] prefix if verbose logging is enabled.
     *
     * @param message The message to log.
     */
    private static void logVerbose(String message) {
        if (verboseLogging) {
            System.out.println("[PermissionsEx] " + message);
        }
    }
}
