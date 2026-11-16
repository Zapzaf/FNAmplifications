package ne.fnfal113.fnamplifications.staffs.implementations;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestStaffUseGuard {

    private static final UUID ALICE = UUID.randomUUID();
    private static final UUID BOB = UUID.randomUUID();

    @Test
    @DisplayName("The first click casts")
    void firstClickCasts() {
        assertTrue(new StaffUseGuard().tryUse(ALICE, 100));
    }

    @Test
    @DisplayName("The duplicate event of the same click (same tick) is ignored")
    void sameTickIgnored() {
        StaffUseGuard guard = new StaffUseGuard();
        assertTrue(guard.tryUse(ALICE, 100));
        assertFalse(guard.tryUse(ALICE, 100));
    }

    @Test
    @DisplayName("The duplicate event arriving one tick later is ignored")
    void nextTickIgnored() {
        StaffUseGuard guard = new StaffUseGuard();
        assertTrue(guard.tryUse(ALICE, 100));
        assertFalse(guard.tryUse(ALICE, 101));
    }

    @Test
    @DisplayName("Holding right-click (a new click every 4 ticks) casts every time")
    void heldRightClickCastsEachTime() {
        StaffUseGuard guard = new StaffUseGuard();
        for (int tick = 100; tick < 140; tick += 4) {
            assertTrue(guard.tryUse(ALICE, tick), "tick " + tick);
        }
    }

    @Test
    @DisplayName("A duplicate does not push the window forward")
    void duplicateDoesNotExtendWindow() {
        StaffUseGuard guard = new StaffUseGuard();
        assertTrue(guard.tryUse(ALICE, 100));
        assertFalse(guard.tryUse(ALICE, 101));
        assertTrue(guard.tryUse(ALICE, 102));
    }

    @Test
    @DisplayName("Players don't block each other")
    void playersIndependent() {
        StaffUseGuard guard = new StaffUseGuard();
        assertTrue(guard.tryUse(ALICE, 100));
        assertTrue(guard.tryUse(BOB, 100));
    }

    @Test
    @DisplayName("A player who left and rejoined can cast right away")
    void forgetResets() {
        StaffUseGuard guard = new StaffUseGuard();
        assertTrue(guard.tryUse(ALICE, 100));
        guard.forget(ALICE);
        assertTrue(guard.tryUse(ALICE, 100));
    }

    // ---- one click on a block = two interact events; only one use may be spent ----

    private static final NamespacedKey KEY = new NamespacedKey("fnamplifications", "test_staff_uses");
    private PlayerMock player;

    @BeforeEach
    void setUp() {
        player = MockBukkit.mock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    private static ItemStack newStaff(int amount) {
        ItemStack staff = new ItemStack(Material.BLAZE_ROD, amount);
        ItemMeta meta = staff.getItemMeta();
        meta.setDisplayName(ChatColor.RED + "Staff of Force");
        meta.setLore(List.of("", ChatColor.YELLOW + "Uses: " + ChatColor.YELLOW + "10 left"));
        staff.setItemMeta(meta);
        return staff;
    }

    /** What the listener does for one interact event at {@code tick}. */
    private void interactEvent(StaffUseGuard guard, int tick) {
        if (!guard.tryUse(player.getUniqueId(), tick)) {
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        Predicate<ItemStack> same = it -> it != null && it.getType() == Material.BLAZE_ROD;
        StaffUses.spendUseInMainHand(player, hand, hand.getItemMeta(), KEY, 10, same);
    }

    private int usesLeft() {
        return StaffUses.count(player.getInventory().getStorageContents(), it -> it != null && it.getType() == Material.BLAZE_ROD, KEY, 10).usesLeft();
    }

    @Test
    @DisplayName("Clicking a block once with a new staff leaves 9/10, not 8/10")
    void oneClickOneUse() {
        StaffUseGuard guard = new StaffUseGuard();
        player.getInventory().setItemInMainHand(newStaff(1));

        interactEvent(guard, 100); // use item on block
        interactEvent(guard, 100); // use item (fired again because the staff changed)

        assertEquals(9, usesLeft());
    }

    @Test
    @DisplayName("Clicking a block once with a stack of 16 spends one use")
    void oneClickOneUseFromStack() {
        StaffUseGuard guard = new StaffUseGuard();
        player.getInventory().setItemInMainHand(newStaff(16));

        interactEvent(guard, 100);
        interactEvent(guard, 101);

        assertEquals(159, usesLeft());
    }

    @Test
    @DisplayName("Five separate clicks spend exactly five uses")
    void fiveClicksFiveUses() {
        StaffUseGuard guard = new StaffUseGuard();
        player.getInventory().setItemInMainHand(newStaff(1));

        for (int click = 0; click < 5; click++) {
            int tick = 100 + click * 4;
            interactEvent(guard, tick);
            interactEvent(guard, tick);
        }

        assertEquals(5, usesLeft());
    }
}
