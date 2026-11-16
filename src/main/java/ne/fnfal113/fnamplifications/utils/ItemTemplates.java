package ne.fnfal113.fnamplifications.utils;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.lang.reflect.Field;

/**
 * Access to the {@link ItemStack} a {@link SlimefunItem} is registered with.
 * <p>
 * On older Slimefun, {@code getItem()} returned that stack itself, and FNAmplifications edited it in place
 * (for example to fill in "Uses: N left" from the config). On the Zapzaf Slimefun fork, {@code getItem()}
 * returns a copy, so those edits were silently lost. Edits made to the stack returned here change the
 * item shown in the guide and given when crafting, as they did before.
 */
public final class ItemTemplates {

    private static final Field TEMPLATE;

    static {
        try {
            TEMPLATE = SlimefunItem.class.getDeclaredField("itemStackTemplate");
            TEMPLATE.setAccessible(true);
        } catch (NoSuchFieldException e) {
            throw new IllegalStateException("Unsupported Slimefun version: SlimefunItem.itemStackTemplate not found", e);
        }
    }

    private ItemTemplates() {}

    /** The registered item stack of {@code item}, not a copy. Edit it only while the item is being set up. */
    @Nonnull
    public static ItemStack of(@Nonnull SlimefunItem item) {
        try {
            return (ItemStack) TEMPLATE.get(item);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Cannot access the item template of " + item.getId(), e);
        }
    }
}
