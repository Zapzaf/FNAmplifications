package ne.fnfal113.fnamplifications.utils;

import org.bukkit.ChatColor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class TestChatFormat {

    private PlayerMock player;

    @BeforeEach
    void setUp() {
        player = MockBukkit.mock().addPlayer();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Messages start with 'FNAmp »' (double greater-than, U+00BB)")
    void prefix() {
        Utils.sendMessage("You spawned a cloud effect with forward force", player);

        String message = player.nextMessage();
        assertEquals("FNAmp » You spawned a cloud effect with forward force", ChatColor.stripColor(message));
    }

    @Test
    @DisplayName("Messages contain no raw & color codes")
    void noRawColorCodes() {
        Utils.sendMessage(ChatColor.RED + "Staff of Force" + " &7has broken &8(&7out of uses&8)", player);

        String message = player.nextMessage();
        assertFalse(message.matches("(?s).*&[0-9a-fk-or].*"), message);
        assertEquals("FNAmp » Staff of Force has broken (out of uses)", ChatColor.stripColor(message));
    }
}
