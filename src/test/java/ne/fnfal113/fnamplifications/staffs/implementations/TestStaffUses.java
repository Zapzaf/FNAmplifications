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
import org.junit.jupiter.api.Nested;
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

    /** A single staff that has been used down to {@code left} uses, made by really using it. */
    private ItemStack wornStaff(int left) {
        ItemStack staff = newStaff(1);
        for (int i = MAX; i > left; i--) {
            StaffUses.spendUse(staff, staff.getItemMeta(), KEY, MAX);
        }
        return staff;
    }

    /** A stack as the previous version made it: every staff worn to {@code left}, no worn tag. */
    private static ItemStack legacyStack(int amount, int left) {
        ItemStack staff = newStaff(amount);
        ItemMeta meta = staff.getItemMeta();
        meta.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER, left);
        meta.setLore(List.of(color("&dRight click to cast"), "", color("&eUses: &e" + left + " left")));
        staff.setItemMeta(meta);
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

    private void useUntilEmpty() {
        while (!hand().getType().isAir()) {
            use();
        }
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

    /** How many inventory slots hold a staff of the given type. */
    private int slotsWith(String name) {
        int slots = 0;
        for (ItemStack it : player.getInventory().getStorageContents()) {
            if (it != null && !it.getType().isAir() && sameAs(name).test(it)) {
                slots++;
            }
        }
        return slots;
    }

    // ---- one slot ----

    @Nested
    @DisplayName("A stack stays in one slot")
    class OneSlot {

        @Test
        @DisplayName("Using a stack of 16 keeps all 16 in the hand: 159/160")
        void stackStaysInHand() {
            player.getInventory().setItemInMainHand(newStaff(16));

            use();

            assertEquals(16, hand().getAmount());
            assertEquals(1, slotsWith(TP));
            assertEquals("Uses: 9 left", usesLine(hand()));
            assertEquals(new Totals(159, 160, 16), totals());
        }

        @ParameterizedTest(name = "{0} new staffs with {1} uses each give exactly {2} uses in one slot")
        @CsvSource({"1, 10, 10", "3, 10, 30", "64, 10, 640", "5, 1, 5", "16, 8, 128"})
        @DisplayName("All staffs together give exactly their total uses, never using a second slot")
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
                assertTrue(slotsWith(TP) <= 1, "the staffs never take a second slot");
            }

            assertEquals(expected, uses);
        }

        @Test
        @DisplayName("When the top staff breaks, the rest stay in the hand and are like newly crafted staffs")
        void topBreaksRestStay() {
            player.getInventory().setItemInMainHand(newStaff(3));

            for (int i = 1; i < MAX; i++) {
                assertEquals(Result.USED, use());
            }
            assertEquals(Result.BROKE, use());

            assertEquals(2, hand().getAmount());
            assertEquals("Uses: 10 left", usesLine(hand()));
            assertTrue(hand().isSimilar(newStaff(1)), "stacks with newly crafted staffs again");
            assertEquals(new Totals(20, 20, 2), totals());
        }

        @Test
        @DisplayName("Changes the staff made (e.g. a stored entity) apply to the stack in the hand")
        void pendingMetaGoesToStack() {
            player.getInventory().setItemInMainHand(newStaff(3));
            NamespacedKey stored = new NamespacedKey("fnamplifications", "stored_entity");

            ItemStack h = hand();
            ItemMeta meta = h.getItemMeta();
            meta.getPersistentDataContainer().set(stored, PersistentDataType.STRING, "zombie");
            StaffUses.spendUseInMainHand(player, h, meta, KEY, MAX, sameAs(TP));

            assertEquals(3, hand().getAmount());
            assertEquals("zombie", hand().getItemMeta().getPersistentDataContainer().get(stored, PersistentDataType.STRING));
        }
    }

    // ---- reported bugs ----

    @Nested
    @DisplayName("Reported bugs stay fixed")
    class ReportedBugs {

        @Test
        @DisplayName("Bug: no uses in the lore -> the lore shows the uses left")
        void loreShowsUses() {
            player.getInventory().setItemInMainHand(newStaff(1));

            use();

            assertEquals("Uses: 9 left", usesLine(hand()));
            assertEquals(9, hand().getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER));
        }

        @Test
        @DisplayName("Bug: staffs from before the lore fix ('Uses: left') -> lore fixed on use")
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
        @DisplayName("Bug: the whole stack broke at once -> only one staff breaks")
        void onlyOneStaffBreaks() {
            player.getInventory().setItemInMainHand(newStaff(16));

            for (int i = 0; i < MAX; i++) {
                use();
            }

            assertEquals(15, hand().getAmount());
        }

        @Test
        @DisplayName("Bug: two 1/10 staffs stacked showed 11/20 -> used staffs never stack")
        void wornStaffsNeverStack() {
            ItemStack a = wornStaff(1);
            ItemStack b = wornStaff(1);

            assertFalse(a.isSimilar(b), "two staffs with 1 use left must not merge");
            assertFalse(wornStaff(5).isSimilar(newStaff(1)), "a used staff must not merge with a new one");

            player.getInventory().setItemInMainHand(a);
            player.getInventory().setItem(5, b);
            assertEquals(new Totals(2, 20, 2), totals());
            assertEquals("Staff of Teleportation: 2/20 uses (2 staffs)", bar());

            int uses = 0;
            while (!hand().getType().isAir()) {
                use();
                uses++;
            }
            assertEquals(2, uses, "exactly 2 uses, not 11");
        }

        @Test
        @DisplayName("Bug: two stacks worn to the same uses would merge -> they don't")
        void wornStacksNeverStack() {
            ItemStack a = newStaff(4);
            ItemStack b = newStaff(4);
            for (int i = 0; i < 3; i++) {
                StaffUses.spendUse(a, a.getItemMeta(), KEY, MAX);
                StaffUses.spendUse(b, b.getItemMeta(), KEY, MAX);
            }

            assertEquals(usesLine(a), usesLine(b));
            assertFalse(a.isSimilar(b));
        }

        @Test
        @DisplayName("Splitting a used stack and merging it back never gives extra uses")
        void splittingGivesNothing() {
            ItemStack stack = newStaff(8);
            for (int i = 0; i < 4; i++) {
                StaffUses.spendUse(stack, stack.getItemMeta(), KEY, MAX);
            }
            int before = StaffUses.totalUsesLeft(stack, KEY, MAX);
            assertEquals(7 * MAX + 6, before);

            ItemStack half1 = stack.clone();
            half1.setAmount(3);
            ItemStack half2 = stack.clone();
            half2.setAmount(5);
            assertTrue(StaffUses.totalUsesLeft(half1, KEY, MAX) + StaffUses.totalUsesLeft(half2, KEY, MAX) <= before,
                    "split halves have at most the uses of the whole stack");

            assertTrue(half1.isSimilar(half2), "the halves can be put back together");
            ItemStack merged = half1.clone();
            merged.setAmount(8);
            assertEquals(before, StaffUses.totalUsesLeft(merged, KEY, MAX));
        }

        @Test
        @DisplayName("Bug: one click spent 2 uses (8/10) -> covered by TestStaffUseGuard")
        void doubleUseCoveredElsewhere() {
            // One use per call here; the duplicate click event is filtered by StaffUseGuard.
            player.getInventory().setItemInMainHand(newStaff(1));
            use();
            assertEquals(9, totals().usesLeft());
        }
    }

    // ---- stacks from the previous version ----

    @Nested
    @DisplayName("Stacks from the previous version")
    class Legacy {

        @Test
        @DisplayName("A stack where every staff has 4 uses left still gives exactly 3 x 4 = 12")
        void legacyStackGivesExactUses() {
            player.getInventory().setItemInMainHand(legacyStack(3, 4));

            assertEquals(new Totals(12, 30, 3), totals());

            int uses = 0;
            while (!hand().getType().isAir()) {
                use();
                uses++;
            }
            assertEquals(12, uses);
        }

        @Test
        @DisplayName("Two old 1/10 staffs that were stacked give exactly 2 uses")
        void legacyTwoWornStaffs() {
            player.getInventory().setItemInMainHand(legacyStack(2, 1));

            assertEquals(new Totals(2, 20, 2), totals());
            useUntilEmpty();
            assertEquals(0, totals().staffs());
        }

        @Test
        @DisplayName("If the inventory is full, the rest of an old stack drops at the player's feet")
        void fullInventoryDropsRest() {
            for (int slot = 0; slot < 36; slot++) {
                player.getInventory().setItem(slot, new ItemStack(Material.DIRT, 64));
            }
            player.getInventory().setItemInMainHand(legacyStack(5, 7));

            use();

            assertEquals(1, hand().getAmount());
            List<Item> dropped = List.copyOf(player.getWorld().getEntitiesByClass(Item.class));
            assertEquals(1, dropped.size());
            assertEquals(4, dropped.get(0).getItemStack().getAmount());
            assertTrue(dropped.get(0).getItemStack().isSimilar(legacyStack(1, 7)));
        }
    }

    // ---- refill ----

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

    @Test
    @DisplayName("When the last staff in the hand breaks, the most used staff of the same type moves in")
    void refillPrefersMostUsed() {
        player.getInventory().setItemInMainHand(wornStaff(1));
        player.getInventory().setItem(10, newStaff(5));
        player.getInventory().setItem(20, wornStaff(3));

        assertEquals(Result.BROKE, use());

        assertEquals("Uses: 3 left", usesLine(hand()));
        assertEquals(1, hand().getAmount());
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

    // ---- action bar ----

    @Test
    @DisplayName("A full stack of 16 staffs with 8 uses shows 128/128, and 127/128 after one use")
    void barFullStack() {
        player.getInventory().setItemInMainHand(newStaff(TP, 16, 8));
        Predicate<ItemStack> tp = sameAs(TP);

        Totals t = StaffUses.count(player.getInventory().getStorageContents(), tp, KEY, 8);
        assertEquals("Staff of Teleportation: 128/128 uses (16 staffs)", ChatColor.stripColor(color(StaffUses.usesBar(color(TP), t))));

        ItemStack h = hand();
        StaffUses.spendUseInMainHand(player, h, h.getItemMeta(), KEY, 8, tp);
        t = StaffUses.count(player.getInventory().getStorageContents(), tp, KEY, 8);
        assertEquals("Staff of Teleportation: 127/128 uses (16 staffs)", ChatColor.stripColor(color(StaffUses.usesBar(color(TP), t))));
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
}
