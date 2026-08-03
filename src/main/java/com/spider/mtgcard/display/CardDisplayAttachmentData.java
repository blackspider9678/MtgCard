package com.spider.mtgcard.display;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class CardDisplayAttachmentData {
    public static final int MAX_ATTACHMENTS = 16;

    private static final String ATTACHMENTS_KEY = "mtg_display_attachments";
    private static final String ENTRY_ID_KEY = "Id";
    private static final String ENTRY_STACK_KEY = "Stack";
    private static final String ENTRY_ROT_KEY = "Rot";

    public record Attachment(UUID id, ItemStack stack, int rotStep) {
        public Attachment {
            id = id == null ? UUID.randomUUID() : id;
            stack = normalizeCardStack(stack);
            rotStep &= 1;
        }

        public Attachment withRotStep(int nextRotStep) {
            return new Attachment(id, stack, nextRotStep);
        }

        public Attachment withStack(ItemStack nextStack) {
            return new Attachment(id, nextStack, rotStep);
        }
    }

    public static List<Attachment> readAttachments(ItemStack hostStack) {
        if (hostStack == null || hostStack.isEmpty()) {
            return List.of();
        }

        CompoundTag root = readCustom(hostStack);
        ListTag list = root.getList(ATTACHMENTS_KEY).orElse(null);
        if (list == null || list.isEmpty()) {
            return List.of();
        }

        ArrayList<Attachment> out = new ArrayList<>(Math.min(list.size(), MAX_ATTACHMENTS));
        for (int i = 0; i < list.size() && out.size() < MAX_ATTACHMENTS; i++) {
            CompoundTag entry = list.getCompound(i).orElse(null);
            if (entry == null) {
                continue;
            }

            Tag encodedStack = entry.get(ENTRY_STACK_KEY);
            if (encodedStack == null) {
                continue;
            }

            ItemStack stack = ItemStack.CODEC.parse(NbtOps.INSTANCE, encodedStack)
                    .result()
                    .orElse(ItemStack.EMPTY);
            if (stack.isEmpty()) {
                continue;
            }

            UUID id = parseUuid(entry.getString(ENTRY_ID_KEY).orElse(""));
            int rotStep = entry.getIntOr(ENTRY_ROT_KEY, 0);
            out.add(new Attachment(id, stack, rotStep));
        }

        return List.copyOf(out);
    }

    public static void writeAttachments(ItemStack hostStack, List<Attachment> attachments) {
        if (hostStack == null || hostStack.isEmpty()) {
            return;
        }

        CompoundTag root = readCustom(hostStack);
        if (attachments == null || attachments.isEmpty()) {
            root.remove(ATTACHMENTS_KEY);
            writeCustom(hostStack, root);
            return;
        }

        ListTag list = new ListTag();
        for (Attachment attachment : attachments) {
            if (attachment == null || attachment.stack().isEmpty()) {
                continue;
            }
            if (list.size() >= MAX_ATTACHMENTS) {
                break;
            }

            encodeStack(NbtOps.INSTANCE, normalizeCardStack(attachment.stack())).result().ifPresent(encoded -> {
                CompoundTag entry = new CompoundTag();
                entry.putString(ENTRY_ID_KEY, attachment.id().toString());
                entry.putInt(ENTRY_ROT_KEY, attachment.rotStep() & 1);
                entry.put(ENTRY_STACK_KEY, encoded);
                list.add(entry);
            });
        }

        if (list.isEmpty()) {
            root.remove(ATTACHMENTS_KEY);
        } else {
            root.put(ATTACHMENTS_KEY, list);
        }
        writeCustom(hostStack, root);
    }

    public static ItemStack stripDisplayData(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stack.copy();
        CompoundTag root = readCustom(copy);
        if (root.contains(ATTACHMENTS_KEY)) {
            root.remove(ATTACHMENTS_KEY);
            writeCustom(copy, root);
        }
        return copy;
    }

    public static ItemStack normalizeCardStack(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack copy = stripDisplayData(stack);
        copy.setCount(1);
        return copy;
    }

    public static int attachmentCount(ItemStack hostStack) {
        return readAttachments(hostStack).size();
    }

    private static <T> DataResult<T> encodeStack(DynamicOps<T> ops, ItemStack stack) {
        return ItemStack.CODEC.encodeStart(ops, stack);
    }

    private static CompoundTag readCustom(ItemStack stack) {
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        return comp == null ? new CompoundTag() : comp.copyTag();
    }

    private static void writeCustom(ItemStack stack, CompoundTag tag) {
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
    }

    private static UUID parseUuid(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return UUID.fromString(raw);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return UUID.randomUUID();
    }

    private CardDisplayAttachmentData() {
    }
}
