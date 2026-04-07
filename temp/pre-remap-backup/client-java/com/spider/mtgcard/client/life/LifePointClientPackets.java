package com.spider.mtgcard.client.life;

import com.spider.mtgcard.life.LifePointPackets;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public final class LifePointClientPackets {

    public static void register() {

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.OpenLifeScreenPayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    LifePointScreen.open(payload.pos(), payload.state());
                    ClientPlayNetworking.send(new LifePointPackets.RequestGroupsC2S());
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.SyncLifePayload.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    LifePointClientState.onSync(payload.pos(), payload.state());
                    if (Minecraft.getInstance().screen instanceof LifePointScreen s
                            && s.getPos().equals(payload.pos())) {
                        s.refresh();
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.YourPresetIdS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    var scr = Minecraft.getInstance().screen;
                    if (scr instanceof LifePointScreen s) {
                        s.setYourPresetId(payload.id());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.NearbyResultS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    LifePointClientState.onNearby(payload.origin(), payload.found());

                    if (Minecraft.getInstance().screen instanceof LifePointScreen s) {
                        s.onScanArrived(payload.origin());
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.GroupsListS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    LifePointClientState.onGroupsList(payload.groups());

                    if (Minecraft.getInstance().screen instanceof LifePointScreen s) {
                        s.refresh();
                    }
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.GroupSnapshotS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    LifePointClientState.onGroup(
                            payload.groupId(),
                            payload.name(),
                            payload.started(),
                            payload.activeIndex(),
                            payload.members(),
                            payload.memberNames(),
                            payload.dead()
                    );

                    var st = Minecraft.getInstance().screen;
                    if (st instanceof LifePointScreen lp) lp.refresh();
                })
        );

        ClientPlayNetworking.registerGlobalReceiver(LifePointPackets.GroupRemovedS2C.ID, (payload, ctx) ->
                ctx.client().execute(() -> {
                    LifePointClientState.onGroupRemoved(payload.groupId());

                    if (Minecraft.getInstance().screen instanceof LifePointScreen s) {
                        s.refresh();
                    }
                })
        );
    }

    private LifePointClientPackets() {}
}
