package ne.fnfal113.fnamplifications.staffs.implementations;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import ne.fnfal113.fnamplifications.FNAmplifications;
import ne.fnfal113.fnamplifications.utils.Utils;

import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Objects;

public class StaffTask {

    private final NamespacedKey storageKey;

    private final String id;

    public StaffTask(NamespacedKey storageKey, String id) {
        this.storageKey = storageKey;
        this.id = id;
    }

    /**
     * this is used for updating the lore (uses left) when using the staffs
     * @param item the staff used
     * @param meta the item meta of the staff
     * @param player the player who used the staff
     */
    public void updateMeta(ItemStack item, ItemMeta meta, Player player) {
        int maxUses = getMaxUses();
        String name = meta.getDisplayName();

        // One staff is split off a stack and used; when it breaks, the next one moves into the hand.
        StaffUses.Result result = StaffUses.spendUseInMainHand(player, item, meta, getStorageKey(), maxUses, this::isSameStaff);

        if (result == StaffUses.Result.BROKE) {
            Utils.sendMessage(name + " &7has broken &8(&7out of uses&8)", player);

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1 ,1);
        }

        showUses(player, name);

        Objects.requireNonNull(player.getLocation().getWorld()).playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1, 1);
    }

    /** The uses a new staff of this type has, from staffs-settings. */
    public int getMaxUses() {
        return FNAmplifications.getInstance().getConfigManager().getCustomConfig("staffs-settings").getInt(this.getId() + "." + "max-uses");
    }

    /** Whether {@code item} is a staff of this type. */
    public boolean isSameStaff(ItemStack item) {
        SlimefunItem sfItem = SlimefunItem.getByItem(item);
        return sfItem != null && sfItem.getId().equals(getId());
    }

    /** Shows the uses left in all staffs of this type in the inventory, in the action bar of the player. */
    public void showUses(Player player, String staffName) {
        StaffUses.Totals totals = StaffUses.count(player.getInventory().getStorageContents(), this::isSameStaff, getStorageKey(), getMaxUses());
        if (totals.staffs() == 0) {
            return;
        }

        String bar = StaffUses.usesBar(staffName, totals);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(Utils.colorTranslator(bar)));
    }

    public NamespacedKey getStorageKey() {
        return storageKey;
    }

    public String getId() {
        return id;
    }

}