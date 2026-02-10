package com.spider.mtgcard.guidebook;

import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.function.Supplier;

public class BasicGuideChapter implements GuideChapter {

    private final Identifier id;
    private final GuideCategory category;
    private final String titleKey;
    private final Supplier<ItemStack> iconSupplier;
    private final Identifier textureIcon;

    public BasicGuideChapter(Identifier id, GuideCategory category, String titleKey) {
        this(id, category, titleKey, () -> ItemStack.EMPTY, null);
    }

    public BasicGuideChapter(Identifier id, GuideCategory category, String titleKey, Supplier<ItemStack> iconSupplier) {
        this(id, category, titleKey, iconSupplier, null);
    }

    public BasicGuideChapter(Identifier id, GuideCategory category, String titleKey, Identifier textureIcon) {
        this(id, category, titleKey, () -> ItemStack.EMPTY, textureIcon);
    }

    private BasicGuideChapter(Identifier id, GuideCategory category, String titleKey, Supplier<ItemStack> iconSupplier, Identifier textureIcon) {
        this.id = id;
        this.category = category;
        this.titleKey = titleKey;
        this.iconSupplier = iconSupplier;
        this.textureIcon = textureIcon;
    }

    @Override public Identifier id() { return id; }
    @Override public GuideCategory category() { return category; }
    @Override public String titleKey() { return titleKey; }
    @Override public List<GuideSection> sections() { return List.of(); } // blank for now

    @Override public ItemStack itemIcon() { return iconSupplier.get(); }
    @Override public Identifier textureIcon() { return textureIcon; }
}
