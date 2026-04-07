package com.spider.mtgcard.displayblock;

import net.minecraft.util.StringRepresentable;

public enum DisplayShape implements StringRepresentable {
    SINGLE("single"),

    H_LEFT("horizontal_right"),
    H_MIDDLE("horizontal_middle"),
    H_RIGHT("horizontal_left"),

    V_BOTTOM("vertical_bottom"),
    V_MIDDLE("vertical_middle"),
    V_TOP("vertical_top"),

    CORNER_TL("corner_tr"),
    CORNER_TR("corner_tl"),
    CORNER_BL("corner_br"),
    CORNER_BR("corner_bl"),

    SIDE_TOP("side_top"),
    SIDE_BOTTOM("side_bottom"),
    SIDE_LEFT("side_right"),
    SIDE_RIGHT("side_left"),

    MIDDLE("middle");

    private final String id;
    DisplayShape(String id) { this.id = id; }

    @Override public String getSerializedName() { return id; }
}
