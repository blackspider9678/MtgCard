package com.spider.mtgcard.api;

import com.spider.mtgcard.Mtgcard;
import com.spider.mtgcard.deckbox.DeckboxBlockEntity;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.*;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.LevelResource;

import java.io.IOException;
import java.nio.file.*;
import java.util.Optional;
import java.util.UUID;

/** Public server-side API for UUID-backed deckbox records. Records are never automatically deleted. */
public final class DeckboxStorage {
    public static final int FORMAT_VERSION = 1;
    public static final String ITEM_ID_KEY = "mtgcard_deckbox_id";
    public static final String BLOCK_ID_KEY = "DeckboxId";

    private DeckboxStorage() {}

    public record Record(UUID id, String type, String name, long lastAccessed,
                         NonNullList<ItemStack> items) {}

    public static Path directory(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("mtgcard").resolve("deckbox");
    }

    public static Path path(MinecraftServer server, UUID id) {
        return directory(server).resolve(id + ".dat");
    }

    public static synchronized void save(MinecraftServer server, HolderLookup.Provider registries,
                                         UUID id, String type, String name, NonNullList<ItemStack> items) {
        CompoundTag root = new CompoundTag();
        root.putInt("Version", FORMAT_VERSION);
        root.putString("Id", id.toString());
        root.putString("Type", type == null ? "" : type);
        root.putString("Name", name == null ? "" : name);
        root.putLong("LastAccessed", System.currentTimeMillis());
        root.putInt("SlotCount", DeckboxBlockEntity.INVENTORY_SIZE);

        ListTag slots = new ListTag();
        var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
        for (int slot = 0; slot < Math.min(items.size(), DeckboxBlockEntity.INVENTORY_SIZE); slot++) {
            ItemStack stack = items.get(slot);
            if (stack == null || stack.isEmpty()) continue;
            final int slotIndex = slot;
            ItemStack.CODEC.encodeStart(ops, stack).resultOrPartial(message ->
                    Mtgcard.LOGGER.error("Could not encode deckbox {} slot {}: {}", id, slotIndex, message)
            ).ifPresent(encoded -> {
                if (encoded instanceof CompoundTag stackTag) {
                    CompoundTag entry = new CompoundTag();
                    entry.putInt("Slot", slotIndex);
                    entry.put("Stack", stackTag);
                    slots.add(entry);
                }
            });
        }
        root.put("Items", slots);

        Path target = path(server, id);
        Path temporary = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            NbtIo.writeCompressed(root, temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Mtgcard.LOGGER.error("Could not save deckbox record {}", target, exception);
            try { Files.deleteIfExists(temporary); } catch (IOException ignored) {}
        }
    }

    public static synchronized Optional<Record> load(MinecraftServer server, HolderLookup.Provider registries, UUID id) {
        Path source = path(server, id);
        if (!Files.isRegularFile(source)) return Optional.empty();
        try {
            CompoundTag root = NbtIo.readCompressed(source, NbtAccounter.defaultQuota());
            NonNullList<ItemStack> items = NonNullList.withSize(DeckboxBlockEntity.INVENTORY_SIZE, ItemStack.EMPTY);
            var ops = RegistryOps.create(NbtOps.INSTANCE, registries);
            root.getList("Items").ifPresent(slots -> {
                for (int i = 0; i < slots.size(); i++) {
                    var entry = slots.getCompound(i);
                    if (entry.isEmpty()) continue;
                    int slot = entry.get().getInt("Slot").orElse(-1);
                    if (slot < 0 || slot >= items.size()) continue;
                    entry.get().getCompound("Stack").ifPresent(stackTag ->
                            ItemStack.CODEC.parse(ops, stackTag).resultOrPartial(message ->
                                    Mtgcard.LOGGER.error("Could not decode deckbox {} slot {}: {}", id, slot, message)
                            ).ifPresent(stack -> items.set(slot, stack)));
                }
            });
            return Optional.of(new Record(id, root.getString("Type").orElse(""),
                    root.getString("Name").orElse(""), root.getLong("LastAccessed").orElse(0L), items));
        } catch (IOException | RuntimeException exception) {
            Mtgcard.LOGGER.error("Could not load deckbox record {}", source, exception);
            return Optional.empty();
        }
    }
}
