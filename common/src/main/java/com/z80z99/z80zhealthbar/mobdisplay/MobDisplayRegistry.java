package com.z80z99.z80zhealthbar.mobdisplay;

import java.util.Collection;
import net.minecraft.resources.ResourceLocation;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class MobDisplayRegistry {
    private static final Map<ResourceLocation, IMobDisplayRenderer> RENDERERS = new LinkedHashMap<>();

    private MobDisplayRegistry() {}

    public static void register(IMobDisplayRenderer renderer) {
        RENDERERS.put(renderer.getKey(), renderer);
    }

    public static IMobDisplayRenderer get(ResourceLocation key) {
        return RENDERERS.get(key);
    }

    public static Collection<IMobDisplayRenderer> getAll() {
        return Collections.unmodifiableCollection(RENDERERS.values());
    }

    public static void clear() {
        RENDERERS.clear();
    }
}
