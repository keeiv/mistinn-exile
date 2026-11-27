package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.grid.Carried;
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
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The homestead at work, once its hub is woken.
 * <ul>
 * <li>晶煉爐: crystal grit loaded into the refinery is pressed into crystal cores, eight grit to a core,
 * one every two minutes; the cores wait in it to be taken.</li>
 * <li>工作台: at the workshop's bench a hunter's worn gear is mended whole, for crystal grit by how
 * worn it is.</li>
 * </ul>
 */
public final class HomesteadWorks {
	/** Grit to a core, the grit a refinery holds, and the cores it keeps waiting at the most. */
	public static final int GRIT_PER_CORE = 8;
	public static final int HOLDS = 32;
	public static final int CORES_HELD = 8;
	/** Grit to mend a thing worn all the way through (less, by how worn it is; never under one). */
	public static final int MEND = 6;
	/** How long a core takes to press (a test seam: shortened in tests). */
	public static int refining = 2400;

	/** One homestead's refinery: where it stands, the grit in it, how far the core being pressed has come, and the cores waiting. */
	public record Refinery(int domain, BlockPos pos, int grit, int progress, int cores) {
		public static final Codec<Refinery> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Refinery::domain),
			BlockPos.CODEC.fieldOf("pos").forGetter(Refinery::pos),
			Codec.INT.optionalFieldOf("grit", 0).forGetter(Refinery::grit),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(Refinery::progress),
			Codec.INT.optionalFieldOf("cores", 0).forGetter(Refinery::cores)
		).apply(i, Refinery::new));
	}

	public static final AttachmentType<List<Refinery>> REFINERIES = AttachmentRegistry.<List<Refinery>>builder()
		.persistent(Refinery.CODEC.listOf())
		.buildAndRegister(Mistinn.id("homestead_refineries"));

	private HomesteadWorks() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tick(server);
			}
		});
	}

	public static @Nullable Refinery refinery(final MinecraftServer server, final int domain) {
		for (Refinery refinery : server.overworld().getAttachedOrElse(REFINERIES, List.of())) {
			if (refinery.domain() == domain) {
				return refinery;
			}
		}
		return null;
	}

	private static void set(final MinecraftServer server, final Refinery changed) {
		List<Refinery> all = new ArrayList<>(server.overworld().getAttachedOrElse(REFINERIES, List.of()));
		all.removeIf(refinery -> refinery.domain() == changed.domain());
		all.add(changed);
		server.overworld().setAttached(REFINERIES, List.copyOf(all));
	}

	/** The settlement a facility at {@code pos} belongs to. */
	public static Settlements.@Nullable Settlement around(final MinecraftServer server, final BlockPos pos) {
		for (Settlements.Settlement settlement : Settlements.ledger(server).built()) {
			if (Math.hypot(pos.getX() - settlement.hub().getX(), pos.getZ() - settlement.hub().getZ()) <= Settlements.CLEAR + 2) {
				return settlement;
			}
		}
		return null;
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		for (Refinery refinery : level.getAttachedOrElse(REFINERIES, List.of())) {
			Settlements.Settlement home = Settlements.of(server, refinery.domain());
			if (home == null || !home.lit() || refinery.grit() < GRIT_PER_CORE || refinery.cores() >= CORES_HELD) {
				continue;
			}
			int progress = refinery.progress() + 20;
			if (progress >= refining) {
				set(server, new Refinery(refinery.domain(), refinery.pos(), refinery.grit() - GRIT_PER_CORE, 0, refinery.cores() + 1));
				level.playSound(null, refinery.pos(), SoundEvents.AMETHYST_CLUSTER_PLACE, SoundSource.BLOCKS, 1.0F, 0.8F);
			} else {
				set(server, new Refinery(refinery.domain(), refinery.pos(), refinery.grit(), progress, refinery.cores()));
			}
			if (level.isLoaded(refinery.pos())) {
				// smoke from the stack while it works
				BlockPos pos = refinery.pos();
				level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 1.4, pos.getY() + 2.1, pos.getZ() + 1.4, 1, 0.05, 0.1, 0.05, 0.01);
				if (level.getRandom().nextInt(4) == 0) {
					level.playSound(null, pos, SoundEvents.BLASTFURNACE_FIRE_CRACKLE, SoundSource.BLOCKS, 0.6F, 0.8F);
				}
			}
		}
	}

	/**
	 * A hunter at the refinery: the cores waiting are handed over; else the crystal grit in hand (or,
	 * empty-handed, all they carry) goes in; and they are told how it stands.
	 */
	public static void useRefinery(final ServerLevel level, final BlockPos pos, final ServerPlayer hunter, final ItemStack held) {
		Settlements.Settlement home = around(level.getServer(), pos);
		if (home == null) {
			return;
		}
		if (!home.lit()) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.refinery.asleep").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		Refinery refinery = refinery(level.getServer(), home.domain());
		if (refinery == null) {
			refinery = new Refinery(home.domain(), pos, 0, 0, 0);
		}
		if (refinery.cores() > 0) {
			Carried.give(hunter, new ItemStack(MistinnItems.CRYSTAL_CORE, refinery.cores()));
			hunter.sendSystemMessage(Component.translatable("message.mistinn.refinery.cores", refinery.cores()).withStyle(ChatFormatting.AQUA), true);
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 1.0F, 1.0F);
			set(level.getServer(), new Refinery(refinery.domain(), pos, refinery.grit(), refinery.progress(), 0));
			return;
		}
		int room = HOLDS - refinery.grit();
		int loaded = 0;
		if (room > 0 && held.is(MistinnItems.CRYSTAL_DUST)) {
			loaded = Math.min(room, held.getCount());
			held.shrink(loaded);
		} else if (room > 0 && held.isEmpty()) {
			loaded = Carried.take(hunter, MistinnItems.CRYSTAL_DUST, room);
		}
		refinery = new Refinery(refinery.domain(), pos, refinery.grit() + loaded, refinery.progress(), refinery.cores());
		set(level.getServer(), refinery);
		if (loaded > 0) {
			level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_STEP, SoundSource.BLOCKS, 1.0F, 1.2F);
		}
		hunter.sendSystemMessage(status(refinery, loaded).withStyle(ChatFormatting.GRAY), true);
	}

	private static net.minecraft.network.chat.MutableComponent status(final Refinery refinery, final int loaded) {
		if (refinery.grit() < GRIT_PER_CORE) {
			return Component.translatable("message.mistinn.refinery.short", refinery.grit(), GRIT_PER_CORE);
		}
		int seconds = Mth.ceil((refining - refinery.progress()) / 20.0F);
		return Component.translatable(loaded > 0 ? "message.mistinn.refinery.loaded" : "message.mistinn.refinery.working", loaded, refinery.grit(), seconds);
	}

	/** How much crystal grit mending a thing costs: by how worn it is, never under one. */
	public static int mendCost(final ItemStack stack) {
		return Math.max(1, Mth.ceil((float) stack.getDamageValue() / stack.getMaxDamage() * MEND));
	}

	/** A hunter at the workshop's bench with their worn gear in hand: mended whole, for grit. */
	public static void useBench(final ServerLevel level, final BlockPos pos, final ServerPlayer hunter, final ItemStack held) {
		Settlements.Settlement home = around(level.getServer(), pos);
		if (home == null) {
			return;
		}
		if (!home.lit()) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.bench.asleep").withStyle(ChatFormatting.GRAY), true);
			return;
		}
		if (held.isEmpty() || !held.isDamageableItem() || !held.isDamaged()) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.bench.what", MEND).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		int cost = mendCost(held);
		if (Carried.count(hunter, MistinnItems.CRYSTAL_DUST) < cost) {
			hunter.sendSystemMessage(Component.translatable("message.mistinn.bench.short", cost).withStyle(ChatFormatting.GRAY), true);
			return;
		}
		Carried.take(hunter, MistinnItems.CRYSTAL_DUST, cost);
		held.setDamageValue(0);
		level.playSound(null, pos, SoundEvents.SMITHING_TABLE_USE, SoundSource.BLOCKS, 1.0F, 1.0F);
		level.sendParticles(ParticleTypes.WAX_OFF, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, 8, 0.3, 0.2, 0.3, 0.0);
		hunter.sendSystemMessage(Component.translatable("message.mistinn.bench.mended", held.getHoverName(), cost).withStyle(ChatFormatting.AQUA), true);
	}
}
