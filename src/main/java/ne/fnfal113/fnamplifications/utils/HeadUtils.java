package ne.fnfal113.fnamplifications.utils;

import io.github.thebusybiscuit.slimefun4.utils.compatibility.VersionedPlayerHead;
import org.bukkit.inventory.ItemStack;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class HeadUtils {

    private HeadUtils() {}

    /**
     * Creates a custom player head from a textures.minecraft.net hash.
     * <p>
     * dough's {@code PlayerHead}/{@code PlayerSkin} subclass Mojang's {@code GameProfile}, which is final
     * on Minecraft 26.x and crashes with an {@link IncompatibleClassChangeError}. Slimefun's
     * {@link VersionedPlayerHead} uses the Bukkit profile API on 26.3+ and dough on older versions.
     */
    @Nonnull
    public static ItemStack headFromHash(@Nonnull String hash) {
        final String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + hash + "\"}}}";
        return VersionedPlayerHead.getItemStack(Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8)));
    }
}
