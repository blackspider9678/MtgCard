package com.spider.mtgcard.client.gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.systems.RenderSystem;
final class LoomGuiTextures {
 static void blit(GuiGraphics g,ResourceLocation texture,int x,int y,float u,float v,int w,int h,int tw,int th){g.blit(texture,x,y,u,v,w,h,tw,th);}
 static void blit(GuiGraphics g,ResourceLocation texture,int x,int y,float u,float v,int w,int h,int rw,int rh,int tw,int th){g.blit(texture,x,y,w,h,u,v,rw,rh,tw,th);}
 static void blit(GuiGraphics g,ResourceLocation texture,int x,int y,float u,float v,int w,int h,int rw,int rh,int tw,int th,int color){
  g.flush();RenderSystem.enableBlend();RenderSystem.setShaderColor(((color>>16)&255)/255f,((color>>8)&255)/255f,(color&255)/255f,((color>>>24)&255)/255f);
  try{g.blit(texture,x,y,w,h,u,v,rw,rh,tw,th);g.flush();}finally{RenderSystem.setShaderColor(1,1,1,1);RenderSystem.disableBlend();}
 }
}
