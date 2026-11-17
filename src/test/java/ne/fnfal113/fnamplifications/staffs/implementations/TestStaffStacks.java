package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.StaffType;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;
import org.mockbukkit.mockbukkit.inventory.SimpleInventoryViewMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The listener that keeps staff pools right while players split, merge and drop staffs. */
class TestStaffStacks {

    private static final NamespacedKey KEY = new NamespacedKey("fnamplifications", "test_staff_uses");
    private static final StaffType TP = new StaffType("FN_STAFF_TP", KEY, 10);
    private static final StaffType FORCE = new StaffType("FN_STAFF_FORCE", new NamespacedKey("fnamplifications", "force"), 10);

    /** Raw slot 9 of the player's own inventory view is inventory slot 9. */
    private static final int SLOT = 9;

    private ServerMock server;
    private PlayerMock player;
    private StaffStacks listener;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
        listener = new StaffStacks(MockBukkit.createMockPlugin());
        StaffStacks.useTypes(item -> {
            if (item.getType() != Material.BLAZE_ROD) {
                return null;
            }
            String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
            return name.equals("Staff of Teleportation") ? TP : name.equals("Staff of Force") ? FORCE : null;
        });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack staffs(String name, int amount, int uses) {
        ItemStack staff = new ItemStack(Material.BLAZE_ROD, amount);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(ChatColor.RED + name);
        meta.setLore(List.of("", ChatColor.YELLOW + "Uses: " + ChatColor.YELLOW + "10 left"));
        staff.setItemMeta(meta);
        StaffUses.write(staff, name.endsWith("Force") ? FORCE : TP, uses);
        return staff;
    }

    private static ItemStack staffs(int amount, int uses) {
        return staffs("Staff of Teleportation", amount, uses);
    }

    private static int uses(ItemStack stack) {
        return StaffUses.usesLeft(stack, TP);
    }

    /** A view like the server's: raw slots of the top inventory first, then the player's inventory. */
    private final class View extends SimpleInventoryViewMock {
        View(Inventory top) {
            super(player, top, player.getInventory(), top.getType());
        }

        private int topSize() {
            return getTopInventory().getSize();
        }

        @Override
        public Inventory getInventory(int rawSlot) {
            return rawSlot < topSize() ? getTopInventory() : getBottomInventory();
        }

        @Override
        public int convertSlot(int rawSlot) {
            return rawSlot < topSize() ? rawSlot : rawSlot - topSize();
        }

        @Override
        public ItemStack getItem(int rawSlot) {
            return getInventory(rawSlot).getItem(convertSlot(rawSlot));
        }

        @Override
        public ItemStack getCursor() {
            return player.getItemOnCursor();
        }
    }

    /** A click on slot {@link #SLOT} of the player's own inventory. */
    private InventoryClickEvent click(InventoryAction action, ClickType type) {
        Inventory top = Bukkit.createInventory(null, 27);
        InventoryClickEvent event = new InventoryClickEvent(new View(top), InventoryType.SlotType.CONTAINER, top.getSize() + SLOT, type, action);
        listener.onClick(event);
        return event;
    }

    @Test
    @DisplayName("Placing staffs onto a used stack of the same type merges them and adds the pools")
    void placeMerges() {
        player.getInventory().setItem(SLOT, staffs(32, 320));
        player.setItemOnCursor(staffs(16, 153));

        InventoryClickEvent event = click(InventoryAction.SWAP_WITH_CURSOR, ClickType.LEFT);

        assertTrue(event.isCancelled(), "Minecraft must not swap them");
        ItemStack merged = player.getInventory().getItem(SLOT);
        assertEquals(48, merged.getAmount());
        assertEquals(473, uses(merged));
        assertTrue(player.getItemOnCursor().getType().isAir());
    }

    @Test
    @DisplayName("Right-click places one staff with its share of the pool")
    void placeOne() {
        player.getInventory().setItem(SLOT, staffs(3, 30));
        player.setItemOnCursor(staffs(10, 55));

        click(InventoryAction.PLACE_ONE, ClickType.RIGHT);

        assertEquals(4, player.getInventory().getItem(SLOT).getAmount());
        assertEquals(9, player.getItemOnCursor().getAmount());
        assertEquals(85, uses(player.getInventory().getItem(SLOT)) + uses(player.getItemOnCursor()));
    }

    @Test
    @DisplayName("Different staff types are not merged")
    void differentTypesSwap() {
        player.getInventory().setItem(SLOT, staffs("Staff of Force", 3, 30));
        player.setItemOnCursor(staffs(10, 55));

        InventoryClickEvent event = click(InventoryAction.SWAP_WITH_CURSOR, ClickType.LEFT);

        assertFalse(event.isCancelled());
        assertEquals(3, player.getInventory().getItem(SLOT).getAmount());
    }

    @Test
    @DisplayName("Creative mode keeps Minecraft's behaviour")
    void creativeUntouched() {
        player.setGameMode(GameMode.CREATIVE);
        player.getInventory().setItem(SLOT, staffs(3, 30));
        player.setItemOnCursor(staffs(10, 55));

        assertFalse(click(InventoryAction.SWAP_WITH_CURSOR, ClickType.LEFT).isCancelled());
    }

    @Test
    @DisplayName("Right-click splitting a used stack shares its pool on the next tick")
    void rightClickSplit() {
        ItemStack stack = staffs(32, 305);
        player.getInventory().setItem(SLOT, stack);

        click(InventoryAction.PICKUP_HALF, ClickType.RIGHT);
        // What Minecraft then does: both halves are copies
        ItemStack half = stack.clone();
        half.setAmount(16);
        player.setItemOnCursor(half.clone());
        player.getInventory().setItem(SLOT, half.clone());

        server.getScheduler().performOneTick();

        int cursor = uses(player.getItemOnCursor());
        int slot = uses(player.getInventory().getItem(SLOT));
        assertEquals(305, cursor + slot);
        assertEquals(153, cursor, "leftover uses go to the cursor");
    }

    @Test
    @DisplayName("Dropping part of a used stack takes its share of the pool with it")
    void dropTakesShare() {
        ItemStack stack = staffs(32, 305);
        ItemStack rest = stack.clone();
        rest.setAmount(31);
        player.getInventory().setItem(SLOT, rest);
        ItemStack one = stack.clone();
        one.setAmount(1);
        Item dropped = player.getWorld().dropItem(player.getLocation(), one);

        listener.onDrop(new PlayerDropItemEvent(player, dropped));

        assertEquals(305, uses(player.getInventory().getItem(SLOT)) + uses(dropped.getItemStack()));
        assertEquals(9, uses(dropped.getItemStack()));
    }
}
