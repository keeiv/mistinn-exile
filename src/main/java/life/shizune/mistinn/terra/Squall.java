package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.hardship.Hardship;
import life.shizune.mistinn.registry.MistinnBlocks;
import life.shizune.mistinn.registry.MistinnItems;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * 晶嵐: now and then the wind over a Terra highland turns, and a squall of crystal grit comes down
 * on it. A hunter out under the open sky is cut by it every little while (a helmet takes the worst,
 * and wears for it); under a roof, a facility's deck, a tree, a ledge, they are safe. While it
 * blows, flakes of the lattice come down here and there out in the open, crystal dust and now and
 * then a lattice chip, for whoever dares go and get them. Each highland has its own weather: a
 * squall gathers over one only while a hunter is there, and not again for a while after it passes.
 */
public final class Squall {
	/** How long it gathers before it breaks, and how long it blows at the least and the most. */
	public static final int GATHER = 200;
	public static final int SHORTEST = 1800;
	public static final int LONGEST = 2400;
	/** How long a highland stays calm after a squall has passed (a quarter of an hour). */
	public static final int CALM = 18000;
	/** With a hunter there and the calm over, a squall gathers on one second in this many (some twenty-five minutes, as a rule). */
	public static final int ODDS = 1500;
	/** How often it cuts a hunter in the open, and how deep on a normal world. */
	public static final int CUT_EVERY = 40;
	public static final float CUT = 1.5F;
	/** How often flakes come down round each hunter, and how near and far. */
	public static final int FALL_EVERY = 60;
	public static final int FALL_NEAR = 4;
	public static final int FALL_FAR = 10;
	public static final ResourceKey<DamageType> DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, Mistinn.id("crystal_squall"));
	/** Under the automated tests no squall comes of itself, so that the other tests in Terra are not cut. */
	private static final boolean AUTOMATED = System.getProperty("fabric.client.gametest") != null;

	/** Where the weather over a highland stands, as each hunter's client sees it. */
	public enum Stage {
		CALM, GATHERING, RAGING
	}

	/**
	 * One highland's weather: when the squall over it breaks and when it passes (both 0 when none is
	 * coming), and the soonest the next may gather.
	 */
	public record Storm(int domain, long breaks, long passes, long next) {
		public static final Codec<Storm> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Storm::domain),
			Codec.LONG.fieldOf("breaks").forGetter(Storm::breaks),
			Codec.LONG.fieldOf("passes").forGetter(Storm::passes),
			Codec.LONG.fieldOf("next").forGetter(Storm::next)
		).apply(i, Storm::new));

		public boolean coming() {
			return this.passes != 0L;
		}

		public Stage stage(final long now) {
			return !this.coming() || now >= this.passes ? Stage.CALM : now >= this.breaks ? Stage.RAGING : Stage.GATHERING;
		}
	}

	public static final AttachmentType<List<Storm>> STORMS = AttachmentRegistry.<List<Storm>>builder()
		.persistent(Storm.CODEC.listOf())
		.buildAndRegister(Mistinn.id("crystal_squalls"));
	/** On each hunter under a squall (gathering or blowing): what their client should draw and play. */
	public static final AttachmentType<Integer> SKY = AttachmentRegistry.<Integer>builder()
		.syncWith(ByteBufCodecs.VAR_INT, AttachmentSyncPredicate.targetOnly())
		.buildAndRegister(Mistinn.id("squall_sky"));

	private Squall() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 20 == 0) {
				tick(server, server.getTickCount() % CUT_EVERY == 0, server.getTickCount() % FALL_EVERY == 0);
			}
		});
	}

	public static @Nullable Storm storm(final MinecraftServer server, final int domain) {
		for (Storm storm : server.overworld().getAttachedOrElse(STORMS, List.of())) {
			if (storm.domain() == domain) {
				return storm;
			}
		}
		return null;
	}

	private static void set(final MinecraftServer server, final Storm changed) {
		List<Storm> all = new ArrayList<>(server.overworld().getAttachedOrElse(STORMS, List.of()));
		all.removeIf(storm -> storm.domain() == changed.domain());
		all.add(changed);
		server.overworld().setAttached(STORMS, List.copyOf(all));
	}

	/** What a hunter's client is told of the sky over them. */
	public static Stage sky(final ServerPlayer player) {
		return Stage.values()[player.getAttachedOrElse(SKY, 0)];
	}

	private static void tick(final MinecraftServer server, final boolean cut, final boolean fall) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		List<ServerPlayer> seen = new ArrayList<>();
		for (Domain domain : DomainService.of(server, Domain.Kind.TERRA)) {
			List<ServerPlayer> there = there(level, domain);
			Storm storm = storm(server, domain.id());
			if (storm != null && storm.coming()) {
				Stage stage = storm.stage(now);
				if (stage == Stage.CALM) {
					passes(level, domain, storm);
				} else {
					if (stage == Stage.RAGING) {
						rage(level, domain, storm, there, now, cut, fall);
					}
					for (ServerPlayer player : there) {
						show(player, stage);
						seen.add(player);
					}
				}
				continue;
			}
			long next = storm == null ? 0L : storm.next();
			if (!AUTOMATED && !there.isEmpty() && now >= next && level.getRandom().nextInt(ODDS) == 0) {
				gather(level, domain, GATHER);
			}
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (!seen.contains(player)) {
				show(player, Stage.CALM);
			}
		}
	}

	private static List<ServerPlayer> there(final ServerLevel level, final Domain domain) {
		List<ServerPlayer> there = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			if (!player.isSpectator() && domain.distance(player.getX(), player.getZ()) <= domain.radius()) {
				there.add(player);
			}
		}
		return there;
	}

	private static void show(final ServerPlayer player, final Stage stage) {
		if (sky(player) != stage) {
			if (stage == Stage.CALM) {
				player.removeAttached(SKY);
			} else {
				player.setAttached(SKY, stage.ordinal());
			}
		}
	}

	/** A squall gathers over a highland, to break in so many ticks: the wind turns, and everyone near hears it. */
	public static void gather(final ServerLevel level, final Domain domain, final int ticks) {
		long now = level.getGameTime();
		long breaks = now + Math.max(1, ticks);
		long passes = breaks + SHORTEST + level.getRandom().nextInt(LONGEST - SHORTEST + 1);
		set(level.getServer(), new Storm(domain.id(), breaks, passes, passes + CALM));
		for (ServerPlayer player : level.players()) {
			if (domain.distance(player.getX(), player.getZ()) <= domain.radius() * 1.3) {
				player.sendSystemMessage(Component.translatable("message.mistinn.squall.gathers").withStyle(ChatFormatting.AQUA));
				level.playSound(null, player.getX(), player.getY() + 8.0, player.getZ(), SoundEvents.AMETHYST_BLOCK_RESONATE, SoundSource.WEATHER, 2.0F, 0.5F);
			}
		}
	}

	/** For tests: the squall over a highland passes now. */
	public static void stop(final ServerLevel level, final Domain domain) {
		Storm storm = storm(level.getServer(), domain.id());
		if (storm != null && storm.coming()) {
			long now = level.getGameTime();
			set(level.getServer(), new Storm(domain.id(), Math.min(storm.breaks(), now), now, now + CALM));
		}
	}

	private static void passes(final ServerLevel level, final Domain domain, final Storm storm) {
		set(level.getServer(), new Storm(domain.id(), 0L, 0L, storm.next()));
		for (ServerPlayer player : there(level, domain)) {
			player.sendSystemMessage(Component.translatable("message.mistinn.squall.passes").withStyle(ChatFormatting.GRAY));
		}
	}

	/** A moment of the squall: it breaks (a title), cuts those in the open, and lets flakes fall round each hunter. */
	private static void rage(final ServerLevel level, final Domain domain, final Storm storm, final List<ServerPlayer> there, final long now,
		final boolean cut, final boolean fall) {
		boolean broke = now - storm.breaks() < 20L;
		RandomSource random = level.getRandom();
		for (ServerPlayer player : there) {
			if (broke) {
				player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 30));
				player.connection.send(new ClientboundSetTitleTextPacket(Component.translatable("title.mistinn.squall")));
				player.connection.send(new ClientboundSetSubtitleTextPacket(Component.translatable("title.mistinn.squall.sub")));
			}
			if (player.isCreative() || !player.isAlive()) {
				continue;
			}
			if (cut && exposed(level, player)) {
				cut(level, player);
			}
			if (fall && random.nextInt(3) != 0) {
				flake(level, player, random);
			}
		}
	}

	/** Whether nothing stands over a hunter's head: no roof, deck, leaf or ledge between them and the sky. */
	public static boolean exposed(final Level level, final ServerPlayer player) {
		Vec3 eye = player.getEyePosition();
		return level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(eye.x), Mth.floor(eye.z)) <= Mth.floor(eye.y);
	}

	/** How deep it cuts on this world. */
	public static float depth(final MinecraftServer server) {
		return switch (Hardship.of(server)) {
			case EASY -> CUT * 0.7F;
			case NORMAL -> CUT;
			case RUST_ANCHOR -> CUT * 1.6F;
		};
	}

	private static void cut(final ServerLevel level, final ServerPlayer player) {
		DamageSource grit = new DamageSource(level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE));
		player.hurtServer(level, grit, depth(level.getServer()));
		ItemStack helmet = player.getItemBySlot(EquipmentSlot.HEAD);
		if (!helmet.isEmpty() && helmet.isDamageableItem()) {
			helmet.hurtAndBreak(1, player, EquipmentSlot.HEAD);
		}
		level.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.WEATHER, 0.8F, 1.6F);
	}

	/** Flakes of the lattice come down somewhere out in the open near a hunter: dust, and now and then a chip. */
	private static void flake(final ServerLevel level, final ServerPlayer player, final RandomSource random) {
		for (int attempt = 0; attempt < 6; attempt++) {
			double angle = random.nextDouble() * Math.PI * 2.0;
			double reach = FALL_NEAR + random.nextDouble() * (FALL_FAR - FALL_NEAR);
			int x = Mth.floor(player.getX() + Math.cos(angle) * reach);
			int z = Mth.floor(player.getZ() + Math.sin(angle) * reach);
			if (!level.hasChunk(x >> 4, z >> 4)) {
				continue;
			}
			BlockPos ground = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
			if (Math.abs(ground.getY() - player.getY()) > 8 || !level.getFluidState(ground.below()).isEmpty()) {
				continue;
			}
			ItemStack flake = random.nextInt(12) == 0 ? new ItemStack(MistinnItems.LATTICE_CHIP) : new ItemStack(MistinnItems.CRYSTAL_DUST, 1 + random.nextInt(2));
			ItemEntity item = new ItemEntity(level, ground.getX() + 0.5, ground.getY() + 0.2, ground.getZ() + 0.5, flake, 0.0, -0.1, 0.0);
			item.setPickUpDelay(10);
			level.addFreshEntity(item);
			level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, MistinnBlocks.LATTICE_CRYSTAL.defaultBlockState()),
				ground.getX() + 0.5, ground.getY() + 0.3, ground.getZ() + 0.5, 14, 0.3, 0.2, 0.3, 0.1);
			level.sendParticles(ParticleTypes.END_ROD, ground.getX() + 0.5, ground.getY() + 0.8, ground.getZ() + 0.5, 4, 0.2, 0.4, 0.2, 0.02);
			level.playSound(null, ground.getX() + 0.5, ground.getY(), ground.getZ() + 0.5, SoundEvents.AMETHYST_CLUSTER_FALL, SoundSource.WEATHER, 1.0F, 1.2F);
			return;
		}
	}
}
