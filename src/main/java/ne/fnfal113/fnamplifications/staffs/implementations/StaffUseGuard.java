package ne.fnfal113.fnamplifications.staffs.implementations;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Lets a player cast a staff only once per click.
 * <p>
 * Right-clicking a block makes the client send two packets: "use item on block" and then "use item".
 * The server only skips the second {@code PlayerInteractEvent} if the held item is unchanged, but using a
 * staff changes it (uses left, or a staff split off a stack). Without this guard one click would then
 * cast the staff twice and spend two uses. Both packets arrive within the same tick or the next one,
 * while holding right-click repeats only every 4 ticks, so a second use within {@link #WINDOW_TICKS}
 * is always a duplicate.
 */
public final class StaffUseGuard {

    /** A use this many ticks or fewer after the previous one is the same click. */
    static final int WINDOW_TICKS = 1;

    private final Map<UUID, Integer> lastUseTick = new HashMap<>();

    /**
     * Records a staff use by {@code player} at server tick {@code tick}.
     *
     * @return {@code true} if this is a new click and the staff should be cast,
     *         {@code false} if it is the duplicate event of the previous click
     */
    public boolean tryUse(@Nonnull UUID player, int tick) {
        Integer last = lastUseTick.get(player);
        if (last != null && tick - last >= 0 && tick - last <= WINDOW_TICKS) {
            return false;
        }

        lastUseTick.put(player, tick);
        return true;
    }

    /** Forgets a player, for example when they leave. */
    public void forget(@Nonnull UUID player) {
        lastUseTick.remove(player);
    }
}
