package com.spider.mtgcard.dice;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record DiceAppearance(
        int primaryColor,
        int secondaryColor,
        int borderColor,
        int textColor,
        int bannerColor,
        DiceGradientType gradientType,
        boolean glitter,
        boolean foil,
        long glitterSeed,
        Optional<Identifier> bannerPattern
) {
    public static final int DEFAULT_PRIMARY = 0x8B25D6;
    public static final int DEFAULT_SECONDARY = 0xC8248D;
    public static final int DEFAULT_BORDER = 0x05000A;
    public static final int DEFAULT_TEXT = 0xFF8A12;
    public static final int DEFAULT_BANNER = 0xF4E7FF;

    public static final DiceAppearance DEFAULT = new DiceAppearance(
            DEFAULT_PRIMARY,
            DEFAULT_SECONDARY,
            DEFAULT_BORDER,
            DEFAULT_TEXT,
            DEFAULT_BANNER,
            DiceGradientType.VERTICAL,
            false,
            false,
            0L,
            Optional.empty()
    );

    public static final Codec<DiceAppearance> CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.INT.optionalFieldOf("primary_color", DEFAULT_PRIMARY).forGetter(DiceAppearance::primaryColor),
            Codec.INT.optionalFieldOf("secondary_color", DEFAULT_SECONDARY).forGetter(DiceAppearance::secondaryColor),
            Codec.INT.optionalFieldOf("border_color", DEFAULT_BORDER).forGetter(DiceAppearance::borderColor),
            Codec.INT.optionalFieldOf("text_color", DEFAULT_TEXT).forGetter(DiceAppearance::textColor),
            Codec.INT.optionalFieldOf("banner_color", DEFAULT_BANNER).forGetter(DiceAppearance::bannerColor),
            DiceGradientType.CODEC.optionalFieldOf("gradient_type", DiceGradientType.VERTICAL).forGetter(DiceAppearance::gradientType),
            Codec.BOOL.optionalFieldOf("glitter", false).forGetter(DiceAppearance::glitter),
            Codec.BOOL.optionalFieldOf("foil", false).forGetter(DiceAppearance::foil),
            Codec.LONG.optionalFieldOf("glitter_seed", 0L).forGetter(DiceAppearance::glitterSeed),
            Identifier.CODEC.optionalFieldOf("banner_pattern").forGetter(DiceAppearance::bannerPattern)
    ).apply(inst, DiceAppearance::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiceAppearance> STREAM_CODEC = StreamCodec.of(
            DiceAppearance::write,
            DiceAppearance::read
    );

    public DiceAppearance {
        primaryColor = clampRgb(primaryColor);
        secondaryColor = clampRgb(secondaryColor);
        borderColor = clampRgb(borderColor);
        textColor = clampRgb(textColor);
        bannerColor = clampRgb(bannerColor);
        gradientType = gradientType == null ? DiceGradientType.SOLID : gradientType;
        bannerPattern = bannerPattern == null ? Optional.empty() : bannerPattern;
        if (!glitter) glitterSeed = 0L;
    }

    public DiceAppearance withServerControlledEffects(boolean glitter, boolean foil, long glitterSeed, Optional<Identifier> bannerPattern) {
        return new DiceAppearance(
                primaryColor,
                secondaryColor,
                borderColor,
                textColor,
                bannerColor,
                gradientType,
                glitter,
                foil,
                glitter ? glitterSeed : 0L,
                bannerPattern
        );
    }

    public DiceAppearance withBannerPattern(Optional<Identifier> bannerPattern) {
        return new DiceAppearance(
                primaryColor,
                secondaryColor,
                borderColor,
                textColor,
                bannerColor,
                gradientType,
                glitter,
                foil,
                glitterSeed,
                bannerPattern
        );
    }

    private static DiceAppearance read(RegistryFriendlyByteBuf buf) {
        int primary = buf.readVarInt();
        int secondary = buf.readVarInt();
        int border = buf.readVarInt();
        int text = buf.readVarInt();
        int bannerColor = buf.readVarInt();
        DiceGradientType gradient = DiceGradientType.byId(buf.readUtf(32));
        boolean glitter = buf.readBoolean();
        boolean foil = buf.readBoolean();
        long glitterSeed = buf.readLong();
        Optional<Identifier> banner = buf.readBoolean()
                ? Optional.of(Identifier.STREAM_CODEC.decode(buf))
                : Optional.empty();
        return new DiceAppearance(primary, secondary, border, text, bannerColor, gradient, glitter, foil, glitterSeed, banner);
    }

    private static void write(RegistryFriendlyByteBuf buf, DiceAppearance appearance) {
        DiceAppearance safe = appearance == null ? DEFAULT : appearance;
        buf.writeVarInt(safe.primaryColor());
        buf.writeVarInt(safe.secondaryColor());
        buf.writeVarInt(safe.borderColor());
        buf.writeVarInt(safe.textColor());
        buf.writeVarInt(safe.bannerColor());
        buf.writeUtf(safe.gradientType().id(), 32);
        buf.writeBoolean(safe.glitter());
        buf.writeBoolean(safe.foil());
        buf.writeLong(safe.glitterSeed());
        buf.writeBoolean(safe.bannerPattern().isPresent());
        safe.bannerPattern().ifPresent(id -> Identifier.STREAM_CODEC.encode(buf, id));
    }

    public static int clampRgb(int rgb) {
        return rgb & 0xFFFFFF;
    }
}
