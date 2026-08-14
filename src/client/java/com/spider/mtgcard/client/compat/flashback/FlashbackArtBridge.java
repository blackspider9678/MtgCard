package com.spider.mtgcard.client.compat.flashback;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.client.java.CardArtManager;
import com.spider.mtgcard.display.CardDisplayAttachmentData;
import com.spider.mtgcard.display.CardDisplayEntity;
import com.spider.mtgcard.shared.CardArtCommon;
import com.spider.mtgcard.shared.MtgCardPaths;
import com.spider.mtgcard.util.ArtImageStorage;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;

public final class FlashbackArtBridge {
    public static final String ART_FOLDER_NAME = "MTG Art Cards";

    private static final Identifier ACTION_ID = Identifier.fromNamespaceAndPath("mtgcard", "flashback_art_optional");
    private static final int ACTION_VERSION = 1;
    private static final int MAX_ART_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EMBEDDED_ART_PER_PUMP = 4;
    private static final int MAX_EMBEDDED_ART_PER_SNAPSHOT = 96;

    private static final ConcurrentLinkedQueue<ArtRecord> PENDING = new ConcurrentLinkedQueue<>();
    private static final Set<String> QUEUED_KEYS = ConcurrentHashMap.newKeySet();
    private static final Set<String> RECORDED_KEYS = ConcurrentHashMap.newKeySet();
    private static final Set<String> MIRRORED_KEYS = ConcurrentHashMap.newKeySet();

    private static volatile boolean initialized = false;
    private static volatile boolean flashbackAvailable = false;
    private static volatile Object currentRecorder = null;

    private static Field recorderField;
    private static Method recorderReadyToWriteMethod;
    private static Method submitCustomTaskMethod;
    private static Method writerStartActionMethod;
    private static Method writerFinishActionMethod;
    private static Method writerFriendlyByteBufMethod;
    private static Object artAction;

