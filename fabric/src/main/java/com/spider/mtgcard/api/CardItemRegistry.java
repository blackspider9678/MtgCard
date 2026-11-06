package com.spider.mtgcard.api;
import net.minecraft.world.item.ItemStack;
public final class CardItemRegistry {
 public static boolean isCard(ItemStack s) { return s!=null && !s.isEmpty() && (s.getItem() instanceof com.spider.mtgcard.item.CardItem || com.spider.mtgcard.util.StackData.readCustom(s).contains("mtg_meta")); }
}
