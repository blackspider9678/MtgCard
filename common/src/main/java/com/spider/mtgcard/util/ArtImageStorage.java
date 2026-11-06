package com.spider.mtgcard.util;

import com.spider.mtgcard.Mtgcard;
import com.luciad.imageio.webp.WebPImageReaderSpi;
import com.luciad.imageio.webp.WebPImageWriterSpi;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.spi.IIORegistry;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ArtImageStorage {
    private static final float WEBP_QUALITY = 0.90f;
    private static final String[] KNOWN_EXTS = {"webp", "png", "jpg", "jpeg"};
    private static final AtomicBoolean WEBP_CODECS_REGISTERED = new AtomicBoolean(false);
    private static final AtomicBoolean WEBP_CODEC_STATUS_LOGGED = new AtomicBoolean(false);

    public record StoredArt(byte[] bytes, String ext) {
        public Path pathIn(Path dir, String baseName) {
            return dir.resolve(baseName + "." + ext);
        }
    }

    public record StorageDecision(StoredArt art, String sourceExt, boolean fellBackFromWebp, String note) {}

    private record WebpAttempt(byte[] bytes, String failureReason) {}

    public static StorageDecision normalizeForStorage(byte[] input, String hintedNameOrExt) {
        if (input == null || input.length == 0) {
            return new StorageDecision(null, "bin", true, "empty image input");
        }

        String detectedExt = detectExt(input);
        String fallbackExt = detectedExt.equals("bin") ? normalizeExt(hintedNameOrExt) : detectedExt;

        if ("webp".equals(detectedExt)) {
            return new StorageDecision(new StoredArt(input, "webp"), "webp", false, "source already webp");
        }

        WebpAttempt attempt = tryEncodeWebp(input);
        if (attempt.bytes() != null && attempt.bytes().length > 0) {
            return new StorageDecision(new StoredArt(attempt.bytes(), "webp"), detectedExt, false, "converted to webp");
        }

        if ("bin".equals(detectedExt)) {
            String reason = attempt.failureReason() == null
                    ? "unknown image format; hinted ext=" + fallbackExt
                    : attempt.failureReason();
            return new StorageDecision(null, detectedExt, true, reason);
        }

        String reason = attempt.failureReason() == null
                ? "webp conversion unavailable; using original ." + fallbackExt
                : attempt.failureReason();
        return new StorageDecision(new StoredArt(input, fallbackExt), detectedExt, true, reason);
    }

    public static Path write(Path dir, String baseName, StoredArt art) throws IOException {
        Files.createDirectories(dir);
        deleteSiblingFormats(dir, baseName, art.ext);

        Path out = art.pathIn(dir, baseName);
        Files.write(out, art.bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        return out;
    }

    public static void deleteSiblingFormats(Path dir, String baseName, String keepExt) throws IOException {
        for (String ext : KNOWN_EXTS) {
            if (ext.equalsIgnoreCase(keepExt)) {
                continue;
            }
            Files.deleteIfExists(dir.resolve(baseName + "." + ext));
        }
    }

    public static String detectExt(byte[] bytes) {
        if (bytes == null || bytes.length < 12) {
            return "bin";
        }

        if ((bytes[0] & 0xFF) == 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) {
            return "png";
        }
        if ((bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8) {
            return "jpg";
        }
        if (bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
                && bytes[8] == 'W' && bytes[9] == 'E' && bytes[10] == 'B' && bytes[11] == 'P') {
            return "webp";
        }

        return "bin";
    }

    public static String normalizeExt(String hintedNameOrExt) {
        if (hintedNameOrExt == null || hintedNameOrExt.isBlank()) {
            return "png";
        }

        String lower = hintedNameOrExt.trim().toLowerCase(Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        String ext = dot >= 0 ? lower.substring(dot + 1) : lower;

        return switch (ext) {
            case "webp" -> "webp";
            case "png" -> "png";
            case "jpg", "jpeg" -> ext;
            default -> "png";
        };
    }

    public static void ensureWebpCodecsRegistered() {
        try {
            Thread.currentThread().setContextClassLoader(ArtImageStorage.class.getClassLoader());

            if (WEBP_CODECS_REGISTERED.compareAndSet(false, true)) {
                IIORegistry registry = IIORegistry.getDefaultInstance();
                registry.registerServiceProvider(new WebPImageReaderSpi());
                registry.registerServiceProvider(new WebPImageWriterSpi());
            }

            ImageIO.scanForPlugins();
            logCodecStatusOnce();
        } catch (Throwable ignored) {
        }
    }

    public static void configureWebpWriteParam(ImageWriteParam param) {
        if (param == null || !param.canWriteCompressed()) {
            return;
        }

        param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);

        String[] types = param.getCompressionTypes();
        if (types != null && types.length > 0) {
            String preferred = types[0];
            for (String type : types) {
                if ("Lossy".equalsIgnoreCase(type)) {
                    preferred = type;
                    break;
                }
            }
            param.setCompressionType(preferred);
        }

        param.setCompressionQuality(WEBP_QUALITY);
    }

    private static WebpAttempt tryEncodeWebp(byte[] input) {
        try {
            BufferedImage image = readImage(input);
            if (image == null) {
                return new WebpAttempt(null, "decoded image was null");
            }

            ensureWebpCodecsRegistered();

            var writers = ImageIO.getImageWritersByFormatName("webp");
            if (!writers.hasNext()) {
                return new WebpAttempt(null, "no webp writer available");
            }

            var writer = writers.next();
            try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                 var ios = ImageIO.createImageOutputStream(baos)) {
                writer.setOutput(ios);

                ImageWriteParam param = writer.getDefaultWriteParam();
                configureWebpWriteParam(param);

                writer.write(null, new IIOImage(image, null, null), param);
                ios.flush();

                byte[] webp = baos.toByteArray();
                if (!canDecode(webp)) {
                    return new WebpAttempt(null, "encoded webp could not be decoded");
                }
                return new WebpAttempt(webp, null);
            } finally {
                writer.dispose();
            }
        } catch (Throwable t) {
            return new WebpAttempt(null, shortMessage(t));
        }
    }

    private static BufferedImage readImage(byte[] input) throws IOException {
        ensureWebpCodecsRegistered();
        try (ByteArrayInputStream in = new ByteArrayInputStream(input)) {
            BufferedImage image = ImageIO.read(in);
            if (image == null) {
                throw new IOException("Unsupported image format");
            }
            return ensureArgb(image);
        }
    }

    private static boolean canDecode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return false;
        }

        ensureWebpCodecsRegistered();
        try (ByteArrayInputStream in = new ByteArrayInputStream(bytes)) {
            return ImageIO.read(in) != null;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static BufferedImage ensureArgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_ARGB) {
            return image;
        }

        BufferedImage converted = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = converted.createGraphics();
        try {
            graphics.drawImage(image, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return converted;
    }

    private static void logCodecStatusOnce() {
        if (!WEBP_CODEC_STATUS_LOGGED.compareAndSet(false, true)) {
            return;
        }

        boolean hasReader = ImageIO.getImageReadersByFormatName("webp").hasNext();
        boolean hasWriter = ImageIO.getImageWritersByFormatName("webp").hasNext();

        if (hasReader && hasWriter) {
            Mtgcard.LOGGER.info("[MTGCard] WebP ImageIO codecs ready: reader={}, writer={}", hasReader, hasWriter);
        } else {
            Mtgcard.LOGGER.warn("[MTGCard] WebP ImageIO codecs incomplete: reader={}, writer={}", hasReader, hasWriter);
        }
    }

    private static String shortMessage(Throwable t) {
        if (t == null) {
            return "unknown error";
        }
        String simple = t.getClass().getSimpleName();
        String message = t.getMessage();
        if (message == null || message.isBlank()) {
            return simple;
        }
        return simple + ": " + message;
    }

    private ArtImageStorage() {}
}
