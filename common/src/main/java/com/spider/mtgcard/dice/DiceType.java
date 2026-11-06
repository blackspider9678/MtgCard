package com.spider.mtgcard.dice;

import com.spider.mtgcard.item.DiceItem;
import com.spider.mtgcard.item.ModItems;
import net.minecraft.world.item.Item;

import java.util.Optional;

public enum DiceType {
    D4(4),
    D6(6),
    D8(8),
    D10(10),
    D12(12),
    D20(20),
    D100(100);

    private final int sides;

    DiceType(int sides) {
        this.sides = sides;
    }

    public int sides() {
        return sides;
    }

    public Item item() {
        return switch (this) {
            case D4 -> ModItems.D4_DICE;
            case D6 -> ModItems.D6_DICE;
            case D8 -> ModItems.D8_DICE;
            case D10 -> ModItems.D10_DICE;
            case D12 -> ModItems.D12_DICE;
            case D20 -> ModItems.D20_DICE;
            case D100 -> ModItems.D100_DICE;
        };
    }

    public String label() {
        return "D" + sides;
    }

    public static Optional<DiceType> bySides(int sides) {
        for (DiceType type : values()) {
            if (type.sides == sides) return Optional.of(type);
        }
        return Optional.empty();
    }

    public static DiceType byItem(Item item) {
        if (item instanceof DiceItem diceItem) {
            return bySides(diceItem.getSides()).orElse(D20);
        }
        return D20;
    }
}
