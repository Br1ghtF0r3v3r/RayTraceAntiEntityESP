package RayTraceAntiEntityESP.bukkit.nms;

import org.bukkit.NamespacedKey;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

public final class EntityTypeResolver {

    private EntityTypeResolver() {
    }

    private static volatile Object armorStand;
    private static volatile Object blockDisplay;
    private static volatile Object player;

    public static Object armorStand() {
        if (armorStand == null) armorStand = resolve("armor_stand", "ARMOR_STAND");
        return armorStand;
    }

    public static Object blockDisplay() {
        if (blockDisplay == null) blockDisplay = resolve("block_display", "BLOCK_DISPLAY");
        return blockDisplay;
    }

    public static Object player() {
        if (player == null) player = resolve("player", "PLAYER");
        return player;
    }

    private static Object resolve(String registryName, String fieldName) {
        Object fromField = tryStaticField(fieldName);
        if (fromField != null) return fromField;

        Object resourceLocation = buildResourceLocation(registryName);

        if (resourceLocation != null) {
            Object fromRegistry = tryRegistryLookup(resourceLocation);
            if (fromRegistry != null) return fromRegistry;
        }

        Object fromIteration = tryRegistryIteration(registryName);
        if (fromIteration != null) return fromIteration;

        throw new IllegalStateException(
                "Failed to resolve EntityType." + fieldName
                        + " on server version " + safeMinecraftVersion()
                        + " — all lookup strategies failed");
    }

