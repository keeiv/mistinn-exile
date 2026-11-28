package life.shizune.mistinn.expedition;

import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.Carried;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.Nullable;

/**
 * 遺物: each ground keeps one thing the forebears made that mattered most to them, waiting in the same place every time
 * the ground is stocked (the dam's is the gate cassette, in the control room's safe). Like the salvage it is at stake
 * until it is home: it does not go in the safe box. Brought home and shown to an elder, the elder takes it and gives
 * crystal cores for it; the first time, the elder tells what it was.
 */
public final class Relics {
	/** What the elder gives for a relic shown. */
	public static final int CORES = 3;
	/** The grounds' relics. */
	public static final Map<ResourceKey<Level>, Item> OF = Map.of(Grounds.DAM, MistinnItems.GATE_CASSETTE);
	/** The grounds whose relic has been shown to an elder in this world. */
	public static final AttachmentType<List<String>> SHOWN = AttachmentRegistry.<List<String>>builder()
		.persistent(Codec.STRING.listOf())
		.buildAndRegister(Mistinn.id("relics_shown"));

	private Relics() {
	}

	/** Whether this is a ground's relic. */
	public static boolean is(final ItemStack stack) {
		return !stack.isEmpty() && OF.containsValue(stack.getItem());
	}

	/** Whether this was found out there and is at stake until it is home: salvage, or a relic. */
	public static boolean atStake(final ItemStack stack) {
		return Salvage.is(stack) || is(stack);
	}

	/** The relic a hunter carries, if any (the first ground's first). */
	public static @Nullable ResourceKey<Level> carried(final ServerPlayer hunter) {
		for (Map.Entry<ResourceKey<Level>, Item> relic : OF.entrySet()) {
			if (Carried.count(hunter, relic.getValue()) > 0) {
				return relic.getKey();
			}
		}
		return null;
	}

	/** Whether a ground's relic has been shown to an elder in this world. */
	public static boolean shown(final MinecraftServer server, final ResourceKey<Level> ground) {
		return server.overworld().getAttachedOrElse(SHOWN, List.of()).contains(ground.identifier().toString());
	}

	/**
	 * The hunter shows an elder a relic they carry: the elder takes it and gives crystal cores for it; the first time in
	 * this world, the elder says what it was. False when they carry none.
	 */
	public static boolean show(final ServerPlayer hunter) {
		ResourceKey<Level> ground = carried(hunter);
		if (ground == null || Carried.take(hunter, OF.get(ground), 1) < 1) {
			return false;
		}
		MinecraftServer server = hunter.level().getServer();
		Carried.give(hunter, new ItemStack(MistinnItems.CRYSTAL_CORE, CORES));
		String key = "relic.mistinn." + ground.identifier().getPath();
		if (!shown(server, ground)) {
			List<String> shown = new ArrayList<>(server.overworld().getAttachedOrElse(SHOWN, List.of()));
			shown.add(ground.identifier().toString());
			server.overworld().setAttached(SHOWN, List.copyOf(shown));
			for (int line = 1; line <= 3; line++) {
				hunter.sendSystemMessage(Component.translatable(key + "." + line).withStyle(ChatFormatting.AQUA));
			}
			life.shizune.mistinn.achievement.AchievementService.grant(hunter, life.shizune.mistinn.achievement.Achievement.RELIC_SHOWN);
		} else {
			hunter.sendSystemMessage(Component.translatable(key + ".again").withStyle(ChatFormatting.GRAY));
		}
		hunter.sendSystemMessage(Component.translatable("message.mistinn.relic.cores", CORES).withStyle(ChatFormatting.GOLD));
		hunter.level().playSound(null, hunter.blockPosition(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.PLAYERS, 1.0F, 1.2F);
		return true;
	}
}
