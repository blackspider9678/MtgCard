package com.spider.mtgcard.client.dice;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.spider.mtgcard.client.render.DiceTextureCache;
import com.spider.mtgcard.data.ModDataComponents;
import com.spider.mtgcard.dice.DiceAppearance;
import com.spider.mtgcard.dice.DiceEntity;
import com.spider.mtgcard.dice.DicePolyhedra;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Quaternionf;

public final class DiceEntityRenderer extends EntityRenderer<DiceEntity, DiceEntityRenderer.State> {
    public static final class State extends EntityRenderState {
        ItemStack stack = ItemStack.EMPTY;
        Quaternionf rotation = new Quaternionf();
        int sides = 6;
    }

    public DiceEntityRenderer(EntityRendererProvider.Context context) { super(context); }
    @Override public State createRenderState() { return new State(); }

    @Override public void extractRenderState(DiceEntity entity, State state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        state.stack = entity.getStack().copy();
        state.rotation.set(entity.getRenderRotation(partialTick));
        state.sides = entity.getSides();
    }

    @Override public void submit(State state, PoseStack pose, SubmitNodeCollector queue, CameraRenderState camera) {
        if (state.stack.isEmpty()) return;
        DiceAppearance appearance = state.stack.get(ModDataComponents.DICE_APPEARANCE);
        DiceAppearance safe = appearance == null ? DiceAppearance.DEFAULT : appearance;
        pose.pushPose();
        // Entity coordinates are at the bottom-center of its 0.5-block collision box.
        pose.translate(0, 0.25, 0);
        pose.mulPose(state.rotation);
        pose.scale(0.5f, 0.5f, 0.5f);
        if (state.sides == 4) {
            submitTriangle(pose, queue, safe, 1,true, .5f,-.5f,-.5f, -.5f,.5f,-.5f, -.5f,-.5f,.5f, -1,-1,-1);
            submitTriangle(pose, queue, safe, 2,true, .5f,.5f,.5f, -.5f,-.5f,.5f, -.5f,.5f,-.5f, -1,1,1);
            submitTriangle(pose, queue, safe, 3,true, .5f,.5f,.5f, .5f,-.5f,-.5f, -.5f,-.5f,.5f, 1,-1,1);
            submitTriangle(pose, queue, safe, 4,true, .5f,.5f,.5f, -.5f,.5f,-.5f, .5f,-.5f,-.5f, 1,1,-1);
            pose.popPose();
            return;
        }
        if (state.sides == 8) {
            float r = 0.8660254f;
            submitTriangle(pose,queue,safe,1,false, r,0,0, 0,r,0, 0,0,r, 1,1,1);
            submitTriangle(pose,queue,safe,2,false, -r,0,0, 0,0,r, 0,r,0, -1,1,1);
            submitTriangle(pose,queue,safe,3,false, -r,0,0, 0,r,0, 0,0,-r, -1,1,-1);
            submitTriangle(pose,queue,safe,4,false, r,0,0, 0,0,-r, 0,r,0, 1,1,-1);
            submitTriangle(pose,queue,safe,5,false, -r,0,0, 0,-r,0, 0,0,r, -1,-1,1);
            submitTriangle(pose,queue,safe,6,false, r,0,0, 0,0,r, 0,-r,0, 1,-1,1);
            submitTriangle(pose,queue,safe,7,false, r,0,0, 0,-r,0, 0,0,-r, 1,-1,-1);
            submitTriangle(pose,queue,safe,8,false, -r,0,0, 0,0,-r, 0,-r,0, -1,-1,-1);
            pose.popPose(); return;
        }
        DicePolyhedra.Shape polyhedron=DicePolyhedra.get(state.sides);
        if(polyhedron!=null){
            for(int i=0;i<polyhedron.faces().length;i++)submitPolygonFace(pose,queue,safe,i+1,polyhedron,i);
            pose.popPose();return;
        }
        submitFace(pose, queue, safe, 2, -0.5f,-0.5f, 0.5f,  0.5f,-0.5f,0.5f,  0.5f,0.5f,0.5f, -0.5f,0.5f,0.5f, 0,0,1);
        submitFace(pose, queue, safe, 5,  0.5f,-0.5f,-0.5f, -0.5f,-0.5f,-0.5f, -0.5f,0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0,0,-1);
        submitFace(pose, queue, safe, 3,  0.5f,-0.5f,0.5f, 0.5f,-0.5f,-0.5f, 0.5f,0.5f,-0.5f, 0.5f,0.5f,0.5f, 1,0,0);
        submitFace(pose, queue, safe, 4, -0.5f,-0.5f,-0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,0.5f,-0.5f,0.5f,-0.5f,-1,0,0);
        submitFace(pose, queue, safe, 1, -0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,0,1,0);
        submitFace(pose, queue, safe, 6, -0.5f,-0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f,-0.5f,0.5f,-0.5f,-0.5f,0.5f,0,-1,0);
        pose.popPose();
    }

