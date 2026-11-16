package ne.fnfal113.fnamplifications.staffs.implementations;

import ne.fnfal113.fnamplifications.staffs.implementations.StaffUses.Result;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStaffUses {

    private static final NamespacedKey KEY = new NamespacedKey("fnamplifications", "test_staff_uses");
    private static final int MAX_USES = 10;

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

    /** A staff as it comes out of crafting: full uses in the lore, nothing stored yet. */
    private static ItemStack newStaff(int amount, int maxUses) {
        ItemStack staff = new ItemStack(Material.BLAZE_ROD, amount);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(color("&cStaff of Teleportation"));
        meta.setLore(List.of(color("&dRight click to teleport"), "", color("&eUses: &e" + maxUses + " left")));
        staff.setItemMeta(meta);
        return staff;
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    /** Uses the staff in the player's main hand, the way the staffs do. */
    private Result use() {
        ItemStack hand = player.getInventory().getItemInMainHand();
        return StaffUses.spendUseInMainHand(player, hand, hand.getItemMeta(), KEY, MAX_USES);
    }

    private ItemStack hand() {
        return player.getInventory().getItemInMainHand();
    }

    private static String usesLine(ItemStack item) {
        return ChatColor.stripColor(item.getItemMeta().getLore().get(2));
    }

    @Test
    @DisplayName("Using a staff counts its uses down")
    void countsDown() {
        player.getInventory().setItemInMainHand(newStaff(1, MAX_USES));

        assertEquals(Result.USED, use());

        assertEquals(1, hand().getAmount());
        assertEquals("Uses: 9 left", usesLine(hand()));
        assertEquals(9, hand().getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.INTEGER));
    }

    @Test
    @DisplayName("A single staff breaks on its last use")
    void singleStaffBreaks() {
        player.getInventory().setItemInMainHand(newStaff(1, MAX_USES));

        for (int i = 1; i < MAX_USES; i++) {
            assertEquals(Result.USED, use(), "use " + i);
        }
        assertEquals(Result.BROKE, use());

        assertTrue(hand().getType().isAir(), "the broken staff should be gone");
    }

    @Test
    @DisplayName("Only one staff of a stack breaks on the last use")
    void onlyOneStaffOfStackBreaks() {
        player.getInventory().setItemInMainHand(newStaff(16, MAX_USES));

        for (int i = 1; i < MAX_USES; i++) {
            use();
        }
        assertEquals(Result.BROKE, use());

        assertEquals(Material.BLAZE_ROD, hand().getType());
        assertEquals(15, hand().getAmount(), "15 staffs should be left, not 0");
    }

    @Test
    @DisplayName("The rest of the stack goes back to full uses after one breaks")
    void restOfStackHasFullUses() {
        player.getInventory().setItemInMainHand(newStaff(3, MAX_USES));

        for (int i = 0; i < MAX_USES; i++) {
            use();
        }

        assertEquals("Uses: 10 left", usesLine(hand()));
        assertFalse(hand().getItemMeta().getPersistentDataContainer().has(KEY, PersistentDataType.INTEGER));

        assertEquals(Result.USED, use());
        assertEquals("Uses: 9 left", usesLine(hand()));
        assertEquals(2, hand().getAmount());
    }

    @Test
    @DisplayName("After one breaks, the rest of the stack still stacks with newly crafted staffs")
    void resetStackStacksWithNewStaffs() {
        player.getInventory().setItemInMainHand(newStaff(3, MAX_USES));

        for (int i = 0; i < MAX_USES; i++) {
            use();
        }

        assertTrue(hand().isSimilar(newStaff(1, MAX_USES)));
    }

    @ParameterizedTest(name = "{0} staffs with {1} uses each give {2} uses in total")
    @CsvSource({"1, 10, 10", "3, 10, 30", "64, 10, 640", "5, 1, 5", "2, 3, 6"})
    @DisplayName("A stack gives every staff's uses before it is used up")
    void wholeStackLasts(int amount, int maxUses, int expectedUses) {
        player.getInventory().setItemInMainHand(newStaff(amount, maxUses));

        int uses = 0;
        int broke = 0;
        while (!hand().getType().isAir()) {
            ItemStack h = hand();
            if (StaffUses.spendUseInMainHand(player, h, h.getItemMeta(), KEY, maxUses) == Result.BROKE) {
                broke++;
            }
            uses++;
            assertTrue(uses <= expectedUses, "the stack lasted longer than expected");
        }

        assertEquals(expectedUses, uses);
        assertEquals(amount, broke, "every staff should break exactly once");
    }

    @Test
    @DisplayName("Staffs from before the fix (lore 'Uses: left') get their lore fixed on use")
    void fixesOldLore() {
        ItemStack staff = newStaff(1, MAX_USES);
        ItemMeta meta = staff.getItemMeta();
        meta.setLore(List.of(color("&dRight click to teleport"), "", color("&eUses: &eleft")));
        staff.setItemMeta(meta);
        player.getInventory().setItemInMainHand(staff);

        use();

        assertEquals("Uses: 9 left", usesLine(hand()));
    }

    @Test
    @DisplayName("Other changes the staff made to the item are kept")
    void keepsPendingMetaChanges() {
        player.getInventory().setItemInMainHand(newStaff(2, MAX_USES));
        NamespacedKey other = new NamespacedKey("fnamplifications", "stored_entity");

        ItemStack h = hand();
        ItemMeta meta = h.getItemMeta();
        meta.getPersistentDataContainer().set(other, PersistentDataType.STRING, "zombie");
        StaffUses.spendUseInMainHand(player, h, meta, KEY, MAX_USES);

        assertEquals("zombie", hand().getItemMeta().getPersistentDataContainer().get(other, PersistentDataType.STRING));
        assertEquals("Uses: 9 left", usesLine(hand()));
    }

    @Test
    @DisplayName("Staffs in other inventory slots are not touched")
    void otherSlotsUntouched() {
        player.getInventory().setItemInMainHand(newStaff(1, MAX_USES));
        player.getInventory().setItem(5, newStaff(4, MAX_USES));

        for (int i = 1; i < MAX_USES; i++) {
            use();
        }
        assertEquals(Result.BROKE, use());

        assertTrue(hand().getType().isAir());
        assertEquals(4, player.getInventory().getItem(5).getAmount());
    }
}
