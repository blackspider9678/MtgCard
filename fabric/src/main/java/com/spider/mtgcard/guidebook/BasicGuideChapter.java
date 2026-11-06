package com.spider.mtgcard.guidebook;

import net.minecraft.world.item.ItemStack;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.function.Supplier;

public class BasicGuideChapter implements GuideChapter {

    private final ResourceLocation id;
    private final GuideCategory category;
    private final String titleKey;
    private final Supplier<ItemStack> iconSupplier;
    private final ResourceLocation textureIcon;

    public BasicGuideChapter(ResourceLocation id, GuideCategory category, String titleKey) {
        this(id, category, titleKey, () -> ItemStack.EMPTY, null);
    }

    public BasicGuideChapter(ResourceLocation id, GuideCategory category, String titleKey, Supplier<ItemStack> iconSupplier) {
        this(id, category, titleKey, iconSupplier, null);
    }

    public BasicGuideChapter(ResourceLocation id, GuideCategory category, String titleKey, ResourceLocation textureIcon) {
        this(id, category, titleKey, () -> ItemStack.EMPTY, textureIcon);
    }

    private BasicGuideChapter(ResourceLocation id, GuideCategory category, String titleKey, Supplier<ItemStack> iconSupplier, ResourceLocation textureIcon) {
        this.id = id;
        this.category = category;
        this.titleKey = titleKey;
        this.iconSupplier = iconSupplier;
        this.textureIcon = textureIcon;
    }

    @Override public ResourceLocation id() { return id; }
    @Override public GuideCategory category() { return category; }
    @Override public String titleKey() { return titleKey; }
    @Override public List<GuideSection> sections() { return List.of(); } // blank for now

    @Override public ItemStack itemIcon() { return iconSupplier.get(); }
    @Override public ResourceLocation textureIcon() { return textureIcon; }
}
