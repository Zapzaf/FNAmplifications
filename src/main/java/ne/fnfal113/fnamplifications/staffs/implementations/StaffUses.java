package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.utils.Utils;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Counts down the uses of staffs.
 * <p>
 * A stack of staffs stays in one slot while it is used: only the <b>top staff</b> of the stack is worn down,
 * and the staffs below it are full. A stack of 16 staffs with 8 uses whose top staff has 5 uses left has
 * {@code 15 × 8 + 5 = 125} uses left. When the top staff breaks, the stack is one staff smaller and is again
 * exactly like newly crafted staffs.
 * <p>
 * Minecraft stacks items that are identical, so a worn staff must never stack with another staff: two staffs
 * with 1 use left each would become "a full staff with a worn one on top", which is far more than 2 uses.
 * Every worn stack therefore gets a random {@link #WORN_KEY tag}, so it only ever stacks with its own halves.
 * Splitting a worn stack copies the worn top staff into both halves, which only loses uses, and putting the
 * halves back together restores them.
 * <p>
 * Stacks from earlier versions, where every staff had the same uses left, have no tag. They keep their exact
 * uses: one staff is split off to be used, as before.
 */
public final class StaffUses {

    /** Marks a stack whose top staff is worn; the value is unique per stack. */
    public static final NamespacedKey WORN_KEY = new NamespacedKey("fnamplifications", "staff_worn");

    private static final String LORE_PREFIX = "Uses: ";

    public enum Result {
        /** A use was spent and the staff still has uses left. */
        USED,
        /** The last use of the top staff was spent and it broke. */
        BROKE
    }

    private StaffUses() {}

    /**
     * Spends one use of the top staff of {@code stack}, editing it in place. When the top staff breaks, the
     * stack shrinks by one (to 0 for a single staff) and the rest are like newly crafted staffs.
     * <p>
     * Only for single staffs and stacks of this version ({@link #isLegacyWornStack} is {@code false}).
     *
     * @param stack the staff stack
     * @param meta the item meta of {@code stack}, possibly with other pending changes; it is applied to the stack
     * @param key the key the uses left are stored under
     * @param maxUses the uses a new staff has
     * @return whether the top staff still has uses left or broke
     */
    @Nonnull
    public static Result spendUse(@Nonnull ItemStack stack, @Nonnull ItemMeta meta, @Nonnull NamespacedKey key, int maxUses) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int left = pdc.getOrDefault(key, PersistentDataType.INTEGER, maxUses) - 1;

        if (left > 0) {
            pdc.set(key, PersistentDataType.INTEGER, left);
            if (!pdc.has(WORN_KEY, PersistentDataType.STRING)) {
                pdc.set(WORN_KEY, PersistentDataType.STRING, UUID.randomUUID().toString());
            }
            Utils.setLoreByPdc(stack, meta, String.valueOf(left), LORE_PREFIX, "&e", "&e", " left");
            return Result.USED;
        }

        int rest = stack.getAmount() - 1;
        if (rest > 0) {
            // The next staff is full: make the stack like newly crafted staffs again, so it stacks with them.
            pdc.remove(key);
            pdc.remove(WORN_KEY);
            Utils.setLoreByPdc(stack, meta, String.valueOf(maxUses), LORE_PREFIX, "&e", "&e", " left");
        }
        stack.setAmount(rest);
        return Result.BROKE;
    }

    /**
     * Spends one use of the staff (stack) the player holds in their main hand. The stack stays in the hand;
     * if it is used up, the next staff that {@code sameStaff} accepts moves into the hand.
     *
     * @param player the player using the staff
     * @param item the staff (stack) in the player's main hand
     * @param meta the item meta of {@code item}, possibly with other pending changes
     * @param key the key the uses left are stored under
     * @param maxUses the uses a new staff has
     * @param sameStaff whether an item is a staff of the same type
     * @return whether the top staff still has uses left or broke
     */
    @Nonnull
    public static Result spendUseInMainHand(@Nonnull Player player, @Nonnull ItemStack item, @Nonnull ItemMeta meta,
                                            @Nonnull NamespacedKey key, int maxUses, @Nonnull Predicate<ItemStack> sameStaff) {
        if (isLegacyWornStack(item, key, maxUses)) {
            return spendUseOfLegacyStack(player, item, meta, key, maxUses, sameStaff);
        }

        PlayerInventory inventory = player.getInventory();
        Result result = spendUse(item, meta, key, maxUses);
        boolean usedUp = item.getAmount() <= 0;
        inventory.setItemInMainHand(usedUp ? null : item);

        if (usedUp) {
            refillMainHand(player, key, maxUses, sameStaff);
        }
        return result;
    }

    /**
     * Whether this is a stack from an earlier version in which every staff is worn by the same amount.
     * It cannot be worn down from the top without changing its uses, so one staff is split off instead.
     */
    static boolean isLegacyWornStack(@Nonnull ItemStack stack, @Nonnull NamespacedKey key, int maxUses) {
        return stack.getAmount() > 1 && !isTagged(stack) && usesLeft(stack, key, maxUses) < maxUses;
    }

    @Nonnull
    private static Result spendUseOfLegacyStack(@Nonnull Player player, @Nonnull ItemStack item, @Nonnull ItemMeta meta,
                                                @Nonnull NamespacedKey key, int maxUses, @Nonnull Predicate<ItemStack> sameStaff) {
        PlayerInventory inventory = player.getInventory();

        // The untouched rest of the stack, without the pending changes in meta.
        ItemStack rest = item.clone();
        rest.setAmount(item.getAmount() - 1);

        ItemStack used = item.clone();
        used.setAmount(1);
        Result result = spendUse(used, meta, key, maxUses);

        inventory.setItemInMainHand(used.getAmount() > 0 ? used : null);

        ItemStack leftover = putInStorage(inventory, rest);
        if (leftover != null) {
            Location location = player.getLocation();
            player.getWorld().dropItemNaturally(location, leftover);
        }

        if (result == Result.BROKE) {
            refillMainHand(player, key, maxUses, sameStaff);
        }
        return result;
    }

    /**
     * Puts {@code item} into the main inventory (hotbar and the 27 slots above it, never armor or offhand):
     * first onto matching stacks, then into empty slots.
     *
     * @return what did not fit, or {@code null}
     */
    @Nullable
    static ItemStack putInStorage(@Nonnull PlayerInventory inventory, @Nonnull ItemStack item) {
        ItemStack remaining = item.clone();
        ItemStack[] storage = inventory.getStorageContents();
        int maxStack = remaining.getMaxStackSize();

        for (int slot = 0; slot < storage.length && remaining.getAmount() > 0; slot++) {
            ItemStack existing = storage[slot];
            if (!isEmpty(existing) && existing.isSimilar(remaining) && existing.getAmount() < maxStack) {
                int moved = Math.min(maxStack - existing.getAmount(), remaining.getAmount());
                existing.setAmount(existing.getAmount() + moved);
                inventory.setItem(slot, existing);
                remaining.setAmount(remaining.getAmount() - moved);
            }
        }

        for (int slot = 0; slot < storage.length && remaining.getAmount() > 0; slot++) {
            if (isEmpty(inventory.getItem(slot))) {
                ItemStack placed = remaining.clone();
                placed.setAmount(Math.min(maxStack, remaining.getAmount()));
                inventory.setItem(slot, placed);
                remaining.setAmount(remaining.getAmount() - placed.getAmount());
            }
        }

        return remaining.getAmount() > 0 ? remaining : null;
    }

    /**
     * If the main hand is empty, moves the most worn staff stack of the same type from the inventory into it.
     */
    static void refillMainHand(@Nonnull Player player, @Nonnull NamespacedKey key, int maxUses, @Nonnull Predicate<ItemStack> sameStaff) {
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
            if (slot == heldSlot || isEmpty(candidate) || !sameStaff.test(candidate)) {
                continue;
            }

            int left = usesLeft(candidate, key, maxUses);
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

    /** The uses left on the top staff of this stack. */
    public static int usesLeft(@Nonnull ItemStack staff, @Nonnull NamespacedKey key, int maxUses) {
        if (!staff.hasItemMeta()) {
            return maxUses;
        }
        return staff.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, maxUses);
    }

    /**
     * The uses left in a stack: the top staff's uses plus full uses for every staff below it
     * (for a stack from an earlier version: its size times the uses left on each staff).
     */
    public static int totalUsesLeft(@Nullable ItemStack stack, @Nonnull NamespacedKey key, int maxUses) {
        if (isEmpty(stack)) {
            return 0;
        }

        int top = usesLeft(stack, key, maxUses);
        if (isTagged(stack)) {
            return (stack.getAmount() - 1) * maxUses + top;
        }
        return stack.getAmount() * top;
    }

    /** Uses left, full uses and number of staffs over several stacks. */
    public record Totals(int usesLeft, int maxUses, int staffs) {}

    /** Adds up the staffs that {@code sameStaff} accepts among {@code items}. */
    @Nonnull
    public static Totals count(@Nonnull ItemStack[] items, @Nonnull Predicate<ItemStack> sameStaff, @Nonnull NamespacedKey key, int maxUses) {
        int left = 0;
        int staffs = 0;
        for (ItemStack it : items) {
            if (isEmpty(it) || !sameStaff.test(it)) {
                continue;
            }
            left += totalUsesLeft(it, key, maxUses);
            staffs += it.getAmount();
        }
        return new Totals(left, staffs * maxUses, staffs);
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

    private static boolean isTagged(@Nonnull ItemStack stack) {
        return stack.hasItemMeta() && stack.getItemMeta().getPersistentDataContainer().has(WORN_KEY, PersistentDataType.STRING);
    }

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
