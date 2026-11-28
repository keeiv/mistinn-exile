package life.shizune.mistinn.client.epic;

import java.io.IOException;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.epic.EpicService;
import life.shizune.mistinn.epic.EpicState;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.story.WorldMode;
import life.shizune.mistinn.udasru.Udasru;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FileUtil;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.DataPackConfig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.jspecify.annotations.Nullable;

/**
 * The epic on the client: making its world (Udasru, at the Hunter's hardship), its panel, and the Chronicler's question
 * at the end, asked once the cards before it have been read.
 */
public final class EpicClient {
	/** Whether the Chronicler's question waits to be asked. */
	private static boolean askName;

	private EpicClient() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(EpicService.AskNamePayload.TYPE, (payload, context) -> askName = payload.ask());
		ClientTickEvents.END_CLIENT_TICK.register(EpicClient::tick);
		HudElementRegistry.addLast(Mistinn.id("epic"), new EpicHud());
	}

	/** The epic of the world this client is in, or null outside one. */
	public static @Nullable EpicState state(final Minecraft mc) {
		return mc.level == null ? null : mc.level.getAttached(EpicService.STATE);
	}

	/** Whether this client is in a world of the epic. */
	public static boolean inEpic() {
		return state(Minecraft.getInstance()) != null;
	}

	private static void tick(final Minecraft mc) {
		if (mc.player == null) {
			askName = false;
			return;
		}
		EpicState state = state(mc);
		if (askName && state != null && mc.gui.screen() == null && life.shizune.mistinn.client.story.StoryClient.idle()) {
			askName = false;
			mc.gui.setScreen(new EpicNameScreen());
		}
	}

	/** Makes a world of the epic: Udasru, at the Hunter's hardship, named after the epic. */
	public static void createWorld(final Minecraft mc) {
		String title = Component.translatable("mode.mistinn.epic").getString();
		try {
			String folder = FileUtil.findAvailableName(mc.getLevelSource().getBaseDir(), title, "");
			var config = new WorldDataConfiguration(new DataPackConfig(List.of("vanilla"), List.of()), WorldDataConfiguration.DEFAULT.enabledFeatures());
			var settings = new LevelSettings(title, GameType.SURVIVAL, new LevelSettings.DifficultySettings(Difficulty.NORMAL, false, false), true, config);
			// read by the new world's server when it first starts
			WorldMode.choose(WorldMode.EPIC);
			Hardship.choose(Hardship.HUNTER);
			mc.createWorldOpenFlows().createFreshLevel(folder, settings, WorldOptions.defaultWithRandomSeed(),
				registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(Udasru.PRESET).value().createWorldDimensions(), mc.gui.screen());
		} catch (IOException error) {
			Mistinn.LOGGER.warn("Could not create the epic's save", error);
			SystemToast.onWorldAccessFailure(mc, title);
		}
	}
}
