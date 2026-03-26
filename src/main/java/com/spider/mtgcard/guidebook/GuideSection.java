// src/main/java/com/spider/mtgcard/guidebook/GuideSection.java
package com.spider.mtgcard.guidebook;

import net.minecraft.network.chat.Component;

import java.util.List;

public sealed interface GuideSection permits
        GuideSection.Heading,
        GuideSection.Paragraph,
        GuideSection.Bullets {

    record Heading(Component text) implements GuideSection {}
    record Paragraph(Component text) implements GuideSection {}
    record Bullets(List<Component> bullets) implements GuideSection {}
}
