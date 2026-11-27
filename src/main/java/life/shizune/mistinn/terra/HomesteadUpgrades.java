package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.registry.MistinnEntities;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * A homestead grows as the hunters bring its hub more crystal cores: three steps, each of more
 * cores than the last.
 * <ol>
 * <li>晶田進貢: every morning the fields send crystal grit to the hub, to be taken from it.</li>
 * <li>第二位守衛: a second warden keeps the homestead, two crystal turrets rise on its deck, and the fields send more.</li>
 * <li>晶脈歸途: the way home to it closes for half as long, and the tillers' thanks for holding it are doubled.</li>
 * </ol>
 */
public final class HomesteadUpgrades {
	/** The cores each step takes. */
	public static final int[] COSTS = {3, 5, 8};
	public static final int TOP = COSTS.length;
	/** Grit sent each morning at the first step and from the second on, and how much the hub holds at the most. */
	public static final int TRIBUTE = 3;
	public static final int TRIBUTE_MORE = 5;
	public static final int HOLDS = 32;

	/** One homestead's growth: its step, the grit waiting at its hub, and the last day the fields sent any. */
	public record Growth(int domain, int level, int tribute, long day) {
		public static final Codec<Growth> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Growth::domain),
			Codec.INT.fieldOf("level").forGetter(Growth::level),
			Codec.INT.optionalFieldOf("tribute", 0).forGetter(Growth::tribute),
			Codec.LONG.optionalFieldOf("day", -1L).forGetter(Growth::day)
		).apply(i, Growth::new));
	}

	public static final AttachmentType<List<Growth>> GROWTH = AttachmentRegistry.<List<Growth>>builder()
		.persistent(Growth.CODEC.listOf())
		.buildAndRegister(Mistinn.id("homestead_growth"));

	private HomesteadUpgrades() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 0) {
				mornings(server);
			}
		});
	}

	public static Growth growth(final MinecraftServer server, final int domain) {
		for (Growth growth : server.overworld().getAttachedOrElse(GROWTH, List.of())) {
			if (growth.domain() == domain) {
				return growth;
			}
		}
		return new Growth(domain, 0, 0, -1L);
	}

	public static int level(final MinecraftServer server, final int domain) {
		return growth(server, domain).level();
	}

	private static void set(final MinecraftServer server, final Growth changed) {
		List<Growth> all = new ArrayList<>(server.overworld().getAttachedOrElse(GROWTH, List.of()));
		all.removeIf(growth -> growth.domain() == changed.domain());
		all.add(changed);
		server.overworld().setAttached(GROWTH, List.copyOf(all));
	}

	private static Settlements.@Nullable Settlement at(final MinecraftServer server, final BlockPos hub) {
		for (Settlements.Settlement home : Settlements.homesteads(server)) {
			if (home.hub().equals(hub)) {
				return home;
			}
		}
		return null;
	}

	/** Each morning the fields of every grown homestead send their grit to its hub. */
	public static void mornings(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		if (level.isDarkOutside()) {
			return;
		}
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		for (Settlements.Settlement home : Settlements.homesteads(server)) {
			Growth growth = growth(server, home.domain());
			if (growth.level() >= 1 && growth.day() != day) {
				int sent = growth.level() >= 2 ? TRIBUTE_MORE : TRIBUTE;
				set(server, new Growth(home.domain(), growth.level(), Math.min(HOLDS, growth.tribute() + sent), day));
			}
		}
	}

	/** A hunter offers the hub of a homestead crystal cores: it grows a step if they are enough. */
	public static boolean offer(final ServerLevel level, final BlockPos hub, final ServerPlayer hunter, final ItemStack cores) {
		Settlements.Settlement home = at(level.getServer(), hub);
		if (home == null) {
			return false;
		}
		Growth growth = growth(level.getServer(), home.domain());
		if (growth.level() >= TOP) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.hub.grown").withStyle(ChatFormatting.GRAY), true);
			return false;
		}
		int cost = COSTS[growth.level()];
		if (cores.getCount() < cost && !hunter.isCreative()) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.hub.grow_cost", cost, growth.level() + 1), true);
			return false;
		}
		if (!hunter.isCreative()) {
			cores.shrink(cost);
		}
		int next = growth.level() + 1;
		long day = Math.floorDiv(level.getOverworldClockTime(), 24000L);
		set(level.getServer(), new Growth(home.domain(), next, growth.tribute(), growth.level() == 0 ? day : growth.day()));
		level.sendParticles(ParticleTypes.END_ROD, hub.getX() + 0.5, hub.getY() + 1.2, hub.getZ() + 0.5, 50 + 20 * next, 0.5, 1.2, 0.5, 0.06);
		level.playSound(null, hub, SoundEvents.BEACON_POWER_SELECT, SoundSource.BLOCKS, 2.0F, 0.9F + 0.15F * next);
		level.playSound(null, hub, SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.BLOCKS, 2.0F, 0.7F + 0.2F * next);
		if (next == 2) {
			warden(level, home);
		}
		if (next >= HomesteadDefence.TURRET_STEP) {
			HomesteadDefence.raiseTurrets(level, home);
		}
		for (ServerPlayer player : level.players()) {
			player.sendSystemMessage(Component.translatable("message.mistinn.hub.grew." + next, hunter.getDisplayName()).withStyle(ChatFormatting.AQUA));
		}
		return true;
	}

	/** At the second step a second warden comes to keep the homestead. */
	private static void warden(final ServerLevel level, final Settlements.Settlement home) {
		Tiller tiller = MistinnEntities.TILLER.create(level, EntitySpawnReason.STRUCTURE);
		if (tiller != null) {
			BlockPos at = home.hub().offset(-3, 0, -3);
			tiller.snapTo(at.getX() + 0.5, home.deck() + 1.0, at.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			tiller.settle(Tiller.Role.WARDEN, home.hub());
			level.addFreshEntity(tiller);
			level.sendParticles(ParticleTypes.END_ROD, tiller.getX(), tiller.getY() + 1.0, tiller.getZ(), 20, 0.3, 0.6, 0.3, 0.04);
		}
	}

	/** A hunter takes the grit waiting at a homestead's hub; false when there is none (or it is no homestead). */
	public static boolean collect(final ServerLevel level, final BlockPos hub, final ServerPlayer hunter) {
		Settlements.Settlement home = at(level.getServer(), hub);
		if (home == null) {
			return false;
		}
		Growth growth = growth(level.getServer(), home.domain());
		if (growth.tribute() <= 0) {
			return false;
		}
		hunter.spawnAtLocation(level, new ItemStack(MistinnItems.CRYSTAL_DUST, growth.tribute()));
		hunter.sendSystemMessage(Component.translatable("message.mistinn.hub.tribute", growth.tribute()).withStyle(ChatFormatting.AQUA), true);
		level.playSound(null, hub, SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 1.2F, 1.2F);
		set(level.getServer(), new Growth(home.domain(), growth.level(), 0, growth.day()));
		return true;
	}

	/** What a woken hub says of itself: its step, and what the next takes. */
	public static Component describe(final MinecraftServer server, final BlockPos hub) {
		Settlements.Settlement home = at(server, hub);
		int step = home == null ? 0 : level(server, home.domain());
		return step >= TOP ? Component.translatable("message.mistinn.hub.home_top")
			: Component.translatable("message.mistinn.hub.home_step", step, COSTS[step]);
	}

	/** How long the way home to this homestead closes after: half as long once it has grown its third step. */
	public static long travelCooldown(final MinecraftServer server, final Settlements.Settlement home, final long cooldown) {
		return level(server, home.domain()) >= 3 ? cooldown / 2 : cooldown;
	}

	/** How many times over the tillers thank hunters for holding this homestead. */
	public static int thanks(final MinecraftServer server, final int domain) {
		return level(server, domain) >= 3 ? 2 : 1;
	}
}
