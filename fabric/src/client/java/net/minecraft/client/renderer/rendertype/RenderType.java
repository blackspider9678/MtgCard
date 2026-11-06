package net.minecraft.client.renderer.rendertype;

public final class RenderType {
    private final net.minecraft.client.renderer.RenderType delegate;

    private RenderType(net.minecraft.client.renderer.RenderType delegate) {
        this.delegate = delegate;
    }

    static RenderType wrap(net.minecraft.client.renderer.RenderType delegate) {
        return new RenderType(delegate);
    }

    public net.minecraft.client.renderer.RenderType unwrap() {
        return delegate;
    }
}
