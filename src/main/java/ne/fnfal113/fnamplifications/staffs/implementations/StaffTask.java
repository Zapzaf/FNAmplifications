package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.FNAmplifications;
import ne.fnfal113.fnamplifications.utils.Utils;

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
        int maxUses = FNAmplifications.getInstance().getConfigManager().getCustomConfig("staffs-settings").getInt(this.getId() + "." + "max-uses");
        String name = meta.getDisplayName();

        // Only one staff of a stack breaks when the uses run out; the rest keep working.
        StaffUses.Result result = StaffUses.spendUseInMainHand(player, item, meta, getStorageKey(), maxUses);

        if (result == StaffUses.Result.BROKE) {
            Utils.sendMessage(name + " &d&lhas reached max uses!", player);

            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1 ,1);
        }

        Objects.requireNonNull(player.getLocation().getWorld()).playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1, 1);
    }

    public NamespacedKey getStorageKey() {
        return storageKey;
    }

    public String getId() {
        return id;
    }

}