    private static Object tryStaticField(String fieldName) {
        try {
            Class<?> entityTypeClass = Class.forName("net.minecraft.world.entity.EntityType");
            try {
                Field f = entityTypeClass.getField(fieldName);
                return f.get(null);
            } catch (NoSuchFieldException ignored) {
                return null;
            }
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object buildResourceLocation(String path) {

        String namespace = "minecraft";
        String combined = namespace + ":" + path;

        Object fromCraft = tryCraftNamespacedKey(namespace, path);
        if (fromCraft != null) return fromCraft;

        Object fromStaticFactory = tryStaticFactoryMethods(combined, namespace, path);
        if (fromStaticFactory != null) return fromStaticFactory;

        return tryDeclaredConstructors(namespace, path, combined);
    }

    private static Object tryCraftNamespacedKey(String namespace, String path) {
        try {
            NamespacedKey bukkitKey = new NamespacedKey(namespace, path);
            Class<?> craftClass = Class.forName("org.bukkit.craftbukkit.util.CraftNamespacedKey");
            Method toMinecraft = craftClass.getMethod("toMinecraft", NamespacedKey.class);
            return toMinecraft.invoke(null, bukkitKey);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object tryStaticFactoryMethods(String combined, String namespace, String path) {
        try {
            Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");

            try {
                Method m = rlClass.getMethod("tryParse", String.class);
                Object result = m.invoke(null, combined);
                if (result != null) return result;
            } catch (Throwable ignored) {}

            try {
                Method m = rlClass.getMethod("parse", String.class);
                return m.invoke(null, combined);
            } catch (Throwable ignored) {}

            try {
                Method m = rlClass.getMethod("fromNamespaceAndPath", String.class, String.class);
                return m.invoke(null, namespace, path);
            } catch (Throwable ignored) {}
        } catch (ClassNotFoundException ignored) {}
        return null;
    }

    private static Object tryDeclaredConstructors(String namespace, String path, String combined) {
        try {
            Class<?> rlClass = Class.forName("net.minecraft.resources.ResourceLocation");

            try {
                Constructor<?> c = rlClass.getDeclaredConstructor(String.class, String.class);
                c.setAccessible(true);
                return c.newInstance(namespace, path);
            } catch (Throwable ignored) {}

            try {
                Constructor<?> c = rlClass.getDeclaredConstructor(String.class);
                c.setAccessible(true);
                return c.newInstance(combined);
            } catch (Throwable ignored) {}
        } catch (ClassNotFoundException ignored) {}
        return null;
    }

    private static Object tryRegistryLookup(Object resourceLocation) {
        Object registry = findEntityTypeRegistry();
        if (registry == null) return null;

        try {
            Method get = registry.getClass().getMethod("get", resourceLocation.getClass());
            Object result = get.invoke(registry, resourceLocation);
            Object unwrapped = unwrapResult(result);
            if (unwrapped != null) return unwrapped;
        } catch (Throwable ignored) {}

        try {
            Method getOptional = registry.getClass().getMethod("getOptional", resourceLocation.getClass());
            Object result = getOptional.invoke(registry, resourceLocation);
            Object unwrapped = unwrapResult(result);
            if (unwrapped != null) return unwrapped;
        } catch (Throwable ignored) {}

        try {
            Method getValue = registry.getClass().getMethod("getValue", resourceLocation.getClass());
            Object result = getValue.invoke(registry, resourceLocation);
            Object unwrapped = unwrapResult(result);
            if (unwrapped != null) return unwrapped;
        } catch (Throwable ignored) {}

        return null;
    }

    private static Object unwrapResult(Object result) {
        return unwrapResultDepth(result, 0);
    }

    private static Object unwrapResultDepth(Object result, int depth) {
        if (result == null || depth > 8) return null;

        if (result instanceof java.util.Optional<?> opt) {
            return unwrapResultDepth(opt.orElse(null), depth + 1);
        }

        String className = result.getClass().getName();
        if (className.equals("net.minecraft.world.entity.EntityType")) {
            return result;
        }

        Object fromHolder = tryUnwrapHolder(result);
        if (fromHolder != null && fromHolder != result) {
            return unwrapResultDepth(fromHolder, depth + 1);
        }

        return result;
    }

    private static Object tryUnwrapHolder(Object obj) {
        if (obj == null) return null;

        Class<?> c = obj.getClass();
        while (c != null) {
            if (isHolder(c)) {
                return invokeHolderValue(obj, c);
            }
            for (Class<?> iface : c.getInterfaces()) {
                if (isHolderOrSubinterface(iface)) {
                    Object result = invokeHolderValue(obj, iface);
                    if (result != null) return result;
                }
            }
            c = c.getSuperclass();
        }
        return null;
    }

    private static boolean isHolder(Class<?> cls) {
        return "net.minecraft.core.Holder".equals(cls.getName());
    }

    private static boolean isHolderOrSubinterface(Class<?> cls) {
        if (isHolder(cls)) return true;
        for (Class<?> iface : cls.getInterfaces()) {
            if (isHolderOrSubinterface(iface)) return true;
        }
        return false;
    }

    private static Object invokeHolderValue(Object obj, Class<?> holderClass) {
        try {
            java.lang.reflect.Method value = holderClass.getMethod("value");
            return value.invoke(obj);
        } catch (Throwable ignored) {}

        try {
            java.lang.reflect.Method get = holderClass.getMethod("get");
            return get.invoke(obj);
        } catch (Throwable ignored) {}

        for (java.lang.reflect.Method m : obj.getClass().getMethods()) {
            if (m.getParameterCount() != 0) continue;
            if (m.getName().equals("value") || m.getName().equals("get")) {
                try {
                    Object result = m.invoke(obj);
                    if (result != null) return result;
                } catch (Throwable ignored) {
                }
            }
        }
        return null;
    }

    private static Object tryRegistryIteration(String registryName) {
        Object registry = findEntityTypeRegistry();
        if (registry == null) return null;

        try {
            Method iterator = registry.getClass().getMethod("iterator");
            Iterable<?> iterable = (Iterable<?>) iterator.invoke(registry);
            for (Object entry : iterable) {
                if (entry == null) continue;
                Object unwrapped = unwrapResult(entry);
                if (unwrapped == null) continue;
                String str = unwrapped.toString();
                if (str.contains("\"" + registryName + "\"") || str.contains(registryName)) {
                    return unwrapped;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static volatile Object cachedRegistry;

    private static Object findEntityTypeRegistry() {
        if (cachedRegistry != null) return cachedRegistry;

        Object reg = tryRegistryField("net.minecraft.core.registries.BuiltInRegistries", "ENTITY_TYPE");
        if (reg == null) reg = tryRegistryField("net.minecraft.core.Registry", "ENTITY_TYPE");
        if (reg == null) reg = tryRegistryField("net.minecraft.core.IRegistry", "ENTITY_TYPE");

        if (reg != null) {
            cachedRegistry = reg;
            return reg;
        }
        return null;
    }

    private static Object tryRegistryField(String className, String fieldName) {
        try {
            Class<?> clazz = Class.forName(className);
            Field f = clazz.getField(fieldName);
            return f.get(null);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String safeMinecraftVersion() {
        try {
            Class<?> serverClass = Class.forName("org.bukkit.Bukkit");
            Object server = serverClass.getMethod("getServer").invoke(null);
            return (String) server.getClass().getMethod("getMinecraftVersion").invoke(server);
        } catch (Throwable t) {
            return "<unknown>";
        }
    }
}