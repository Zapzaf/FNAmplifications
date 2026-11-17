package ne.fnfal113.fnamplifications.staffs.implementations;

import io.github.thebusybiscuit.slimefun4.api.items.SlimefunItem;
import ne.fnfal113.fnamplifications.staffs.abstracts.AbstractStaff;
import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Slot;
import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.StaffType;
import ne.fnfal113.fnamplifications.utils.Utils;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.block.Container;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.Plugin;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * Keeps the shared pool of staff stacks right while players split, merge, drop and pick up staffs.
 * <ul>
 *     <li>After a player moves staffs around, the pools of split stacks are shared between the parts.</li>
 *     <li>Placing staffs onto a stack of the same type merges them and adds their pools together
 *     (Minecraft would swap them instead, because their uses differ).</li>
 *     <li>A dropped part of a stack takes its share of the pool with it.</li>
 * </ul>
 */
public final class StaffStacks implements Listener {

    private static final Set<InventoryAction> PLACING = EnumSet.of(
        InventoryAction.PLACE_ALL, InventoryAction.PLACE_SOME, InventoryAction.PLACE_ONE, InventoryAction.SWAP_WITH_CURSOR);

    private static final Set<InventoryType> PLAIN_CONTAINERS = EnumSet.of(
        InventoryType.CHEST, InventoryType.BARREL, InventoryType.SHULKER_BOX, InventoryType.ENDER_CHEST,
        InventoryType.DISPENSER, InventoryType.DROPPER, InventoryType.HOPPER);

    /** Looks up staff types; replaced in tests, which have no Slimefun. */
    private static Function<ItemStack, StaffType> types = StaffStacks::slimefunTypeOf;

    private final Plugin plugin;
    private final Set<UUID> pending = new HashSet<>();

    public StaffStacks(@Nonnull Plugin plugin) {
        this.plugin = plugin;
    }

    static void useTypes(@Nonnull Function<ItemStack, StaffType> lookup) {
        types = lookup;
    }

    /** The staff type of an item, or {@code null} if it is not a staff. */
    @Nullable
    public static StaffType typeOf(@Nullable ItemStack item) {
        return StaffUses.isEmpty(item) || !item.hasItemMeta() ? null : types.apply(item);
    }

    @Nullable
    private static StaffType slimefunTypeOf(@Nonnull ItemStack item) {
        return SlimefunItem.getByItem(item) instanceof AbstractStaff staff ? staff.getStaffTask().getType() : null;
    }

    /** Shares the pools of split staff stacks in the player's inventory, cursor and open container. */
    public static void resolve(@Nonnull Player player) {
        resolve(player, null);
    }

    private static void resolve(@Nonnull Player player, @Nullable Slot extra) {
        List<Slot> slots = slotsOf(player);
        if (extra != null) {
            slots.add(extra);
        }

        int broken = StaffUses.resolve(slots, StaffStacks::typeOf);
        if (broken > 0) {
            Utils.sendMessage("&7" + broken + (broken == 1 ? " staff" : " staffs") + " had no uses left in its part of the stack and broke", player);
        }
    }

    /** The cursor first, then the player's inventory, then the open container (if it is a plain container). */
    @Nonnull
    private static List<Slot> slotsOf(@Nonnull Player player) {
        List<Slot> slots = new ArrayList<>();
        slots.add(new Slot() {
            @Override
            public ItemStack get() {
                return player.getItemOnCursor();
            }

            @Override
            public void set(ItemStack item) {
                player.setItemOnCursor(item);
            }
        });

        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < 36; i++) {
            slots.add(slotOf(inventory, i));
        }
        slots.add(slotOf(inventory, 40)); // off hand

