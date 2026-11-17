package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.utils.Utils;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

/**
 * Counts the uses of staffs.
 * <p>
 * <b>A stack of staffs shares one pool of uses.</b> 64 new staffs with 10 uses each have 640/640. Using the
 * stack takes one use from the pool; the staffs only break when the whole pool is used up. Splitting a stack
 * shares its pool between the parts (305 uses split in half: 153 + 152), and merging stacks adds their pools.
 * <p>
 * Minecraft splits stacks on its own (right-click, drag, drop, hoppers) by <i>copying</i> the item, so both
 * parts first carry the pool of the whole stack. Each written pool therefore records the {@link #AMOUNT_KEY
 * number of staffs} it was written for and a {@link #ID_KEY unique id}. Parts with the same id came from the
 * same stack: {@link #resolve} shares the pool between them exactly when it sees all of them, and a part on its
 * own only ever counts its proportional share (rounded down). Copies can never add uses.
 * <p>
 * New staffs have no pool data, so they still stack with newly crafted staffs.
 */
public final class StaffUses {

    /** The uses left in the whole stack. */
    public static final NamespacedKey POOL_KEY = new NamespacedKey("fnamplifications", "staff_pool");
    /** The number of staffs the pool was written for. */
    public static final NamespacedKey AMOUNT_KEY = new NamespacedKey("fnamplifications", "staff_pool_amount");
    /** Unique per written pool, so the parts of a split stack can be found. */
    public static final NamespacedKey ID_KEY = new NamespacedKey("fnamplifications", "staff_pool_id");
    /** Marked worn stacks in v4.2.4.2, where the uses key held the uses of the top staff only. */
    static final NamespacedKey LEGACY_WORN_KEY = new NamespacedKey("fnamplifications", "staff_worn");

    private static final String LORE_PREFIX = "Uses: ";

    /** A type of staff: its Slimefun id, the key older versions stored uses under, and the uses of a new staff. */
    public record StaffType(@Nonnull String id, @Nonnull NamespacedKey legacyKey, int maxUses) {}

    /** A place an item can be read from and written to: an inventory slot, the cursor, a dropped item. */
    public interface Slot {
        @Nullable
        ItemStack get();

        void set(@Nullable ItemStack item);
    }

    public enum Result {
        /** A use was spent and the stack still has uses left. */
        USED,
        /** The last use was spent and the stack broke. */
        BROKE
    }

    private StaffUses() {}

    // ---- reading and writing ----

    /** The uses left in this stack. */
    public static int usesLeft(@Nullable ItemStack stack, @Nonnull StaffType type) {
        if (isEmpty(stack)) {
            return 0;
        }

        int amount = stack.getAmount();
        long full = (long) amount * type.maxUses();
        if (!stack.hasItemMeta()) {
            return (int) full;
        }

        PersistentDataContainer pdc = stack.getItemMeta().getPersistentDataContainer();
        long left;

        Integer pool = pdc.get(POOL_KEY, PersistentDataType.INTEGER);
        Integer legacy = pdc.get(type.legacyKey(), PersistentDataType.INTEGER);
        if (pool != null) {
            int writtenFor = pdc.getOrDefault(AMOUNT_KEY, PersistentDataType.INTEGER, amount);
            // A part of a split stack only counts its share of the pool
            left = writtenFor <= 0 ? 0 : (long) pool * amount / writtenFor;
        } else if (legacy != null && pdc.has(LEGACY_WORN_KEY, PersistentDataType.STRING)) {
            left = (long) (amount - 1) * type.maxUses() + legacy;
        } else if (legacy != null) {
            left = (long) amount * legacy;
        } else {
            left = full;
        }

        return (int) Math.max(0, Math.min(full, left));
    }

    /** The uses this stack would have if all its staffs were new. */
    public static int maxUses(@Nullable ItemStack stack, @Nonnull StaffType type) {
        return isEmpty(stack) ? 0 : stack.getAmount() * type.maxUses();
    }

    /** Sets the uses left in the whole stack and updates its lore. With no uses left, the stack's amount becomes 0. */
    public static void write(@Nonnull ItemStack stack, @Nonnull StaffType type, int uses) {
        write(stack, stack.getItemMeta(), type, uses);
    }

