package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.utils.Utils;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import javax.annotation.Nonnull;

/**
 * Counts down the uses of a staff (or a stack of identical staffs).
 * <p>
 * The uses left are stored on the item stack, so a stack of staffs shares one counter. When the
 * last use is spent, only one staff from the stack breaks. The rest of the stack goes back to
 * full uses, the same as freshly crafted staffs, so they still stack with them.
 */
public final class StaffUses {

    private static final String LORE_PREFIX = "Uses: ";

    public enum Result {
        /** A use was spent and the staff still has uses left. */
        USED,
        /** The last use was spent and one staff from the stack broke. */
        BROKE
    }

    private StaffUses() {}

    /**
     * Spends one use of the staff stack {@code item}, editing it in place.
     *
     * @param item the staff stack being used
     * @param meta the item meta of {@code item}, possibly with other pending changes; it is applied to the item
     * @param key the key the uses left are stored under
     * @param maxUses the uses a new staff has
     * @return whether the staff still has uses left or one staff broke
     */
    @Nonnull
    public static Result spendUse(@Nonnull ItemStack item, @Nonnull ItemMeta meta, @Nonnull NamespacedKey key, int maxUses) {
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        int left = pdc.getOrDefault(key, PersistentDataType.INTEGER, maxUses) - 1;

        if (left > 0) {
            pdc.set(key, PersistentDataType.INTEGER, left);
            setUsesLore(item, meta, left);
            return Result.USED;
        }

        if (item.getAmount() > 1) {
            // The remaining staffs in the stack are unused: reset them to a new staff's state.
            pdc.remove(key);
            setUsesLore(item, meta, maxUses);
        }

        item.setAmount(item.getAmount() - 1);
        return Result.BROKE;
    }

    /**
     * Spends one use of the staff stack the player is holding in their main hand, and puts the result
     * back in that hand (nothing, if the last staff broke).
     *
     * @see #spendUse(ItemStack, ItemMeta, NamespacedKey, int)
     */
    @Nonnull
    public static Result spendUseInMainHand(@Nonnull Player player, @Nonnull ItemStack item, @Nonnull ItemMeta meta, @Nonnull NamespacedKey key, int maxUses) {
        Result result = spendUse(item, meta, key, maxUses);
        player.getInventory().setItemInMainHand(item.getAmount() > 0 ? item : null);
        return result;
    }

    /** The uses left on the staff currently in use (the one at the top of the stack). */
    public static int usesLeftOnCurrent(@Nonnull ItemStack item, @Nonnull NamespacedKey key, int maxUses) {
        if (!item.hasItemMeta()) {
            return maxUses;
        }
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(key, PersistentDataType.INTEGER, maxUses);
    }

    /** The uses left in the whole stack: every untouched staff's uses plus what is left on the current one. */
    public static int totalUsesLeft(@Nonnull ItemStack item, @Nonnull NamespacedKey key, int maxUses) {
        if (item.getType().isAir() || item.getAmount() <= 0) {
            return 0;
        }
        return (item.getAmount() - 1) * maxUses + usesLeftOnCurrent(item, key, maxUses);
    }

    /** The uses a full stack of this size has. */
    public static int totalUses(@Nonnull ItemStack item, int maxUses) {
        if (item.getType().isAir()) {
            return 0;
        }
        return item.getAmount() * maxUses;
    }

    /**
     * The action bar text for a staff stack, with legacy {@code &} color codes,
     * for example {@code "Staff of Teleportation: 127/128 uses (16 staffs)"}.
     */
    @Nonnull
    public static String usesBar(@Nonnull String staffName, @Nonnull ItemStack item, @Nonnull NamespacedKey key, int maxUses) {
        int amount = item.getAmount();
        return staffName + "&7: &e" + totalUsesLeft(item, key, maxUses) + "&7/&e" + totalUses(item, maxUses) + " &7uses"
                + (amount > 1 ? " &8(" + amount + " staffs)" : "");
    }

    private static void setUsesLore(ItemStack item, ItemMeta meta, int uses) {
        Utils.setLoreByPdc(item, meta, String.valueOf(uses), LORE_PREFIX, "&e", "&e", " left");
    }
}
