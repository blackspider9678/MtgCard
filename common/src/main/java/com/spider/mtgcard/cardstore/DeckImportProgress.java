package com.spider.mtgcard.cardstore;

import java.util.List;

/** Tracks incremental deck imports with one small batch outstanding at a time. */
public final class DeckImportProgress<T> {
    private static final int BATCH_SIZE = 20;
    private final List<T> lines;
    private int completed;
    private int imported;

    public DeckImportProgress(List<T> lines) {
        this.lines = List.copyOf(lines);
    }

    public boolean isActive() {
        return completed < lines.size();
    }

    public List<T> currentBatch() {
        if (!isActive()) throw new IllegalStateException("Deck import is complete");
        return lines.subList(completed, Math.min(lines.size(), completed + BATCH_SIZE));
    }

    public void completeBatch(int addedLines) {
        int batchSize = currentBatch().size();
        if (addedLines < 0 || addedLines > batchSize) {
            throw new IllegalArgumentException("Invalid resolved line count: " + addedLines);
        }
        completed += batchSize;
        imported += addedLines;
    }

    public String message() {
        return (isActive() ? "Importing deck: " : "Deck import complete: ")
                + completed + "/" + lines.size() + " lines checked, " + imported + " added"
                + (completed > imported ? ", " + (completed - imported) + " unmatched/failed" : "") + ".";
    }
}
