package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Result;
import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Slot;
import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.StaffType;
import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Totals;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStaffUses {

    private static final NamespacedKey KEY = new NamespacedKey("fnamplifications", "test_staff_uses");
    private static final int MAX = 10;
    private static final String TP = "&cStaff of Teleportation";
    private static final String FORCE = "&cStaff of Force";
    private static final StaffType TP_TYPE = new StaffType("FN_STAFF_TP", KEY, MAX);
    private static final StaffType FORCE_TYPE = new StaffType("FN_STAFF_FORCE", new NamespacedKey("fnamplifications", "force_uses"), MAX);

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

    /** Staff types by display name, like Slimefun's item lookup. */
    private static final Function<ItemStack, StaffType> TYPES = item -> {
        if (item == null || item.getType() != Material.BLAZE_ROD || !item.hasItemMeta()) {
            return null;
        }
        String name = item.getItemMeta().getDisplayName();
        return name.equals(color(TP)) ? TP_TYPE : name.equals(color(FORCE)) ? FORCE_TYPE : null;
    };

    /** Staffs as they come out of crafting. */
    private static ItemStack newStaff(String name, int amount) {
        ItemStack staff = new ItemStack(Material.BLAZE_ROD, amount);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(color(name));
        meta.setLore(List.of(color("&dRight click to cast"), "", color("&eUses: &e" + MAX + " left")));
        staff.setItemMeta(meta);
        return staff;
    }

    private static ItemStack newStaff(int amount) {
        return newStaff(TP, amount);
    }

    /** A stack of {@code amount} staffs with {@code uses} uses left in its pool. */
    private static ItemStack stack(int amount, int uses) {
        ItemStack stack = newStaff(amount);
        StaffUses.write(stack, TP_TYPE, uses);
        return stack;
    }

    /** What Minecraft does when splitting: both parts are copies of the stack. */
    private static ItemStack[] vanillaSplit(ItemStack stack, int taken) {
        ItemStack part = stack.clone();
        part.setAmount(taken);
        ItemStack rest = stack.clone();
        rest.setAmount(stack.getAmount() - taken);
        return new ItemStack[] {part, rest};
    }

    private static int uses(ItemStack stack) {
        return StaffUses.usesLeft(stack, TP_TYPE);
    }

    private static String usesLine(ItemStack item) {
        return ChatColor.stripColor(item.getItemMeta().getLore().get(2));
    }

    /** A slot that just holds an item. */
    private static final class Box implements Slot {
        ItemStack item;

        Box(ItemStack item) {
            this.item = item;
        }

        @Override
        public ItemStack get() {
            return item;
        }

        @Override
        public void set(ItemStack item) {
            this.item = item;
        }
    }

    private static List<Box> boxes(ItemStack... items) {
        List<Box> boxes = new ArrayList<>();
        for (ItemStack item : items) {
            boxes.add(new Box(item));
        }
        return boxes;
    }

    private static int resolve(List<Box> boxes) {
        return StaffUses.resolve(boxes, TYPES);
    }

    private static int total(List<Box> boxes) {
        return boxes.stream().mapToInt(b -> b.item == null ? 0 : uses(b.item)).sum();
    }

    private Result use() {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return StaffUses.spendUseInMainHand(player, hand, hand.getItemMeta(), TP_TYPE, TYPES);
    }

    private ItemStack hand() {
        return player.getInventory().getItemInMainHand();
    }

    // ---- the example from the request ----

    @Test
    @DisplayName("Example: 64 staffs 640/640 -> split 320+320 -> use 15 -> 305/320 -> split 305 into two -> merge back")
    void requestExample() {
        List<Box> inv = boxes(newStaff(64));
        resolve(inv);
        assertEquals("Uses: 640/640 left", usesLine(inv.get(0).item));

        // Split into two stacks of 32
        ItemStack[] halves = vanillaSplit(inv.get(0).item, 32);
        inv = boxes(halves[0], halves[1]);
        resolve(inv);
        assertEquals("Uses: 320/320 left", usesLine(inv.get(0).item));
        assertEquals("Uses: 320/320 left", usesLine(inv.get(1).item));

        // Use stack 1 fifteen times
        player.getInventory().setItemInMainHand(inv.get(0).item);
        for (int i = 0; i < 15; i++) {
            assertEquals(Result.USED, use());
        }
        ItemStack stack1 = hand();
        assertEquals(32, stack1.getAmount(), "no staff breaks while the pool has uses");
        assertEquals("Uses: 305/320 left", usesLine(stack1));
        assertEquals("Uses: 320/320 left", usesLine(inv.get(1).item));

        // Split stack 1 again: its 305 uses are shared out exactly
        ItemStack[] parts = vanillaSplit(stack1, 16);
        List<Box> split = boxes(parts[0], parts[1]);
        resolve(split);
        assertEquals(305, total(split));
        assertEquals("Uses: 153/160 left", usesLine(split.get(0).item));
        assertEquals("Uses: 152/160 left", usesLine(split.get(1).item));

        // Merge them back together
        ItemStack[] merged = StaffUses.merge(split.get(0).item, split.get(1).item, TP_TYPE, false);
        assertNotNull(merged);
        assertNull(merged[1], "all staffs moved");
        assertEquals(32, merged[0].getAmount());
        assertEquals("Uses: 305/320 left", usesLine(merged[0]));
    }

    // ---- the pool ----

    @Nested
    @DisplayName("One pool per stack")
    class Pool {

        @ParameterizedTest(name = "{0} new staffs with 10 uses give exactly {1} uses, all in one slot")
        @CsvSource({"1, 10", "3, 30", "16, 160", "64, 640"})
        @DisplayName("A stack gives exactly its pool, and breaks only when the pool is empty")
        void wholePool(int amount, int expected) {
            player.getInventory().setItemInMainHand(newStaff(amount));

            int uses = 0;
            while (!hand().getType().isAir()) {
                if (hand().getAmount() != amount) {
                    throw new AssertionError("staffs disappeared before the pool was empty");
                }
                use();
                uses++;
            }
            assertEquals(expected, uses);
        }

        @Test
        @DisplayName("The lore shows the uses of the whole stack")
        void loreShowsStackTotal() {
            player.getInventory().setItemInMainHand(newStaff(16));
            use();
            assertEquals("Uses: 159/160 left", usesLine(hand()));
        }

        @Test
        @DisplayName("When the pool is empty, the next stack of the same type moves into the hand")
        void refillAfterBreak() {
            player.getInventory().setItemInMainHand(stack(2, 1));
            player.getInventory().setItem(10, newStaff(5));
            player.getInventory().setItem(20, stack(3, 4));

            assertEquals(Result.BROKE, use());
            assertEquals(4, uses(hand()), "the stack with the fewest uses comes first");
        }

        @Test
        @DisplayName("Other staff types are never moved into the hand")
        void otherTypesNotRefilled() {
            player.getInventory().setItemInMainHand(stack(1, 1));
            player.getInventory().setItem(5, newStaff(FORCE, 4));

            assertEquals(Result.BROKE, use());
            assertTrue(hand().getType().isAir());
        }

        @Test
        @DisplayName("A full stack is like newly crafted staffs: no hidden data")
        void fullStackHasNoData() {
            ItemStack stack = stack(4, 40);
            assertFalse(stack.getItemMeta().getPersistentDataContainer().has(StaffUses.POOL_KEY, PersistentDataType.INTEGER));
        }
    }

    // ---- splitting ----

    @Nested
    @DisplayName("Splitting")
    class Splitting {

        @Test
        @DisplayName("Splitting a used stack shares its pool exactly")
        void splitIsExact() {
            for (int taken = 1; taken < 64; taken++) {
                ItemStack[] parts = vanillaSplit(stack(64, 437), taken);
                List<Box> inv = boxes(parts[0], parts[1]);
                resolve(inv);
                assertEquals(437, total(inv), "taking " + taken);
            }
        }

        @Test
        @DisplayName("Splitting a stack into many parts (drag) shares its pool exactly")
        void dragSplitIsExact() {
            ItemStack stack = stack(64, 305);
            List<Box> inv = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                ItemStack part = stack.clone();
                part.setAmount(8);
                inv.add(new Box(part));
            }
            resolve(inv);
            assertEquals(305, total(inv));
        }

        @Test
        @DisplayName("Copies never add uses: a part seen on its own only counts its share")
        void partsAloneNeverAddUses() {
            ItemStack[] parts = vanillaSplit(stack(64, 305), 32);

            // Each part looked at on its own, e.g. one is in a chest that was closed
            List<Box> one = boxes(parts[0]);
            List<Box> other = boxes(parts[1]);
            resolve(one);
            resolve(other);

            assertTrue(total(one) + total(other) <= 305);
            assertTrue(total(one) + total(other) >= 304, "at most a rounding use is lost");
        }

        @Test
        @DisplayName("A part with no uses left in it breaks")
        void emptyPartBreaks() {
            ItemStack[] parts = vanillaSplit(stack(64, 1), 32);
            List<Box> inv = boxes(parts[0], parts[1]);

            int broken = resolve(inv);

            assertEquals(32, broken);
            assertEquals(1, total(inv));
        }

        @Test
        @DisplayName("Splitting new staffs keeps them like newly crafted ones, with the right lore")
        void splitNewStaffs() {
            ItemStack[] parts = vanillaSplit(newStaff(64), 20);
            List<Box> inv = boxes(parts[0], parts[1]);
            resolve(inv);

            assertEquals("Uses: 200/200 left", usesLine(inv.get(0).item));
            assertEquals("Uses: 440/440 left", usesLine(inv.get(1).item));
            assertEquals(640, total(inv));
        }
    }

    // ---- merging ----

    @Nested
    @DisplayName("Merging")
    class Merging {

        @Test
        @DisplayName("Merging two used stacks adds their pools")
        void mergeAddsPools() {
            ItemStack[] merged = StaffUses.merge(stack(16, 153), stack(32, 320), TP_TYPE, false);

            assertEquals(48, merged[0].getAmount());
            assertEquals(473, uses(merged[0]));
            assertEquals("Uses: 473/480 left", usesLine(merged[0]));
        }

        @Test
        @DisplayName("Only as many staffs as fit are moved, with their share of the pool")
        void mergeUpToStackSize() {
            ItemStack[] merged = StaffUses.merge(stack(32, 300), stack(48, 480), TP_TYPE, false);

            assertEquals(64, merged[0].getAmount());
            assertEquals(16, merged[1].getAmount());
            assertEquals(780, uses(merged[0]) + uses(merged[1]));
        }

        @Test
        @DisplayName("Right-click places one staff with its share of the pool")
        void mergeOne() {
            ItemStack[] merged = StaffUses.merge(stack(10, 55), stack(3, 30), TP_TYPE, true);

            assertEquals(4, merged[0].getAmount());
            assertEquals(9, merged[1].getAmount());
            assertEquals(85, uses(merged[0]) + uses(merged[1]));
        }

        @Test
        @DisplayName("Nothing moves onto a full stack")
        void mergeOntoFull() {
            assertNull(StaffUses.merge(stack(5, 40), newStaff(64), TP_TYPE, false));
        }

        @Test
        @DisplayName("Merging new staffs gives new staffs")
        void mergeNewStaffs() {
            ItemStack[] merged = StaffUses.merge(newStaff(20), newStaff(44), TP_TYPE, false);

            assertEquals(640, uses(merged[0]));
            assertEquals("Uses: 640/640 left", usesLine(merged[0]));
            assertFalse(merged[0].getItemMeta().getPersistentDataContainer().has(StaffUses.POOL_KEY, PersistentDataType.INTEGER));
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
            assertEquals("Uses: 9/10 left", usesLine(hand()));
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

            assertEquals("Uses: 9/10 left", usesLine(hand()));
        }

        @Test
        @DisplayName("Bug: the whole stack broke on one staff's uses -> it lasts for the whole pool")
        void stackLastsWholePool() {
            player.getInventory().setItemInMainHand(newStaff(16));
            for (int i = 0; i < MAX; i++) {
                use();
            }
            assertEquals(16, hand().getAmount());
            assertEquals(150, uses(hand()));
        }

        @Test
        @DisplayName("Bug: two 1/10 staffs stacked showed 11/20 -> merged they have exactly 2")
        void twoWornStaffs() {
            ItemStack[] merged = StaffUses.merge(stack(1, 1), stack(1, 1), TP_TYPE, false);
            assertEquals(2, uses(merged[0]));

            player.getInventory().setItemInMainHand(merged[0]);
            int uses = 0;
            while (!hand().getType().isAir()) {
                use();
                uses++;
            }
            assertEquals(2, uses);
        }

        @Test
        @DisplayName("Bug: split showed '4 uses left' and couldn't merge back -> shares and merges exactly")
        void splitAndMergeBack() {
            ItemStack[] parts = vanillaSplit(stack(10, 44), 5);
            List<Box> inv = boxes(parts[0], parts[1]);
            resolve(inv);
            assertEquals("Uses: 22/50 left", usesLine(inv.get(0).item));

            ItemStack[] merged = StaffUses.merge(inv.get(0).item, inv.get(1).item, TP_TYPE, false);
            assertEquals(10, merged[0].getAmount());
            assertEquals(44, uses(merged[0]));
        }

        @Test
        @DisplayName("Bug: one click spent 2 uses -> one use per call (duplicate clicks: TestStaffUseGuard)")
        void oneUsePerCall() {
            player.getInventory().setItemInMainHand(newStaff(1));
            use();
            assertEquals(9, uses(hand()));
        }
    }

    // ---- older versions ----

    @Nested
    @DisplayName("Staffs from older versions")
    class Legacy {

        @Test
        @DisplayName("v4.2.4.1 stack (every staff 3 uses) becomes one pool of 16 x 3 = 48")
        void uniformStack() {
            ItemStack old = newStaff(16);
            ItemMeta meta = old.getItemMeta();
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER, 3);
            old.setItemMeta(meta);

            List<Box> inv = boxes(old);
            resolve(inv);

            assertEquals(16, inv.get(0).item.getAmount());
            assertEquals("Uses: 48/160 left", usesLine(inv.get(0).item));
        }

        @Test
        @DisplayName("v4.2.4.2 stack (top staff 6 uses, 7 full below) becomes one pool of 76")
        void topWornStack() {
            ItemStack old = newStaff(8);
            ItemMeta meta = old.getItemMeta();
            meta.getPersistentDataContainer().set(KEY, PersistentDataType.INTEGER, 6);
            meta.getPersistentDataContainer().set(StaffUses.LEGACY_WORN_KEY, PersistentDataType.STRING, "x");
            old.setItemMeta(meta);

            List<Box> inv = boxes(old);
            resolve(inv);

            assertEquals(76, uses(inv.get(0).item));
            assertFalse(inv.get(0).item.getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.INTEGER));
        }
    }

    // ---- action bar ----

    @Test
    @DisplayName("The action bar adds up all staffs of that type, and only that type")
    void actionBar() {
        player.getInventory().setItemInMainHand(stack(16, 127));
        player.getInventory().setItem(9, newStaff(4));
        player.getInventory().setItem(30, newStaff(FORCE, 5));

        Totals totals = StaffUses.count(player.getInventory().getStorageContents(), TP_TYPE, TYPES);
        assertEquals(new Totals(167, 200, 20), totals);
        assertEquals("Staff of Teleportation: 167/200 uses (20 staffs)", ChatColor.stripColor(color(StaffUses.usesBar(color(TP), totals))));
    }

    @Test
    @DisplayName("Totals ignore empty slots")
    void totalsIgnoreEmpty() {
        ItemStack[] items = Arrays.copyOf(new ItemStack[] {newStaff(2)}, 5);
        assertEquals(new Totals(20, 20, 2), StaffUses.count(items, TP_TYPE, TYPES));
    }
}
