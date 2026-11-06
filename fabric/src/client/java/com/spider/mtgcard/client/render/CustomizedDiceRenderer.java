package com.spider.mtgcard.client.render;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.item.DiceItem;
public final class CustomizedDiceRenderer extends BlockEntityWithoutLevelRenderer {
 public CustomizedDiceRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
 @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
  net.minecraft.resources.ResourceLocation textureId;
  var sleeve=com.spider.mtgcard.api.CardSleeves.get(stack);
  if(com.spider.mtgcard.api.CardItemRegistry.isCard(stack) && sleeve.isPresent()) {
   textureId=sleeve.get().backTexture();
  } else {
   if(!(stack.getItem() instanceof DiceItem dice))return;
   var appearance=stack.get(ModDataComponents.DICE_APPEARANCE);if(appearance==null)return;
   textureId=DiceTextureCache.getTexture(dice.getSides(),appearance).id();
  }
  poses.pushPose();poses.translate(1f/16,1f/16,.5);poses.scale(14f/16,14f/16,14f/16);
  var vertices=ItemRenderer.getFoilBufferDirect(buffers,RenderType.entityCutoutNoCull(textureId),true,stack.hasFoil());
  var p=poses.last();
  vertices.addVertex(p.pose(),1,0,0).setColor(-1).setUv(1,1).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
  vertices.addVertex(p.pose(),1,1,0).setColor(-1).setUv(1,0).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
  vertices.addVertex(p.pose(),0,1,0).setColor(-1).setUv(0,0).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
  vertices.addVertex(p.pose(),0,0,0).setColor(-1).setUv(0,1).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);
  poses.popPose();
 }
}
