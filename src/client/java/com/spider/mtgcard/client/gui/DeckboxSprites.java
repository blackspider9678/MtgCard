package com.spider.mtgcard.client.gui;

import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;

public final class DeckboxSprites {
    private static final Identifier ATLAS = Identifier.fromNamespaceAndPath("mtg", "textures/gui/mana_pips.png");
    private static final int CELL = 16, ATLAS_COLS = 8, ATLAS_W = ATLAS_COLS * CELL, ATLAS_H = 3 * CELL;

    private record Pip(int u, int v) {}
    private DeckboxSprites(){}

    private static Pip cell(int col, int row){ return new Pip(col * CELL, row * CELL); }

    private static Pip tokenToPip(String t){
        switch (t) {
            case "W": return cell(0,0); case "U": return cell(1,0); case "B": return cell(2,0);
            case "R": return cell(3,0); case "G": return cell(4,0); case "C": return cell(5,0);
            case "X": return cell(6,0);
        }
        if (t.matches("\\d+")) {
            int n = Integer.parseInt(t);
            if (n<=7)  return cell(n,1);
            if (n<=10) return cell(n-8,2);
        }
        return cell(7,0); // fallback
    }

    private static List<Pip> parse(String mana){
        List<Pip> out = new ArrayList<>();
        if (mana == null) return out;
        int i = 0;
        while (i < mana.length()){
            int a = mana.indexOf('{', i); if (a < 0) break;
            int b = mana.indexOf('}', a+1); if (b < 0) break;
            String tok = mana.substring(a+1, b).trim().toUpperCase();
            out.add(tokenToPip(tok));
            i = b + 1;
        }
        return out;
    }

    public static int drawManaCost(GuiGraphics ctx, String mana, int x, int y){
        List<Pip> p = parse(mana);
        int dx = x;
        for (Pip pip : p) {
            // NOTE: pipeline first, u/v are floats
            ctx.blit(
                    RenderPipelines.GUI_TEXTURED,
                    ATLAS,
                    dx, y,
                    (float) pip.u, (float) pip.v,
                    CELL, CELL,
                    ATLAS_W, ATLAS_H
            );
            dx += CELL;
        }
        return dx - x;
    }
}
