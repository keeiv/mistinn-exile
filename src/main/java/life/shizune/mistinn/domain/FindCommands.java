package life.shizune.mistinn.domain;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import life.shizune.mistinn.bounty.BountyService;
import life.shizune.mistinn.terra.Elders;
import life.shizune.mistinn.terra.HomesteadUpgrades;
import life.shizune.mistinn.terra.Settlements;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * Short commands, for any hunter, to find the world's places of change: {@code /terra} the nearest
 * Terra highland (and {@code /terra list} every one), {@code /rift} the nearest sky rift (one open
 * first, else a sealed scar), {@code /eco} both at once. Each says which way, how far, and where its
 * heart is; for a highland, whether its elder is out and whether its homestead is woken.
 */
public final class FindCommands {
	private FindCommands() {
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, context, selection) -> register(dispatcher));
	}

	private static void register(final CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(Commands.literal("terra")
			.executes(c -> say(c.getSource(), terra(c.getSource().getPlayerOrException(), false)))
			.then(Commands.literal("list").executes(c -> say(c.getSource(), terra(c.getSource().getPlayerOrException(), true)))));
		dispatcher.register(Commands.literal("rift").executes(c -> say(c.getSource(), rift(c.getSource().getPlayerOrException()))));
		dispatcher.register(Commands.literal("eco").executes(c -> {
			ServerPlayer player = c.getSource().getPlayerOrException();
			List<Component> lines = new ArrayList<>(terra(player, false));
			lines.addAll(rift(player));
			return say(c.getSource(), lines);
		}));
	}

	private static int say(final CommandSourceStack source, final List<Component> lines) throws CommandSyntaxException {
		for (Component line : lines) {
			source.sendSuccess(() -> line, false);
		}
		return lines.size();
	}

	/** Where the nearest Terra highland is (or, listing, every one), and how its elder and homestead stand. */
	public static List<Component> terra(final ServerPlayer player, final boolean all) {
		List<Component> lines = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		if (player.level().dimension() != Level.OVERWORLD) {
			lines.add(Component.translatable("message.mistinn.find.overworld").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		List<Domain> highlands = new ArrayList<>(DomainService.of(server, Domain.Kind.TERRA));
		if (highlands.isEmpty()) {
			lines.add(Component.translatable("message.mistinn.find.no_terra").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		highlands.sort(Comparator.comparingDouble(d -> d.distance(player.getX(), player.getZ())));
		if (all) {
			for (int i = 0; i < highlands.size(); i++) {
				Domain domain = highlands.get(i);
				lines.add(Component.translatable("message.mistinn.find.terra_each", i + 1, BountyService.direction(player, domain.centre()),
					far(player, domain), domain.centre().getX(), domain.centre().getZ()).withStyle(ChatFormatting.AQUA));
			}
			return lines;
		}
		Domain nearest = highlands.getFirst();
		lines.add(Component.translatable(inside(player, nearest) ? "message.mistinn.find.terra_here" : "message.mistinn.find.terra",
			BountyService.direction(player, nearest.centre()), far(player, nearest), nearest.centre().getX(), nearest.centre().getZ())
			.withStyle(ChatFormatting.AQUA));
		lines.add(Component.literal("  ").append(elder(server, player, nearest)).withStyle(ChatFormatting.GRAY));
		lines.add(Component.literal("  ").append(homestead(server, nearest)).withStyle(ChatFormatting.GRAY));
		return lines;
	}

	/** Where the nearest sky rift is: one hanging open first, else the nearest scar of a sealed one. */
	public static List<Component> rift(final ServerPlayer player) {
		List<Component> lines = new ArrayList<>();
		MinecraftServer server = player.level().getServer();
		if (player.level().dimension() != Level.OVERWORLD) {
			lines.add(Component.translatable("message.mistinn.find.overworld").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		Comparator<Domain> nearer = Comparator.comparingDouble(d -> d.distance(player.getX(), player.getZ()));
		List<Domain> rifts = DomainService.of(server, Domain.Kind.RIFT);
		Domain open = rifts.stream().filter(Domain::open).min(nearer).orElse(null);
		Domain scar = rifts.stream().filter(d -> d.phase() == Domain.Phase.SCAR).min(nearer).orElse(null);
		if (open == null && scar == null) {
			lines.add(Component.translatable("message.mistinn.find.no_rift").withStyle(ChatFormatting.GRAY));
			return lines;
		}
		if (open != null) {
			lines.add(Component.translatable("message.mistinn.find.rift_open", BountyService.direction(player, open.centre()), far(player, open),
				open.centre().getX(), open.centre().getZ()).withStyle(ChatFormatting.RED));
		}
		if (scar != null) {
			lines.add(Component.translatable("message.mistinn.find.rift_scar", BountyService.direction(player, scar.centre()), far(player, scar),
				scar.centre().getX(), scar.centre().getZ()).withStyle(ChatFormatting.DARK_RED));
		}
		return lines;
	}

	private static int far(final ServerPlayer player, final Domain domain) {
		return (int) Math.round(domain.distance(player.getX(), player.getZ()));
	}

	private static boolean inside(final ServerPlayer player, final Domain domain) {
		return domain.distance(player.getX(), player.getZ()) <= domain.radius();
	}

	private static MutableComponent elder(final MinecraftServer server, final ServerPlayer player, final Domain domain) {
		Elders.Crown crown = Elders.crown(server, domain.id());
		if (crown != null && crown.out()) {
			return Component.translatable("message.mistinn.find.elder_out");
		}
		long now = player.level().getGameTime();
		if (crown != null && now < crown.next()) {
			return Component.translatable("message.mistinn.find.elder_regrowing", Math.max(1, (crown.next() - now) / 1200L));
		}
		return Component.translatable("message.mistinn.find.elder_asleep");
	}

	private static MutableComponent homestead(final MinecraftServer server, final Domain domain) {
		Settlements.Settlement settlement = Settlements.of(server, domain.id());
		if (settlement == null) {
			return Component.translatable("message.mistinn.find.home_none");
		}
		if (!settlement.lit()) {
			return Component.translatable("message.mistinn.find.home_asleep", settlement.hub().getX(), settlement.hub().getZ());
		}
		return Component.translatable("message.mistinn.find.home_lit", HomesteadUpgrades.level(server, domain.id()), settlement.hub().getX(), settlement.hub().getZ());
	}
}