        Inventory top = player.getOpenInventory().getTopInventory();
        if (isPlainContainer(top)) {
            for (int i = 0; i < top.getSize(); i++) {
                slots.add(slotOf(top, i));
            }
        }
        return slots;
    }

    @Nonnull
    private static Slot slotOf(@Nonnull Inventory inventory, int index) {
        return new Slot() {
            @Override
            public ItemStack get() {
                return inventory.getItem(index);
            }

            @Override
            public void set(ItemStack item) {
                inventory.setItem(index, item);
            }
        };
    }

    private static boolean isPlainContainer(@Nullable Inventory inventory) {
        if (inventory == null || !PLAIN_CONTAINERS.contains(inventory.getType())) {
            return false;
        }
        if (inventory.getType() == InventoryType.ENDER_CHEST) {
            return true;
        }
        // Leaves Slimefun machine menus and other plugin menus alone
        InventoryHolder holder;
        try {
            holder = inventory.getHolder(false); // Paper: no block state snapshot
        } catch (UnsupportedOperationException e) {
            holder = inventory.getHolder();
        }
        return holder instanceof Container || holder instanceof DoubleChest;
    }

    /** Shares the pools after Minecraft finished moving the items, once per player and tick. */
    private void resolveNextTick(@Nonnull Player player) {
        if (!pending.add(player.getUniqueId())) {
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            pending.remove(player.getUniqueId());
            if (player.isOnline()) {
                resolve(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        boolean hotbarStaff = event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0
            && typeOf(player.getInventory().getItem(event.getHotbarButton())) != null;

        if (typeOf(cursor) == null && typeOf(current) == null && !hotbarStaff) {
            return;
        }

        if (player.getGameMode() != GameMode.CREATIVE && PLACING.contains(event.getAction()) && mergeOnto(event, player)) {
            event.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, player::updateInventory);
        }

        resolveNextTick(player);
    }

    /** Merges the staffs on the cursor into the clicked stack of the same type. */
    private boolean mergeOnto(@Nonnull InventoryClickEvent event, @Nonnull Player player) {
        Inventory clicked = event.getClickedInventory();
        if (clicked == null || !(clicked instanceof PlayerInventory || isPlainContainer(clicked))) {
            return false;
        }
        if (event.getSlotType() != InventoryType.SlotType.CONTAINER && event.getSlotType() != InventoryType.SlotType.QUICKBAR) {
            return false;
        }

        ItemStack cursor = event.getCursor();
        ItemStack stack = event.getCurrentItem();
        if (!sameType(cursor, stack) || cursor.isSimilar(stack)) {
            return false; // nothing to merge, or Minecraft stacks them on its own
        }

        // Get the pools right first, then read both stacks again
        resolve(player);
        cursor = player.getItemOnCursor();
        stack = clicked.getItem(event.getSlot());
        if (!sameType(cursor, stack)) {
            return true; // a part broke while sharing the pool; leave the rest as it is
        }
        StaffType type = typeOf(cursor);

        ItemStack[] merged = StaffUses.merge(cursor, stack, type, event.getAction() == InventoryAction.PLACE_ONE);
        if (merged == null) {
            return true; // the stack is full; don't swap
        }

        clicked.setItem(event.getSlot(), merged[0]);
        player.setItemOnCursor(merged[1]);
        return true;
    }

    private static boolean sameType(@Nullable ItemStack a, @Nullable ItemStack b) {
        StaffType typeA = typeOf(a);
        StaffType typeB = typeOf(b);
        return typeA != null && typeB != null && typeA.id().equals(typeB.id());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player && typeOf(event.getOldCursor()) != null) {
            resolveNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            resolve(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (typeOf(dropped.getItemStack()) == null) {
            return;
        }

        // The dropped part takes its share of the pool with it
        resolve(event.getPlayer(), new Slot() {
            @Override
            public ItemStack get() {
                return dropped.getItemStack();
            }

            @Override
            public void set(ItemStack item) {
                if (item == null) {
                    dropped.remove();
                } else {
                    dropped.setItemStack(item);
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player && typeOf(event.getItem().getItemStack()) != null) {
            resolveNextTick(player);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        resolve(event.getPlayer());
        pending.remove(event.getPlayer().getUniqueId());
    }
}
