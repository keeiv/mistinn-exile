package life.shizune.mistinn.client.epic;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.epic.LegacyService;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The epic's pack, as the hunter's own game keeps it: outside any world, in the game's folder (as the ledger is), so a
 * pack sealed in one world can be opened in another. One pack at a time; sealing again replaces it. A piece is struck
 * off the pack only once the server says it was given.
 */
public final class Legacy {
	/** The test hook: where the pack is kept instead (null: the game's folder). */
	public static @Nullable Path override;
	private static List<ItemStack> pack = List.of();
	private static boolean read;
	/** What the last answer said, to show on the pack's screen. */
	private static @Nullable Component notice;

	private Legacy() {
	}

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(LegacyService.SealedPayload.TYPE, (payload, context) -> {
			pack = List.copyOf(payload.pack());
			read = true;
			save();
			notice = Component.translatable("epic.mistinn.legacy.sealed", pack.size());
		});
		ClientPlayNetworking.registerGlobalReceiver(LegacyService.ClaimedPayload.TYPE, (payload, context) -> {
			if (payload.given()) {
				List<ItemStack> left = new ArrayList<>(pack());
				if (payload.index() >= 0 && payload.index() < left.size()) {
					left.remove(payload.index());
				}
				pack = List.copyOf(left);
				save();
				notice = Component.translatable("epic.mistinn.legacy.taken");
			} else {
				notice = Component.translatable(payload.why());
			}
		});
	}

	private static Path file() {
		return override != null ? override : FabricLoader.getInstance().getGameDir().resolve("mistinn").resolve("legacy.dat");
	}

	private static HolderLookup.@Nullable Provider registries() {
		Minecraft mc = Minecraft.getInstance();
		return mc.level != null ? mc.level.registryAccess() : null;
	}

	/** The pieces in the pack now. */
	public static List<ItemStack> pack() {
		if (!read) {
			read = true;
			load();
		}
		return pack;
	}

	/** What the last answer said, once. */
	public static @Nullable Component notice() {
		Component said = notice;
		notice = null;
		return said;
	}

	private static void load() {
		HolderLookup.Provider registries = registries();
		Path path = file();
		if (registries == null || !Files.exists(path)) {
			read = registries != null;
			return;
		}
		try {
			CompoundTag root = NbtIo.readCompressed(path, NbtAccounter.unlimitedHeap());
			RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
			List<ItemStack> found = new ArrayList<>();
			for (Tag entry : root.getListOrEmpty("pack")) {
				// a piece a later version no longer knows is left out, not lost the rest of the pack
				ItemStack.CODEC.parse(ops, entry).result().ifPresent(found::add);
			}
			pack = List.copyOf(found);
		} catch (IOException error) {
			Mistinn.LOGGER.warn("Could not read the epic's pack", error);
		}
	}

	private static void save() {
		HolderLookup.Provider registries = registries();
		if (registries == null) {
			return;
		}
		RegistryOps<Tag> ops = RegistryOps.create(NbtOps.INSTANCE, registries);
		ListTag list = new ListTag();
		for (ItemStack stack : pack) {
			ItemStack.CODEC.encodeStart(ops, stack).result().ifPresent(list::add);
		}
		CompoundTag root = new CompoundTag();
		root.put("pack", list);
		try {
			Files.createDirectories(file().getParent());
			NbtIo.writeCompressed(root, file());
		} catch (IOException error) {
			Mistinn.LOGGER.warn("Could not keep the epic's pack", error);
		}
	}

	/** Asks for a piece of the pack, by its place in it. */
	public static void claim(final int index) {
		List<ItemStack> now = pack();
		if (index >= 0 && index < now.size()) {
			ClientPlayNetworking.send(new LegacyService.ClaimPayload(index, now.get(index)));
		}
	}

	/** Forgets what was read, so the pack is read again (the test hook, and a new world's registries). */
	public static void reread() {
		read = false;
		pack = List.of();
	}
}
