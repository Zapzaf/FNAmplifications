package ne.fnfal113.fnamplifications.utils.compatibility;

import org.bukkit.Material;

public class VersionedMaterial {

    // This build targets Minecraft 26.3, where SHORT_GRASS always exists. The previous version-string
    // parsing broke on 26.x (Bukkit version "26.3.build.N" is not "major.minor.patch").
    public static final Material SHORT_GRASS = Material.SHORT_GRASS;

    public VersionedMaterial() {}

}
