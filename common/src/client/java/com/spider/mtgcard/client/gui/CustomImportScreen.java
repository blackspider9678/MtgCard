// CustomImportScreen.java
package com.spider.mtgcard.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import com.spider.mtgcard.client.compat.MtgGuiScaleHelper;
import com.spider.mtgcard.db.search.ScryfallSyntax;
import com.spider.mtgcard.net.CustomCardPackets;
import com.spider.mtgcard.util.Cockatrice;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import com.spider.mtgcard.client.compat.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Custom card importer
 * - Drag card images, an MSE export file, or an entire MSE export directory.
 * - Scrollable thumbnail grid (left) + edit panel (right) with a draggable scrollbar.
 * - Click a thumb to edit metadata; press "Create" to send batch to server.
 * - NEW:
 *   • Progress bar moved to bottom of right panel, yellow status text.
 *   • Link-hint overlay shown under banner (not covered by “Images added”).
 *   • FRONT/BACK badges on thumbnails.
 */
public final class CustomImportScreen extends com.spider.mtgcard.client.compat.LegacyScreen implements FileDropReceiver {
    // ---- Data model ----
    public static final class Entry {
        public String fileName;
        public String sourceFileName;
        public byte[] imgFront;
        public byte[] imgBack;
        public Meta meta = new Meta();

        // --- Thumbnail texture ---
        public Identifier thumbId;
        public DynamicTexture thumbTex;
        public int thumbW;
        public int thumbH;

        // --- Hover preview texture (lazy) ---
        public Identifier previewId;
        public DynamicTexture previewTex;

        // runtime linking info
        public Link link = new Link();
        public boolean previewWantsBack = false;

        // ✅ add this
        public volatile boolean previewBuilding = false;
    }

    private static final class XmlSource {
        final String xml;
        final Path baseDir;

        XmlSource(String xml, Path baseDir) {
            this.xml = xml;
            this.baseDir = baseDir;
        }
    }

    private static final class ImageMetaMatch {
        final Cockatrice.Meta meta;
        final String method;

        ImageMetaMatch(Cockatrice.Meta meta, String method) {
            this.meta = meta;
            this.method = method;
        }
    }

    private static final class LocalImageMatch {
        final Path path;
        final String sourceFileName;
        final String method;

        LocalImageMatch(Path path, String sourceFileName, String method) {
            this.path = path;
            this.sourceFileName = sourceFileName;
            this.method = method;
        }
    }

    // ---- Manual image import pacing (UI queue) ----
    private final java.util.ArrayDeque<java.util.function.Supplier<java.util.concurrent.CompletableFuture<?>>> importQueue =
            new java.util.ArrayDeque<>();
    private boolean importsRunning = false;
    private int importCooldownTicks = 0;

    private volatile int importQueued = 0;
    private volatile int importDone = 0;
    private volatile int importFailed = 0;
    private volatile String importPhase = "";

    // ---- Upload pacing / cancellation ----
    private static final class UploadJob {
        final Runnable run;
        final int cardsCreated; // 0 for art jobs, >0 for create batch jobs
        final int estimatedBytes;
        UploadJob(Runnable run, int cardsCreated, int estimatedBytes) {
            this.run = run;
            this.cardsCreated = cardsCreated;
            this.estimatedBytes = Math.max(1, estimatedBytes);
        }
    }
    private static final int ART_CHUNK_SIZE = 64 * 1024;
    private static final int UPLOAD_MAX_JOBS_PER_TICK = 4;
    private static final int UPLOAD_MAX_BYTES_PER_TICK = 128 * 1024;
    private static final int CONTROL_PACKET_ESTIMATE = 256;
    private final java.util.ArrayDeque<UploadJob> uploadQueue = new java.util.ArrayDeque<>();
    private boolean uploadsRunning = false;
    private int uploadCooldownTicks = 0; // spacing between sends
    private volatile boolean cancelRequested = false;

    // ---- Upload progress UI (re-uses the right-panel bar) ----
    private volatile boolean uploadUiActive = false;
    private volatile int uploadTotalCards = 0;
    private volatile int uploadCardsSent = 0;   // counts "CustomBatchCreate" sends (1 per card)
    private volatile int uploadTotalJobs  = 0;  // total queued network jobs at start (art chunks + creates)
    private volatile int uploadJobsSent   = 0;  // jobs actually sent
    private volatile String uploadLine2   = "Please keep screen open";

    // ---- Upload ETA tracking ----
    private volatile long uploadStartMs = 0L;
    private volatile long uploadLastSampleMs = 0L;
    private volatile int  uploadLastSampleSent = 0;
    private volatile double uploadRateEma = 0.0; // jobs/sec (smoothed)

    // XML async task handle (so we can stop scheduling more work)
    private java.util.concurrent.CompletableFuture<?> xmlFuture = null;
    private final java.util.concurrent.atomic.AtomicBoolean xmlCancel = new java.util.concurrent.atomic.AtomicBoolean(false);

    // Optional: show upload phase in the existing right-panel progress
    private volatile int netQueued = 0;
    private volatile int netSent = 0;
    private volatile String netPhase = "";

    // ---- Hover preview (Archidekt-style zoom) ----
    private static final int PREVIEW_TEX_W = 384;   // generated texture size (bigger = nicer)
    private static final int PREVIEW_TEX_H = 536;
    private static final int PREVIEW_DRAW_W = 220;  // on-screen drawn size
    private static final int PREVIEW_DRAW_H = 308;
    private int hoveredIndex = -1;
    private int hoveredThumbX = 0, hoveredThumbY = 0;

    // ---- Hover zoom (Archidekt-ish) ----
    private static final int PREVIEW_MAX_W = 360;
    private static final int PREVIEW_MAX_H = 500;
    private static final int PREVIEW_PAD   = 8;
    private static final int PREVIEW_BORDER = 0xFF202020;
    private static final int PREVIEW_BG     = 0xEE0A0A0A;

    // ---- Manual image import pacing (TRUE sequential) ----
    private final ArrayDeque<Path> manualImageQueue = new ArrayDeque<>();
    private boolean manualImportRunning = false;
    private boolean manualInFlight = false;        // <--- NEW: only allow 1 active job
    private int manualImportCooldownTicks = 0;

    private int manualFound = 0;
    private int manualDone = 0;
    private int manualFailed = 0;
    private String manualPhase = "";
    private volatile boolean manualUiActive = false;


    private int gridX1() { return 10; }
    private int gridY1() { return GRID_TOP; }
    private int gridX2() { return panelX - 12; }
    private int gridY2() { return this.height - 40; } // stops hover over bottom bar too

