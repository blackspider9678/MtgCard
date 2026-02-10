// src/main/java/com/spider/mtgcard/guidebook/GuideSection.java
package com.spider.mtgcard.guidebook;

import net.minecraft.text.Text;

import java.util.List;

public sealed interface GuideSection permits
        GuideSection.Heading,
        GuideSection.Paragraph,
        GuideSection.Bullets {

    record Heading(Text text) implements GuideSection {}
    record Paragraph(Text text) implements GuideSection {}
    record Bullets(List<Text> bullets) implements GuideSection {}
}
