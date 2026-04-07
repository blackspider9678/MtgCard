// src/client/java/com/spider/mtgcard/client/model/CardModelLoadingPlugin.java
package com.spider.mtgcard.client.model;

import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;

public final class CardModelLoadingPlugin implements ModelLoadingPlugin {
    @Override
    public void initialize(Context context) {
        // no-op for now
        // (later you can use context.addModels / context.modifyModelAfterBake etc)
    }
}
