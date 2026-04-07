package net.fabricmc.fabric.api.client.model.loading.v1;

public interface ModelLoadingPlugin {
    static void register(ModelLoadingPlugin plugin) {
        // Compatibility no-op. Current project code only registers an empty plugin.
    }

    void initialize(Context context);

    interface Context {
    }
}
