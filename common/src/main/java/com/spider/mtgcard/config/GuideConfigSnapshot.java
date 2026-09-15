package com.spider.mtgcard.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.List;

public record GuideConfigSnapshot(
        boolean anyoneCanImport,
        boolean opsCanImport,
        List<String> importWhitelist,
        String cardLanguage,
        double customCommon,
        double customUncommon,
        double customWildcard,
        double customRare,
        double customRandom,
        double customRandomFoil,
        double customBasicLand,
        double customTokenOrArt,
        boolean packDebug,
        boolean fishingPacks,
        boolean cardStoreEnabled,
        boolean mtgGameEnabled,
        String priceItem,
        String priceBasis
) {
    private static final Gson GSON = new GsonBuilder().create();

    public static GuideConfigSnapshot current() {
        MtgcardConfig cfg = MtgcardConfig.get();
        return new GuideConfigSnapshot(
                cfg.Anyone_Can_Import,
                cfg.Ops_Can_Import,
                List.copyOf(cfg.Import_Whitelist),
                cfg.Card_Language,
                cfg.chance_custom_common,
                cfg.chance_custom_uncommon,
                cfg.chance_custom_wildcard_c_or_u,
                cfg.chance_custom_rare_or_mythic,
                cfg.chance_custom_random,
                cfg.chance_custom_random_foil,
                cfg.chance_custom_basic_land,
                cfg.chance_custom_token_or_art,
                cfg.Pack_Debug,
                cfg.Loot_Packs_From_Fishing,
                cfg.Card_Store_Enabled,
                cfg.MTG_Game_Enabled,
                cfg.Price_Item,
                cfg.Price_Basis
        );
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public static GuideConfigSnapshot fromJson(String json) {
        GuideConfigSnapshot value = GSON.fromJson(json, GuideConfigSnapshot.class);
        if (value == null) throw new IllegalArgumentException("Empty config payload");
        return value;
    }
}
