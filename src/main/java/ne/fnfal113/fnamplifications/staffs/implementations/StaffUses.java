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
import java.util.function.Predicate;

/**
 * Counts down the uses of staffs.
 * <p>
 * The uses left are stored on the item, and Minecraft only stacks identical items, so <b>every staff in a
 * stack has the same uses left</b>. That keeps the counts exact however staffs are stacked or split:
 * <ul>
 *     <li>Using a stack splits one staff off. It stays in the hand and spends the use; the rest of the
 *     stack goes back into the inventory unchanged.</li>
 *     <li>When the staff in the hand breaks, the next staff of the same type (the most used one first)
 *     moves into the hand, so using a stack still feels like using one item.</li>
 *     <li>The uses shown in the action bar are counted over all staffs of that type in the inventory.</li>
 * </ul>
 */
public final class StaffUses {

    private static final String LORE_PREFIX = "Uses: ";

    public enum Result {
        /** A use was spent and the staff still has uses left. */
        USED,
        /** The last use was spent and the staff broke. */
        BROKE
    }

    private StaffUses() {}

    /**
     * Spends one use of a single staff, editing it in place. When the last use is spent, the staff's amount
     * drops to 0.
     *
     * @param staff a single staff (amount 1)
     * @param meta the item meta of {@code staff}, possibly with other pending changes; it is applied to the staff
     * @param key the key the uses left are stored under
     * @param maxUses the uses a new staff has
     * @return whether the staff still has uses left or broke
     */
    @Nonnull
    public static Result spendUse(@Nonnull ItemStack staff, @Nonnull ItemMeta meta, @Nonnull NamespacedKey key, int maxUses) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int left = pdc.getOrDefault(key, PersistentDataType.INTEGER, maxUses) - 1;

        if (left > 0) {
            pdc.set(key, PersistentDataType.INTEGER, left);
            Utils.setLoreByPdc(staff, meta, String.valueOf(left), LORE_PREFIX, "&e", "&e", " left");
            return Result.USED;
        }

        staff.setAmount(0);
        return Result.BROKE;
    }

    /**
     * Spends one use of the staff the player holds in their main hand.
     * <p>
     * If the hand holds a stack, one staff is split off and used, and the rest of the stack is put back into
     * the inventory (or dropped at the player's feet if it is full). If the staff breaks, the next staff that
     * {@code sameStaff} accepts moves into the hand.
     *
     * @param player the player using the staff
     * @param item the staff (stack) in the player's main hand
     * @param meta the item meta of {@code item}, possibly with other pending changes; only the used staff gets them
     * @param key the key the uses left are stored under
     * @param maxUses the uses a new staff has
     * @param sameStaff whether an item is a staff of the same type
     * @return whether the used staff still has uses left or broke
     */
    @Nonnull
    public static Result spendUseInMainHand(@Nonnull Player player, @Nonnull ItemStack item, @Nonnull ItemMeta meta,
                                            @Nonnull NamespacedKey key, int maxUses, @Nonnull Predicate<ItemStack> sameStaff) {
        PlayerInventory inventory = player.getInventory();

        // The untouched rest of the stack, without the pending changes in meta.
        ItemStack rest = null;
        if (item.getAmount() > 1) {
            rest = item.clone();
            rest.setAmount(item.getAmount() - 1);
        }

        ItemStack used = item.clone();
        used.setAmount(1);
        Result result = spendUse(used, meta, key, maxUses);

        inventory.setItemInMainHand(used.getAmount() > 0 ? used : null);

        if (rest != null) {
            ItemStack leftover = putInStorage(inventory, rest);
            if (leftover != null) {
                Location location = player.getLocation();
                player.getWorld().dropItemNaturally(location, leftover);
            }
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
     * If the main hand is empty, moves the most used staff of the same type from the inventory into it.
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

    /** The uses left on each staff of this stack (all staffs in a stack have the same). */
    public static int usesLeft(@Nonnull ItemStack staff, @Nonnull NamespacedKey key, int maxUses) {
        if (!staff.hasItemMeta()) {
            return maxUses;
        }
        return staff.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, maxUses);
    }

    /** The uses left in a stack: its size times the uses left on each staff. */
    public static int totalUsesLeft(@Nullable ItemStack stack, @Nonnull NamespacedKey key, int maxUses) {
        if (isEmpty(stack)) {
            return 0;
        }
        return stack.getAmount() * usesLeft(stack, key, maxUses);
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

    private static boolean isEmpty(@Nullable ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }
}
