package earth.terrarium.heracles;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * NeoForge 26.2 bootstrap entrypoint.
 *
 * <p>The quest implementation is intentionally reintroduced incrementally from the retained
 * 1.21 sources as its Minecraft and library APIs are ported.</p>
 */
@Mod(Heracles.MOD_ID)
public final class Heracles {
    public static final String MOD_ID = "heracles";
    public static final Logger LOGGER = LogUtils.getLogger();

    public Heracles() {
        LOGGER.info("Heracles loaded on NeoForge");
    }
}