    /**
     * Sets the uses left in the whole stack and updates its lore, applying {@code meta} (which may hold other
     * pending changes) to the stack. With no uses left, the stack's amount becomes 0.
     */
    public static void write(@Nonnull ItemStack stack, @Nonnull ItemMeta meta, @Nonnull StaffType type, int uses) {
        int full = maxUses(stack, type);
        if (uses <= 0 || full <= 0) {
            stack.setAmount(0);
            return;
        }
        uses = Math.min(uses, full);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.remove(type.legacyKey());
        pdc.remove(LEGACY_WORN_KEY);

        if (uses == full) {
            // Like newly crafted staffs again
            pdc.remove(POOL_KEY);
            pdc.remove(AMOUNT_KEY);
            pdc.remove(ID_KEY);
        } else {
            pdc.set(POOL_KEY, PersistentDataType.INTEGER, uses);
            pdc.set(AMOUNT_KEY, PersistentDataType.INTEGER, stack.getAmount());
            pdc.set(ID_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
        }

        Utils.setLoreByPdc(stack, meta, uses + "/" + full, LORE_PREFIX, "&e", "&e", " left");
    }

    // ---- using ----

    /**
     * Spends one use from the stack's pool, editing it in place. When the pool is used up, the stack breaks
     * (its amount becomes 0).
     *
     * @param meta the item meta of {@code stack}, possibly with other pending changes; it is applied to the stack
     */
    @Nonnull
    public static Result spendUse(@Nonnull ItemStack stack, @Nonnull ItemMeta meta, @Nonnull StaffType type) {
        int left = usesLeft(stack, type) - 1;
        if (left <= 0) {
            stack.setAmount(0);
            return Result.BROKE;
        }

        write(stack, meta, type, left);
        return Result.USED;
    }

    /**
     * Spends one use of the staff stack in the player's main hand. If the stack breaks, the staff stack of the
     * same type with the fewest uses left moves into the hand.
     */
    @Nonnull
    public static Result spendUseInMainHand(@Nonnull Player player, @Nonnull ItemStack item, @Nonnull ItemMeta meta,
                                            @Nonnull StaffType type, @Nonnull Function<ItemStack, StaffType> types) {
        PlayerInventory inventory = player.getInventory();
        Result result = spendUse(item, meta, type);
        boolean broke = item.getAmount() <= 0;
        inventory.setItemInMainHand(broke ? null : item);

        if (broke) {
            refillMainHand(player, type, types);
        }
        return result;
    }

    /** If the main hand is empty, moves the staff stack of the same type with the fewest uses left into it. */
    static void refillMainHand(@Nonnull Player player, @Nonnull StaffType type, @Nonnull Function<ItemStack, StaffType> types) {
        PlayerInventory inventory = player.getInventory();
        if (!isEmpty(inventory.getItemInMainHand())) {
            return;
        }

        int heldSlot = inventory.getHeldItemSlot();
        ItemStack[] storage = inventory.getStorageContents();
        int best = -1;
        int bestLeft = Integer.MAX_VALUE;

        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack candidate = storage[slot];
            if (slot == heldSlot || !isType(candidate, type, types)) {
                continue;
            }

            int left = usesLeft(candidate, type);
            if (left < bestLeft) {
                best = slot;
                bestLeft = left;
            }
        }

        if (best >= 0) {
            inventory.setItemInMainHand(storage[best]);
            inventory.setItem(best, null);
        }
    }

    // ---- splitting and merging ----

    /**
     * Shares the pools of split stacks between their parts and brings staffs from older versions to the pool
     * format. Parts with the same id came from one stack: if all of them are in {@code slots}, the pool is
     * shared out exactly (leftover uses go to the first parts); otherwise each part gets its share rounded down.
     * A part that ends up with no uses breaks.
     *
     * @param slots the places to look at, for example a player's inventory and cursor; earlier slots get leftover uses first
     * @param types the staff type of an item, or {@code null} if it is not a staff
     * @return the number of staffs that broke because their part of the pool was empty
     */
    public static int resolve(@Nonnull List<? extends Slot> slots, @Nonnull Function<ItemStack, StaffType> types) {
        Map<String, List<Slot>> parts = new LinkedHashMap<>();

        for (Slot slot : slots) {
            ItemStack item = slot.get();
            StaffType type = isEmpty(item) ? null : types.apply(item);
            if (type == null) {
                continue;
            }

            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String id = pdc.get(ID_KEY, PersistentDataType.STRING);
            if (id != null && pdc.has(POOL_KEY, PersistentDataType.INTEGER)) {
                parts.computeIfAbsent(id, key -> new ArrayList<>()).add(slot);
            } else if (pdc.has(type.legacyKey(), PersistentDataType.INTEGER) || !showsUses(item, type)) {
                // A staff from an older version, or new staffs whose lore doesn't show this stack's uses yet
                write(item, type, usesLeft(item, type));
                slot.set(item);
            }
        }

        int broken = 0;
        for (List<Slot> group : parts.values()) {
            broken += share(group, types);
        }
        return broken;
    }

