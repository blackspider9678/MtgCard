package com.spider.mtgcard.api;
import com.spider.mtgcard.deckbox.DeckboxBlockItem;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.nbt.*;
import java.util.*;
public final class SleeveApplication {
 public enum CurrentState { NONE, ONE_SLEEVE, MIXED, EMPTY }
 public record Selection(CurrentState state, Optional<ResourceLocation> sleeveId) {}
 public static boolean isCompatibleInput(ItemStack s) { return CardItemRegistry.isCard(s)||(!s.isEmpty()&&s.getItem() instanceof DeckboxBlockItem); }
 private static List<ItemStack> cards(ItemStack s,HolderLookup.Provider r){
  if(CardItemRegistry.isCard(s))return List.of(s);
  List<ItemStack> cards=new ArrayList<>();s.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).stream().filter(CardItemRegistry::isCard).forEach(cards::add);
  var data=s.get(DataComponents.BLOCK_ENTITY_DATA);if(data!=null){var items=data.copyTag().getList("Items",Tag.TAG_COMPOUND);for(int i=0;i<items.size();i++){var t=items.getCompound(i);var card=ItemStack.parseOptional(r,t.contains("Stack")?t.getCompound("Stack"):t);if(CardItemRegistry.isCard(card))cards.add(card);}}
  var custom=s.get(DataComponents.CUSTOM_DATA);
  if(custom!=null)readLegacyCards(custom.copyTag().getCompound("BlockEntityTag"),r,cards);
  return cards;
 }
 public static Selection current(MinecraftServer server,HolderLookup.Provider r,ItemStack s){
  var cards=cards(s,r);if(cards.isEmpty())return new Selection(CurrentState.EMPTY,Optional.empty());var first=CardSleeves.id(cards.getFirst());
  for(var c:cards)if(!CardSleeves.id(c).equals(first))return new Selection(CurrentState.MIXED,Optional.empty());return new Selection(first.isPresent()?CurrentState.ONE_SLEEVE:CurrentState.NONE,first);
 }
 public static int apply(MinecraftServer server,HolderLookup.Provider r,ItemStack s,ResourceLocation sleeve){
  if(CardItemRegistry.isCard(s)){applyOne(s,sleeve);return 1;}if(!isCompatibleInput(s))return 0;int count=0;
  var updated=s.getOrDefault(DataComponents.CONTAINER,ItemContainerContents.EMPTY).stream().map(ItemStack::copy).toList();for(var c:updated)if(CardItemRegistry.isCard(c)){applyOne(c,sleeve);count++;}
  if(!updated.isEmpty())s.set(DataComponents.CONTAINER,ItemContainerContents.fromItems(updated));
  var data=s.get(DataComponents.BLOCK_ENTITY_DATA);if(data!=null){var root=data.copyTag();var items=root.getList("Items",Tag.TAG_COMPOUND);for(int i=0;i<items.size();i++){var t=items.getCompound(i);boolean nested=t.contains("Stack");var c=ItemStack.parseOptional(r,nested?t.getCompound("Stack"):t);if(!CardItemRegistry.isCard(c))continue;applyOne(c,sleeve);count++;
   if(nested)t.put("Stack",c.save(r));else{var save=(CompoundTag)c.save(r);save.putByte("Slot",t.getByte("Slot"));items.set(i,save);}}
   root.put("Items",items);s.set(DataComponents.BLOCK_ENTITY_DATA,CustomData.of(root));}
  var legacy=s.get(DataComponents.CUSTOM_DATA);
  if(legacy!=null){var root=legacy.copyTag();if(root.contains("BlockEntityTag",Tag.TAG_COMPOUND)){
   var dataTag=root.getCompound("BlockEntityTag");count+=applyLegacyCards(dataTag,r,sleeve);
   root.put("BlockEntityTag",dataTag);s.set(DataComponents.CUSTOM_DATA,CustomData.of(root));
  }}
  return count;
 }
 private static void readLegacyCards(CompoundTag data,HolderLookup.Provider registries,List<ItemStack> cards){
  var items=data.getList("Items",Tag.TAG_COMPOUND);
  for(int i=0;i<items.size();i++){var entry=items.getCompound(i);var card=ItemStack.parseOptional(registries,entry.contains("Stack",Tag.TAG_COMPOUND)?entry.getCompound("Stack"):entry);if(CardItemRegistry.isCard(card))cards.add(card);}
 }
 private static int applyLegacyCards(CompoundTag data,HolderLookup.Provider registries,ResourceLocation sleeve){
  var items=data.getList("Items",Tag.TAG_COMPOUND);int changed=0;
  for(int i=0;i<items.size();i++){
   var entry=items.getCompound(i);boolean nested=entry.contains("Stack",Tag.TAG_COMPOUND);
   var card=ItemStack.parseOptional(registries,nested?entry.getCompound("Stack"):entry);
   if(!CardItemRegistry.isCard(card))continue;
   applyOne(card,sleeve);changed++;
   if(nested)entry.put("Stack",card.save(registries));else{var saved=(CompoundTag)card.save(registries);if(entry.contains("Slot"))saved.put("Slot",entry.get("Slot").copy());items.set(i,saved);}
  }
  data.put("Items",items);return changed;
 }
 private static void applyOne(ItemStack s,ResourceLocation id){if(id==null)CardSleeves.clear(s);else CardSleeves.set(s,id);}
}
