package com.petsplugin.manager;

import org.bukkit.entity.Armadillo;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Pig;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/** Optional cosmetic APIs added after 1.21, resolved once per server startup. */
final class PetAppearanceCompat {
    private static final ClimateVariant PIG = climateVariant(Pig.class);
    private static final ClimateVariant CHICKEN = climateVariant(Chicken.class);
    private static final Method ROLL_UP = optionalMethod(Armadillo.class, "rollUp");
    private static final Method ROLL_OUT = optionalMethod(Armadillo.class, "rollOut");

    private PetAppearanceCompat() {}

    static void setPigVariant(Pig pig, String variant) {
        PIG.apply(pig, variant);
    }

    static void setChickenVariant(Chicken chicken, String variant) {
        CHICKEN.apply(chicken, variant);
    }

    static void setRolledUp(Armadillo armadillo, boolean rolledUp) {
        invoke(rolledUp ? ROLL_UP : ROLL_OUT, armadillo);
    }

    private static Method optionalMethod(Class<?> type, String name, Class<?>... parameters) {
        try {
            return type.getMethod(name, parameters);
        } catch (NoSuchMethodException ignored) {
            return null;
        }
    }

    private static ClimateVariant climateVariant(Class<?> entityType) {
        try {
            Class<?> variantType = Class.forName(entityType.getName() + "$Variant");
            Map<String, Object> variants = new HashMap<>();
            for (String name : new String[]{"COLD", "TEMPERATE", "WARM"}) {
                variants.put(name, variantType.getField(name).get(null));
            }
            return new ClimateVariant(optionalMethod(entityType, "setVariant", variantType), variants);
        } catch (ClassNotFoundException | NoSuchFieldException ignored) {
            return new ClimateVariant(null, Map.of());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access pet variants", e);
        }
    }

    private static void invoke(Method method, Object entity, Object... arguments) {
        if (method == null) return;
        try {
            method.invoke(entity, arguments);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Cannot apply pet appearance", e);
        }
    }

    private record ClimateVariant(Method setter, Map<String, Object> variants) {
        void apply(Object entity, String name) {
            if (name == null) return;
            Object variant = variants.get(name);
            if (variant != null) invoke(setter, entity, variant);
        }
    }
}