    private static int share(@Nonnull List<Slot> group, @Nonnull Function<ItemStack, StaffType> types) {
        ItemStack first = group.get(0).get();
        StaffType type = types.apply(first);
        PersistentDataContainer pdc = first.getItemMeta().getPersistentDataContainer();
        int pool = pdc.getOrDefault(POOL_KEY, PersistentDataType.INTEGER, 0);
        int writtenFor = pdc.getOrDefault(AMOUNT_KEY, PersistentDataType.INTEGER, first.getAmount());

        int seen = 0;
        for (Slot slot : group) {
            seen += slot.get().getAmount();
        }
        if (group.size() == 1 && seen == writtenFor) {
            return 0; // a whole stack, nothing to share
        }

        long divisor = Math.max(writtenFor, seen);
        int[] shares = new int[group.size()];
        int given = 0;
        for (int i = 0; i < shares.length; i++) {
            shares[i] = (int) ((long) pool * group.get(i).get().getAmount() / divisor);
            given += shares[i];
        }

        if (seen >= writtenFor) {
            // All parts are here: hand out the leftover uses, first to parts that would otherwise break
            int leftover = pool - given;
            for (int pass = 0; pass < 2 && leftover > 0; pass++) {
                for (int i = 0; i < shares.length && leftover > 0; i++) {
                    ItemStack part = group.get(i).get();
                    boolean wanted = pass == 1 || shares[i] == 0;
                    if (wanted && shares[i] < part.getAmount() * type.maxUses()) {
                        shares[i]++;
                        leftover--;
                    }
                }
            }
        }

        int broken = 0;
        for (int i = 0; i < shares.length; i++) {
            Slot slot = group.get(i);
            ItemStack part = slot.get();
            int amount = part.getAmount();
            write(part, type, shares[i]);
            if (part.getAmount() <= 0) {
                broken += amount;
                slot.set(null);
            } else {
                slot.set(part);
            }
        }
        return broken;
    }

    /**
     * Merges the staffs on the cursor into a stack of the same type: their pools are added together. Moves as
     * many staffs as fit (or one, if {@code onlyOne}), together with their share of the cursor's pool.
     *
     * @return the new stack and the new cursor (or {@code null} if no staffs are left on it),
     *         or {@code null} if nothing could be moved
     */
    @Nullable
    public static ItemStack[] merge(@Nonnull ItemStack cursor, @Nonnull ItemStack stack, @Nonnull StaffType type, boolean onlyOne) {
        int onCursor = cursor.getAmount();
        int moved = onlyOne ? 1 : Math.min(onCursor, stack.getMaxStackSize() - stack.getAmount());
        if (moved <= 0) {
            return null;
        }

        int cursorUses = usesLeft(cursor, type);
        int stackUses = usesLeft(stack, type);
        int movedUses = moved == onCursor ? cursorUses : (int) ((long) cursorUses * moved / onCursor);

        ItemStack merged = stack.clone();
        merged.setAmount(stack.getAmount() + moved);
        write(merged, type, stackUses + movedUses);

        ItemStack rest = null;
        if (moved < onCursor) {
            rest = cursor.clone();
            rest.setAmount(onCursor - moved);
            write(rest, type, cursorUses - movedUses);
            if (rest.getAmount() <= 0) {
                rest = null;
            }
        }

        return new ItemStack[] {merged, rest};
    }

    // ---- totals ----

    /** Uses left, full uses and number of staffs over several stacks. */
    public record Totals(int usesLeft, int maxUses, int staffs) {}

    /** Adds up the staffs of the given type among {@code items}. */
    @Nonnull
    public static Totals count(@Nonnull ItemStack[] items, @Nonnull StaffType type, @Nonnull Function<ItemStack, StaffType> types) {
        int left = 0;
        int staffs = 0;
        for (ItemStack it : items) {
            if (!isType(it, type, types)) {
                continue;
            }
            left += usesLeft(it, type);
            staffs += it.getAmount();
        }
        return new Totals(left, staffs * type.maxUses(), staffs);
    }

    /**
     * The action bar text, with legacy {@code &} color codes,
     * for example {@code "Staff of Teleportation: 127/128 uses (16 staffs)"}.
     */
    @Nonnull
    public static String usesBar(@Nonnull String staffName, @Nonnull Totals totals) {
        return staffName + "&7: &e" + totals.usesLeft() + "&7/&e" + totals.maxUses() + " &7uses"
                + (totals.staffs() > 1 ? " &8(" + totals.staffs() + " staffs)" : "");
    }

    /** Whether the lore of this stack shows its current uses, for example {@code "Uses: 640/640 left"}. */
    static boolean showsUses(@Nonnull ItemStack stack, @Nonnull StaffType type) {
        List<String> lore = stack.getItemMeta().getLore();
        String expected = Utils.colorTranslator("&e" + LORE_PREFIX + "&e" + usesLeft(stack, type) + "/" + maxUses(stack, type) + " left");
        return lore != null && lore.contains(expected);
    }

    private static boolean isType(@Nullable ItemStack item, @Nonnull StaffType type, @Nonnull Function<ItemStack, StaffType> types) {
        if (isEmpty(item)) {
            return false;
        }
        StaffType other = types.apply(item);
        return other != null && other.id().equals(type.id());
    }

    static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
