package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Result;
import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Totals;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Item;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStaffUses {

    private static final NamespacedKey KEY = new NamespacedKey("fnamplifications", "test_staff_uses");
    private static final int MAX = 10;
    private static final String TP = "&cStaff of Teleportation";
    private static final String FORCE = "&cStaff of Force";

    private ServerMock server;
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        player = server.addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    // ---- helpers ----

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** A staff as it comes out of crafting: full uses in the lore, nothing stored yet. */
    private static ItemStack newStaff(String name, int amount, int maxUses) {
        ItemStack staff = new ItemStack(Material.BLAZE_ROD, amount);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(List.of(color("&dRight click to cast"), "", color("&eUses: &e" + maxUses + " left")));
        staff.setItemMeta(meta);
        return staff;
    }

    private static ItemStack newStaff(int amount) {
        return newStaff(TP, amount, MAX);
    }

    /** A staff that has been used down to {@code left} uses, made by really using it. */
    private ItemStack wornStaff(int left) {
        ItemStack staff = newStaff(1);
        for (int i = MAX; i > left; i--) {
            StaffUses.spendUse(staff, staff.getItemMeta(), KEY, MAX);
        }
        return staff;
    }

    private static Predicate<ItemStack> sameAs(String name) {
        String colored = color(name);
        return it -> it != null && it.getType() == Material.BLAZE_ROD && it.hasItemMeta()
                && colored.equals(it.getItemMeta().getDisplayName());
    }

    private Result use() {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return StaffUses.spendUseInMainHand(player, hand, hand.getItemMeta(), KEY, MAX, sameAs(TP));
    }

    private ItemStack hand() {
        return player.getInventory().getItemInMainHand();
    }

    private Totals totals() {
        return StaffUses.count(player.getInventory().getStorageContents(), sameAs(TP), KEY, MAX);
    }

    private String bar() {
        return ChatColor.stripColor(color(StaffUses.usesBar(color(TP), totals())));
    }

    private static String usesLine(ItemStack item) {
        return ChatColor.stripColor(item.getItemMeta().getLore().get(2));
    }

    // ---- the reported bug ----

    @Test
    @DisplayName("Two staffs with 1 use each, stacked, have 2 uses in total (not 11) and give exactly 2")
    void twoWornStaffsStacked() {
        ItemStack a = wornStaff(1);
        ItemStack b = wornStaff(1);
        assertTrue(a.isSimilar(b), "two staffs with the same uses left stack");

        ItemStack stack = a.clone();
        stack.setAmount(2);
        player.getInventory().setItemInMainHand(stack);

        assertEquals(new Totals(2, 20, 2), totals());
        assertEquals("Staff of Teleportation: 2/20 uses (2 staffs)", bar());

        assertEquals(Result.BROKE, use());
        assertEquals(new Totals(1, 10, 1), totals(), "one staff with 1 use left, not 10");
        assertEquals("Uses: 1 left", usesLine(hand()));

        assertEquals(Result.BROKE, use());
        assertEquals(0, totals().staffs());
        assertTrue(hand().getType().isAir());
    }

    // ---- single staffs ----

    @Test
    @DisplayName("Using a staff counts its uses down")
    void countsDown() {
        player.getInventory().setItemInMainHand(newStaff(1));

        assertEquals(Result.USED, use());

        assertEquals("Uses: 9 left", usesLine(hand()));
        assertEquals(9, hand().getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER));
    }

    @Test
    @DisplayName("A single staff breaks on its last use")
    void singleStaffBreaks() {
        player.getInventory().setItemInMainHand(newStaff(1));

        for (int i = 1; i < MAX; i++) {
            assertEquals(Result.USED, use(), "use " + i);
        }
        assertEquals(Result.BROKE, use());

        assertTrue(hand().getType().isAir());
    }

    // ---- stacks ----

    @Test
    @DisplayName("Using a stack splits one staff off; the rest go back to the inventory unused")
    void usingStackSplitsOneOff() {
        player.getInventory().setItemInMainHand(newStaff(16));

        use();

        assertEquals(1, hand().getAmount());
        assertEquals("Uses: 9 left", usesLine(hand()));

        ItemStack rest = findOther(sameAs(TP));
        assertEquals(15, rest.getAmount());
        assertTrue(rest.isSimilar(newStaff(1)), "the rest is still like newly crafted staffs");

        assertEquals(new Totals(159, 160, 16), totals());
    }

    @Test
    @DisplayName("When the staff in hand breaks, the next one moves into the hand")
    void refillsHandAfterBreak() {
        player.getInventory().setItemInMainHand(newStaff(3));

        for (int i = 0; i < MAX; i++) {
            use();
        }

        assertEquals(Material.BLAZE_ROD, hand().getType());
        assertEquals(2, hand().getAmount());
        assertEquals(new Totals(20, 20, 2), totals());
    }

    @Test
    @DisplayName("The refill picks the most used staff first")
    void refillPrefersMostUsed() {
        player.getInventory().setItemInMainHand(wornStaff(1));
        player.getInventory().setItem(10, newStaff(5));
        player.getInventory().setItem(20, wornStaff(3));

        assertEquals(Result.BROKE, use());

        assertEquals("Uses: 3 left", usesLine(hand()));
        assertEquals(1, hand().getAmount());
    }

    @ParameterizedTest(name = "{0} new staffs with {1} uses each give exactly {2} uses")
    @CsvSource({"1, 10, 10", "3, 10, 30", "64, 10, 640", "5, 1, 5", "16, 8, 128"})
    @DisplayName("All staffs together give exactly their total uses")
    void wholeStackGivesExactUses(int amount, int maxUses, int expected) {
        player.getInventory().setItemInMainHand(newStaff(TP, amount, maxUses));

        int uses = 0;
        while (!hand().getType().isAir()) {
            int before = StaffUses.count(player.getInventory().getStorageContents(), sameAs(TP), KEY, maxUses).usesLeft();
            ItemStack h = hand();
            StaffUses.spendUseInMainHand(player, h, h.getItemMeta(), KEY, maxUses, sameAs(TP));
            uses++;
            int after = StaffUses.count(player.getInventory().getStorageContents(), sameAs(TP), KEY, maxUses).usesLeft();
            assertEquals(before - 1, after, "each use takes exactly one off the total");
            assertTrue(uses <= expected, "gave more uses than the staffs have");
        }

        assertEquals(expected, uses);
    }

    @Test
    @DisplayName("Worn staffs stacked together give exactly their combined uses")
    void wornStackGivesExactUses() {
        ItemStack stack = wornStaff(4);
        stack.setAmount(3);
        player.getInventory().setItemInMainHand(stack);

        assertEquals(new Totals(12, 30, 3), totals());

        int uses = 0;
        while (!hand().getType().isAir()) {
            use();
            uses++;
        }
        assertEquals(12, uses);
    }

    @Test
    @DisplayName("Staffs with different uses left don't stack, so they can't be merged into wrong counts")
    void differentUsesDontStack() {
        assertFalse(wornStaff(9).isSimilar(newStaff(1)));
        assertFalse(wornStaff(1).isSimilar(wornStaff(2)));
    }

    // ---- action bar ----

    @Test
    @DisplayName("A full stack of 16 staffs with 8 uses shows 128/128")
    void barFullStack() {
        player.getInventory().setItemInMainHand(newStaff(TP, 16, 8));

        Totals t = StaffUses.count(player.getInventory().getStorageContents(), sameAs(TP), KEY, 8);
        assertEquals("Staff of Teleportation: 128/128 uses (16 staffs)", ChatColor.stripColor(color(StaffUses.usesBar(color(TP), t))));
    }

    @Test
    @DisplayName("The action bar counts all staffs of that type in the inventory, and only that type")
    void barCountsWholeInventory() {
        player.getInventory().setItemInMainHand(wornStaff(7));
        player.getInventory().setItem(9, newStaff(4));
        player.getInventory().setItem(30, newStaff(FORCE, 5, MAX));

        assertEquals(new Totals(47, 50, 5), totals());
        assertEquals("Staff of Teleportation: 47/50 uses (5 staffs)", bar());
    }

    @Test
    @DisplayName("A single staff shows no staff count")
    void barSingleStaff() {
        player.getInventory().setItemInMainHand(newStaff(1));
        assertEquals("Staff of Teleportation: 10/10 uses", bar());
    }

    // ---- edge cases ----

    @Test
    @DisplayName("If the inventory is full, the rest of the stack drops at the player's feet")
    void fullInventoryDropsRest() {
        for (int slot = 0; slot < 36; slot++) {
            player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
        }
        player.getInventory().setItemInMainHand(newStaff(5));

        use();

        assertEquals(1, hand().getAmount());
        List<Item> dropped = List.copyOf(player.getWorld().getEntitiesByClass(Item.class));
        assertEquals(1, dropped.size());
        assertEquals(4, dropped.get(0).getItemStack().getAmount());
        assertTrue(dropped.get(0).getItemStack().isSimilar(newStaff(1)));
    }

    @Test
    @DisplayName("Staffs from before the lore fix ('Uses: left') get their lore fixed on use")
    void fixesOldLore() {
        ItemStack staff = newStaff(1);
        ItemMeta meta = staff.getItemMeta();
        meta.setLore(List.of(color("&dRight click to cast"), "", color("&eUses: &eleft")));
        staff.setItemMeta(meta);
        player.getInventory().setItemInMainHand(staff);

        use();

        assertEquals("Uses: 9 left", usesLine(hand()));
    }

    @Test
    @DisplayName("Other changes the staff made go to the used staff only, not to the rest of the stack")
    void pendingMetaGoesToUsedStaffOnly() {
        player.getInventory().setItemInMainHand(newStaff(3));
        NamespacedKey stored = new NamespacedKey("fnamplifications", "stored_entity");

        ItemStack h = hand();
        ItemMeta meta = h.getItemMeta();
        meta.getPersistentDataContainer().set(stored, PersistentDataType.STRING, "zombie");
        StaffUses.spendUseInMainHand(player, h, meta, KEY, MAX, sameAs(TP));

        assertEquals("zombie", hand().getItemMeta().getPersistentDataContainer().get(stored, PersistentDataType.STRING));
        ItemStack rest = findOther(sameAs(TP));
        assertFalse(rest.getItemMeta().getPersistentDataContainer().has(stored, PersistentDataType.STRING));
    }

    @Test
    @DisplayName("Other staff types are never moved into the hand")
    void otherTypesNotRefilled() {
        player.getInventory().setItemInMainHand(wornStaff(1));
        player.getInventory().setItem(5, newStaff(FORCE, 4, MAX));

        assertEquals(Result.BROKE, use());

        assertTrue(hand().getType().isAir());
        assertEquals(4, player.getInventory().getItem(5).getAmount());
    }

    /** The first stack accepted by {@code filter} outside the held slot. */
    private ItemStack findOther(Predicate<ItemStack> filter) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            if (slot != player.getInventory().getHeldItemSlot() && storage[slot] != null && filter.test(storage[slot])) {
                return storage[slot];
            }
        }
        throw new AssertionError("no other staff stack in the inventory");
    }
}