    private static final java.util.concurrent.ExecutorService IMG_EXEC =
            java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
                Thread t = new Thread(r, "mtgcard-img");
                t.setDaemon(true);
                return t;
            });


    @Override
    public void tick() {
        super.tick();

        // ---- Manual drag-drop images -> add to grid one-by-one (sequential) ----
        if (manualImportRunning && !cancelRequested) {

            if (manualImportCooldownTicks > 0) {
                manualImportCooldownTicks--;
            } else if (!manualInFlight) { // <--- only start next when none running
                Path p = manualImageQueue.pollFirst();
                if (p == null) {
                    manualImportRunning = false;
                    manualPhase = "Manual import done.";
                } else {
                    manualInFlight = true;
                    manualPhase = "Importing: " + p.getFileName();
                    importOneDroppedImageAsync(p);
                }
            }
        }

        // ---- upload pumping ----
        if (!uploadsRunning) return;
        if (cancelRequested) return;

        if (uploadCooldownTicks > 0) {
            uploadCooldownTicks--;
            return;
        }

        int jobsBudget = UPLOAD_MAX_JOBS_PER_TICK;
        int bytesBudget = UPLOAD_MAX_BYTES_PER_TICK;

        while (jobsBudget > 0) {
            if (cancelRequested) break;

            UploadJob job = uploadQueue.peekFirst();
            netQueued = uploadQueue.size();

            if (job == null) {
                uploadsRunning = false;
                if (uploadUiActive) {
                    uploadLine2 = "Done. You can close this screen.";
                    uploadJobsSent = uploadTotalJobs;
                } else if (!xmlImportInProgress) {
                    netPhase = "All queued uploads sent.";
                }
                if (createBtn != null) createBtn.active = true;
                break;
            }

            if (jobsBudget != UPLOAD_MAX_JOBS_PER_TICK && job.estimatedBytes > bytesBudget) {
                break;
            }

            uploadQueue.pollFirst();
            try {
                job.run.run();
                uploadJobsSent++;
                netSent++;
                jobsBudget--;
                bytesBudget = Math.max(0, bytesBudget - job.estimatedBytes);

                // ---- ETA sampling / EMA update (jobs/sec) ----
                long now = System.currentTimeMillis();

// sample at most ~4x per second (keeps it stable)
                if (now - uploadLastSampleMs >= 250L) {
                    int sentDelta = uploadJobsSent - uploadLastSampleSent;
                    long dtMs = now - uploadLastSampleMs;

                    if (dtMs > 0 && sentDelta > 0) {
                        double instRate = sentDelta / (dtMs / 1000.0); // jobs/sec

                        // Exponential moving average smoothing
                        // 0.25 = reacts quickly but not jittery; tweak 0.15–0.35
                        double alpha = 0.25;
                        uploadRateEma = (uploadRateEma <= 0.00001)
                                ? instRate
                                : (alpha * instRate + (1.0 - alpha) * uploadRateEma);

                        uploadLastSampleMs = now;
                        uploadLastSampleSent = uploadJobsSent;
                    }
                }

                if (job.cardsCreated > 0) uploadCardsSent += job.cardsCreated;

                // ETA sampling block can stay as-is (it uses uploadJobsSent)
            } catch (Throwable t) {
            }
        }
    }

    private void enqueueManualImageImport(Path p) {
        if (p == null) return;
        String name = p.getFileName().toString();
        String lower = name.toLowerCase(Locale.ROOT);

        importQueue.add(() -> java.util.concurrent.CompletableFuture.runAsync(() -> {
            if (cancelRequested) return;

            // read + encode off-thread
            byte[] raw;
            try {
                raw = java.nio.file.Files.readAllBytes(p);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            EncodedImage enc;
            try {
                enc = preferWebpElsePng(raw, lower);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }

            // Decide front/back grouping based on filename suffix
            String stem = stripExt(lower);
            boolean isBack = stem.endsWith("_f1") || stem.endsWith("_back");
            String base = stem;
            if (stem.endsWith("_f1")) base = stem.substring(0, stem.length() - 3);
            if (stem.endsWith("_back")) base = stem.substring(0, stem.length() - 5);
            if (stem.endsWith("_f0")) base = stem.substring(0, stem.length() - 3);
            if (stem.endsWith("_front")) base = stem.substring(0, stem.length() - 6);

            final String finalBase = base;
            final boolean finalIsBack = isBack;
            final byte[] finalBytes = enc.bytes;
            final String finalExt = enc.ext;

            // Now hop to client thread to mutate UI + entries
            if (this.minecraft != null) {
                this.minecraft.execute(() -> {
                    if (cancelRequested) return;

                    // Find existing entry with same base waiting for other face
                    Entry existing = null;
                    for (Entry e : entries) {
                        if (e != null && nz(e.fileName).startsWith(finalBase)) {
                            // crude match, but works well with your base naming
                            existing = e;
                            break;
                        }
                    }

                    if (existing == null) {
                        Entry e = new Entry();
                        e.fileName = finalBase + finalExt;
                        e.sourceFileName = name;

                        if (finalIsBack) {
                            // If only a back was dropped first, treat it as front for now.
                            e.imgFront = finalBytes;
                            e.imgBack = null;
                            e.meta.doubleFaced = false;
                        } else {
                            e.imgFront = finalBytes;
                            e.imgBack = null;
                            e.meta.doubleFaced = false;
                        }

                        e.meta.name = prettyBaseName(finalBase);
                        applyCockatriceToEntry(e);
                        buildThumbnail(e);
                        entries.add(e);
                        applyAutoTransformLinks();

                        if (selected < 0) {
                            selected = entries.size() - 1;
                            syncEditorFromSelected();
                        }
                    } else {
                        existing.sourceFileName = name;
                        // attach as back if possible
                        if (finalIsBack) {
                            existing.imgBack = finalBytes;
                            existing.meta.doubleFaced = true;
                        } else {
                            // If we already had a front and user drops another front with same base, replace it.
                            existing.imgFront = finalBytes;
                        }
                        applyCockatriceToEntry(existing);
                        buildThumbnail(existing);
                        applyAutoTransformLinks();
                    }

                    rebuildVisibleEntries();
                    lastStatus = "Imported: " + name;
                });
            }
        }));
    }

    private void enqueueUpload(Runnable r) { enqueueUpload(r, 0, CONTROL_PACKET_ESTIMATE); }

    private void enqueueUpload(Runnable r, int cardsCreated) {
        enqueueUpload(r, cardsCreated, CONTROL_PACKET_ESTIMATE);
    }

    private void enqueueUpload(Runnable r, int cardsCreated, int estimatedBytes) {
        if (r == null) return;
        if (cancelRequested) return;
        uploadQueue.addLast(new UploadJob(r, cardsCreated, estimatedBytes));
        netQueued = uploadQueue.size();
    }

    private void startUploadsIfNeeded() {
        uploadsRunning = true;
        // tick() will pump the queue
    }

    private void cancelAllAsyncWork() {
        cancelRequested = true;
        xmlCancel.set(true);
        uploadQueue.clear();
        netQueued = 0;
        netPhase = "Cancelled.";
        uploadsRunning = false;

        if (xmlFuture != null) {
            xmlFuture.cancel(true); // won't always interrupt HttpClient.send, but stops continuations
            xmlFuture = null;
        }
    }

    private int hoveredIndexVisibleOnly(double mx, double my) {
        // Must be inside the grid viewport window
        if (mx < gridX1() || mx > gridX2() || my < gridY1() || my > gridY2()) return -1;

        final int maxX = panelX - 12;
        int usableW = maxX - 10;
        int cols = Math.max(1, (usableW + GAP) / (BOX + GAP));
        int x0 = 10;
        int y0 = GRID_TOP - scrollY;

        for (int viewIndex = 0; viewIndex < visibleEntryIndexes.size(); viewIndex++) {
            int entryIndex = visibleEntryIndexes.get(viewIndex);
            int col = viewIndex % cols;
            int row = viewIndex / cols;
            int x = x0 + col * (BOX + GAP);
            int y = y0 + row * (BOX_H + GAP);

            // Only allow hover if the THUMB is fully visible in the viewport
            if (y < gridY1() || (y + BOX_H) > gridY2()) continue;

            if (mx >= x && mx <= x + BOX && my >= y && my <= y + BOX_H) return entryIndex;
        }
        return -1;
    }

    private void rebuildVisibleEntries() {
        String raw = nz(searchQuery);
        boolean blankOnly = hasBlankSearchToken(raw);
        String query = stripBlankSearchToken(raw);
        Predicate<Entry> predicate = buildImportSearchPredicate(query);

        visibleEntryIndexes.clear();
        for (int i = 0; i < entries.size(); i++) {
            Entry entry = entries.get(i);
            if (blankOnly && !isBlankName(entry)) continue;
            if (!predicate.test(entry)) continue;
            visibleEntryIndexes.add(i);
        }

        hoveredIndex = -1;
        updateSearchClearButtonState();
        updateMaxScroll();
    }

    private Predicate<Entry> buildImportSearchPredicate(String rawQuery) {
        ScryfallSyntax.Parsed parsed = ScryfallSyntax.parse(rawQuery);
        return entry -> parsed.matches(new ImportCardView(entry));
    }

    private Predicate<Entry> buildImportTermPredicate(ImportSearchTerm term) {
        String value = nz(term.value).trim();
        if (value.isEmpty()) return entry -> true;

        String field = term.field;
        if (field == null || field.isBlank()) {
            return entry -> containsIgnoreCase(entry.meta.name, value);
        }

        return switch (field) {
            case "name" -> entry -> containsIgnoreCase(entry.meta.name, value);
            case "mana" -> entry -> containsIgnoreCase(entry.meta.manaCost, value);
            case "type" -> entry -> containsIgnoreCase(entry.meta.typeLine, value);
            case "set" -> entry -> containsIgnoreCase(entry.meta.set, value);
            case "rarity" -> entry -> containsIgnoreCase(entry.meta.rarity, value);
            case "text" -> entry -> containsIgnoreCase(entry.meta.oracleText, value);
            case "power" -> entry -> containsIgnoreCase(entry.meta.power, value);
            case "toughness" -> entry -> containsIgnoreCase(entry.meta.toughness, value);
            case "loyalty" -> entry -> containsIgnoreCase(entry.meta.loyalty, value);
            case "id" -> entry -> containsIgnoreCase(entry.meta.id, value);
            case "is" -> buildImportIsPredicate(value);
            default -> entry -> panelContainsIgnoreCase(entry, value);
        };
    }

    private Predicate<Entry> buildImportIsPredicate(String value) {
        String flag = value.toLowerCase(Locale.ROOT);
        return switch (flag) {
            case "blank" -> CustomImportScreen::isBlankName;
            case "token" -> CustomImportScreen::isTokenLike;
            case "dfc", "doublefaced", "double-faced" -> entry -> entry.meta.doubleFaced;
            case "single", "singleface", "single-face" -> entry -> !entry.meta.doubleFaced;
            default -> entry -> panelContainsIgnoreCase(entry, value);
        };
    }

    private static List<List<ImportSearchTerm>> parseImportSearch(String raw) {
        String query = nz(raw).trim();
        if (query.isEmpty()) return List.of(List.of());

        String[] orParts = query.split("(?i)\\s+or\\s+");
        List<List<ImportSearchTerm>> groups = new ArrayList<>();
        for (String part : orParts) {
            List<ImportSearchTerm> group = new ArrayList<>();
            for (String token : lexImportQuery(part)) {
                if (token == null || token.isBlank()) continue;
                boolean neg = false;
                String body = token;
                if (body.startsWith("-")) {
                    neg = true;
                    body = body.substring(1);
                }

                String field = null;
                String value = body;
                int sep = firstFieldSeparator(body);
                if (sep > 0) {
                    field = normalizeImportField(body.substring(0, sep));
                    value = body.substring(sep + 1);
                }
                group.add(new ImportSearchTerm(field, unquoteImportValue(value.trim()), neg));
            }
            groups.add(group);
        }
        return groups;
    }

    private static List<String> lexImportQuery(String input) {
        ArrayList<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);
            if (ch == '"') {
                inQuotes = !inQuotes;
                continue;
            }
            if (!inQuotes && Character.isWhitespace(ch)) {
                if (cur.length() > 0) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
            } else {
                cur.append(ch);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    private static int firstFieldSeparator(String token) {
        int colon = token.indexOf(':');
        int equals = token.indexOf('=');
        if (colon < 0) return equals;
        if (equals < 0) return colon;
        return Math.min(colon, equals);
    }

    private static String normalizeImportField(String rawField) {
        String field = nz(rawField).trim().toLowerCase(Locale.ROOT);
        return switch (field) {
            case "", "n" -> "name";
            case "mana", "cost", "manacost", "mc" -> "mana";
            case "t" -> "type";
            case "s" -> "set";
            case "r", "rar" -> "rarity";
            case "oracle" -> "text";
            case "pow", "p" -> "power";
            case "tou" -> "toughness";
            case "loy" -> "loyalty";
            default -> field;
        };
    }

    private static String unquoteImportValue(String value) {
        if (value == null) return "";
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static boolean panelContainsIgnoreCase(Entry entry, String needle) {
        return containsIgnoreCase(entry.meta.name, needle)
                || containsIgnoreCase(entry.meta.manaCost, needle)
                || containsIgnoreCase(entry.meta.typeLine, needle)
                || containsIgnoreCase(entry.meta.set, needle)
                || containsIgnoreCase(entry.meta.rarity, needle)
                || containsIgnoreCase(entry.meta.oracleText, needle)
                || containsIgnoreCase(entry.meta.power, needle)
                || containsIgnoreCase(entry.meta.toughness, needle)
                || containsIgnoreCase(entry.meta.loyalty, needle)
                || containsIgnoreCase(entry.meta.id, needle);
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        if (haystack == null || needle == null) return false;
        return haystack.toLowerCase(Locale.ROOT).contains(needle.toLowerCase(Locale.ROOT));
    }

    private static final class ImportSearchTerm {
        final String field;
        final String value;
        final boolean neg;

        ImportSearchTerm(String field, String value, boolean neg) {
            this.field = field;
            this.value = value;
            this.neg = neg;
        }
    }

    private static boolean hasBlankSearchToken(String raw) {
        if (raw == null || raw.isBlank()) return false;
        for (String part : raw.trim().split("\\s+")) {
            if (part.equalsIgnoreCase(BLANK_SEARCH_TOKEN)) return true;
        }
        return false;
    }

    private static String stripBlankSearchToken(String raw) {
        if (raw == null || raw.isBlank()) return "";
        List<String> keep = new ArrayList<>();
        for (String part : raw.trim().split("\\s+")) {
            if (!part.equalsIgnoreCase(BLANK_SEARCH_TOKEN)) keep.add(part);
        }
        return String.join(" ", keep).trim();
    }

    private static boolean isBlankName(Entry entry) {
        return entry == null || nz(entry.meta.name).isBlank();
    }

    private static boolean isTokenLike(Entry entry) {
        String typeLine = nz(entry.meta.typeLine).toLowerCase(Locale.ROOT);
        return hasTypeWord(typeLine, "token")
                || hasTypeWord(typeLine, "emblem")
                || hasTypeWord(typeLine, "dungeon")
                || hasTypeWord(typeLine, "attraction")
                || hasTypeWord(typeLine, "sticker")
                || hasTypeWord(typeLine, "contraption")
                || hasTypeWord(typeLine, "scheme")
                || hasTypeWord(typeLine, "plane")
                || hasTypeWord(typeLine, "phenomenon")
                || hasTypeWord(typeLine, "vanguard");
    }

    private static boolean hasTypeWord(String typeLine, String word) {
        if (typeLine == null || typeLine.isBlank()) return false;
        for (String part : typeLine.split("[^a-z]+")) {
            if (part.equals(word)) return true;
        }
        return false;
    }

    public static final class Meta {
        public String id = "";
        public String name = "";
        public String manaCost = "";
        public String typeLine = "";
        public String rarity = "common";
        public String set = "CSTM";
        public String collectorNumber = "";
        public String imageFileName = "";
        public String oracleText = "";
        public String power = "";
        public String toughness = "";
        public String loyalty = "";
        public boolean doubleFaced = false;
        public String backName = "";
        public String backTypeLine = "";
        public String backOracleText = "";
        public String backPower = "";
        public String backToughness = "";
        public String backLoyalty = "";
    }

    private static final class ImportCardView implements ScryfallSyntax.CardView {
        private final Entry entry;
        private final Meta meta;

        ImportCardView(Entry entry) {
            this.entry = entry;
            this.meta = entry == null || entry.meta == null ? new Meta() : entry.meta;
        }

        @Override public String name() { return nz(meta.name); }
        @Override public String otherNames() { return nz(meta.backName); }
        @Override public String set() { return nz(meta.set); }
        @Override public String collectorNumber() {
            String cn = nz(meta.collectorNumber);
            return cn.isBlank() ? nz(meta.id) : cn;
        }
        @Override public String rarity() { return nz(meta.rarity); }
        @Override public String manaCost() { return nz(meta.manaCost); }
        @Override public String typeLine() { return joinSearchText(meta.typeLine, meta.backTypeLine); }
        @Override public String oracleText() { return joinSearchText(meta.oracleText, meta.backOracleText); }
        @Override public String power() { return nz(meta.power); }
        @Override public String toughness() { return nz(meta.toughness); }
        @Override public String loyalty() { return nz(meta.loyalty); }
        @Override public String layout() { return meta.doubleFaced ? "custom_dfc" : "custom"; }
        @Override public int manaValue() { return estimateManaValue(meta.manaCost); }
        @Override public Set<String> colors() { return ScryfallSyntax.colorsFromManaCost(meta.manaCost); }
        @Override public Set<String> colorIdentity() { return ScryfallSyntax.colorsFromManaCost(meta.manaCost); }
        @Override public boolean tokenLike() { return entry != null && isTokenLike(entry); }
        @Override public boolean legendary() { return hasTypeWord(nz(meta.typeLine).toLowerCase(Locale.ROOT), "legendary"); }
        @Override public boolean doubleFaced() { return meta.doubleFaced; }
    }

    private static String joinSearchText(String... values) {
        StringBuilder out = new StringBuilder();
        if (values != null) {
            for (String value : values) {
                if (value == null || value.isBlank()) continue;
                if (!out.isEmpty()) out.append(' ');
                out.append(value);
            }
        }
        return out.toString();
    }

    private static int estimateManaValue(String manaCost) {
        if (manaCost == null || manaCost.isBlank()) return 0;
        int total = 0;
        String s = manaCost.trim();
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == '{') {
                int end = s.indexOf('}', i + 1);
                if (end < 0) break;
                total += manaSymbolValue(s.substring(i + 1, end));
                i = end + 1;
                continue;
            }
            if (Character.isDigit(ch)) {
                int start = i;
                while (i < s.length() && Character.isDigit(s.charAt(i))) i++;
                total += parseManaNumber(s.substring(start, i));
                continue;
            }
            if ("WUBRGC".indexOf(Character.toUpperCase(ch)) >= 0) total++;
            i++;
        }
        return Math.max(0, total);
    }

    private static int manaSymbolValue(String symbol) {
        String s = symbol == null ? "" : symbol.trim().toUpperCase(Locale.ROOT);
        if (s.isBlank() || s.equals("X") || s.equals("Y") || s.equals("Z")) return 0;
        if (s.contains("/")) return 1;
        if ("WUBRGC".contains(s)) return 1;
        return parseManaNumber(s);
    }

    private static int parseManaNumber(String value) {
        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (Exception ignored) {
            return 0;
        }
    }

    // --- Linking state & helpers ---
    private static final class Link {
        int partnerIndex = -1;   // index in `entries`
        boolean isFront = true;  // true = this entry is the FRONT face of the pair
    }
    private enum LinkMode { NONE, LINK_AS_FRONT, LINK_AS_BACK }

    private LinkMode linkPendingMode = LinkMode.NONE; // awaiting a partner click?

    private final List<Entry> entries = new ArrayList<>();
    private final List<Integer> visibleEntryIndexes = new ArrayList<>();
    private int selected = -1;
    private EditBox searchBox;
    private Button searchClearBtn;
    private String searchQuery = "";

    private Button createBtn, rarityBtn, dfcToggleBtn;
    private EditBox nameF, manaF, typeF, setF, powF, touF, loyF;
    private SimpleTextArea textF; // multiline Oracle Text (custom widget below)

    // Metadata cache from Cockatrice XMLs
    private final Map<String, Cockatrice.Meta> cockatriceByName = new HashMap<>();
    private final Map<String, ImageMetaMatch> cockatriceByImageField = new HashMap<>();
    private final Map<String, ImageMetaMatch> cockatriceByCollectorImage = new HashMap<>();
    private final Map<String, ImageMetaMatch> cockatriceByLegacyImage = new HashMap<>();
    private final Map<String, ImageMetaMatch> cockatriceByDuplicateImage = new HashMap<>();

    // Right panel geometry
    private int panelX, panelW;

    // ---- UI palette (add) ----
    private static final int PROG_BG     = 0xFF2A2A2A;
    private static final int PROG_BORDER = 0xFF000000;
    private static final int PROG_FILL   = 0xFF4CAF50; // in-progress green
    private static final int PROG_DONE   = 0xFF1B5E20; // dark green when complete
    private static final int UI_YELLOW = 0xFFFFE070;

    // Gallery layout + scroll
    private static final int BOX = 96;          // thumbnail box (w)
    private static final int BOX_H = 134;       // thumbnail box (h)
    private static final int GAP = 8;

    private static final int THUMB_SCALE = 10;
    private static final String CLEAR_GLYPH = "\u00D7";
    private static final String BLANK_SEARCH_TOKEN = "[Blank]";

    private static final int GRID_TOP = 44;     // area below banner text
    private int scrollY = 0;
    private int maxScrollY = 0;

    // Scrollbar (drawn at left edge of the edit panel)
    private boolean barDragging = false;
    private int barDragOffsetY = 0;

    private String lastStatus = "";
    private final Set<String> importedPicUrls = new HashSet<>();

    // XML import progress
    private volatile boolean xmlImportInProgress = false;
    private volatile int xmlImportFound = 0;
    private volatile int xmlImportDone = 0;
    private volatile int xmlImportFailed = 0;
    private volatile String xmlImportPhase = "";

    private Button removeBtn, linkFrontBtn, linkBackBtn, clearLinkBtn;

    // Shared GLFW cursors (created once)
    private static final int CURSOR_ARROW = 0;
    private static final int CURSOR_IBEAM = 1;
    private static void ensureCursors() {
        if (MOUSE_CURSOR_ARROW == 0L)  MOUSE_CURSOR_ARROW = GLFW.glfwCreateStandardCursor(GLFW.GLFW_ARROW_CURSOR);
        if (MOUSE_CURSOR_IBEAM == 0L)  MOUSE_CURSOR_IBEAM = GLFW.glfwCreateStandardCursor(GLFW.GLFW_IBEAM_CURSOR);
    }
    private static void applyCursor(int which) {
        ensureCursors();
        var win = Minecraft.getInstance().getWindow();
        if (win == null) return;
        long handle = win.handle();
        long cur = (which == CURSOR_IBEAM) ? MOUSE_CURSOR_IBEAM : MOUSE_CURSOR_ARROW;
        GLFW.glfwSetCursor(handle, cur);
    }


    public static void open() {
        var mc = Minecraft.getInstance();
        mc.execute(() -> mc.gui.setScreen(new CustomImportScreen()));
    }

    public CustomImportScreen() { super(Component.literal("Import Custom Cards")); }

    @Override public boolean isPauseScreen() { return false; }

    // ---- Lifecycle ----
    @Override
    protected void init() {
        if (MtgGuiScaleHelper.applyFixedGuiScale(this, 2)) return;

        panelW = Math.max(220, Math.min(260, (int)(this.width * 0.22)));
        panelX = this.width - panelW - 10;

        int bottomY = this.height - 30;

        createBtn = Button.builder(Component.literal("Create (send to server)"), b -> sendToServer())
                .bounds(this.width - 210, bottomY, 200, 20).build();
        addRenderableWidget(createBtn);

        // --- Bottom controls (left): Remove + Link flow ---
        removeBtn = Button.builder(Component.literal("Remove"), b -> removeSelected())
                .bounds(10, bottomY, 70, 20).build();
        addRenderableWidget(removeBtn);

        int linkW = 95, gap = 6;
        int linkX = 90;

        linkFrontBtn = Button.builder(Component.literal("Link as FRONT"), b -> linkSelectedAs(true))
                .bounds(linkX, bottomY, linkW, 20).build();
        addRenderableWidget(linkFrontBtn);

        linkBackBtn = Button.builder(Component.literal("Link as BACK"), b -> linkSelectedAs(false))
                .bounds(linkX + (linkW + gap), bottomY, linkW, 20).build();
        addRenderableWidget(linkBackBtn);

        clearLinkBtn = Button.builder(Component.literal("Clear Link"), b -> clearLinkForSelected())
                .bounds(linkX + 2*(linkW + gap), bottomY, 80, 20).build();
        addRenderableWidget(clearLinkBtn);

        buildSearchWidgets();
        buildEditorWidgets();
        syncEditorFromSelected();
        rebuildVisibleEntries();
    }

    private void buildSearchWidgets() {
        final int headerY = 22;
        final int fieldH = 16;
        final int clearW = 16;
        final int labelRight = 10 + this.font.width("Images added: " + entries.size());
        final int leftX = Math.max(140, labelRight + 14);
        final int clearX = panelX - 18;
        final int searchW = Math.max(70, clearX - 2 - leftX);

        searchBox = new EditBox(this.font, leftX, headerY, searchW, fieldH, Component.literal(""));
        searchBox.setBordered(true);
        searchBox.setEditable(true);
        searchBox.setHint(Component.literal("Scryfall syntax or " + BLANK_SEARCH_TOKEN));
        searchBox.setMaxLength(512);
        searchBox.setValue(searchQuery);
        searchBox.setResponder(value -> {
            searchQuery = value;
            rebuildVisibleEntries();
        });
        addRenderableWidget(searchBox);

        searchClearBtn = Button.builder(Component.literal(CLEAR_GLYPH), b -> {
            searchBox.setValue("");
            searchBox.setFocused(true);
        }).bounds(clearX, headerY, clearW, fieldH).build();
        searchClearBtn.setMessage(Component.literal(CLEAR_GLYPH));
        addRenderableWidget(searchClearBtn);
        updateSearchClearButtonState();
    }

    private void updateSearchClearButtonState() {
        if (searchClearBtn != null) searchClearBtn.active = !nz(searchQuery).isEmpty();
    }

    private void blurSearchIfClickedAway(double mx, double my) {
        if (searchBox == null || !searchBox.isFocused()) return;
        boolean overSearch = searchBox.isMouseOver(mx, my);
        boolean overClear = searchClearBtn != null && searchClearBtn.isMouseOver(mx, my);
        if (!overSearch && !overClear) {
            searchBox.setFocused(false);
            this.setFocused(null);
        }
    }

    private boolean focusSearchIfClicked(MouseButtonEvent click, boolean bl) {
        if (searchBox == null) return false;
        double mx = click.x();
        double my = click.y();
        if (!searchBox.isMouseOver(mx, my)) return false;
        blurAllExcept(searchBox);
        searchBox.setFocused(true);
        this.setFocused(searchBox);
        return searchBox.mouseClicked(click, bl);
    }

    private void updateLinkButtonsVisibility() {
        boolean show = false;
        if (selected >= 0 && selected < entries.size()) {
            show = entries.get(selected).meta.doubleFaced;
        }
        if (linkFrontBtn != null) linkFrontBtn.visible = show;
        if (linkBackBtn  != null) linkBackBtn.visible  = show;
        if (clearLinkBtn != null) clearLinkBtn.visible = show;
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
        this.clearWidgets();
        this.init();
    }

    @Override
    public void removed() {
        cancelAllAsyncWork(); // <-- important: stops future scheduling + stops queued sends
        MtgGuiScaleHelper.restoreGuiScale(this);

        if (minecraft == null) return;
        var tm = minecraft.getTextureManager();
        for (var e : entries) {
            if (e.thumbId != null) tm.release(e.thumbId);
            if (e.thumbTex != null) e.thumbTex.close();
            e.thumbId = null;
            e.thumbTex = null;

            if (e.previewId != null) tm.release(e.previewId);
            if (e.previewTex != null) e.previewTex.close();
            e.previewId = null;
            e.previewTex = null;
        }
    }


    // ---- Render ----
    @Override
    public void render(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
        // Solid dim (avoid 1.21 blur crash)
        ctx.fill(0, 0, this.width, this.height, 0xB0000000);

        // --- Define "window" for the grid (left column only) ---
        final int gridX1 = 10;
        final int gridY1 = GRID_TOP;
        final int gridX2 = panelX - 12;
        final int gridY2 = this.height - 40;

        // ==== THUMBNAILS (clipped to window) ====
        ctx.enableScissor(gridX1, gridY1, gridX2, gridY2);
        {
            final int maxX = panelX - 12;
            int usableW = maxX - 10;
            int cols = Math.max(1, (usableW + GAP) / (BOX + GAP));
            int x0 = 10;
            int y0 = GRID_TOP - scrollY;

            hoveredIndex = -1;

            for (int viewIndex = 0; viewIndex < visibleEntryIndexes.size(); viewIndex++) {
                int entryIndex = visibleEntryIndexes.get(viewIndex);
                int col = viewIndex % cols;
                int row = viewIndex / cols;
                int x = x0 + col * (BOX + GAP);
                int y = y0 + row * (BOX_H + GAP);

                if (mouseX >= x && mouseX <= x + BOX && mouseY >= y && mouseY <= y + BOX_H) {
                    hoveredIndex = entryIndex;
                    hoveredThumbX = x;
                    hoveredThumbY = y;
                }

                // Skip rows far outside viewport
                if (y > gridY2 || y + BOX_H < gridY1 - 40) continue;

                var e = entries.get(entryIndex);
                int border = (entryIndex == selected) ? 0xFFFFD700 : 0xFF404040; // gold for selected
                ctx.fill(x - 2, y - 2, x + BOX + 2, y + BOX_H + 2, border);
                ctx.fill(x, y, x + BOX, y + BOX_H, 0xFF1A1A1A);

                if (e.thumbId != null) {
                    int imgW = BOX - 4;
                    int imgH = BOX_H - 4;

                    int texW = (e.thumbW > 0 ? e.thumbW : imgW);
                    int texH = (e.thumbH > 0 ? e.thumbH : imgH);

                    ctx.blit(
                            RenderPipelines.GUI_TEXTURED,
                            e.thumbId,
                            x+2, y+2,
                            0f, 0f,
                            imgW, imgH,
                            imgW, imgH
                    );

                    // FRONT/BACK badge
                    String faceBadge = faceBadgeFor(e);
                    if (faceBadge != null) drawCornerBadge(ctx, x + 3, y + 3, faceBadge);

                    String label = e.meta.name.isEmpty() ? e.fileName : e.meta.name;
                    int pad = 3;      // inner padding
                    int maxLines = 3; // up to 3 lines on thumbnail
                    drawWrappedThumbLabel(ctx, label, x, y, BOX, BOX_H, pad, maxLines);
                } else {
                    ctx.drawString(this.font, "IMG", x + BOX / 2 - 10, y + BOX_H / 2 - 4, 0xFFAAAAAA, false);
                }

                // Linking target cue
                int partnerIdx = -1;
                if (selected >= 0 && selected < entries.size()) {
                    var sel = entries.get(selected);
                    if (sel.link != null) partnerIdx = sel.link.partnerIndex;
                }
                if (entryIndex == partnerIdx) {
                    int t = 3; // thickness
                    int r = 0xFFFF2D2D;
                    ctx.fill(x - 2, y - 2, x + BOX + 2, y - 2 + t, r);
                    ctx.fill(x - 2, y + BOX_H + 2 - t, x + BOX + 2, y + BOX_H + 2, r);
                    ctx.fill(x - 2, y - 2, x - 2 + t, y + BOX_H + 2, r);
                    ctx.fill(x + BOX + 2 - t, y - 2, x + BOX + 2, y + BOX_H + 2, r);
                }
            }

            if (visibleEntryIndexes.isEmpty()) {
                String empty = nz(searchQuery).isBlank() ? "No images added yet." : "No cards match that search.";
                ctx.drawString(this.font, empty, x0, GRID_TOP + 8, 0xFFAAAAAA, false);
            }
        }
        ctx.disableScissor();

        // --- Top "window frame" edge so cards disappear underneath it ---
        ctx.fill(gridX1, GRID_TOP - 1, gridX2, GRID_TOP, 0xFF000000);
        ctx.fill(gridX1, GRID_TOP,     gridX2, GRID_TOP + 6, 0x40000000);


        // --- Banner text (on top of the window)
        ctx.drawString(this.font,
                "Drag any Magic Set Editor export file, folder, or card images here",
                10, 10, 0xFFEFEFEF, true);
        ctx.drawString(this.font,
                "Images added: " + entries.size(),
                10, 26, 0xFFB0FFB0, true);

        // --- Link mode hint (placed visibly under the banner, not covered)
        renderLinkHint(ctx);

        // Editor panel bg + title
        ctx.fill(panelX - 8, 20, panelX + panelW, this.height - 40, 0xAA101010);
        ctx.drawString(this.font, "Edit Image", panelX, 22, 0xFFFFE070, false); // keep yellow

        // --- Progress UI at the bottom of the right panel (with yellow text)
        renderRightPanelProgress(ctx);

        // Grid scrollbar
        drawGridScrollbar(ctx);

        super.render(ctx, mouseX, mouseY, delta);
    }

    // FRONT/BACK badge text for a thumbnail
    private String faceBadgeFor(Entry e) {
        boolean linked = e.link != null && e.link.partnerIndex >= 0;
        if (linked) return e.link.isFront ? "FRONT" : "BACK";
        if (e.meta.doubleFaced && e.imgBack != null && e.imgBack.length > 0) return "FRONT";
        return null;
    }

    private void drawCornerBadge(GuiGraphics ctx, int x, int y, String text) {
        if (text == null || text.isEmpty()) return;
        int padX = 4, padY = 2;
        int tw = this.font.width(text);
        int h = this.font.lineHeight;
        int w = tw + padX * 2;

        // badge background + subtle border
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + padY * 2 + 1, 0x66000000);
        ctx.fill(x, y, x + w, y + h + padY * 2, 0xCC0E0E0E);
        // text
        ctx.drawString(this.font, Component.literal(text).getVisualOrderText(), x + padX, y + padY, 0xFFFFE070, false);
    }

    private void renderLinkHint(GuiGraphics ctx) {
        if (linkPendingMode == LinkMode.NONE) return;

        String msg = (linkPendingMode == LinkMode.LINK_AS_FRONT)
                ? "Link mode: Select the BACK image for this FRONT (Esc to cancel)."
                : "Link mode: Select the FRONT image for this BACK (Esc to cancel).";

        int yTop = GRID_TOP + 8;               // clear of “Images added”
        int xLeft = 10;
        int pad = 6;
        int textW = this.font.width(msg);
        int h = this.font.lineHeight + pad * 2;
        int w = Math.min(textW + pad * 2, panelX - 20);

        ctx.fill(xLeft - 2, yTop - 2, xLeft + w + 2, yTop + h + 2, 0x80202020);
        ctx.fill(xLeft, yTop, xLeft + w, yTop + h, 0xC0101010);
        ctx.drawString(this.font, msg, xLeft + pad, yTop + pad, 0xFFFFE070, false);
    }

    private void renderRightPanelProgress(GuiGraphics ctx) {
        // Prefer showing upload progress after clicking Create
        boolean showUpload = uploadUiActive;
        boolean showXml = (xmlImportFound != 0 || xmlImportDone != 0 || xmlImportFailed != 0 || xmlImportInProgress);

        // Manual drop progress: show if we have work, or if it was recently active
        boolean showManual = manualUiActive && (manualFound > 0);

        if (!showUpload && !showXml && !showManual) return;


        final int padding = 10;
        final int barH = 8;
        final int barW = panelW - 2 * padding;
        final int barX = panelX + padding;
        final int barY = this.height - 40 - barH - 4;

        String line1;
        String line2;

        int total;
        int progressed;

        if (showUpload) {
            // ---- UPLOAD MODE ----
            int cardsTotal = Math.max(1, uploadTotalCards);
            int cardsDone = Math.max(0, Math.min(uploadCardsSent, cardsTotal));

            line1 = "Creating cards: " + cardsDone + "/" + uploadTotalCards;
            if (uploadsRunning) {
                String eta = uploadEtaText();
                line2 = eta.isEmpty() ? "Please keep screen open" : ("Please keep screen open • " + eta);
            } else {
                line2 = uploadLine2;
            }

            total = Math.max(1, uploadTotalJobs);
            progressed = Math.max(0, Math.min(uploadJobsSent, total));
        } else if (showXml) {
            // ---- XML MODE (your existing behavior) ----
            total = Math.max(1, xmlImportFound);
            progressed = Math.max(0, Math.min(xmlImportDone + xmlImportFailed, total));

            line1 = "Total imported: " + progressed + "/" + (xmlImportFound == 0 ? "?" : String.valueOf(xmlImportFound))
                    + " (failed " + xmlImportFailed + ")";
            line2 = xmlImportInProgress ? xmlImportPhase
                    : (progressed >= total ? "Downloaded all images." : (xmlImportPhase == null ? "" : xmlImportPhase));
        } else {
            // ---- MANUAL MODE ----
            total = Math.max(1, manualFound);
            progressed = Math.max(0, Math.min(manualDone + manualFailed, total));

            line1 = "Imported images: " + progressed + "/" + manualFound
                    + (manualFailed > 0 ? (" (failed " + manualFailed + ")") : "");
            line2 = manualImportRunning ? manualPhase
                    : (progressed >= total ? "Imported all dropped images." : (manualPhase == null ? "" : manualPhase));

            // If finished, you can auto-hide after completion, or keep it until next drop:
            if (!manualImportRunning && progressed >= total) {
                // keep visible but stable:
                manualUiActive = true;
                // OR if you want it to disappear:
                // manualUiActive = false;
            }
        }

        double pct = progressed / (double) total;
        int fillW = (int) Math.round(barW * pct);

        int lh = this.font.lineHeight;
        int textX = panelX + padding;
        int textY = barY - 2 - (2 * lh);

        // Draw line 1 (wrapped to panel width if needed, first line only)
        int textMaxW = barW;
        var wrapped1 = this.font.split(Component.literal(line1), textMaxW);
        if (!wrapped1.isEmpty()) {
            ctx.drawString(this.font, wrapped1.get(0), textX, textY, UI_YELLOW, false);
        } else {
            ctx.drawString(this.font, line1, textX, textY, UI_YELLOW, false);
        }

        // Draw line 2
        ctx.drawString(this.font, line2 == null ? "" : line2, textX, textY + lh, UI_YELLOW, false);

        // Bar bg + fill
        ctx.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, PROG_BORDER);
        ctx.fill(barX, barY, barX + barW, barY + barH, PROG_BG);

        int fillColor = (progressed >= total) ? PROG_DONE : PROG_FILL;
        if (fillW > 0) ctx.fill(barX, barY, barX + fillW, barY + barH, fillColor);
    }


    // ---- Input ----
    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
        double mx = click.x();
        double my = click.y();

        if (focusSearchIfClicked(click, bl)) return true;
        blurSearchIfClickedAway(mx, my);

        // 1) Scrollbar first
        if (hitScrollbar(mx, my)) {
            startDragScrollbar((int) my);
            return true;
        }

        // 2) Give UI widgets priority
        if (super.mouseClicked(click, bl)) return true;

        // 3) Only allow grid selection inside the visible grid "window"
        final int gridX1 = 10;
        final int gridY1 = GRID_TOP;
        final int gridX2 = panelX - 12;
        final int gridY2 = this.height - 40;

        if (mx < gridX1 || mx > gridX2 || my < gridY1 || my > gridY2) {
            return false; // click outside grid viewport
        }

        final int maxX = panelX - 12;
        int usableW = maxX - 10;
        int cols = Math.max(1, (usableW + GAP) / (BOX + GAP));
        int x0 = 10;
        int y0 = GRID_TOP - scrollY;

        for (int viewIndex = 0; viewIndex < visibleEntryIndexes.size(); viewIndex++) {
            int entryIndex = visibleEntryIndexes.get(viewIndex);
            int i = entryIndex;
            int col = viewIndex % cols;
            int row = viewIndex / cols;
            int x = x0 + col * (BOX + GAP);
            int y = y0 + row * (BOX_H + GAP);
            if (mx >= x && mx <= x + BOX && my >= y && my <= y + BOX_H) {
                // If we're in link mode and clicked a partner, perform link instead of reselecting
                if (linkPendingMode != LinkMode.NONE && selected >= 0 && entryIndex != selected) {
                    boolean asFront = (linkPendingMode == LinkMode.LINK_AS_FRONT);
                    linkWith(selected, entryIndex, asFront);
                    toast("Linked " + safeName(entries.get(selected)) + " (" + (asFront ? "FRONT" : "BACK")
                            + ") ↔ " + safeName(entries.get(i)) + " (" + (asFront ? "BACK" : "FRONT") + ")");
                    linkPendingMode = LinkMode.NONE;
                    syncEditorFromSelected();
                    return true;
                }
                // Normal selection
                clearEditorFocus();
                selected = entryIndex;
                syncEditorFromSelected();
                return true;
            }
        }
        return false;
    }

    private String safeName(Entry e) {
        if (e == null) return "card";
        String n = nz(e.meta.name);
        return n.isEmpty() ? nz(e.fileName) : n;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        // Give the Oracle text area first right of refusal
        if (textF != null && textF.isMouseOver(mouseX, mouseY) && textF.mouseScrolled(mouseX, mouseY, horizontal, vertical)) {
            return true;
        }
        int old = scrollY;
        scrollY -= (int)(vertical * 24);
        clampScroll();
        return (scrollY != old) || super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
        if (barDragging) {
            dragScrollbarTo((int) click.y());
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        barDragging = false;
        return super.mouseReleased(click);
    }

    private void updateMaxScroll() {
        final int maxX = panelX - 12;
        int usableW = maxX - 10;
        int cols = Math.max(1, (usableW + GAP) / (BOX + GAP));
        int rows = (int)Math.ceil(visibleEntryIndexes.size() / (double)cols);
        int contentH = rows * (BOX_H + GAP);
        int viewportH = this.height - GRID_TOP - 12;
        maxScrollY = Math.max(0, contentH - viewportH);
        clampScroll();
    }
    private void clampScroll() { if (scrollY < 0) scrollY = 0; if (scrollY > maxScrollY) scrollY = maxScrollY; }

    /** Vanilla may call this; forward to our handler. */
    @Override
    public void onFilesDropped(List<Path> paths) {
        handleFileDrop(paths);
    }

    /** Preferred entry point (wired from GLFW drop callback in MtgcardClient). */
    public void handleFileDrop(List<Path> paths) {
        if (paths == null || paths.isEmpty()) {
            lastStatus = "No files dropped.";
            return;
        }

        boolean uploadWasRunning = uploadsRunning || !uploadQueue.isEmpty();
        boolean manualWasRunning = manualImportRunning || manualInFlight || !manualImageQueue.isEmpty();

        // ---- RESET cancel state so new drops work without cancelling existing queues ----
        cancelRequested = false;
        xmlCancel.set(false);

        if (!uploadWasRunning) {
            uploadUiActive = false;
            uploadTotalCards = uploadCardsSent = 0;
            uploadTotalJobs = uploadJobsSent = 0;
            uploadLine2 = "";
        } else {
            uploadUiActive = true;
            uploadLine2 = "Additional files queued.";
        }

        if (!manualWasRunning) {
            manualUiActive = false;
            manualImportRunning = false;
            manualInFlight = false;
            manualImportCooldownTicks = 0;

            manualFound = 0;
            manualDone = 0;
            manualFailed = 0;
            manualPhase = "";
        } else {
            manualUiActive = true;
        }

        List<Path> expandedPaths = expandMseDrop(paths);
        List<Path> imagePaths = new ArrayList<>();
        List<XmlSource> xmlSources = new ArrayList<>();
        int metaApplied = 0;
        int exportFiles = 0;
        int errors = 0;

        // Pass 1: classify + parse XML metadata (fast)
        for (Path p : expandedPaths) {
            String name = p.getFileName().toString();
            String lower = name.toLowerCase(Locale.ROOT);

            try {
                if (lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".webp")) {
                    imagePaths.add(p);
                } else if (lower.endsWith(".xml") || lower.endsWith(".json")) {
                    var xml = Files.readString(p);
                    List<Cockatrice.Meta> parsed = Cockatrice.parseCards(xml);
                    if (!parsed.isEmpty()) {
                        registerCockatriceMetas(parsed);
                        metaApplied++;
                    }
                    exportFiles++;
                    Path imageBaseDir = p.getParent();
                    if (imageBaseDir != null) {
                        Path companionDir = imageBaseDir.resolve(stripExt(name) + "-files");
                        if (Files.isDirectory(companionDir)) imageBaseDir = companionDir;
                    }
                    if (lower.endsWith(".xml") && !parsed.isEmpty()) {
                        xmlSources.add(new XmlSource(xml, imageBaseDir));
                    }
                } else if (isMseExportDescriptor(lower)) {
                    // These formats vary by exporter. Their useful card renders were
                    // collected while expanding the drop, even when metadata is tool-specific.
                    exportFiles++;
                }
            } catch (Throwable ex) {
                errors++;
                toast("Failed: " + name + " (" + ex.getClass().getSimpleName() + ")");
            }
        }

        // Start XML imports (these will stream in one-by-one already)
        List<Path> droppedImagesSnapshot = List.copyOf(imagePaths);
        for (XmlSource source : xmlSources) {
            try {
                importCockatriceXmlWithImagesAsync(source.xml, source.baseDir, droppedImagesSnapshot);
            } catch (Throwable t) {
                errors++;
                toast("XML import failed (" + t.getClass().getSimpleName() + ")");
            }
        }

        // Apply metadata to existing entries if XML included
        if (metaApplied > 0 && !entries.isEmpty()) {
            applyCockatriceTo(entries);
            applyAutoTransformLinks();
        }

        // Queue manual images to be imported one-by-one
        if (!imagePaths.isEmpty()) {
            manualImageQueue.addAll(imagePaths);
            manualFound += imagePaths.size();
            manualImportRunning = true;
            manualPhase = "Queued " + imagePaths.size() + " image(s)...";
            manualUiActive = true;
        }

        // Status
        StringBuilder sb = new StringBuilder();
        if (!imagePaths.isEmpty()) sb.append("Queued ").append(imagePaths.size()).append(" image(s). ");
        if (metaApplied > 0) sb.append("Applied metadata from ").append(metaApplied).append(" export file(s). ");
        else if (exportFiles > 0 && imagePaths.isEmpty()) sb.append("No card images found in this export. ");
        if (errors > 0) sb.append(errors).append(" file(s) failed.");
        lastStatus = sb.toString();

        // NOTE: tryUploadNewEntries(order) removed because we're no longer building `order` eagerly.
        // If you still want per-image upload, we can enqueue uploads from inside importOneDroppedImageAsync() once each image lands.
    }


    private void importOneDroppedImageAsync(Path p) {
        if (p == null || cancelRequested) return;

        final String name = p.getFileName().toString();
        final String lowerName = name.toLowerCase(Locale.ROOT);

        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> {
                    try {
                        byte[] raw = Files.readAllBytes(p);
                        EncodedImage enc = preferWebpElsePng(raw, lowerName);
                        return enc;
                    } catch (Throwable t) {
                        throw new RuntimeException(t);
                    }
                })
                .whenComplete((enc, err) -> {
                    if (cancelRequested) {
                        manualInFlight = false;
                        return;
                    }

                    if (err != null || enc == null) {
                        manualFailed++;
                        manualPhase = "Manual import failed " + manualFailed;
                        manualInFlight = false;
                        manualImportCooldownTicks = 1;
                        return;
                    }

                    // Compute base/isBack OFF THREAD (safe)
                    String stem = stripExt(lowerName);

                    boolean isBack =
                            stem.endsWith("_f1") || stem.endsWith("_back") ||
                                    stem.endsWith("_b")  || stem.endsWith("_rear");

                    String base = stem;
                    if (stem.endsWith("_f1"))    base = stem.substring(0, stem.length() - 3);
                    if (stem.endsWith("_f0"))    base = stem.substring(0, stem.length() - 3);
                    if (stem.endsWith("_back"))  base = stem.substring(0, stem.length() - 5);
                    if (stem.endsWith("_front")) base = stem.substring(0, stem.length() - 6);
                    if (stem.endsWith("_rear"))  base = stem.substring(0, stem.length() - 5);
                    if (stem.endsWith("_b"))     base = stem.substring(0, stem.length() - 2);

                    final String finalBase = base;
                    final boolean finalIsBack = isBack;
                    final byte[] finalBytes = enc.bytes;
                    final String finalExt = enc.ext;

                    if (this.minecraft == null) {
                        manualInFlight = false;
                        manualImportCooldownTicks = 1;
                        return;
                    }

                    // ✅ Do ALL UI mutations + texture work on client thread
                    this.minecraft.execute(() -> {
                        try {
                            if (cancelRequested) return;

                            Entry existing = findEntryByBase(finalBase);

                            if (existing == null) {
                                Entry e = new Entry();
                                e.fileName = finalBase + finalExt;
                                e.sourceFileName = name;

                                // store dropped face
                                if (finalIsBack) {
                                    // if back arrives first, keep as front for now (your old behavior)
                                    e.imgFront = finalBytes;
                                    e.imgBack = null;
                                    e.meta.doubleFaced = false;
                                } else {
                                    e.imgFront = finalBytes;
                                    e.imgBack = null;
                                    e.meta.doubleFaced = false;
                                }

                                e.meta.name = prettyBaseName(finalBase);
                                applyCockatriceToEntry(e);

                                buildThumbnail(e);       // ✅ safe now
                                entries.add(e);
                                applyAutoTransformLinks();

                                lastStatus = "Imported: " + name;
                            } else {
                                existing.sourceFileName = name;
                                if (finalIsBack) {
                                    existing.imgBack = finalBytes;
                                    existing.meta.doubleFaced = true;
                                } else {
                                    existing.imgFront = finalBytes;
                                }

                                applyCockatriceToEntry(existing);
                                buildThumbnail(existing); // ✅ safe now
                                applyAutoTransformLinks();
                                lastStatus = "Updated: " + name;
                            }

                            manualDone++;
                            manualPhase = "Manual import " + manualDone + "/" + manualFound
                                    + (manualFailed > 0 ? (" (failed " + manualFailed + ")") : "");

                            rebuildVisibleEntries();
                            if (selected < 0 && !entries.isEmpty()) {
                                selected = entries.size() - 1;
                                syncEditorFromSelected();
                            }
                        } finally {
                            manualInFlight = false;
                            manualImportCooldownTicks = 1;
                        }
                    });
                });
    }


    private Entry findEntryByBase(String base) {
        if (base == null || base.isEmpty()) return null;
        for (Entry e : entries) {
            if (e == null) continue;
            // we stored fileName like "base.ext"
            if (e.fileName != null && stripExt(e.fileName.toLowerCase(Locale.ROOT)).equals(base)) {
                return e;
            }
        }
        return null;
    }

    private void tryUploadNewEntries(List<String> orderKeys) {
        if (orderKeys == null || orderKeys.isEmpty()) return;
        // hook for optional per-image upload
    }

    // ---- Editor ----
    private void buildEditorWidgets() {
        int sx = panelX;
        int sy = 34;
        int w  = panelW - 20; // padding inside panel
        int h = 16, pad = 4;

        nameF = addField(sx, sy, w, h, "Name", v -> { withSel(e -> e.meta.name = v); rebuildVisibleEntries(); }); sy += h + pad;
        manaF = addField(sx, sy, w, h, "Mana Cost", v -> { withSel(e -> e.meta.manaCost = v); rebuildVisibleEntries(); }); sy += h + pad;
        typeF = addField(sx, sy, w, h, "Type Line", v -> { withSel(e -> e.meta.typeLine = v); rebuildVisibleEntries(); }); sy += h + pad;
        setF  = addField(sx, sy, w, h, "Set", v -> { withSel(e -> e.meta.set = v); rebuildVisibleEntries(); }); sy += h + pad;

        rarityBtn = Button.builder(Component.literal("Rarity: common"), b -> {
            withSel(e -> { e.meta.rarity = nextRarity(e.meta.rarity); rarityBtn.setMessage(Component.literal("Rarity: " + e.meta.rarity)); });
            rebuildVisibleEntries();
        }).bounds(sx, sy, (w/2)-5, h).build();
        addRenderableWidget(rarityBtn);

        dfcToggleBtn = Button.builder(Component.literal("Single Face"), b -> {
            withSel(e -> {
                e.meta.doubleFaced = !e.meta.doubleFaced;
                dfcToggleBtn.setMessage(Component.literal(e.meta.doubleFaced ? "Double-Faced" : "Single Face"));
                updateLinkButtonsVisibility();
            });
            rebuildVisibleEntries();
        }).bounds(sx + (w/2)+5, sy, (w/2)-5, h).build();

        addRenderableWidget(dfcToggleBtn);
        sy += h + pad;

        // Oracle Text -> multiline area (custom)
        int oracleH = 90;
        textF = new SimpleTextArea(sx, sy, w, oracleH, Component.literal("Oracle Text"));
        textF.setPlaceholder("Oracle Text");
        textF.setMaxLength(1_000_000);
        textF.setChangedListener(v -> { withSel(e -> e.meta.oracleText = v); rebuildVisibleEntries(); });
        addRenderableWidget(textF);
        sy += oracleH + pad;

        // 3-up line
        int col = (w - 2*10) / 3;
        powF  = addField(sx,              sy, col, h, "Power",     v -> { withSel(e -> e.meta.power = v); rebuildVisibleEntries(); });
        touF  = addField(sx + col + 10,   sy, col, h, "Toughness", v -> { withSel(e -> e.meta.toughness = v); rebuildVisibleEntries(); });
        loyF  = addField(sx + 2*(col+10), sy, col, h, "Loyalty",   v -> { withSel(e -> e.meta.loyalty = v); rebuildVisibleEntries(); });
    }

    private EditBox addField(int x, int y, int w, int h, String placeholder, Consumer<String> onChange) {
        EditBox tf = new EditBox(this.font, x, y, w, h, Component.literal(placeholder));
        tf.setHint(Component.literal(placeholder));
        tf.setResponder(onChange);
        tf.setMaxLength(1_000_000); // uncap
        addRenderableWidget(tf);
        return tf;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private void withSel(Consumer<Entry> c) { if (selected >= 0 && selected < entries.size()) c.accept(entries.get(selected)); }
    private static String nextRarity(String r) {
        String[] rs = {"common","uncommon","rare","mythic"};
        int i = 0; for (int k = 0; k < rs.length; k++) if (rs[k].equalsIgnoreCase(r)) { i = k; break; }
        return rs[(i + 1) % rs.length];
    }

    private void buildThumbnailAsync(Entry e) {
        if (e == null || e.imgFront == null || e.imgFront.length == 0) return;

        byte[] bytes = e.imgFront; // capture
        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> {
                    try {
                        NativeImage ni = buildThumbNativeImage(bytes);
                        return ni;
                    } catch (Throwable t) {
                        return null;
                    }
                }, IMG_EXEC)
                .thenAccept(ni -> {
                    if (ni == null) return;
                    int tw = ni.getWidth();
                    int th = ni.getHeight();
                    if (minecraft != null) {
                        minecraft.execute(() -> installThumbTexture(e, ni, tw, th));
                    } else {
                        ni.close();
                    }
                });
    }

    // ---- Thumbnails ----
    private static NativeImage buildPreviewNativeImage(byte[] srcBytes) throws IOException {
        // Decode to BufferedImage (same robust path you used)
        BufferedImage src = null;
        try (var bais = new ByteArrayInputStream(srcBytes)) {
            src = ImageIO.read(bais);
        } catch (Throwable ignored) {}

        if (src == null) {
            try (InputStream is = new ByteArrayInputStream(srcBytes)) {
                NativeImage ni = NativeImage.read(is);
                try {
                    int w = ni.getWidth(), h = ni.getHeight();
                    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        int abgr = ni.getPixel(x, y);
                        int a = (abgr >>> 24) & 0xFF;
                        int b = (abgr >>> 16) & 0xFF;
                        int g = (abgr >>> 8) & 0xFF;
                        int r = (abgr) & 0xFF;
                        out.setRGB(x, y, (a<<24) | (r<<16) | (g<<8) | b);
                    }
                    src = out;
                } finally { ni.close(); }
            }
        }

        if (src == null) throw new IOException("Could not decode preview image");

        if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = out.createGraphics();
            try { g.drawImage(src, 0, 0, null); } finally { g.dispose(); }
            src = out;
        }

        int tw = PREVIEW_TEX_W;
        int th = PREVIEW_TEX_H;

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            double sx = (double) tw / src.getWidth();
            double sy = (double) th / src.getHeight();
            double s = Math.min(sx, sy);

            int w = Math.max(1, (int)Math.round(src.getWidth() * s));
            int h = Math.max(1, (int)Math.round(src.getHeight() * s));
            int ox = (tw - w) / 2;
            int oy = (th - h) / 2;

            g.drawImage(src, ox, oy, ox + w, oy + h, 0, 0, src.getWidth(), src.getHeight(), null);
        } finally { g.dispose(); }

        // Convert to NativeImage RGBA
        NativeImage niOut = new NativeImage(NativeImage.Format.RGBA, tw, th, true);
        int[] argb = out.getRGB(0, 0, tw, th, null, 0, tw);
        for (int y = 0; y < th; y++) {
            int row = y * tw;
            for (int x = 0; x < tw; x++) {
                int c = argb[row + x];
                int a = (c >>> 24) & 0xFF;
                int r = (c >>> 16) & 0xFF;
                int gg = (c >>> 8) & 0xFF;
                int b = c & 0xFF;
                int abgr = (a << 24) | (b << 16) | (gg << 8) | r;
                niOut.setPixelABGR(x, y, abgr);
            }
        }
        return niOut;
    }

    private void installThumbTexture(Entry e, NativeImage thumb, int tw, int th) {
        if (minecraft == null) { thumb.close(); return; }

        var tm = minecraft.getTextureManager();
        if (e.thumbId != null) tm.release(e.thumbId);
        if (e.thumbTex != null) e.thumbTex.close();

        var tex = new DynamicTexture(() -> "mtgcard/thumb", thumb);
        var id  = Identifier.fromNamespaceAndPath("mtgcard", "thumb/" + UUID.randomUUID());
        tm.register(id, tex);

        e.thumbId = id;
        e.thumbTex = tex;
        e.thumbW = tw;
        e.thumbH = th;
    }

    // Convenience wrapper used all over the screen
    private void buildThumbnail(Entry e) {
        // If you want it synchronous, you'd do it on client thread.
        // But you already built a good async pipeline, so just call it:
        buildThumbnailAsync(e);
    }

    // ---- Thumbnails ----
    private static NativeImage buildThumbNativeImage(byte[] srcBytes) throws IOException {
        // Decode to BufferedImage (robust path: ImageIO -> NativeImage fallback)
        BufferedImage src = null;
        try (var bais = new ByteArrayInputStream(srcBytes)) {
            src = ImageIO.read(bais);
        } catch (Throwable ignored) {}

        if (src == null) {
            try (InputStream is = new ByteArrayInputStream(srcBytes)) {
                NativeImage ni = NativeImage.read(is);
                try {
                    int w = ni.getWidth(), h = ni.getHeight();
                    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        int abgr = ni.getPixel(x, y);
                        int a = (abgr >>> 24) & 0xFF;
                        int b = (abgr >>> 16) & 0xFF;
                        int g = (abgr >>> 8) & 0xFF;
                        int r = (abgr) & 0xFF;
                        out.setRGB(x, y, (a<<24) | (r<<16) | (g<<8) | b);
                    }
                    src = out;
                } finally {
                    ni.close();
                }
            }
        }

        if (src == null) throw new IOException("Could not decode thumbnail image");

        // Ensure ARGB
        if (src.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = out.createGraphics();
            try { g.drawImage(src, 0, 0, null); } finally { g.dispose(); }
            src = out;
        }

        // Thumb texture size (2x the UI draw size for crispness)
        final int tw = BOX * 2;
        final int th = BOX_H * 2;

        BufferedImage out = new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB);
        var g = out.createGraphics();
        try {
            g.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,
                    java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(java.awt.RenderingHints.KEY_RENDERING,
                    java.awt.RenderingHints.VALUE_RENDER_QUALITY);

            // Scale to fit and center (preserve aspect ratio)
            double sx = (double) tw / src.getWidth();
            double sy = (double) th / src.getHeight();
            double s = Math.min(sx, sy);

            int w = Math.max(1, (int)Math.round(src.getWidth() * s));
            int h = Math.max(1, (int)Math.round(src.getHeight() * s));
            int ox = (tw - w) / 2;
            int oy = (th - h) / 2;

            g.drawImage(src, ox, oy, ox + w, oy + h, 0, 0, src.getWidth(), src.getHeight(), null);
        } finally {
            g.dispose();
        }

        // Convert ARGB -> NativeImage ABGR (Minecraft format)
        NativeImage niOut = new NativeImage(NativeImage.Format.RGBA, tw, th, true);
        int[] argb = out.getRGB(0, 0, tw, th, null, 0, tw);
        for (int y = 0; y < th; y++) {
            int row = y * tw;
            for (int x = 0; x < tw; x++) {
                int c = argb[row + x];
                int a = (c >>> 24) & 0xFF;
                int r = (c >>> 16) & 0xFF;
                int gg = (c >>> 8) & 0xFF;
                int b = c & 0xFF;
                int abgr = (a << 24) | (b << 16) | (gg << 8) | r;
                niOut.setPixelABGR(x, y, abgr);
            }
        }
        return niOut;
    }

    // ---- Hover preview texture (lazy) ----
    private void ensurePreviewTexture(Entry e, boolean wantBackFace) {
        if (e == null) return;
        if (e.previewId != null && e.previewTex != null && e.previewWantsBack == wantBackFace) return;
        if (e.previewBuilding) return;

        e.previewBuilding = true;
        e.previewWantsBack = wantBackFace;

        byte[] srcBytes = wantBackFace && e.imgBack != null && e.imgBack.length > 0 ? e.imgBack : e.imgFront;
        if (srcBytes == null || srcBytes.length == 0) { e.previewBuilding = false; return; }

        java.util.concurrent.CompletableFuture
                .supplyAsync(() -> {
                    try {
                        // You can reuse your preview pipeline, but DO IT HERE off-thread
                        // (same decode/scale/convert pattern as thumbnail, but PREVIEW_TEX_W/H)
                        return buildPreviewNativeImage(srcBytes); // implement similar to thumb builder
                    } catch (Throwable t) {
                        return null;
                    }
                }, IMG_EXEC)
                .thenAccept(ni -> {
                    if (ni == null) { e.previewBuilding = false; return; }
                    int tw = ni.getWidth(), th = ni.getHeight();
                    if (minecraft != null) {
                        minecraft.execute(() -> {
                            try {
                                var tm = minecraft.getTextureManager();
                                if (e.previewId != null) tm.release(e.previewId);
                                if (e.previewTex != null) e.previewTex.close();

                                var tex = new DynamicTexture(() -> "mtgcard/preview", ni);
                                var id  = Identifier.fromNamespaceAndPath("mtgcard", "preview/" + UUID.randomUUID());
                                tm.register(id, tex);

                                e.previewId = id;
                                e.previewTex = tex;
                            } finally {
                                e.previewBuilding = false;
                            }
                        });
                    } else {
                        ni.close();
                        e.previewBuilding = false;
                    }
                });
    }


    private void drawHoverPreview(GuiGraphics ctx, int mouseX, int mouseY) {
        if (hoveredIndex < 0 || hoveredIndex >= entries.size()) return;
        Entry e = entries.get(hoveredIndex);

        // Optional: hold SHIFT to preview back face if present
        boolean wantBack = e.meta.doubleFaced && e.imgBack != null && e.imgBack.length > 0 && isShiftDown();

        // Build preview texture lazily
        ensurePreviewTexture(e, wantBack);
        if (e.previewId == null) return;

        // Position near the hovered thumbnail (prefer right side, otherwise left)
        int pad = 8;
        int w = PREVIEW_DRAW_W;
        int h = PREVIEW_DRAW_H;

        int x = hoveredThumbX + BOX + pad;
        int y = hoveredThumbY;

        // If it would overlap the right edit panel, flip to the left
        if (x + w + 10 > panelX - 8) {
            x = hoveredThumbX - w - pad;
        }

        // Clamp to screen
        x = Math.max(10, Math.min(x, this.width - w - 10));
        y = Math.max(28, Math.min(y, this.height - h - 50));

        // Backplate + border
        ctx.fill(x - 3, y - 3, x + w + 3, y + h + 3, 0xCC000000);
        ctx.fill(x - 2, y - 2, x + w + 2, y + h + 2, 0xFF303030);
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF000000);

        // Draw preview
        ctx.blit(RenderPipelines.GUI_TEXTURED, e.previewId, x, y, 0f, 0f, w, h, PREVIEW_TEX_W, PREVIEW_TEX_H);

        // Small label (name)
        String label = (e.meta.name == null || e.meta.name.isEmpty()) ? e.fileName : e.meta.name;
        int tw = this.font.width(label);
        int lx = x + 6;
        int ly = y + h + 6;
        if (ly + this.font.lineHeight + 6 < this.height - 40) {
            ctx.fill(x - 1, ly - 3, Math.min(x + w + 1, lx + Math.min(tw, w - 12) + 10), ly + this.font.lineHeight + 3, 0xAA000000);
            ctx.drawString(this.font, label, lx, ly, 0xFFEFEFEF, false);
        }
    }
    private void enqueueArtUpload(String artKey, String setCode, byte[] bytes) {
        if (artKey == null || artKey.isEmpty()) return;
        if (bytes == null || bytes.length == 0) return;

        final String uploadId = UUID.randomUUID().toString();

        // server expects "png"/"webp"/"jpg" (no dot)
        String extTmp = sniffExt(bytes);
        if (extTmp.startsWith(".")) extTmp = extTmp.substring(1);
        final String ext = extTmp; // ✅ now effectively final for lambdas

        final int totalBytes = bytes.length;
        final int chunkSize = ART_CHUNK_SIZE;
        final int totalChunks = (int) Math.ceil(totalBytes / (double) chunkSize);

        enqueueUpload(() -> ClientPlayNetworking.send(
                new CustomCardPackets.CustomArtBegin(uploadId, artKey, nz(setCode), ext, totalBytes, chunkSize, totalChunks)
        ));

        for (int i = 0; i < totalChunks; i++) {
            int from = i * chunkSize;
            int to = Math.min(totalBytes, from + chunkSize);
            final byte[] slice = Arrays.copyOfRange(bytes, from, to);
            final int idx = i;

            enqueueUpload(() -> ClientPlayNetworking.send(
                    new CustomCardPackets.CustomArtChunk(uploadId, idx, slice)
            ), 0, slice.length);
        }

        enqueueUpload(() -> ClientPlayNetworking.send(
                new CustomCardPackets.CustomArtFinish(uploadId)
        ));
    }


    private static String newArtKey(String prefix) {
        // Keep it filesystem-friendly
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
    }

    private static BufferedImage unsharpMask(BufferedImage src, float amount) {
        amount = Math.max(0f, Math.min(1.5f, amount));
        int w = src.getWidth(), h = src.getHeight();

        float[] k = {
                1f/16f, 2f/16f, 1f/16f,
                2f/16f, 4f/16f, 2f/16f,
                1f/16f, 2f/16f, 1f/16f
        };
        var kernel = new java.awt.image.Kernel(3, 3, k);
        var op = new java.awt.image.ConvolveOp(kernel, java.awt.image.ConvolveOp.EDGE_NO_OP, null);

        BufferedImage blur = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        op.filter(src, blur);

        int[] a = new int[w*h];
        int[] b = new int[w*h];
        src.getRGB(0, 0, w, h, a, 0, w);
        blur.getRGB(0, 0, w, h, b, 0, w);

        for (int i = 0; i < a.length; i++) {
            int ca = a[i], cb = b[i];
            int Aa = (ca >>> 24) & 0xFF;
            int Ra = (ca >>> 16) & 0xFF, Ga = (ca >>> 8) & 0xFF, Ba = ca & 0xFF;
            int Rb = (cb >>> 16) & 0xFF, Gb = (cb >>> 8) & 0xFF, Bb = cb & 0xFF;

            int R = clamp255((int)Math.round(Ra + amount * (Ra - Rb)));
            int G = clamp255((int)Math.round(Ga + amount * (Ga - Gb)));
            int B = clamp255((int)Math.round(Ba + amount * (Ba - Bb)));

            a[i] = (Aa << 24) | (R << 16) | (G << 8) | B;
        }

        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        out.setRGB(0, 0, w, h, a, 0, w);
        return out;
    }
    private static int clamp255(int v) { return v < 0 ? 0 : (v > 255 ? 255 : v); }

    // ---- Cockatrice mapping ----
    private static String prettyBaseName(String base) {
        String b = base;
        if (b.endsWith("_f0")) b = b.substring(0, b.length()-3);
        if (b.endsWith("_front")) b = b.substring(0, b.length()-6);
        return Arrays.stream(b.split("[ _\\-]"))
                .filter(s -> !s.isEmpty())
                .map(s -> s.substring(0,1).toUpperCase(Locale.ROOT) + s.substring(1))
                .reduce((a,b2) -> a + " " + b2).orElse(b);
    }

    private void registerCockatriceMetas(List<Cockatrice.Meta> metas) {
        if (metas == null || metas.isEmpty()) return;

        Map<String, Integer> duplicateCounts = new HashMap<>();
        for (Cockatrice.Meta meta : metas) {
            if (meta == null || nz(meta.name).isBlank()) continue;

            String name = nz(meta.name).trim();
            cockatriceByName.put(name.toLowerCase(Locale.ROOT), meta);

            if (!nz(meta.imageFileName).isBlank()) {
                registerImageMeta(cockatriceByImageField, meta.imageFileName, meta, "image field");
            }

            for (String collectorFile : collectorImageFileNames(name, meta.collectorNumber, meta.set)) {
                registerImageMeta(cockatriceByCollectorImage, collectorFile, meta, "collector number");
            }
            String safeName = cockatriceImageSafeName(name);
            if (!safeName.equals(name)) {
                for (String collectorFile : collectorImageFileNames(safeName, meta.collectorNumber, meta.set)) {
                    registerImageMeta(cockatriceByCollectorImage, collectorFile, meta, "collector number");
                }
            }

            String nameKey = normalizedCardName(name);
            int count = duplicateCounts.merge(nameKey, 1, Integer::sum);
            Map<String, ImageMetaMatch> target = count == 1 ? cockatriceByLegacyImage : cockatriceByDuplicateImage;
            String method = count == 1 ? "legacy name" : "duplicate fallback";
            String legacyStem = count == 1 ? name : name + "_" + count;
            String safeLegacyStem = count == 1 ? safeName : safeName + "_" + count;
            for (String ext : COCKATRICE_IMAGE_EXTENSIONS) {
                registerImageMeta(target, legacyStem + ext, meta, method);
                if (!safeLegacyStem.equals(legacyStem)) {
                    registerImageMeta(target, safeLegacyStem + ext, meta, method);
                }
            }
        }
    }

    private static void registerImageMeta(Map<String, ImageMetaMatch> target, String fileName,
                                          Cockatrice.Meta meta, String method) {
        if (target == null || meta == null || nz(fileName).isBlank()) return;
        ImageMetaMatch match = new ImageMetaMatch(meta, method);
        for (String key : imageLookupKeys(fileName)) {
            if (!key.isBlank()) target.put(key, match);
        }
    }

    private ImageMetaMatch findCockatriceMetaForSourceImageFile(String fileName) {
        for (String key : imageLookupKeys(fileName)) {
            ImageMetaMatch match = cockatriceByImageField.get(key);
            if (match != null) return match;
            match = cockatriceByCollectorImage.get(key);
            if (match != null) return match;
            match = cockatriceByLegacyImage.get(key);
            if (match != null) return match;
            match = cockatriceByDuplicateImage.get(key);
            if (match != null) return match;
        }
        return null;
    }

    private static List<String> imageLookupKeys(String raw) {
        String normalized = nz(raw).trim().replace('\\', '/');
        if (normalized.isBlank()) return List.of();

        ArrayList<String> keys = new ArrayList<>(3);
        addImageLookupKey(keys, normalized);
        int slash = normalized.lastIndexOf('/');
        String base = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        addImageLookupKey(keys, base);
        addImageLookupKey(keys, stripExt(base));
        return keys;
    }

    private static void addImageLookupKey(List<String> keys, String raw) {
        String key = nz(raw).trim().toLowerCase(Locale.ROOT);
        if (!key.isBlank() && !keys.contains(key)) keys.add(key);
    }

    private static final List<String> COCKATRICE_IMAGE_EXTENSIONS = List.of(".jpg", ".jpeg", ".png", ".webp");

    private static List<String> collectorImageFileNames(String name, String collectorNumber, String setCode) {
        String n = nz(name).trim();
        String collector = nz(collectorNumber).trim();
        String set = nz(setCode).trim();
        if (n.isBlank() || collector.isBlank() || set.isBlank()) return List.of();

        ArrayList<String> names = new ArrayList<>(COCKATRICE_IMAGE_EXTENSIONS.size() * 2);
        for (String ext : COCKATRICE_IMAGE_EXTENSIONS) {
            // MSE 2.6 / Cockatrice 3 format, followed by our older compatibility format.
            names.add(n + "_" + set + "_" + collector + ext);
            names.add(n + "_" + collector + "_" + set + ext);
        }
        return names;
    }

    private static String cockatriceImageSafeName(String name) {
        return nz(name)
                .replace("\u2019", "'")
                .replace(":", "")
                .replace(";", "")
                .replace("\n", "")
                .replace(".", "")
                .replace("\"", "")
                .trim();
    }

    private static void applyCockatriceMeta(Entry e, Cockatrice.Meta m) {
        if (e == null || m == null) return;
        if (!m.name.isEmpty())            e.meta.name = m.name;
        if (!m.manaCost.isEmpty())        e.meta.manaCost = m.manaCost;
        if (!m.typeLine.isEmpty())        e.meta.typeLine = m.typeLine;
        if (!m.rarity.isEmpty())          e.meta.rarity = m.rarity;
        if (!m.set.isEmpty())             e.meta.set = m.set;
        if (!m.collectorNumber.isEmpty()) e.meta.collectorNumber = m.collectorNumber;
        if (!m.imageFileName.isEmpty())   e.meta.imageFileName = m.imageFileName;
        if (!m.oracleText.isEmpty())      e.meta.oracleText = m.oracleText;
        if (!m.power.isEmpty())           e.meta.power = m.power;
        if (!m.toughness.isEmpty())       e.meta.toughness = m.toughness;
        if (!m.loyalty.isEmpty())         e.meta.loyalty = m.loyalty;
    }

    private static void copyEntryMeta(Entry target, Entry source) {
        if (target == null || source == null || source.meta == null) return;
        target.meta.name = nz(source.meta.name);
        target.meta.manaCost = nz(source.meta.manaCost);
        target.meta.typeLine = nz(source.meta.typeLine);
        target.meta.rarity = nz(source.meta.rarity);
        target.meta.set = nz(source.meta.set);
        target.meta.collectorNumber = nz(source.meta.collectorNumber);
        target.meta.imageFileName = nz(source.meta.imageFileName);
        target.meta.oracleText = nz(source.meta.oracleText);
        target.meta.power = nz(source.meta.power);
        target.meta.toughness = nz(source.meta.toughness);
        target.meta.loyalty = nz(source.meta.loyalty);
        target.meta.doubleFaced = source.meta.doubleFaced;
        target.meta.backName = nz(source.meta.backName);
        target.meta.backTypeLine = nz(source.meta.backTypeLine);
        target.meta.backOracleText = nz(source.meta.backOracleText);
        target.meta.backPower = nz(source.meta.backPower);
        target.meta.backToughness = nz(source.meta.backToughness);
        target.meta.backLoyalty = nz(source.meta.backLoyalty);
    }

    private static void logImageLookup(String method, String cardName, String fileName) {
        System.out.println("[MTGCard/XML] Image lookup " + method + " for \"" + nz(cardName) + "\": " + nz(fileName));
    }

    private void applyCockatriceTo(List<Entry> list) {
        for (var e : list) applyCockatriceToEntry(e);
        syncEditorFromSelected();
    }
    private void applyCockatriceToEntry(Entry e) {
        if (e == null) return;
        ImageMetaMatch imageMatch = findCockatriceMetaForSourceImageFile(!nz(e.sourceFileName).isBlank() ? e.sourceFileName : e.fileName);
        if (imageMatch != null) {
            applyCockatriceMeta(e, imageMatch.meta);
            logImageLookup(imageMatch.method, e.meta.name, !nz(e.sourceFileName).isBlank() ? e.sourceFileName : e.fileName);
            return;
        }

        String guess = e.meta.name.isEmpty()
                ? prettyBaseName(e.fileName.replace(".webp",""))
                : e.meta.name;
        var m = cockatriceByName.get(guess.toLowerCase(Locale.ROOT));
        if (m == null) return;

        applyCockatriceMeta(e, m);
        logImageLookup("legacy name", e.meta.name, e.fileName);
    }

    private void applyAutoTransformLinks() {
        if (entries.isEmpty()) return;

        Map<String, Integer> byName = new HashMap<>();
        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            String name = normalizedCardName(e == null ? "" : e.meta.name);
            if (!name.isEmpty()) byName.putIfAbsent(name, i);
        }

        for (int i = 0; i < entries.size(); i++) {
            Entry e = entries.get(i);
            if (e == null || e.link == null || e.link.partnerIndex >= 0) continue;

            String ownName = normalizedCardName(e.meta.name);
            Cockatrice.Meta meta = cockatriceByName.get(ownName);

            String frontMarker = oracleFaceMarker(e.meta.oracleText, "front");
            if (!frontMarker.isEmpty()) {
                Integer frontIdx = byName.get(normalizedCardName(frontMarker));
                if (frontIdx != null && frontIdx != i) {
                    linkWith(i, frontIdx, false);
                }
                continue;
            }

            String backMarker = oracleFaceMarker(e.meta.oracleText, "back");
            if (!backMarker.isEmpty()) {
                Integer backIdx = byName.get(normalizedCardName(backMarker));
                if (backIdx != null && backIdx != i) {
                    linkWith(i, backIdx, true);
                }
                continue;
            }

            String related = meta == null ? "" : nz(meta.relatedTransform);
            if (!related.isBlank()) {
                Integer otherIdx = byName.get(normalizedCardName(related));
                if (otherIdx != null && otherIdx != i) {
                    int frontIdx = Math.min(i, otherIdx);
                    int backIdx = Math.max(i, otherIdx);
                    linkWith(frontIdx, backIdx, true);
                }
            }
        }

        updateLinkButtonsVisibility();
    }

    private static String normalizedCardName(String name) {
        return nz(name).trim().toLowerCase(Locale.ROOT);
    }

    private static String oracleFaceMarker(String oracle, String face) {
        if (oracle == null || oracle.isBlank() || face == null || face.isBlank()) return "";
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?im)^\\s*---\\s*\\(" + java.util.regex.Pattern.quote(face) + "\\)\\s*:\\s*(.+?)\\s*$"
        );
        java.util.regex.Matcher m = p.matcher(oracle);
        return m.find() ? m.group(1).trim() : "";
    }

    // ---- Send ----
    private void sendToServer() {
        if (entries.isEmpty()) { toast("Nothing to send."); return; }

        boolean appending = uploadsRunning || !uploadQueue.isEmpty();
        int jobsBefore = uploadQueue.size();
        cancelRequested = false;
        if (!appending) {
            netSent = 0;
            netPhase = "Queued uploads...";
        } else {
            netPhase = "Appending uploads...";
        }

        final int CREATE_BATCH_SIZE = 75; // try 50–150
        List<CustomCardPackets.BatchEntry> batch = new ArrayList<>(CREATE_BATCH_SIZE);
        int queuedCards = 0;

        for (int i = 0; i < entries.size(); i++) {
            Entry frontEntry = entries.get(i);
            if (frontEntry == null) continue;

            // Skip entries that are explicitly the BACK half of a linked pair
            if (frontEntry.link != null && frontEntry.link.partnerIndex >= 0 && !frontEntry.link.isFront) {
                continue;
            }

            boolean isLinked = (frontEntry.link != null && frontEntry.link.partnerIndex >= 0);
            Entry linkedPartner = null;
            if (isLinked) {
                int pi = frontEntry.link.partnerIndex;
                if (pi >= 0 && pi < entries.size()) linkedPartner = entries.get(pi);
            }

            boolean df = frontEntry.meta.doubleFaced || isLinked;

            // ---- Resolve front bytes ----
            byte[] frontBytes = frontEntry.imgFront;
            if (frontBytes == null || frontBytes.length == 0) {
                // no usable front image -> skip
                continue;
            }

            // ---- Resolve back bytes + back meta ----
            byte[] backBytes = null;

            String backName       = nz(frontEntry.meta.backName);
            String backTypeLine   = nz(frontEntry.meta.backTypeLine);
            String backOracleText = nz(frontEntry.meta.backOracleText);
            String backPower      = nz(frontEntry.meta.backPower);
            String backToughness  = nz(frontEntry.meta.backToughness);
            String backLoyalty    = nz(frontEntry.meta.backLoyalty);

            // Prefer explicit imgBack on the FRONT entry
            if (df && frontEntry.imgBack != null && frontEntry.imgBack.length > 0) {
                backBytes = frontEntry.imgBack;
            }

            // If linked, the partner entry’s front image is the back face art
            if (df && (backBytes == null || backBytes.length == 0) && linkedPartner != null) {
                if (linkedPartner.imgFront != null && linkedPartner.imgFront.length > 0) {
                    backBytes = linkedPartner.imgFront;
                }

                // Fill missing back meta from partner if needed
                if (backName.isEmpty())       backName       = nz(linkedPartner.meta.name);
                if (backTypeLine.isEmpty())   backTypeLine   = nz(linkedPartner.meta.typeLine);
                if (backOracleText.isEmpty()) backOracleText = nz(linkedPartner.meta.oracleText);
                if (backPower.isEmpty())      backPower      = nz(linkedPartner.meta.power);
                if (backToughness.isEmpty())  backToughness  = nz(linkedPartner.meta.toughness);
                if (backLoyalty.isEmpty())    backLoyalty    = nz(linkedPartner.meta.loyalty);
            }

            // If df but still no back bytes, treat as single-faced
            if (df && (backBytes == null || backBytes.length == 0)) {
                df = false;
            }

            // ✅ Stable custom id + stable art keys
            String customId = stableCustomId(frontEntry);
            String frontKey = "custom_" + customId + "_f0";
            String backKey  = (df ? "custom_" + customId + "_f1" : "");
            String setCode = nz(frontEntry.meta.set);

            // 1) Upload art first (chunked)
            enqueueArtUpload(frontKey, setCode, frontBytes);
            if (!backKey.isEmpty()) enqueueArtUpload(backKey, setCode, backBytes);

            // 2) Send batch create referencing those keys
            //    (Assumes BatchEntry now has `id` as first param)
            var one = new CustomCardPackets.BatchEntry(
                    customId,

                    nz(frontEntry.meta.name),
                    nz(frontEntry.meta.manaCost),
                    nz(frontEntry.meta.typeLine),
                    nz(frontEntry.meta.rarity),
                    nz(frontEntry.meta.set),
                    nz(frontEntry.meta.oracleText),

                    nz(frontEntry.meta.power),
                    nz(frontEntry.meta.toughness),
                    nz(frontEntry.meta.loyalty),

                    df,

                    nz(backName),
                    nz(backTypeLine),
                    nz(backOracleText),
                    nz(backPower),
                    nz(backToughness),
                    nz(backLoyalty),

                    frontKey,
                    backKey
            );

            batch.add(one);
            queuedCards++;

            if (batch.size() >= CREATE_BATCH_SIZE) {
                List<CustomCardPackets.BatchEntry> toSend = List.copyOf(batch);
                enqueueUpload(() -> ClientPlayNetworking.send(
                        new CustomCardPackets.CustomBatchCreate(toSend)
                ), toSend.size()); // cardsCreated
                batch.clear();
            }
        }

        if (!batch.isEmpty()) {
            List<CustomCardPackets.BatchEntry> toSend = List.copyOf(batch);
            enqueueUpload(() -> ClientPlayNetworking.send(
                    new CustomCardPackets.CustomBatchCreate(toSend)
            ), toSend.size());
            batch.clear();
        }

        netQueued = uploadQueue.size();

        if (queuedCards == 0) {
            toast("Nothing to send (no valid front images).");
            return;
        }

        int jobsAdded = Math.max(0, uploadQueue.size() - jobsBefore);

        // ---- Switch the right-panel progress to UPLOAD mode ----
        uploadUiActive = true;
        if (appending) {
            uploadTotalCards += queuedCards;
            uploadTotalJobs += jobsAdded;
            uploadLine2 = "Queued " + queuedCards + " more card(s).";
        } else {
            uploadTotalCards = queuedCards;
            uploadCardsSent = 0;
            uploadTotalJobs = uploadQueue.size(); // snapshot total jobs at start
            uploadJobsSent  = 0;
            uploadLine2 = "Please keep screen open";

            long now = System.currentTimeMillis();
            uploadStartMs = now;
            uploadLastSampleMs = now;
            uploadLastSampleSent = 0;
            uploadRateEma = 0.0;
        }

        if (createBtn != null) createBtn.active = false;

        netPhase = (appending ? "Appended " : "Uploading ") + queuedCards + " card(s)...";
        startUploadsIfNeeded();

        toast((appending ? "Appended " : "Queued ") + queuedCards + " upload(s). Keep this screen open until done.");
    }

    private void toast(String s) {
        if (minecraft != null && minecraft.player != null)
            minecraft.player.sendSystemMessage(Component.literal(s));
    }

    // ---- Image helpers ----
    private static String stripExt(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        return dot > 0 ? lowerName.substring(0, dot) : lowerName;
    }

    private static String stableCustomId(Entry entry) {
        if (entry == null) return "custom_" + shortHash(UUID.randomUUID().toString());

        String explicit = sanitizeCustomId(entry.meta == null ? "" : entry.meta.id);
        if (!explicit.isBlank()) return explicit;

        String set = nz(entry.meta == null ? "" : entry.meta.set).trim();
        String name = nz(entry.meta == null ? "" : entry.meta.name).trim();
        String collector = nz(entry.meta == null ? "" : entry.meta.collectorNumber).trim();
        String imageFile = nz(entry.meta == null ? "" : entry.meta.imageFileName).trim();
        if (name.isBlank()) {
            name = stripExt(nz(entry.fileName).toLowerCase(Locale.ROOT));
        }
        if (name.isBlank()) {
            name = "card";
        }
        if (set.isBlank()) {
            set = "cstm";
        }

        String identity = set.toLowerCase(Locale.ROOT) + ":" + name.toLowerCase(Locale.ROOT);
        String slug = slugPart(set) + "_" + slugPart(name);
        if (!collector.isBlank()) {
            identity += ":" + collector.toLowerCase(Locale.ROOT);
            slug += "_" + slugPart(collector);
        } else if (!imageFile.isBlank()) {
            identity += ":" + imageFile.toLowerCase(Locale.ROOT);
        }
        if (slug.length() > 48) {
            slug = slug.substring(0, 48);
        }

        return sanitizeCustomId(slug + "_" + shortHash(identity));
    }

    private static String sanitizeCustomId(String raw) {
        if (raw == null) return "";

        String s = raw.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]+", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+", "")
                .replaceAll("_+$", "");

        if (s.length() > 64) {
            s = s.substring(0, 64);
        }
        return s;
    }

    private static String slugPart(String raw) {
        String s = sanitizeCustomId(raw);
        return s.isBlank() ? "card" : s;
    }

    private static String shortHash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1")
                    .digest(nz(raw).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(10);
            for (int i = 0; i < 5 && i < digest.length; i++) {
                out.append(String.format(Locale.ROOT, "%02x", digest[i] & 0xFF));
            }
            return out.toString();
        } catch (Throwable ignored) {
            return Integer.toHexString(nz(raw).hashCode()).replace("-", "0");
        }
    }

    // === Cockatrice XML -> auto-add images (async, with progress + debug logs) ===
    private void importCockatriceXmlWithImagesAsync(String xml, Path xmlBaseDir, List<Path> droppedImagePaths) {
        xmlImportInProgress = true;
        xmlImportFound = 0; xmlImportDone = 0; xmlImportFailed = 0;
        xmlImportPhase = "Scanning XML...";
        System.out.println("[MTGCard/XML] Starting import...");

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                var dbf = javax.xml.parsers.DocumentBuilderFactory.newInstance();
                dbf.setNamespaceAware(false);
                dbf.setExpandEntityReferences(false);
                var doc = dbf.newDocumentBuilder().parse(
                        new ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));

                var cards = doc.getElementsByTagName("card");
                if (cards == null || cards.getLength() == 0) { xmlImportPhase = "No <card> nodes"; return; }

                final java.util.regex.Pattern URL_RE = java.util.regex.Pattern.compile("(https?://[^\\s\"<>]+)", java.util.regex.Pattern.CASE_INSENSITIVE);

                class Job {
                    Cockatrice.Meta meta;
                    String name;
                    String url;
                    Path localPath;
                    String sourceFileName;
                    String lookupMethod;
                }
                List<Job> jobs = new ArrayList<>();
                List<Cockatrice.Meta> metas = Cockatrice.parseCards(xml);
                Map<String, Path> droppedByName = indexDroppedImages(droppedImagePaths);
                Set<String> usedLocalImages = new HashSet<>();
                Map<String, Integer> duplicateCounts = new HashMap<>();

                for (int i = 0; i < cards.getLength(); i++) {
                    var cardElem = (org.w3c.dom.Element) cards.item(i);
                    Cockatrice.Meta meta = i < metas.size() ? metas.get(i) : null;
                    String cardName = meta == null ? "" : nz(meta.name).trim();
                    if (cardName.isEmpty()) {
                        var nameNodes = cardElem.getElementsByTagName("name");
                        if (nameNodes.getLength() > 0) cardName = nameNodes.item(0).getTextContent().trim();
                    }

                    int duplicateIndex = 1;
                    String duplicateKey = normalizedCardName(cardName);
                    if (!duplicateKey.isBlank()) {
                        duplicateIndex = duplicateCounts.merge(duplicateKey, 1, Integer::sum);
                    }

                    LocalImageMatch local = resolveLocalImageForMeta(meta, xmlBaseDir, droppedByName, usedLocalImages, duplicateIndex);
                    if (local != null) {
                        Job j = new Job();
                        j.meta = meta;
                        j.name = cardName;
                        j.localPath = local.path;
                        j.sourceFileName = local.sourceFileName;
                        j.lookupMethod = local.method;
                        jobs.add(j);
                        logImageLookup(local.method, cardName, local.sourceFileName);
                        continue;
                    }

                    String cardXml;
                    {
                        var sw = new java.io.StringWriter();
                        var tf = javax.xml.transform.TransformerFactory.newInstance().newTransformer();
                        tf.setOutputProperty(javax.xml.transform.OutputKeys.OMIT_XML_DECLARATION, "yes");
                        tf.transform(new javax.xml.transform.dom.DOMSource(cardElem),
                                new javax.xml.transform.stream.StreamResult(sw));
                        cardXml = sw.toString();
                    }

                    var m = URL_RE.matcher(cardXml);
                    while (m.find()) {
                        String u = m.group(1);
                        String ul = u.toLowerCase(Locale.ROOT);
                        if (ul.endsWith(".png") || ul.endsWith(".jpg") || ul.endsWith(".jpeg") || ul.endsWith(".webp")
                                || ul.contains("/card_images/")) {
                            synchronized (importedPicUrls) {
                                if (!importedPicUrls.add(u)) { System.out.println("[MTGCard/XML] Skip duplicate URL: " + u); continue; }
                            }
                            Job j = new Job();
                            j.meta = meta;
                            j.name = cardName;
                            j.url = u;
                            j.sourceFileName = (cardName == null || cardName.isEmpty() ? "card" : cardName) + inferLowerExtFromUrl(u);
                            j.lookupMethod = "image URL";
                            jobs.add(j);
                            System.out.println("[MTGCard/XML] Found image URL for \"" + cardName + "\": " + u);
                            break;
                        }
                    }
                }

                xmlImportFound = jobs.size();
                xmlImportPhase = jobs.isEmpty() ? "No local images or image URLs found" : "Importing...";
                if (jobs.isEmpty()) return;

                var http = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
                        .connectTimeout(java.time.Duration.ofSeconds(10)).build();

                for (Job job : jobs) {
                    if (xmlCancel.get() || cancelRequested) {
                        xmlImportPhase = "Cancelled.";
                        break;
                    }
                    try {
                        byte[] raw;
                        String lowerHint;
                        String source;
                        if (job.localPath != null) {
                            System.out.println("[MTGCard/XML] Loading local image (" + job.lookupMethod + "): " + job.localPath);
                            raw = Files.readAllBytes(job.localPath);
                            lowerHint = job.localPath.getFileName().toString().toLowerCase(Locale.ROOT);
                            source = job.localPath.toUri().toString();
                        } else {
                            System.out.println("[MTGCard/XML] Downloading: " + job.url);
                            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create(job.url))
                                    .timeout(java.time.Duration.ofSeconds(15)).header("User-Agent", "mtgcard-mod/1.0").GET().build();
                            var resp = http.send(req, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
                            if (resp.statusCode() / 100 != 2) { xmlImportFailed++; xmlImportPhase = "HTTP " + resp.statusCode(); continue; }
                            raw = resp.body();
                            lowerHint = inferLowerExtFromUrl(job.url);
                            source = job.url == null ? "" : job.url;
                        }

                        EncodedImage enc = preferWebpElsePng(raw, lowerHint);

                        String sourceFileName = fileNameOnly(!nz(job.sourceFileName).isBlank()
                                ? job.sourceFileName
                                : ((job.name == null || job.name.isEmpty() ? "card" : job.name) + lowerHint));
                        String fileName = stripExt(sourceFileName) + enc.ext;

                        ClientPlayNetworking.send(new com.spider.mtgcard.net.payload.XmlArtUploadPayload(
                                fileName,
                                source,
                                nz(job.meta == null ? "" : job.meta.set),
                                enc.bytes
                        ));

                        Entry e = new Entry();
                        e.fileName = fileName;
                        e.sourceFileName = sourceFileName;
                        e.imgFront = enc.bytes;

                        if (job.meta != null) {
                            applyCockatriceMeta(e, job.meta);
                        } else if (job.name != null && !job.name.isEmpty()) {
                            e.meta.name = job.name;
                        }

                        Entry finalE = e;
                        if (this.minecraft != null) {
                            this.minecraft.execute(() -> {
                                String base = stripExt(finalE.fileName.toLowerCase(Locale.ROOT));
                                Entry existing = findEntryByBase(base);
                                if (existing == null) {
                                    buildThumbnail(finalE);
                                    entries.add(finalE);
                                } else {
                                    existing.imgFront = finalE.imgFront;
                                    existing.sourceFileName = finalE.sourceFileName;
                                    copyEntryMeta(existing, finalE);
                                    buildThumbnail(existing);
                                }
                                applyAutoTransformLinks();

                                boolean initSelection = (selected < 0);
                                if (initSelection) {
                                    selected = entries.size() - 1;
                                    syncEditorFromSelected(); // only on first selection
                                }
                                rebuildVisibleEntries();
                            });
                        }

                        xmlImportDone++;
                        xmlImportPhase = "Imported " + xmlImportDone + "/" + xmlImportFound;
                        System.out.println("[MTGCard/XML] Added \"" + e.meta.name + "\" to grid.");
                    } catch (Throwable t) {
                        xmlImportFailed++;
                        xmlImportPhase = "Failed " + xmlImportFailed;
                        System.out.println("[MTGCard/XML] Failed: " + (job.localPath != null ? job.localPath : job.url) + " (" + t + ")");
                        t.printStackTrace();
                    }
                }
            } catch (Throwable t) {
                System.out.println("[MTGCard/XML] Import failed: " + t);
                xmlImportPhase = "Import error";
            }
        }).whenComplete((v, err) -> {
            if (this.minecraft != null) this.minecraft.execute(() -> {
                xmlImportInProgress = false;
                if (err != null) {
                    System.out.println("[MTGCard/XML] Importer threw: " + err);
                    toast("XML import error: " + err.getClass().getSimpleName());
                } else {
                    toast("XML import finished: " + xmlImportDone + "/" + xmlImportFound
                            + (xmlImportFailed > 0 ? (" (failed " + xmlImportFailed + ")") : ""));
                }
            });
        });
    }

    private static Map<String, Path> indexDroppedImages(List<Path> paths) {
        Map<String, Path> out = new HashMap<>();
        if (paths == null) return out;
        for (Path path : paths) {
            if (path == null || path.getFileName() == null) continue;
            String name = path.getFileName().toString();
            String key = name.toLowerCase(Locale.ROOT);
            out.putIfAbsent(key, path);
        }
        return out;
    }

    private LocalImageMatch resolveLocalImageForMeta(Cockatrice.Meta meta,
                                                     Path xmlBaseDir,
                                                     Map<String, Path> droppedByName,
                                                     Set<String> usedLocalImages,
                                                     int duplicateIndex) {
        if (meta == null || nz(meta.name).isBlank()) return null;

        String name = nz(meta.name).trim();
        LocalImageMatch match = tryResolveLocalImage(meta.imageFileName, "image field", meta, xmlBaseDir, droppedByName, usedLocalImages);
        if (match != null) return match;

        for (String collectorCandidate : collectorImageFileNames(name, meta.collectorNumber, meta.set)) {
            match = tryResolveLocalImage(collectorCandidate, "collector number", meta, xmlBaseDir, droppedByName, usedLocalImages);
            if (match != null) return match;
        }

        String safeName = cockatriceImageSafeName(name);
        if (!safeName.equals(name)) {
            for (String safeCollectorCandidate : collectorImageFileNames(safeName, meta.collectorNumber, meta.set)) {
                match = tryResolveLocalImage(safeCollectorCandidate, "collector number", meta, xmlBaseDir, droppedByName, usedLocalImages);
                if (match != null) return match;
            }
        }

        // MSE can preserve otherwise invisible whitespace in the rendered filename while
        // XML text parsing trims it. The set/collector suffix is still authoritative.
        match = tryResolveLocalImageByCollectorSuffix(meta, xmlBaseDir, droppedByName, usedLocalImages);
        if (match != null) return match;

        for (String ext : COCKATRICE_IMAGE_EXTENSIONS) {
            match = tryResolveLocalImage(name + ext, "legacy name", meta, xmlBaseDir, droppedByName, usedLocalImages);
            if (match != null) return match;
            if (!safeName.equals(name)) {
                match = tryResolveLocalImage(safeName + ext, "legacy name", meta, xmlBaseDir, droppedByName, usedLocalImages);
                if (match != null) return match;
            }
        }

        int start = Math.max(2, duplicateIndex);
        for (int i = start; i < start + 100; i++) {
            for (String ext : COCKATRICE_IMAGE_EXTENSIONS) {
                match = tryResolveLocalImage(name + "_" + i + ext, "duplicate fallback", meta, xmlBaseDir, droppedByName, usedLocalImages);
                if (match != null) return match;
                if (!safeName.equals(name)) {
                    match = tryResolveLocalImage(safeName + "_" + i + ext, "duplicate fallback", meta, xmlBaseDir, droppedByName, usedLocalImages);
                    if (match != null) return match;
                }
            }
        }

        return null;
    }

    private static boolean isCardImage(String lowerName) {
        return lowerName.endsWith(".png") || lowerName.endsWith(".jpg")
                || lowerName.endsWith(".jpeg") || lowerName.endsWith(".webp");
    }

    private static boolean isMseExportDescriptor(String lowerName) {
        return lowerName.endsWith(".xml") || lowerName.endsWith(".json")
                || lowerName.endsWith(".txt") || lowerName.endsWith(".dat")
                || lowerName.endsWith(".html") || lowerName.endsWith(".htm");
    }

    /**
     * Expands a dropped MSE export directory, or the directory containing a dropped
     * exporter descriptor, so exporters that put card renders in img/, cardimages/,
     * a set-code folder, or beside the descriptor all work without special cases.
     */
    private static List<Path> expandMseDrop(List<Path> dropped) {
        LinkedHashSet<Path> files = new LinkedHashSet<>();
        LinkedHashSet<Path> scanRoots = new LinkedHashSet<>();
        for (Path path : dropped) {
            if (path == null) continue;
            Path normalized = path.toAbsolutePath().normalize();
            if (Files.isDirectory(normalized)) {
                scanRoots.add(normalized);
            } else if (Files.isRegularFile(normalized)) {
                files.add(normalized);
                String lower = normalized.getFileName().toString().toLowerCase(Locale.ROOT);
                if (isMseExportDescriptor(lower) && normalized.getParent() != null) {
                    scanRoots.add(normalized.getParent());
                }
            }
        }

        for (Path root : scanRoots) {
            try (var walk = Files.walk(root, 5)) {
                walk.filter(Files::isRegularFile)
                        .limit(10_000)
                        .filter(CustomImportScreen::isUsefulMseExportFile)
                        .map(path -> path.toAbsolutePath().normalize())
                        .forEach(files::add);
            } catch (IOException ignored) {}
        }
        return new ArrayList<>(files);
    }

    private static boolean isUsefulMseExportFile(Path path) {
        if (path == null || path.getFileName() == null) return false;
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (isMseExportDescriptor(lower)) return true;
        if (!isCardImage(lower)) return false;
        String stem = stripExt(lower);
        // MSE exporters also emit UI assets and thumbnails that are not cards.
        return !stem.equals("icon") && !stem.equals("logo") && !stem.equals("preview")
                && !stem.startsWith("card-preview");
    }

    private static LocalImageMatch tryResolveLocalImageByCollectorSuffix(Cockatrice.Meta meta,
                                                                          Path xmlBaseDir,
                                                                          Map<String, Path> droppedByName,
                                                                          Set<String> usedLocalImages) {
        String set = nz(meta == null ? "" : meta.set).trim();
        String collector = nz(meta == null ? "" : meta.collectorNumber).trim();
        if (set.isBlank() || collector.isBlank()) return null;

        ArrayList<String> suffixes = new ArrayList<>(COCKATRICE_IMAGE_EXTENSIONS.size());
        for (String ext : COCKATRICE_IMAGE_EXTENSIONS) {
            suffixes.add(("_" + set + "_" + collector + ext).toLowerCase(Locale.ROOT));
        }

        if (droppedByName != null) {
            for (Path path : droppedByName.values()) {
                LocalImageMatch found = collectorSuffixMatch(path, suffixes, usedLocalImages);
                if (found != null) return found;
            }
        }

        if (xmlBaseDir != null) {
            LocalImageMatch found = findCollectorSuffixInDirectory(xmlBaseDir, suffixes, usedLocalImages);
            if (found != null) return found;
            found = findCollectorSuffixInDirectory(xmlBaseDir.resolve(set), suffixes, usedLocalImages);
            if (found != null) return found;
        }
        return null;
    }

    private static LocalImageMatch findCollectorSuffixInDirectory(Path dir,
                                                                   List<String> suffixes,
                                                                   Set<String> usedLocalImages) {
        if (dir == null || !Files.isDirectory(dir)) return null;
        try (var children = Files.newDirectoryStream(dir)) {
            for (Path child : children) {
                LocalImageMatch found = collectorSuffixMatch(child, suffixes, usedLocalImages);
                if (found != null) return found;
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static LocalImageMatch collectorSuffixMatch(Path path,
                                                         List<String> suffixes,
                                                         Set<String> usedLocalImages) {
        if (path == null || path.getFileName() == null || !Files.isRegularFile(path)) return null;
        String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (suffixes.stream().noneMatch(lower::endsWith)) return null;
        String key = localPathKey(path);
        if (!usedLocalImages.add(key)) return null;
        return new LocalImageMatch(path, path.getFileName().toString(), "collector suffix");
    }

    private static LocalImageMatch tryResolveLocalImage(String sourceFileName,
                                                        String method,
                                                        Cockatrice.Meta meta,
                                                        Path xmlBaseDir,
                                                        Map<String, Path> droppedByName,
                                                        Set<String> usedLocalImages) {
        if (nz(sourceFileName).isBlank()) return null;
        Path path = resolveLocalImagePath(sourceFileName, nz(meta == null ? "" : meta.set), xmlBaseDir, droppedByName);
        if (path == null) return null;
        String key = localPathKey(path);
        if (!usedLocalImages.add(key)) return null;
        return new LocalImageMatch(path, fileNameOnly(sourceFileName), method);
    }

    private static Path resolveLocalImagePath(String sourceFileName,
                                              String setCode,
                                              Path xmlBaseDir,
                                              Map<String, Path> droppedByName) {
        String fileName = fileNameOnly(sourceFileName);
        if (!fileName.isBlank() && droppedByName != null) {
            Path dropped = droppedByName.get(fileName.toLowerCase(Locale.ROOT));
            if (dropped != null && Files.isRegularFile(dropped)) return dropped;
        }

        if (xmlBaseDir == null) return null;

        Path direct = existingFile(xmlBaseDir.resolve(sourceFileName));
        if (direct != null) return direct;

        String set = nz(setCode).trim();
        if (!set.isBlank()) {
            Path setDir = xmlBaseDir.resolve(set);
            Path inSetDir = existingFile(setDir.resolve(fileName));
            if (inSetDir != null) return inSetDir;
        }

        return null;
    }

    private static Path existingFile(Path path) {
        if (path == null) return null;
        if (Files.isRegularFile(path)) return path;

        Path parent = path.getParent();
        Path fileName = path.getFileName();
        if (parent == null || fileName == null || !Files.isDirectory(parent)) return null;

        try (var children = Files.newDirectoryStream(parent)) {
            String wanted = fileName.toString();
            for (Path child : children) {
                Path childName = child.getFileName();
                if (childName != null && childName.toString().equalsIgnoreCase(wanted) && Files.isRegularFile(child)) {
                    return child;
                }
            }
        } catch (IOException ignored) {}
        return null;
    }

    private static String localPathKey(Path path) {
        if (path == null) return "";
        return path.toAbsolutePath().normalize().toString().toLowerCase(Locale.ROOT);
    }

    private static String fileNameOnly(String sourceFileName) {
        String normalized = nz(sourceFileName).trim().replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : normalized;
    }

    private static String inferLowerExtFromUrl(String url) {
        int q = url.indexOf('?'); if (q >= 0) url = url.substring(0, q);
        int dot = url.lastIndexOf('.'); String ext = (dot > 0) ? url.substring(dot).toLowerCase(Locale.ROOT) : "";
        if (ext.isEmpty()) ext = ".jpg"; return ext;
    }

    // --- grid scrollbar helpers ---
    private void drawGridScrollbar(GuiGraphics ctx) {
        int trackX = panelX - 6;
        int trackW = 4;
        int trackY = GRID_TOP;
        int trackH = this.height - GRID_TOP - 40;

        ctx.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x60000000);
        ctx.fill(trackX - 1, trackY - 1, trackX + trackW + 1, trackY + trackH + 1, 0x40202020);

        if (maxScrollY > 0) {
            int thumbH = Math.max(24, (int) Math.round(trackH * (trackH / (double)(trackH + maxScrollY))));
            int thumbY = trackY + (int) Math.round((scrollY / (double) maxScrollY) * (trackH - thumbH));
            ctx.fill(trackX, thumbY, trackX + trackW, thumbY + thumbH, 0xFFA0A0A0);
            ctx.fill(trackX + 1, thumbY + 1, trackX + trackW - 1, thumbY + thumbH - 1, 0xFFE0E0E0);
        } else {
            ctx.fill(trackX, trackY, trackX + trackW, trackY + trackH, 0x40A0A0A0);
        }
    }
    private boolean hitScrollbar(double mx, double my) {
        int trackX = panelX - 6, trackW = 4, trackY = GRID_TOP, trackH = this.height - GRID_TOP - 40;
        return mx >= trackX - 2 && mx <= trackX + trackW + 2 && my >= trackY && my <= trackY + trackH;
    }
    private void startDragScrollbar(int mouseY) {
        barDragging = true;
        int trackY = GRID_TOP, trackH = this.height - GRID_TOP - 40;
        int thumbH = Math.max(24, (int) Math.round(trackH * (trackH / (double)(trackH + maxScrollY))));
        int thumbY = (maxScrollY > 0) ? trackY + (int) Math.round((scrollY / (double) maxScrollY) * (trackH - thumbH)) : trackY;
        barDragOffsetY = mouseY - thumbY;
    }
    private void dragScrollbarTo(int mouseY) {
        int trackY = GRID_TOP, trackH = this.height - GRID_TOP - 40;
        if (maxScrollY <= 0) return;
        int thumbH = Math.max(24, (int) Math.round(trackH * (trackH / (double)(trackH + maxScrollY))));
        int minY = trackY, maxY = trackY + trackH - thumbH;
        int newThumbY = Math.max(minY, Math.min(maxY, mouseY - barDragOffsetY));
        double ratio = (newThumbY - trackY) / (double) (trackH - thumbH);
        scrollY = (int) Math.round(ratio * maxScrollY);
        clampScroll();
    }

    // --- Simple multiline text area (full editor behaviors) ---
    private final class SimpleTextArea extends com.spider.mtgcard.client.compat.LegacyWidget {
        private String value = "";
        private String placeholder = "";
        private int maxLength = 1_000_000;
        private Consumer<String> onChange = s -> {};
        private int padding = 4;

        // layout / wrapping
        private int lineHeight;
        private int scrollY = 0;
        private List<Line> lines = Collections.emptyList();
        private int cachedWrapW = -1;
        private String cachedTextRef = null;

        // caret/selection (character indices)
        private int caret = 0;
        private int selStart = -1, selEnd = -1;
        private boolean selectingWithMouse = false;
        private int mouseSelectAnchor = 0;
        private Integer preferredCaretX = null; // keep X when moving up/down

        // scrollbar drag
        private boolean draggingBar = false;
        private int dragOffsetY = 0;

        // click count state
        private long lastClickTimeMs = 0;
        private double lastClickX = 0, lastClickY = 0;
        private int clickCount = 0;
        private static final int DOUBLE_CLICK_MS = 300;
        private static final int CLICK_SLOP = 4;

        // wrapping line payload
        private static final class Line {
            int start; // inclusive
            int end;   // exclusive (no newline)
        }

        SimpleTextArea(int x, int y, int w, int h, Component message) {
            super(x, y, w, h, message);
            this.lineHeight = Math.max(9, font.lineHeight);
            this.active = true;
            this.visible = true;
        }

        // ---------- API ----------
        void setPlaceholder(String p) { this.placeholder = (p == null ? "" : p); }
        void setMaxLength(int n) { this.maxLength = Math.max(1, n); }
        void setChangedListener(Consumer<String> c) { this.onChange = (c == null ? s -> {} : c); }
        void setText(String s) {
            this.value = (s == null ? "" : s);
            this.caret = this.value.length();
            clearSelection();
            this.cachedTextRef = null;
            if (!this.isFocused()) this.scrollY = 0;
        }
        String getText() { return this.value; }
        void scrollBy(int dy) { this.scrollY = Math.max(0, this.scrollY + dy); }

        // ---------- utils ----------
        private boolean hasSelection() { return selStart >= 0 && selEnd >= 0 && selStart != selEnd; }
        private void clearSelection() { selStart = selEnd = -1; }
        private int selMin() { return Math.min(selStart, selEnd); }
        private int selMax() { return Math.max(selStart, selEnd); }
        private static boolean isWord(char ch) { return Character.isLetterOrDigit(ch) || ch == '_'; }

        private int prevWord(int from) {
            from = Math.max(0, Math.min(from, value.length()));
            int i = from;
            if (i > 0) i--;
            while (i > 0 && !isWord(value.charAt(i))) i--;
            while (i > 0 && isWord(value.charAt(i-1))) i--;
            return i;
        }
        private int nextWord(int from) {
            from = Math.max(0, Math.min(from, value.length()));
            int i = from, n = value.length();
            while (i < n && isWord(value.charAt(i))) i++;
            while (i < n && !isWord(value.charAt(i))) i++;
            return i;
        }

        private int paragraphStart(int from) {
            int i = Math.max(0, Math.min(from, value.length()));
            int nl = value.lastIndexOf('\n', Math.max(0, i - 1));
            return Math.max(0, nl + 1);
        }
        private int paragraphEnd(int from) {
            int i = Math.max(0, Math.min(from, value.length()));
            int nl = value.indexOf('\n', i);
            return (nl < 0) ? value.length() : nl;
        }

        private void setCaret(int idx, boolean extendSelection) {
            idx = Math.max(0, Math.min(idx, value.length()));
            if (extendSelection) {
                if (!hasSelection()) selStart = caret;
                caret = idx;
                selEnd = caret;
            } else {
                caret = idx;
                clearSelection();
            }
            preferredCaretX = null;
            ensureCaretVisible();
        }
        private void deleteSelectionIfAny() {
            if (!hasSelection()) return;
            int a = selMin(), b = selMax();
            value = value.substring(0, a) + value.substring(b);
            caret = a;
            clearSelection();
            cachedTextRef = null;
            onChange.accept(value);
        }

        // ---------- wrapping / hit-testing ----------
        private void rewrapIfNeeded(int wrapW) {
            if (wrapW <= 0) wrapW = 1;
            if (wrapW == cachedWrapW && cachedTextRef == value) return;

            cachedWrapW = wrapW;
            cachedTextRef = value;

            lines = new ArrayList<>();
            if (value.isEmpty()) {
                Line L = new Line(); L.start = 0; L.end = 0; lines.add(L);
                return;
            }

            int n = value.length();
            int idx = 0;
            while (idx < n) {
                int nl = value.indexOf('\n', idx);
                int hardEnd = (nl < 0 ? n : nl);

                int lineStart = idx;
                while (lineStart < hardEnd) {
                    int lo = lineStart, hi = hardEnd;
                    while (lo < hi) {
                        int mid = (lo + hi + 1) >>> 1;
                        int w = font.width(value.substring(lineStart, mid));
                        if (w <= wrapW) lo = mid; else hi = mid - 1;
                    }
                    int fitEnd = (lo == lineStart) ? Math.min(lineStart + 1, hardEnd) : lo;

                    Line L = new Line(); L.start = lineStart; L.end = fitEnd; lines.add(L);
                    lineStart = fitEnd;
                }
                idx = (nl < 0 ? hardEnd : nl + 1);
            }
            if (lines.isEmpty()) { Line L = new Line(); L.start = 0; L.end = 0; lines.add(L); }
        }

        private int[] caretVisualPos(int wrapW) {
            rewrapIfNeeded(wrapW);
            int lineIdx = 0;
            for (int i = 0; i < lines.size(); i++) {
                Line L = lines.get(i);
                if (caret >= L.start && caret <= L.end) { lineIdx = i; break; }
                if (i == lines.size() - 1 && caret > L.end) lineIdx = i;
            }
            Line L = lines.get(lineIdx);
            int x = font.width(value.substring(L.start, Math.min(caret, L.end)));
            return new int[]{ lineIdx, x };
        }

        private int caretFromMouse(double mx, double my) {
            int textW = getWidth() - padding*2 - 6;
            rewrapIfNeeded(Math.max(1, textW));

            int drawX = getX() + padding;
            int drawY0 = getY() + padding;

            int relY = (int) (my - drawY0 + scrollY);
            if (relY < 0) relY = 0;
            int lineIdx = Math.max(0, Math.min(lines.size() - 1, relY / lineHeight));
            Line L = lines.get(lineIdx);
            String seg = value.substring(L.start, L.end);

            int relX = (int) (mx - drawX);
            if (relX <= 0) return L.start;
            int width = font.width(seg);
            if (relX >= width) return L.end;

            int lo = 0, hi = seg.length();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                int w = font.width(seg.substring(0, mid));
                if (w < relX) lo = mid + 1; else hi = mid;
            }
            int leftW = (lo == 0) ? 0 : font.width(seg.substring(0, lo-1));
            int hereW = font.width(seg.substring(0, lo));
            int choose = (Math.abs(relX - leftW) <= Math.abs(hereW - relX)) ? (lo - 1) : lo;
            choose = Math.max(0, Math.min(choose, seg.length()));
            return L.start + choose;
        }

        private void ensureCaretVisible() {
            int textW = getWidth() - padding*2 - 6;
            rewrapIfNeeded(Math.max(1, textW));

            int viewH = getHeight() - padding*2;
            int[] pos = caretVisualPos(Math.max(1, textW));
            int caretLine = pos[0];

            int totalH = Math.max(1, lines.size()) * lineHeight;
            int top = caretLine * lineHeight;
            int bottom = top + lineHeight;

            if (top < scrollY) scrollY = top;
            else if (bottom > scrollY + viewH) scrollY = bottom - viewH;

            if (scrollY < 0) scrollY = 0;
            int maxScroll = Math.max(0, totalH - viewH);
            if (scrollY > maxScroll) scrollY = maxScroll;
        }

        @Override
        protected void renderWidget(GuiGraphics ctx, int mouseX, int mouseY, float delta) {
            ensureCursors();
            if (this.isMouseOver(mouseX, mouseY)) applyCursor(CURSOR_IBEAM);
            else applyCursor(CURSOR_ARROW);

            int bg = isFocused() ? 0xFF202020 : 0xFF151515;
            int br = isFocused() ? 0xFFFFD070 : 0xFF404040;
            ctx.fill(getX()-1, getY()-1, getX()+getWidth()+1, getY()+getHeight()+1, br);
            ctx.fill(getX(), getY(), getX()+getWidth(), getY()+getHeight(), bg);

            ctx.enableScissor(getX(), getY(), getX() + getWidth(), getY() + getHeight());

            int textW = getWidth() - padding*2 - 6;
            rewrapIfNeeded(Math.max(1, textW));

            int drawX = getX() + padding;
            int drawY0 = getY() + padding;
            int viewH = getHeight() - padding*2;

            int totalH = Math.max(1, lines.size()) * lineHeight;
            int maxScroll = Math.max(0, totalH - viewH);
            if (scrollY > maxScroll) scrollY = maxScroll;
            if (scrollY < 0) scrollY = 0;

            // selection paint
            if (hasSelection()) {
                int a = selMin(), b = selMax();
                for (int i = 0; i < lines.size(); i++) {
                    Line L = lines.get(i);
                    int lo = Math.max(a, L.start);
                    int hi = Math.min(b, L.end);
                    if (lo >= hi) continue;

                    int y = drawY0 + (i * lineHeight - scrollY);
                    if (y + lineHeight < drawY0 || y > drawY0 + viewH) continue;

                    int x0 = drawX + font.width(value.substring(L.start, lo));
                    int x1 = drawX + font.width(value.substring(L.start, hi));
                    ctx.fill(x0, y, Math.max(x0+1, x1), y + lineHeight, 0x803072C4);
                }
            }

            // text / placeholder
            if (value.isEmpty()) {
                ctx.drawString(font, Component.literal(placeholder).getVisualOrderText(), drawX, drawY0, 0xFF7F7F7F, false);
            } else {
                int firstLine = Math.max(0, scrollY / lineHeight);
                int lastLine = Math.min(lines.size() - 1, (scrollY + viewH) / lineHeight);
                for (int i = firstLine; i <= lastLine; i++) {
                    Line L = lines.get(i);
                    int y = drawY0 + (i * lineHeight - scrollY);
                    ctx.drawString(font, Component.literal(value.substring(L.start, L.end)).getVisualOrderText(), drawX, y, 0xFFEFEFEF, false);
                }
            }

            // caret blink
            if (this.isFocused()) {
                int[] pos = caretVisualPos(Math.max(1, textW));
                int caretLine = pos[0];
                int caretX = drawX + pos[1];
                int caretY = drawY0 + (caretLine * lineHeight - scrollY);
                boolean on = ((System.currentTimeMillis() / 500) % 2) == 0;
                if (on) ctx.fill(caretX, caretY, caretX + 1, caretY + lineHeight, 0xFFEFEFEF);
            }

            // scrollbar
            if (maxScroll > 0) {
                int sbX = getX() + getWidth() - 5;
                int sbY = getY() + padding;
                int sbH = viewH;
                ctx.fill(sbX, sbY, sbX + 3, sbY + sbH, 0x40000000);
                int thumbH = Math.max(16, (int)Math.round(sbH * (viewH / (double)totalH)));
                int thumbY = sbY + (int)Math.round((scrollY / (double)maxScroll) * (sbH - thumbH));
                ctx.fill(sbX, thumbY, sbX + 3, sbY + thumbH, 0xFFA0A0A0);
                ctx.fill(sbX + 1, thumbY + 1, sbX + 2, sbY + thumbH - 1, 0xFFE0E0E0);
            }

            ctx.disableScissor();
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput builder) {
            builder.add(NarratedElementType.TITLE, getMessage());
        }

        // wheel
        public boolean mouseScrolled(double mx, double my, double horizontal, double vertical) {
            if (!this.isMouseOver(mx, my)) return false;
            int delta = (int) Math.round(-vertical * lineHeight * 3);
            scrollBy(delta);
            return true;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent click, boolean bl) {
            double mx = click.x(), my = click.y();
            if (!this.isMouseOver(mx, my)) return false;

            setFocused(true);
            CustomImportScreen.this.setFocused(this);
            CustomImportScreen.this.blurAllExcept(this);

            // click counting
            long now = System.currentTimeMillis();
            if (now - lastClickTimeMs <= DOUBLE_CLICK_MS
                    && Math.abs(mx - lastClickX) <= CLICK_SLOP
                    && Math.abs(my - lastClickY) <= CLICK_SLOP) {
                clickCount++;
            } else {
                clickCount = 1;
            }
            lastClickTimeMs = now;
            lastClickX = mx; lastClickY = my;

            int idx = caretFromMouse(mx, my);

            boolean shift = isShiftDown();
            if (clickCount == 1) {
                if (shift) {
                    if (!hasSelection()) selStart = caret;
                    caret = idx; selEnd = caret;
                } else {
                    caret = idx; clearSelection();
                }
                ensureCaretVisible();
                selectingWithMouse = true;
                mouseSelectAnchor = caret;
            } else if (clickCount == 2) {
                if (value.isEmpty()) return true;
                int a = idx, b = idx;
                while (a > 0 && isWord(value.charAt(a-1))) a--;
                while (b < value.length() && isWord(value.charAt(b))) b++;
                selStart = a; selEnd = b; caret = b;
                ensureCaretVisible();
            } else {
                int a = paragraphStart(idx);
                int b = paragraphEnd(idx);
                selStart = a; selEnd = b; caret = b;
                ensureCaretVisible();
            }

            // scrollbar drag test
            int sbX0 = getX() + getWidth() - 5;
            if (mx >= sbX0 && mx <= sbX0 + 5) {
                int viewH = getHeight() - padding*2;
                int textW = getWidth() - padding*2 - 6;
                rewrapIfNeeded(Math.max(1, textW));
                int totalH = Math.max(1, lines.size()) * lineHeight;
                int maxScroll = Math.max(0, totalH - viewH);
                if (maxScroll > 0) {
                    int sbY = getY() + padding;
                    int sbH = viewH;
                    int thumbH = Math.max(16, (int)Math.round(sbH * (viewH / (double)totalH)));
                    int thumbY = sbY + (int)Math.round((scrollY / (double)maxScroll) * (sbH - thumbH));
                    if (my >= thumbY && my <= thumbY + thumbH) {
                        draggingBar = true;
                        dragOffsetY = (int) (my - thumbY);
                    }
                }
            }
            return true;
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent click, double dx, double dy) {
            double mx = click.x(), my = click.y();
            if (draggingBar) {
                int viewH = getHeight() - padding*2;
                int textW = getWidth() - padding*2 - 6;
                rewrapIfNeeded(Math.max(1, textW));
                int totalH = Math.max(1, lines.size()) * lineHeight;
                int maxScroll = Math.max(0, totalH - viewH);
                if (maxScroll <= 0) return true;

                int sbY = getY() + padding;
                int sbH = viewH;
                int thumbH = Math.max(16, (int)Math.round(sbH * (viewH / (double)totalH)));
                int minY = sbY, maxY = sbY + sbH - thumbH;

                int newThumbY = (int)Math.max(minY, Math.min(maxY, my - dragOffsetY));
                double ratio = (newThumbY - sbY) / (double)(sbH - thumbH);
                scrollY = (int)Math.round(ratio * maxScroll);
                return true;
            }

            if (selectingWithMouse) {
                int margin = Math.max(8, lineHeight);
                if (my < getY() + margin) scrollBy(-lineHeight);
                if (my > getY() + getHeight() - margin) scrollBy(lineHeight);

                int idx = caretFromMouse(mx, my);
                caret = idx;
                selStart = mouseSelectAnchor;
                selEnd = caret;
                ensureCaretVisible();
                return true;
            }
            return false;
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent click) {
            draggingBar = false;
            selectingWithMouse = false;
            return false;
        }

        public boolean charTyped(CharacterEvent ch) {
            int cp = 0;
            try { cp = (int) ch.getClass().getMethod("codePoint").invoke(ch); } catch (Throwable ignored) {}
            if (cp == 0) { try { cp = (int) ch.getClass().getMethod("character").invoke(ch); } catch (Throwable ignored) {} }
            if (cp == 0) { try { cp = (int) ch.getClass().getMethod("codepoint").invoke(ch); } catch (Throwable ignored) {} }
            int mods = 0;
            try { mods = (int) ch.getClass().getMethod("modifiers").invoke(ch); } catch (Throwable ignored) {}
            return this.charTyped((char) cp, mods);
        }
        public boolean keyPressed(KeyEvent key) {
            int kc = 0, sc = 0, mods = 0;
            try { kc = (int) key.getClass().getMethod("keyCode").invoke(key); } catch (Throwable ignored) {}
            if (kc == 0) { try { kc = (int) key.getClass().getMethod("key").invoke(key); } catch (Throwable ignored) {} }
            try { sc = (int) key.getClass().getMethod("scanCode").invoke(key); } catch (Throwable ignored) {}
            if (sc == 0) { try { sc = (int) key.getClass().getMethod("scancode").invoke(key); } catch (Throwable ignored) {} }
            try { mods = (int) key.getClass().getMethod("modifiers").invoke(key); } catch (Throwable ignored) {}
            return this.keyPressed(kc, sc, mods);
        }

        public boolean charTyped(char chr, int modifiers) {
            if (!this.isFocused()) return false;
            if (chr == 0) return false;
            if (chr == '\r') chr = '\n';
            if (Character.isISOControl(chr) && chr != '\n' && chr != '\t') return false;

            deleteSelectionIfAny();

            String s = String.valueOf(chr);
            if (value == null) value = "";
            String newVal = value.substring(0, caret) + s + value.substring(caret);
            if (newVal.length() > maxLength) return false;
            value = newVal;
            caret += s.length();
            cachedTextRef = null;
            onChange.accept(value);
            ensureCaretVisible();
            return true;
        }

        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!this.isFocused()) return false;

            final int KEY_BACKSPACE = 259, KEY_DELETE = 261, KEY_ENTER = 257, KEY_KP_ENTER = 335;
            final int KEY_LEFT = 263, KEY_RIGHT = 262, KEY_UP = 265, KEY_DOWN = 264;
            final int KEY_HOME = 268, KEY_END = 269, KEY_PAGE_UP = 266, KEY_PAGE_DOWN = 267;
            final int KEY_ESCAPE = 256;

            final boolean SHIFT = (modifiers & 0x0001) != 0; // GLFW_MOD_SHIFT
            final boolean CTRL_OR_CMD = isCtrlOrCmdDown();

            switch (keyCode) {
                case KEY_ESCAPE:
                    if (linkPendingMode != LinkMode.NONE) {
                        linkPendingMode = LinkMode.NONE;
                        return true;
                    }
                    return false;

                case KEY_ENTER:
                case KEY_KP_ENTER:
                    deleteSelectionIfAny();
                    insert("\n");
                    ensureCaretVisible();
                    return true;

                case KEY_BACKSPACE:
                    if (hasSelection()) { deleteSelectionIfAny(); ensureCaretVisible(); return true; }
                    if (CTRL_OR_CMD) {
                        int from = prevWord(caret);
                        if (from != caret) {
                            value = value.substring(0, from) + value.substring(caret);
                            caret = from;
                            cachedTextRef = null;
                            onChange.accept(value);
                            ensureCaretVisible();
                            return true;
                        }
                        return false;
                    } else if (caret > 0) {
                        value = value.substring(0, caret - 1) + value.substring(caret);
                        caret -= 1;
                        cachedTextRef = null;
                        onChange.accept(value);
                        ensureCaretVisible();
                        return true;
                    }
                    return false;

                case KEY_DELETE:
                    if (hasSelection()) { deleteSelectionIfAny(); ensureCaretVisible(); return true; }
                    if (CTRL_OR_CMD) {
                        int to = nextWord(caret);
                        if (to != caret) {
                            value = value.substring(0, caret) + value.substring(to);
                            cachedTextRef = null;
                            onChange.accept(value);
                            ensureCaretVisible();
                            return true;
                        }
                        return false;
                    } else if (caret < value.length()) {
                        value = value.substring(0, caret) + value.substring(caret + 1);
                        cachedTextRef = null;
                        onChange.accept(value);
                        ensureCaretVisible();
                        return true;
                    }
                    return false;

                case KEY_LEFT:  moveHoriz(CTRL_OR_CMD ? prevWord(caret) : caret - 1, SHIFT); return true;
                case KEY_RIGHT: moveHoriz(CTRL_OR_CMD ? nextWord(caret) : caret + 1, SHIFT); return true;

                case KEY_HOME:
                    if (CTRL_OR_CMD) { setCaret(0, SHIFT); return true; }
                    setCaret(lineStartForCaret(), SHIFT);
                    return true;

                case KEY_END:
                    if (CTRL_OR_CMD) { setCaret(value.length(), SHIFT); return true; }
                    setCaret(lineEndForCaret(), SHIFT);
                    return true;

                case KEY_UP:   moveVert(-1, SHIFT); return true;
                case KEY_DOWN: moveVert(+1, SHIFT); return true;

                case KEY_PAGE_UP:   scrollBy(-(getHeight() - padding*2)); return true;
                case KEY_PAGE_DOWN: scrollBy( (getHeight() - padding*2)); return true;

                default:
                    if (CTRL_OR_CMD) {
                        if (keyCode == 65) { // A
                            selStart = 0; selEnd = value.length(); caret = selEnd; ensureCaretVisible(); return true;
                        }
                        if (keyCode == 67) { // C
                            if (hasSelection()) {
                                Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(selMin(), selMax()));
                            }
                            return true;
                        }
                        if (keyCode == 88) { // X
                            if (hasSelection()) {
                                Minecraft.getInstance().keyboardHandler.setClipboard(value.substring(selMin(), selMax()));
                                deleteSelectionIfAny();
                                ensureCaretVisible();
                            }
                            return true;
                        }
                        if (keyCode == 86) { // V
                            String clip = Minecraft.getInstance().keyboardHandler.getClipboard();
                            if (clip != null && !clip.isEmpty()) {
                                deleteSelectionIfAny();
                                clip = clip.replace("\r\n", "\n").replace('\r', '\n');
                                insert(clip);
                                ensureCaretVisible();
                            }
                            return true;
                        }
                    }
                    return false;
            }
        }

        private void moveHoriz(int newIdx, boolean extend) {
            newIdx = Math.max(0, Math.min(newIdx, value.length()));
            setCaret(newIdx, extend);
        }

        private int lineStartForCaret() {
            int textW = getWidth() - padding*2 - 6;
            rewrapIfNeeded(Math.max(1, textW));
            for (int i = 0; i < lines.size(); i++) {
                Line L = lines.get(i);
                if (caret >= L.start && caret <= L.end) return L.start;
            }
            return 0;
        }
        private int lineEndForCaret() {
            int textW = getWidth() - padding*2 - 6;
            rewrapIfNeeded(Math.max(1, textW));
            for (int i = 0; i < lines.size(); i++) {
                Line L = lines.get(i);
                if (caret >= L.start && caret <= L.end) return L.end;
            }
            return value.length();
        }

        private void moveVert(int deltaLines, boolean extend) {
            int textW = getWidth() - padding*2 - 6;
            rewrapIfNeeded(Math.max(1, textW));

            int[] pos = caretVisualPos(Math.max(1, textW));
            int lineIdx = pos[0];
            int x = (preferredCaretX != null) ? preferredCaretX : pos[1];
            int target = Math.max(0, Math.min(lines.size() - 1, lineIdx + deltaLines));
            Line L = lines.get(target);

            String seg = value.substring(L.start, L.end);
            int lo = 0, hi = seg.length();
            while (lo < hi) {
                int mid = (lo + hi) >>> 1;
                int w = font.width(seg.substring(0, mid));
                if (w < x) lo = mid + 1; else hi = mid;
            }
            int leftW = (lo == 0) ? 0 : font.width(seg.substring(0, lo-1));
            int hereW = font.width(seg.substring(0, lo));
            int choose = (Math.abs(x - leftW) <= Math.abs(hereW - x)) ? (lo - 1) : lo;
            choose = Math.max(0, Math.min(choose, seg.length()));

            preferredCaretX = x;
            setCaret(L.start + choose, extend);
        }

        private void insert(String s) {
            if (s == null || s.isEmpty()) return;
            if (value == null) value = "";
            String newVal = value.substring(0, caret) + s + value.substring(caret);
            if (newVal.length() > maxLength) return;
            value = newVal;
            caret += s.length();
            cachedTextRef = null;
            onChange.accept(value);
        }
    }

    private void syncEditorFromSelected() {
        if (selected < 0 || selected >= entries.size()) return;
        var m = entries.get(selected).meta;

        nameF.setValue(nz(m.name));
        manaF.setValue(nz(m.manaCost));
        typeF.setValue(nz(m.typeLine));
        setF.setValue(nz(m.set));

        if (textF != null) textF.setText(nz(m.oracleText));

        powF.setValue(nz(m.power));
        touF.setValue(nz(m.toughness));
        loyF.setValue(nz(m.loyalty));
        rarityBtn.setMessage(Component.literal("Rarity: " + (m.rarity == null || m.rarity.isEmpty() ? "common" : m.rarity)));
        dfcToggleBtn.setMessage(Component.literal(m.doubleFaced ? "Double-Faced" : "Single Face"));

        updateLinkButtonsVisibility();
    }

    private void clearEditorFocus() {
        if (nameF != null) nameF.setFocused(false);
        if (manaF != null) manaF.setFocused(false);
        if (typeF != null) typeF.setFocused(false);
        if (setF != null) setF.setFocused(false);
        if (powF != null) powF.setFocused(false);
        if (touF != null) touF.setFocused(false);
        if (loyF != null) loyF.setFocused(false);
        if (textF != null) textF.setFocused(false);
        if (this.getFocused() != searchBox) {
            this.setFocused(null);
        }
    }

    @Override
    public boolean charTyped(CharacterEvent ch) {
        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.charTyped(ch)) return true;
        }
        if (textF != null && textF.isFocused()) {
            final int cp   = ciCodePoint(ch);
            final int mods = ciModifiers(ch);
            if (cp != 0 && textF.charTyped((char) cp, mods)) return true;
        }
        return super.charTyped(ch);
    }

    @Override
    public boolean keyPressed(KeyEvent key) {
        // Allow Esc to cancel link mode globally
        int kc = kiKeyCode(key);
        if (kc == 256 /* ESC */ && linkPendingMode != LinkMode.NONE) {
            linkPendingMode = LinkMode.NONE;
            return true;
        }

        if (searchBox != null && searchBox.isFocused()) {
            if (searchBox.keyPressed(key)) return true;
        }

        if (textF != null && textF.isFocused()) {
            final int sc   = kiScanCode(key);
            final int mods = kiModifiers(key);
            if (textF.keyPressed(kc, sc, mods)) return true;
        }
        return super.keyPressed(key);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return linkPendingMode == LinkMode.NONE;
    }

    // ---- mapping-agnostic helpers ----
    private static int ciCodePoint(CharacterEvent ch) {
        try { return (int) ch.getClass().getMethod("codePoint").invoke(ch); } catch (Throwable ignored) {}
        try { return (int) ch.getClass().getMethod("character").invoke(ch); } catch (Throwable ignored) {}
        try { return (int) ch.getClass().getMethod("codepoint").invoke(ch); } catch (Throwable ignored) {}
        try { var f = ch.getClass().getDeclaredField("codePoint"); f.setAccessible(true); return f.getInt(ch); } catch (Throwable ignored) {}
        try { var f = ch.getClass().getDeclaredField("character"); f.setAccessible(true); return f.getInt(ch); } catch (Throwable ignored) {}
        try { var f = ch.getClass().getDeclaredField("codepoint"); f.setAccessible(true); return f.getInt(ch); } catch (Throwable ignored) {}
        return 0;
    }
    private static int ciModifiers(CharacterEvent ch) {
        try { return (int) ch.getClass().getMethod("modifiers").invoke(ch); } catch (Throwable ignored) {}
        try { var f = ch.getClass().getDeclaredField("modifiers"); f.setAccessible(true); return f.getInt(ch); } catch (Throwable ignored) {}
        return 0;
    }
    private static int kiKeyCode(KeyEvent key) {
        try { return (int) key.getClass().getMethod("keyCode").invoke(key); } catch (Throwable ignored) {}
        try { return (int) key.getClass().getMethod("key").invoke(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("keyCode"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("key"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        return 0;
    }
    private static int kiScanCode(KeyEvent key) {
        try { return (int) key.getClass().getMethod("scanCode").invoke(key); } catch (Throwable ignored) {}
        try { return (int) key.getClass().getMethod("scancode").invoke(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("scanCode"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("scancode"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        return 0;
    }
    private static int kiModifiers(KeyEvent key) {
        try { return (int) key.getClass().getMethod("modifiers").invoke(key); } catch (Throwable ignored) {}
        try { var f = key.getClass().getDeclaredField("modifiers"); f.setAccessible(true); return f.getInt(key); } catch (Throwable ignored) {}
        return 0;
    }

    // --- (once) cursor helpers ---
    private static long MOUSE_CURSOR_ARROW = 0L, MOUSE_CURSOR_IBEAM = 0L;

    private static void setCursor(int which) {
        var win = Minecraft.getInstance().getWindow();
        long handle = win.handle();
        long cur = (which == CURSOR_IBEAM) ? MOUSE_CURSOR_IBEAM : MOUSE_CURSOR_ARROW;
        GLFW.glfwSetCursor(handle, cur);
    }
    private static boolean isShiftDown() {
        long h = Minecraft.getInstance().getWindow().handle();
        return GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SHIFT) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SHIFT) == GLFW.GLFW_PRESS;
    }
    private static boolean isCtrlOrCmdDown() {
        long h = Minecraft.getInstance().getWindow().handle();
        boolean ctrl = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_CONTROL) == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_CONTROL) == GLFW.GLFW_PRESS;
        boolean cmd  = GLFW.glfwGetKey(h, GLFW.GLFW_KEY_LEFT_SUPER)   == GLFW.GLFW_PRESS
                || GLFW.glfwGetKey(h, GLFW.GLFW_KEY_RIGHT_SUPER)  == GLFW.GLFW_PRESS;
        return ctrl || cmd;
    }

    private void drawWrappedThumbLabel(GuiGraphics ctx, String text, int x, int y, int boxW, int boxH, int pad, int maxLines) {
        if (text == null) text = "";
        int innerX = x + 2;
        int innerW = boxW - 4;

        var wrapped = this.font.split(Component.literal(text), innerW - pad * 2);
        if (wrapped.isEmpty()) return;

        int linesToDraw = Math.min(maxLines, wrapped.size());

        int lineH = this.font.lineHeight;
        int overlayH = pad + linesToDraw * lineH + pad;

        int overlayTop = y + boxH - overlayH;
        int overlayLeft = innerX;
        int overlayRight = x + boxW - 2;
        int overlayBottom = y + boxH - 2;

        ctx.fill(overlayLeft, overlayTop, overlayRight, overlayBottom, 0xAA000000);
        ctx.fill(overlayLeft, overlayTop, overlayRight, overlayTop + 1, 0x33000000);

        int drawX = overlayLeft + pad;
        int drawY = overlayTop + pad;
        for (int i = 0; i < linesToDraw; i++) {
            ctx.drawString(this.font, wrapped.get(i), drawX, drawY, 0xFFEFEFEF, false);
            drawY += lineH;
        }

        if (wrapped.size() > maxLines) {
            int dotsW = this.font.width("…");
            int dotsX = overlayRight - pad - dotsW;
            int dotsY = overlayTop + pad + (linesToDraw - 1) * lineH;
            ctx.drawString(this.font, "…", dotsX, dotsY, 0xFFEFEFEF, false);
        }
    }

    private void blurAllExcept(AbstractWidget keep) {
        for (var w : this.children()) {
            if (w instanceof AbstractWidget cw && cw != keep) cw.setFocused(false);
        }
    }

    // ---------- Linking helpers ----------
    private void removeSelected() {
        if (selected < 0 || selected >= entries.size()) { toast("Nothing selected."); return; }

        int rem = selected;
        Entry victim = entries.get(rem);

        // break partner reciprocity if linked
        if (victim.link != null && victim.link.partnerIndex >= 0) {
            int pi = victim.link.partnerIndex;
            if (pi >= 0 && pi < entries.size()) {
                Entry partner = entries.get(pi);
                if (partner.link != null && partner.link.partnerIndex == rem) {
                    partner.link.partnerIndex = -1;
                    partner.meta.doubleFaced = false;
                }
            }
        }

        // Destroy textures for victim
        if (minecraft != null) {
            var tm = minecraft.getTextureManager();
            if (victim.thumbId != null) tm.release(victim.thumbId);
            if (victim.thumbTex != null) victim.thumbTex.close();
        }

        // Remove it
        entries.remove(rem);

        // Fix partner indices for everyone after removal
        for (var e : entries) {
            if (e.link != null && e.link.partnerIndex >= 0) {
                if (e.link.partnerIndex == rem) {
                    e.link.partnerIndex = -1;
                    e.meta.doubleFaced = false;
                } else if (e.link.partnerIndex > rem) {
                    e.link.partnerIndex--;
                }
            }
        }

        if (entries.isEmpty()) selected = -1;
        else selected = Math.min(rem, entries.size() - 1);

        syncEditorFromSelected();
        rebuildVisibleEntries();
    }

    private void clearLinkForSelected() {
        if (selected < 0 || selected >= entries.size()) { toast("Nothing selected."); return; }
        clearLink(entries.get(selected));
        toast("Link cleared.");
        syncEditorFromSelected();
    }

    private void clearLink(Entry a) {
        if (a == null || a.link == null || a.link.partnerIndex < 0) return;
        int pi = a.link.partnerIndex;
        if (pi >= 0 && pi < entries.size()) {
            Entry b = entries.get(pi);
            if (b.link != null && b.link.partnerIndex == indexOfEntry(a)) {
                b.link.partnerIndex = -1;
                b.meta.doubleFaced = false;
            }
        }
        a.link.partnerIndex = -1;
        a.meta.doubleFaced = false;
    }

    private int indexOfEntry(Entry e) {
        for (int i = 0; i < entries.size(); i++) if (entries.get(i) == e) return i;
        return -1;
    }

    private void linkSelectedAs(boolean asFront) {
        if (selected < 0 || selected >= entries.size()) { toast("Select a card first."); return; }
        linkPendingMode = asFront ? LinkMode.LINK_AS_FRONT : LinkMode.LINK_AS_BACK;
        String s = asFront ? "Link mode: click a BACK image to pair with this FRONT."
                : "Link mode: click a FRONT image to pair with this BACK.";
        toast(s);
    }

    /**
     * Link entry `aIdx` to `bIdx`.
     * If selectedAsFront = true, A is FRONT, B is BACK.
     * Otherwise A is BACK, B is FRONT.
     * Sets reciprocal link, and marks both double-faced.
     */
    private void linkWith(int aIdx, int bIdx, boolean selectedAsFront) {
        if (aIdx < 0 || bIdx < 0 || aIdx >= entries.size() || bIdx >= entries.size() || aIdx == bIdx) return;
        Entry A = entries.get(aIdx);
        Entry B = entries.get(bIdx);

        // Clear previous links on both (if any)
        clearLink(A);
        clearLink(B);

        // Set reciprocal
        A.link.partnerIndex = bIdx;
        B.link.partnerIndex = aIdx;
        A.link.isFront = selectedAsFront;
        B.link.isFront = !selectedAsFront;

        // Mark DF
        A.meta.doubleFaced = true;
        B.meta.doubleFaced = true;

        // Fill missing back* convenience fields
        if (A.link.isFront) {
            if (nz(A.meta.backName).isEmpty())       A.meta.backName       = nz(B.meta.name);
            if (nz(A.meta.backTypeLine).isEmpty())   A.meta.backTypeLine   = nz(B.meta.typeLine);
            if (nz(A.meta.backOracleText).isEmpty()) A.meta.backOracleText = nz(B.meta.oracleText);
            if (nz(A.meta.backPower).isEmpty())      A.meta.backPower      = nz(B.meta.power);
            if (nz(A.meta.backToughness).isEmpty())  A.meta.backToughness  = nz(B.meta.toughness);
            if (nz(A.meta.backLoyalty).isEmpty())    A.meta.backLoyalty    = nz(B.meta.loyalty);
        } else {
            if (nz(B.meta.backName).isEmpty())       B.meta.backName       = nz(A.meta.name);
            if (nz(B.meta.backTypeLine).isEmpty())   B.meta.backTypeLine   = nz(A.meta.typeLine);
            if (nz(B.meta.backOracleText).isEmpty()) B.meta.backOracleText = nz(A.meta.oracleText);
            if (nz(B.meta.backPower).isEmpty())      B.meta.backPower      = nz(A.meta.power);
            if (nz(B.meta.backToughness).isEmpty())  B.meta.backToughness  = nz(A.meta.toughness);
            if (nz(B.meta.backLoyalty).isEmpty())    B.meta.backLoyalty    = nz(A.meta.loyalty);
        }
    }

    private static byte[] ensureWebpBytes(byte[] input, String lowerName) throws IOException {
        if (lowerName.endsWith(".webp")) return input;

        // Make sure ImageIO sees plugins (writer is already true in your log, but keep it safe)
        try {
            Thread.currentThread().setContextClassLoader(CustomImportScreen.class.getClassLoader());
            com.spider.mtgcard.util.ArtImageStorage.ensureWebpCodecsRegistered();
        } catch (Throwable ignored) {}

        BufferedImage img = null;
        Throwable firstErr = null;

        // 1) Try ImageIO decode (works for many PNG/JPG)
        try (ByteArrayInputStream bais = new ByteArrayInputStream(input)) {
            img = ImageIO.read(bais);
        } catch (Throwable t) {
            firstErr = t;
            img = null;
        }

        // 2) Fallback: use Minecraft NativeImage decode (handles many JPG edge cases ImageIO can't)
        if (img == null) {
            try (InputStream is = new ByteArrayInputStream(input)) {
                NativeImage ni = NativeImage.read(is); // STB decode
                try {
                    int w = ni.getWidth();
                    int h = ni.getHeight();

                    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < h; y++) {
                        for (int x = 0; x < w; x++) {
                            // NativeImage.getColor returns ABGR (Minecraft format)
                            int abgr = ni.getPixel(x, y);
                            int a = (abgr >>> 24) & 0xFF;
                            int b = (abgr >>> 16) & 0xFF;
                            int g = (abgr >>> 8) & 0xFF;
                            int r = (abgr) & 0xFF;
                            int argb = (a << 24) | (r << 16) | (g << 8) | b;
                            out.setRGB(x, y, argb);
                        }
                    }
                    img = out;
                } finally {
                    ni.close();
                }
            } catch (Throwable t) {
                IOException ioe = new IOException("Could not decode image bytes for " + lowerName
                        + (firstErr != null ? ("; ImageIO error=" + firstErr.getClass().getSimpleName() + ": " + firstErr.getMessage()) : ""), t);
                throw ioe;
            }
        }

        // Ensure ARGB
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = out.createGraphics();
            try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
            img = out;
        }

        // 3) Encode WebP (your dependency)
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            var writers = ImageIO.getImageWritersByFormatName("webp");
            if (!writers.hasNext()) throw new IOException("No WebP writer found (unexpected: writer logged true earlier)");
            var writer = writers.next();

            var ios = ImageIO.createImageOutputStream(baos);
            writer.setOutput(ios);

            var param = writer.getDefaultWriteParam();
            com.spider.mtgcard.util.ArtImageStorage.configureWebpWriteParam(param);

            writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
            ios.close();
            writer.dispose();

            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IOException("Failed to encode WebP", ex);
        }
    }

    private static final class EncodedImage {
        final byte[] bytes;
        final String ext; // ".webp" or ".png"
        EncodedImage(byte[] bytes, String ext) { this.bytes = bytes; this.ext = ext; }
    }

    /**
     * Prefer WebP if possible, but ALWAYS fall back to PNG if:
     * - no webp writer exists
     * - encoding fails
     * - encoded bytes can't be decoded back (prevents "won't load" later)
     *
     * Also handles WEBP input that can't be decoded by converting it to PNG.
     */
    private static EncodedImage preferWebpElsePng(byte[] input, String lowerNameOrExt) throws IOException {
        String lower = (lowerNameOrExt == null ? "" : lowerNameOrExt.toLowerCase(Locale.ROOT));

        // First decode bytes into a BufferedImage using your existing robust path (ImageIO -> NativeImage)
        BufferedImage img = null;
        Throwable firstErr = null;

        // 1) Try ImageIO decode
        try (ByteArrayInputStream bais = new ByteArrayInputStream(input)) {
            img = ImageIO.read(bais);
        } catch (Throwable t) {
            firstErr = t;
            img = null;
        }

        // 2) Fallback decode via NativeImage (STB)
        if (img == null) {
            try (InputStream is = new ByteArrayInputStream(input)) {
                NativeImage ni = NativeImage.read(is);
                try {
                    int w = ni.getWidth(), h = ni.getHeight();
                    BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
                    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
                        int abgr = ni.getPixel(x, y);
                        int a = (abgr >>> 24) & 0xFF;
                        int b = (abgr >>> 16) & 0xFF;
                        int g = (abgr >>> 8) & 0xFF;
                        int r = (abgr) & 0xFF;
                        out.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
                    }
                    img = out;
                } finally {
                    ni.close();
                }
            } catch (Throwable t) {
                throw new IOException("Could not decode image bytes for " + lower
                        + (firstErr != null ? ("; ImageIO error=" + firstErr.getClass().getSimpleName() + ": " + firstErr.getMessage()) : ""), t);
            }
        }

        // Ensure ARGB for consistent encoding
        if (img.getType() != BufferedImage.TYPE_INT_ARGB) {
            BufferedImage out = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_ARGB);
            var g = out.createGraphics();
            try { g.drawImage(img, 0, 0, null); } finally { g.dispose(); }
            img = out;
        }

        // If the input is already PNG, you could keep original bytes,
        // but re-encoding is safer/consistent and fixes weird PNG chunks sometimes.
        // We'll still attempt WebP first, then fallback PNG.

        // --- Try WebP ---
        try {
            Thread.currentThread().setContextClassLoader(CustomImportScreen.class.getClassLoader());
            com.spider.mtgcard.util.ArtImageStorage.ensureWebpCodecsRegistered();

            var writers = ImageIO.getImageWritersByFormatName("webp");
            if (writers.hasNext()) {
                var writer = writers.next();
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    var ios = ImageIO.createImageOutputStream(baos);
                    writer.setOutput(ios);

                    var param = writer.getDefaultWriteParam();
                    com.spider.mtgcard.util.ArtImageStorage.configureWebpWriteParam(param);

                    writer.write(null, new javax.imageio.IIOImage(img, null, null), param);
                    ios.close();
                    writer.dispose();

                    byte[] webp = baos.toByteArray();

                    // Critical: verify decodes (prevents “still won’t load”)
                    boolean ok;
                    try (ByteArrayInputStream test = new ByteArrayInputStream(webp)) {
                        ok = (ImageIO.read(test) != null);
                    } catch (Throwable ignored) {
                        ok = false;
                    }

                    if (ok) return new EncodedImage(webp, ".webp");
                }
            }
        } catch (Throwable ignored) {
            // fall through to PNG
        }

        // --- Fallback: PNG ---
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            boolean wrote = ImageIO.write(img, "png", baos);
            if (!wrote) throw new IOException("No PNG writer available (unexpected).");
            return new EncodedImage(baos.toByteArray(), ".png");
        }
    }
    private static String sniffExt(byte[] bytes) {
        if (bytes == null || bytes.length < 12) return ".png";
        // PNG magic
        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return ".png";
        // RIFF....WEBP
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes.length >= 12
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') return ".webp";
        // JPEG magic
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) return ".jpg";
        return ".png";
    }

    private static String formatEtaSeconds(long sec) {
        if (sec < 0) sec = 0;
        if (sec < 60) return sec + "s";
        long m = sec / 60;
        long s = sec % 60;
        if (m < 60) return m + "m " + s + "s";
        long h = m / 60;
        long mm = m % 60;
        return h + "h " + mm + "m";
    }

    private String uploadEtaText() {
        if (!uploadsRunning) return ""; // don't show ETA when done/cancelled
        int remaining = Math.max(0, uploadTotalJobs - uploadJobsSent);

        // Need a bit of data before we trust it
        long now = System.currentTimeMillis();
        long elapsedMs = now - uploadStartMs;
        if (uploadRateEma <= 0.0001 || elapsedMs < 1200L || uploadJobsSent < 5) {
            return "Estimating…";
        }

        double sec = remaining / uploadRateEma;
        // clamp extremes so it doesn't look insane if rate hiccups
        long etaSec = (long) Math.max(0, Math.min(sec, 24 * 3600)); // cap at 24h
        return "ETA " + formatEtaSeconds(etaSec);
    }
}
