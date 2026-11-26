package life.shizune.mistinn.terra;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.domain.Domain;
import life.shizune.mistinn.domain.DomainService;
import life.shizune.mistinn.registry.MistinnEntities;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.levelgen.Heightmap;
import org.jspecify.annotations.Nullable;

/**
 * The crown elders: one grows at the heart of each Terra highland. It takes the air when a hunter
 * first comes near the crown, and once brought down the crown grows another three days later.
 */
public final class Elders {
	/** How near the heart a hunter comes before the elder takes the air, and how long the crown takes to grow another. */
	public static final double WAKE = 96.0;
	public static final long REGROW = 72000L;

	/** One highland's elder: whether one is out, and when the next may grow. */
	public record Crown(int domain, boolean out, long next) {
		public static final Codec<Crown> CODEC = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("domain").forGetter(Crown::domain),
			Codec.BOOL.fieldOf("out").forGetter(Crown::out),
			Codec.LONG.fieldOf("next").forGetter(Crown::next)
		).apply(i, Crown::new));
	}

	public static final AttachmentType<List<Crown>> CROWNS = AttachmentRegistry.<List<Crown>>builder()
		.persistent(Crown.CODEC.listOf())
		.buildAndRegister(Mistinn.id("crown_elders"));

	private Elders() {
	}

	public static void register() {
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % 100 == 0) {
				tick(server);
			}
		});
	}

	public static @Nullable Crown crown(final MinecraftServer server, final int domain) {
		for (Crown crown : server.overworld().getAttachedOrElse(CROWNS, List.of())) {
			if (crown.domain() == domain) {
				return crown;
			}
		}
		return null;
	}

	private static void set(final MinecraftServer server, final Crown changed) {
		List<Crown> all = new ArrayList<>(server.overworld().getAttachedOrElse(CROWNS, List.of()));
		all.removeIf(crown -> crown.domain() == changed.domain());
		all.add(changed);
		server.overworld().setAttached(CROWNS, all);
	}

	private static void tick(final MinecraftServer server) {
		ServerLevel level = server.overworld();
		long now = level.getGameTime();
		for (Domain domain : DomainService.of(server, Domain.Kind.TERRA)) {
			Crown crown = crown(server, domain.id());
			if (crown != null && (crown.out() || now < crown.next())) {
				continue;
			}
			ServerPlayer near = null;
			for (ServerPlayer player : level.players()) {
				if (!player.isSpectator() && domain.distance(player.getX(), player.getZ()) <= WAKE) {
					near = player;
				}
			}
			if (near != null && level.isLoaded(domain.centre())) {
				spawn(level, domain);
			}
		}
	}

	/** The elder takes the air over the crown. */
	public static @Nullable CrystalElder spawn(final ServerLevel level, final Domain domain) {
		BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, domain.centre());
		CrystalElder elder = MistinnEntities.CRYSTAL_ELDER.create(level, EntitySpawnReason.EVENT);
		if (elder == null) {
			return null;
		}
		elder.snapTo(ground.getX() + 0.5, ground.getY() + 24.0, ground.getZ() + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
		elder.home(ground);
		level.addFreshEntity(elder);
		set(level.getServer(), new Crown(domain.id(), true, 0L));
		level.playSound(null, ground.getX(), ground.getY() + 24, ground.getZ(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 6.0F, 1.2F);
		for (ServerPlayer player : level.players()) {
			if (domain.distance(player.getX(), player.getZ()) <= WAKE * 1.5) {
				player.sendSystemMessage(Component.translatable("message.mistinn.elder.wakes").withStyle(ChatFormatting.AQUA));
			}
		}
		return elder;
	}

	/** An elder has fallen: its crown grows another in a while. */
	static void died(final ServerLevel level, final CrystalElder elder) {
		Domain domain = DomainService.at(level.getServer(), elder.getX(), elder.getZ());
		for (Domain each : DomainService.of(level.getServer(), Domain.Kind.TERRA)) {
			Crown crown = crown(level.getServer(), each.id());
			if (crown != null && crown.out() && (domain == null || domain.id() == each.id())) {
				set(level.getServer(), new Crown(each.id(), false, level.getGameTime() + REGROW));
				return;
			}
		}
	}
}