    public static synchronized void init() {
        if (initialized) {
            return;
        }
        initialized = true;

        if (!FabricLoader.getInstance().isModLoaded("flashback")) {
            return;
        }

        try {
            ClassLoader loader = FlashbackArtBridge.class.getClassLoader();
            Class<?> flashbackClass = Class.forName("com.moulberry.flashback.Flashback", false, loader);
            Class<?> recorderClass = Class.forName("com.moulberry.flashback.record.Recorder", false, loader);
            Class<?> replayWriterClass = Class.forName("com.moulberry.flashback.io.ReplayWriter", false, loader);
            Class<?> actionClass = Class.forName("com.moulberry.flashback.action.Action", false, loader);
            Class<?> actionRegistryClass = Class.forName("com.moulberry.flashback.action.ActionRegistry", false, loader);

            recorderField = flashbackClass.getField("RECORDER");
            recorderReadyToWriteMethod = recorderClass.getMethod("readyToWrite");
            submitCustomTaskMethod = recorderClass.getMethod("submitCustomTask", Consumer.class);
            writerStartActionMethod = replayWriterClass.getMethod("startAction", actionClass);
            writerFinishActionMethod = replayWriterClass.getMethod("finishAction", actionClass);
            writerFriendlyByteBufMethod = replayWriterClass.getMethod("friendlyByteBuf");

            artAction = Proxy.newProxyInstance(
                    loader,
                    new Class<?>[]{actionClass},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "name" -> ACTION_ID;
                        case "handle" -> {
                            if (args != null && args.length >= 2 && args[1] instanceof RegistryFriendlyByteBuf buf) {
                                handleEmbeddedArt(buf);
                            }
                            yield null;
                        }
                        case "toString" -> "MTGCard Flashback Art Action";
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == (args == null ? null : args[0]);
                        default -> throw new UnsupportedOperationException(method.toString());
                    }
            );

            Method registerMethod = actionRegistryClass.getMethod("register", actionClass);
            try {
                registerMethod.invoke(null, artAction);
            } catch (InvocationTargetException e) {
                Throwable cause = e.getCause();
                if (cause == null || cause.getMessage() == null || !cause.getMessage().contains("Action already registered")) {
                    throw e;
                }
            }

            flashbackAvailable = true;
            Mtgcard.LOGGER.info("[MTGCard] Flashback art bridge enabled; cache folder is {}", flashbackArtRoot());
        } catch (Throwable t) {
            flashbackAvailable = false;
            Mtgcard.LOGGER.warn("[MTGCard] Flashback art bridge disabled: {}", t.toString());
        }
    }

    public static void pumpQueue() {
        Object recorder = activeRecorder();
        if (recorder == null || !recorderReadyToWrite(recorder)) {
            return;
        }

        int submitted = 0;
        while (submitted < MAX_EMBEDDED_ART_PER_PUMP) {
            ArtRecord record = PENDING.poll();
            if (record == null) {
                break;
            }
            QUEUED_KEYS.remove(record.key());
            if (submitArtRecord(recorder, record)) {
                submitted++;
            }
        }
    }

    public static void writeSnapshotArt(Object recorder) {
        if (!ensureAvailable() || recorder == null) {
            return;
        }

        beginRecorderSession(recorder);
        if (!recorderReadyToWrite(recorder)) {
            return;
        }
        collectWorldCardArt();

        int submitted = 0;
        while (submitted < MAX_EMBEDDED_ART_PER_SNAPSHOT) {
            ArtRecord record = PENDING.poll();
            if (record == null) {
                break;
            }
            QUEUED_KEYS.remove(record.key());
            if (submitArtRecord(recorder, record)) {
                submitted++;
            }
        }
    }

    public static void rememberArt(String game, String artKey, String setCode, Path file) {
        if (!ensureAvailable() || file == null || artKey == null || artKey.isBlank()) {
            return;
        }
        if (!Files.isRegularFile(file)) {
            return;
        }

        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        String safeKey = CardArtCommon.sanitizeArtKey(artKey);
        String safeSet = setCode == null || setCode.isBlank() ? "" : MtgCardPaths.sanitizeSetFolder(setCode);
        if (safeKey.isBlank()) {
            return;
        }

        mirrorArtFile(safeGame, safeKey, safeSet, file);

        Object recorder = activeRecorder();
        if (recorder == null) {
            return;
        }

        ArtRecord record = new ArtRecord(safeGame, safeKey, safeSet, file);
        if (RECORDED_KEYS.contains(record.key()) || !QUEUED_KEYS.add(record.key())) {
            return;
        }
        PENDING.add(record);
    }

    public static Path findCachedArt(String game, String artKey, List<String> fallbackKeys, String setCode) {
        if (!ensureAvailable() || artKey == null || artKey.isBlank()) {
            return null;
        }

        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        String safeSet = setCode == null || setCode.isBlank() ? "" : MtgCardPaths.sanitizeSetFolder(setCode);

        LinkedHashSet<Path> dirs = new LinkedHashSet<>();
        if (!safeSet.isBlank()) {
            addCustomArtSearchDirs(dirs, safeGame, safeSet);
        }
        addMainArtSearchDirs(dirs, safeGame);

        for (Path dir : dirs) {
            for (String candidate : artKeyCandidates(artKey, fallbackKeys)) {
                Path exact = resolveExactFile(dir, candidate);
                if (exact != null) {
                    return exact;
                }
            }
        }

        return null;
    }

    public static void mirrorArtFile(String game, String artKey, String setCode, Path source) {
        if (!ensureAvailable() || source == null || !Files.isRegularFile(source)) {
            return;
        }

        String safeGame = MtgCardPaths.sanitizeGameFolder(game);
        String safeKey = CardArtCommon.sanitizeArtKey(artKey);
        String ext = extensionOf(source);
        if (safeKey.isBlank() || ext.isBlank()) {
            return;
        }

        String safeSet = MtgCardPaths.sanitizeSetFolder(setCode);
        String mirrorKey = safeGame + ":" + safeSet + ":" + safeKey + "." + ext;
        if (!MIRRORED_KEYS.add(mirrorKey)) {
            return;
        }

        Path targetDir = setCode == null || setCode.isBlank()
                ? mainArtDir(safeGame)
                : customArtDir(safeGame, safeSet);
        Path target = targetDir.resolve(safeKey + "." + ext);

        try {
            if (Files.exists(target) && Files.isSameFile(source, target)) {
                return;
            }
        } catch (Exception ignored) {
        }

        Util.backgroundExecutor().execute(() -> {
            try {
                Files.createDirectories(targetDir);
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
                ArtImageStorage.deleteSiblingFormats(targetDir, safeKey, ext);
            } catch (Exception e) {
                Mtgcard.LOGGER.debug("[MTGCard] Could not mirror Flashback art {}: {}", safeKey, e.toString());
            }
        });
    }

    private static boolean submitArtRecord(Object recorder, ArtRecord record) {
        if (recorder == null || record == null || RECORDED_KEYS.contains(record.key())) {
            return false;
        }
        if (!recorderReadyToWrite(recorder)) {
            QUEUED_KEYS.add(record.key());
            PENDING.add(record);
            return false;
        }

        try {
            submitCustomTaskMethod.invoke(recorder, (Consumer<Object>) writer -> writeAction(writer, record));
            RECORDED_KEYS.add(record.key());
            return true;
        } catch (Throwable t) {
            Mtgcard.LOGGER.debug("[MTGCard] Could not submit Flashback art {}: {}", record.artKey(), t.toString());
            return false;
        }
    }

    private static void writeAction(Object writer, ArtRecord record) {
        try {
            if (!Files.isRegularFile(record.file())) {
                return;
            }

            long size = Files.size(record.file());
            if (size <= 0 || size > MAX_ART_BYTES) {
                return;
            }

            byte[] bytes = Files.readAllBytes(record.file());
            if (bytes.length == 0 || bytes.length > MAX_ART_BYTES) {
                return;
            }

            RegistryFriendlyByteBuf buf = (RegistryFriendlyByteBuf) writerFriendlyByteBufMethod.invoke(writer);
            writerStartActionMethod.invoke(writer, artAction);
            try {
                buf.writeByte(ACTION_VERSION);
                buf.writeUtf(record.game());
                buf.writeUtf(record.artKey());
                buf.writeUtf(record.setCode());
                buf.writeUtf(extensionOf(record.file()));
                buf.writeByteArray(bytes);
            } finally {
                writerFinishActionMethod.invoke(writer, artAction);
            }
        } catch (Throwable t) {
            Mtgcard.LOGGER.debug("[MTGCard] Could not write Flashback art {}: {}", record.artKey(), t.toString());
        }
    }

    private static void handleEmbeddedArt(RegistryFriendlyByteBuf buf) {
        try {
            int version = buf.readUnsignedByte();
            if (version != ACTION_VERSION) {
                skipRemaining(buf);
                return;
            }

            String game = buf.readUtf();
            String artKey = buf.readUtf();
            String setCode = buf.readUtf();
            buf.readUtf();
            byte[] bytes = buf.readByteArray(MAX_ART_BYTES);

            if (bytes.length == 0 || artKey == null || artKey.isBlank()) {
                return;
            }

            Minecraft.getInstance().execute(() ->
                    CardArtManager.onFlashbackEmbeddedArt(game, artKey, setCode, bytes)
            );
        } catch (Throwable t) {
            Mtgcard.LOGGER.debug("[MTGCard] Could not read Flashback embedded art: {}", t.toString());
        } finally {
            skipRemaining(buf);
        }
    }

    private static void collectWorldCardArt() {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return;
        }

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof CardDisplayEntity display) {
                CardArtManager.prefetchFlashbackArt(display.getCleanHostStack());
                for (CardDisplayAttachmentData.Attachment attachment : display.getCardAttachments()) {
                    CardArtManager.prefetchFlashbackArt(attachment.stack());
                }
            } else if (entity instanceof ItemEntity itemEntity) {
                CardArtManager.prefetchFlashbackArt(itemEntity.getItem());
            }
        }

        if (mc.player != null) {
            CardArtManager.prefetchFlashbackArt(mc.player.getMainHandItem());
            CardArtManager.prefetchFlashbackArt(mc.player.getOffhandItem());
        }
    }

    private static Object activeRecorder() {
        if (!ensureAvailable()) {
            return null;
        }

        try {
            Object recorder = recorderField.get(null);
            if (recorder == null) {
                beginRecorderSession(null);
                return null;
            }
            beginRecorderSession(recorder);
            return recorder;
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean recorderReadyToWrite(Object recorder) {
        if (recorder == null || recorderReadyToWriteMethod == null) {
            return false;
        }

        try {
            return Boolean.TRUE.equals(recorderReadyToWriteMethod.invoke(recorder));
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static void beginRecorderSession(Object recorder) {
        if (currentRecorder == recorder) {
            return;
        }

        currentRecorder = recorder;
        PENDING.clear();
        QUEUED_KEYS.clear();
        RECORDED_KEYS.clear();
    }

    private static boolean ensureAvailable() {
        if (!initialized) {
            init();
        }
        return flashbackAvailable;
    }

    private static Path flashbackArtRoot() {
        return FabricLoader.getInstance().getGameDir().resolve("flashback").resolve(ART_FOLDER_NAME);
    }

    private static Path gameRoot(String game) {
        return flashbackArtRoot().resolve(MtgCardPaths.sanitizeGameFolder(game));
    }

    private static Path mainArtDir(String game) {
        return gameRoot(game).resolve("main_art");
    }

    private static Path customArtRoot(String game) {
        return gameRoot(game).resolve("custom_art");
    }

    private static Path customArtDir(String game, String setCode) {
        return customArtRoot(game).resolve(MtgCardPaths.sanitizeSetFolder(setCode));
    }

    private static void addMainArtSearchDirs(Set<Path> dirs, String game) {
        Path root = mainArtDir(game);
        dirs.add(root);
        addDirectChildDirs(dirs, root);
    }

    private static void addCustomArtSearchDirs(Set<Path> dirs, String game, String setCode) {
        Path root = customArtRoot(game);
        dirs.add(root.resolve(MtgCardPaths.sanitizeSetFolder(setCode)));

        try (var scopes = Files.list(root)) {
            scopes.filter(Files::isDirectory).forEach(scope -> {
                dirs.add(scope.resolve(MtgCardPaths.sanitizeSetFolder(setCode)));
                addDirectChildDirs(dirs, scope);
            });
        } catch (Exception ignored) {
        }
    }

    private static void addDirectChildDirs(Set<Path> dirs, Path root) {
        try (var stream = Files.list(root)) {
            stream.filter(Files::isDirectory).forEach(dirs::add);
        } catch (Exception ignored) {
        }
    }

    private static Path resolveExactFile(Path dir, String artKey) {
        if (dir == null || artKey == null || artKey.isBlank()) {
            return null;
        }

        Path webp = dir.resolve(artKey + ".webp");
        if (Files.exists(webp)) return webp;

        Path png = dir.resolve(artKey + ".png");
        if (Files.exists(png)) return png;

        Path jpg = dir.resolve(artKey + ".jpg");
        if (Files.exists(jpg)) return jpg;

        Path jpeg = dir.resolve(artKey + ".jpeg");
        if (Files.exists(jpeg)) return jpeg;

        return null;
    }

    private static Iterable<String> artKeyCandidates(String artKey, List<String> fallbackKeys) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        addArtKeyCandidate(keys, artKey);

        if (fallbackKeys != null) {
            for (String fallback : fallbackKeys) {
                addArtKeyCandidate(keys, fallback);
            }
        }

        LinkedHashSet<String> expanded = new LinkedHashSet<>(keys);
        for (String key : expanded) {
            String lower = key.trim().toLowerCase(Locale.ROOT);
            addArtKeyCandidate(keys, lower);

            if (lower.startsWith("custom_")) {
                addArtKeyCandidate(keys, lower.substring("custom_".length()));
            } else {
                addArtKeyCandidate(keys, "custom_" + lower);
            }
        }

        return keys;
    }

    private static void addArtKeyCandidate(Set<String> keys, String artKey) {
        if (artKey == null) {
            return;
        }

        String sanitized = CardArtCommon.sanitizeArtKey(artKey);
        if (!sanitized.isBlank()) {
            keys.add(sanitized);
        }
    }

    private static String extensionOf(Path path) {
        String name = path.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "webp";
    }

    private static void skipRemaining(RegistryFriendlyByteBuf buf) {
        try {
            int readable = buf.readableBytes();
            if (readable > 0) {
                buf.skipBytes(readable);
            }
        } catch (Throwable ignored) {
        }
    }

    private record ArtRecord(String game, String artKey, String setCode, Path file) {
        String key() {
            return game + ":" + setCode + ":" + artKey;
        }
    }

    private FlashbackArtBridge() {
    }
}