    private static void submitPolygonFace(PoseStack pose,SubmitNodeCollector queue,DiceAppearance appearance,int number,DicePolyhedra.Shape shape,int faceIndex){
        int[] face=shape.faces()[faceIndex];org.joml.Vector3f normal=shape.normals()[faceIndex];
        org.joml.Vector3f origin=shape.vertices()[face[0]], u=new org.joml.Vector3f(shape.vertices()[face[1]]).sub(origin).normalize();
        org.joml.Vector3f v=new org.joml.Vector3f(normal).cross(u).normalize();
        float[] us=new float[face.length],vs=new float[face.length];float minU=Float.MAX_VALUE,maxU=-Float.MAX_VALUE,minV=Float.MAX_VALUE,maxV=-Float.MAX_VALUE;
        for(int i=0;i<face.length;i++){org.joml.Vector3f p=new org.joml.Vector3f(shape.vertices()[face[i]]).sub(origin);us[i]=p.dot(u);vs[i]=p.dot(v);minU=Math.min(minU,us[i]);maxU=Math.max(maxU,us[i]);minV=Math.min(minV,vs[i]);maxV=Math.max(maxV,vs[i]);}
        float du=Math.max(1e-5f,maxU-minU),dv=Math.max(1e-5f,maxV-minV);for(int i=0;i<face.length;i++){us[i]=(us[i]-minU)/du;vs[i]=1-(vs[i]-minV)/dv;}
        DiceTextureCache.TextureRef texture=DiceTextureCache.getNumberedFaceTexture(appearance,number);
        queue.submitCustomGeometry(pose,RenderTypes.entityCutoutNoCull(texture.id()),(entry,out)->{
            Matrix4f m=entry.pose();for(int i=1;i<face.length-1;i++){
                polyVertex(m,entry,out,shape.vertices()[face[0]],us[0],vs[0],normal);
                polyVertex(m,entry,out,shape.vertices()[face[i]],us[i],vs[i],normal);
                polyVertex(m,entry,out,shape.vertices()[face[i+1]],us[i+1],vs[i+1],normal);
                polyVertex(m,entry,out,shape.vertices()[face[i+1]],us[i+1],vs[i+1],normal);
            }});
    }
    private static void polyVertex(Matrix4f m,PoseStack.Pose pose,com.mojang.blaze3d.vertex.VertexConsumer out,org.joml.Vector3f p,float u,float v,org.joml.Vector3f n){
        vertex(m,pose,out,p.x*2,p.y*2,p.z*2,u,v,n.x,n.y,n.z);
    }

    private static void submitTriangle(PoseStack pose, SubmitNodeCollector queue, DiceAppearance appearance, int number, boolean d4,
                                       float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float nx,float ny,float nz) {
        DiceTextureCache.TextureRef texture = d4 ? DiceTextureCache.getD4FaceTexture(appearance, number)
                : DiceTextureCache.getNumberedFaceTexture(appearance, number);
        float length = (float)Math.sqrt(nx*nx + ny*ny + nz*nz);
        float fnx = nx / length, fny = ny / length, fnz = nz / length;
        queue.submitCustomGeometry(pose, RenderTypes.entityCutoutNoCull(texture.id()), (entry, v) -> {
            Matrix4f m = entry.pose();
            vertex(m,entry,v,ax,ay,az,.5f,0,fnx,fny,fnz); vertex(m,entry,v,bx,by,bz,0,1,fnx,fny,fnz);
            vertex(m,entry,v,cx,cy,cz,1,1,fnx,fny,fnz); vertex(m,entry,v,cx,cy,cz,1,1,fnx,fny,fnz);
        });
    }

    private static void submitFace(PoseStack pose, SubmitNodeCollector queue, DiceAppearance appearance, int number,
                                   float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz) {
        DiceTextureCache.TextureRef texture = DiceTextureCache.getNumberedFaceTexture(appearance, number);
        queue.submitCustomGeometry(pose, RenderTypes.entityCutout(texture.id()), (entry, vertices) ->
                face(entry.pose(), entry, vertices, ax,ay,az,bx,by,bz,cx,cy,cz,dx,dy,dz,nx,ny,nz));
    }

    private static void face(Matrix4f m, PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer v,
                             float ax,float ay,float az,float bx,float by,float bz,float cx,float cy,float cz,float dx,float dy,float dz,float nx,float ny,float nz) {
        vertex(m,p,v,ax,ay,az,0,1,nx,ny,nz); vertex(m,p,v,bx,by,bz,1,1,nx,ny,nz);
        vertex(m,p,v,cx,cy,cz,1,0,nx,ny,nz); vertex(m,p,v,dx,dy,dz,0,0,nx,ny,nz);
    }
    private static void vertex(Matrix4f m, PoseStack.Pose p, com.mojang.blaze3d.vertex.VertexConsumer v,float x,float y,float z,float u,float w,float nx,float ny,float nz) {
        v.addVertex(m,x,y,z).setColor(0xFFFFFFFF).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0x00F000F0).setNormal(p,nx,ny,nz);
    }
}
