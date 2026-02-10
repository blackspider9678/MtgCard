package com.spider.mtgcard.cardstore;

public record CardStoreCartLine(String setCode, String collectorNumber, int qty) {
    public CardStoreCartLine {
        if (setCode == null) setCode = "";
        if (collectorNumber == null) collectorNumber = "";
        if (qty < 0) qty = 0;
    }
}
