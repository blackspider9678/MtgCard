package com.spider.mtgcard.dice;

import com.mojang.serialization.Codec;

import java.util.Locale;

public enum DiceGradientType {
    SOLID("solid"),
    VERTICAL("vertical"),
    HORIZONTAL("horizontal"),
    DIAGONAL("diagonal"),
    RADIAL("radial");

    public static final Codec<DiceGradientType> CODEC = Codec.STRING.xmap(DiceGradientType::byId, DiceGradientType::id);

    private final String id;

    DiceGradientType(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public static DiceGradientType byId(String id) {
        if (id == null || id.isBlank()) return SOLID;
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        for (DiceGradientType type : values()) {
            if (type.id.equals(normalized)) {
                return type;
            }
        }
        return SOLID;
    }
}
