package life.shizune.mistinn.client.guide;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import life.shizune.mistinn.Mistinn;
import life.shizune.mistinn.medical.RepairKitItem;
import life.shizune.mistinn.medical.TreatmentItem;
import life.shizune.mistinn.weapon.Weapons;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The guide's crafting pages, read from the mod's own recipe files so they always say what the game
 * does: every recipe's grid, smithing or cooking, and what it makes. Cooking the same thing in the
 * furnace, the smoker and on the campfire is one page.
 */
public final class GuideRecipes {
	/** How a recipe is made. */
	public enum Kind {
		SHAPED, SHAPELESS, SMITHING, COOKING
	}

	/** The chapters of the crafting pages, in order. */
	public enum Chapter {
		WEAPONS, GEAR, MEDICINE, REPAIR, FOOD, OTHER;

		public String key() {
			return "gui.mistinn.guide.recipes." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** One ingredient: a single item or a tag of them (shown in turn). */
	public record Ingredient(String id) {
		/** The items it stands for, resolved now (tags need a world). */
		public List<Item> items() {
			if (this.id.startsWith("#")) {
				TagKey<Item> tag = TagKey.create(Registries.ITEM, Identifier.parse(this.id.substring(1)));
				List<Item> items = new ArrayList<>();
				for (Holder<Item> holder : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
					items.add(holder.value());
				}
				return items;
			}
			Optional<Item> item = BuiltInRegistries.ITEM.getOptional(Identifier.parse(this.id));
			return item.map(List::of).orElse(List.of());
		}

		/** The item to draw at this moment: tags cycle once a second. */
		public ItemStack shown(final long millis) {
			List<Item> items = this.items();
			return items.isEmpty() ? ItemStack.EMPTY : new ItemStack(items.get((int) (millis / 1000L % items.size())));
		}
	}

	/**
	 * One page: the recipe's file name, how it is made, its 3x3 grid (row by row, null for an empty
	 * cell) or list of ingredients, what it makes and how many, and for cooking the ways it can be cooked.
	 */
	public record Entry(String id, Kind kind, List<@Nullable Ingredient> grid, List<Ingredient> list, Item result, int count, Set<String> cookedIn) {
		public Chapter chapter() {
			ItemStack stack = new ItemStack(this.result);
			if (Weapons.profile(this.result) != null) {
				return Chapter.WEAPONS;
			}
			String path = BuiltInRegistries.ITEM.getKey(this.result).getPath();
			if (this.result instanceof RepairKitItem) {
				return Chapter.REPAIR;
			}
			if (this.result instanceof TreatmentItem) {
				return Chapter.MEDICINE;
			}
			if (path.startsWith("helmet_") || path.startsWith("armor_") || path.startsWith("leggings_") || path.startsWith("boots_")
				|| path.startsWith("mask_") || path.startsWith("rig_") || path.startsWith("backpack_")) {
				return Chapter.GEAR;
			}
			if (stack.has(DataComponents.FOOD)) {
				return Chapter.FOOD;
			}
			return Chapter.OTHER;
		}
	}

	private static @Nullable List<Entry> entries;

	private GuideRecipes() {
	}

	/** Every page, chapter by chapter, in the order the files sort within a chapter. */
	public static List<Entry> entries() {
		if (entries == null) {
			entries = load();
		}
		return entries;
	}

	/** The first page of each chapter that has any. */
	public static Map<Chapter, Integer> chapters() {
		Map<Chapter, Integer> first = new LinkedHashMap<>();
		List<Entry> all = entries();
		for (int i = 0; i < all.size(); i++) {
			first.putIfAbsent(all.get(i).chapter(), i);
		}
		return first;
	}

	private static List<Entry> load() {
		Optional<ModContainer> mod = FabricLoader.getInstance().getModContainer(Mistinn.MOD_ID);
		Optional<Path> folder = mod.flatMap(container -> container.findPath("data/" + Mistinn.MOD_ID + "/recipe"));
		if (folder.isEmpty()) {
			return List.of();
		}
		Map<String, Entry> byKey = new LinkedHashMap<>();
		try (Stream<Path> files = Files.list(folder.get())) {
			for (Path file : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
				try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
					String name = file.getFileName().toString();
					Entry entry = parse(name.substring(0, name.length() - 5), JsonParser.parseReader(reader).getAsJsonObject());
					if (entry == null) {
						continue;
					}
					if (entry.kind() == Kind.COOKING) {
						// the furnace, the smoker and the campfire cook it alike: one page
						String key = "cook:" + entry.list().getFirst().id() + ">" + BuiltInRegistries.ITEM.getKey(entry.result());
						Entry known = byKey.get(key);
						if (known != null) {
							Set<String> ways = new java.util.TreeSet<>(known.cookedIn());
							ways.addAll(entry.cookedIn());
							entry = new Entry(known.id(), known.kind(), known.grid(), known.list(), known.result(), known.count(), Set.copyOf(ways));
						}
						byKey.put(key, entry);
					} else {
						byKey.put(entry.id(), entry);
					}
				}
			}
		} catch (IOException | RuntimeException e) {
			Mistinn.LOGGER.warn("The guide could not read the recipes", e);
		}
		List<Entry> all = new ArrayList<>(byKey.values());
		all.sort(Comparator.comparing(Entry::chapter));
		return List.copyOf(all);
	}

	private static @Nullable Entry parse(final String id, final JsonObject json) {
		String type = json.get("type").getAsString();
		JsonObject result = json.getAsJsonObject("result");
		Optional<Item> made = BuiltInRegistries.ITEM.getOptional(Identifier.parse(result.get("id").getAsString()));
		if (made.isEmpty()) {
			return null;
		}
		int count = result.has("count") ? result.get("count").getAsInt() : 1;
		return switch (type) {
			case "minecraft:crafting_shaped" -> {
				JsonObject key = json.getAsJsonObject("key");
				JsonArray pattern = json.getAsJsonArray("pattern");
				List<@Nullable Ingredient> grid = new ArrayList<>();
				for (int row = 0; row < 3; row++) {
					String line = row < pattern.size() ? pattern.get(row).getAsString() : "";
					for (int col = 0; col < 3; col++) {
						char c = col < line.length() ? line.charAt(col) : ' ';
						grid.add(c == ' ' || !key.has(String.valueOf(c)) ? null : ingredient(key.get(String.valueOf(c))));
					}
				}
				yield new Entry(id, Kind.SHAPED, grid, List.of(), made.get(), count, Set.of());
			}
			case "minecraft:crafting_shapeless" -> {
				List<Ingredient> list = new ArrayList<>();
				for (JsonElement element : json.getAsJsonArray("ingredients")) {
					list.add(ingredient(element));
				}
				List<@Nullable Ingredient> grid = new ArrayList<>();
				for (int i = 0; i < 9; i++) {
					grid.add(i < list.size() ? list.get(i) : null);
				}
				yield new Entry(id, Kind.SHAPELESS, grid, list, made.get(), count, Set.of());
			}
			case "minecraft:smithing_transform" -> new Entry(id, Kind.SMITHING, List.of(),
				List.of(ingredient(json.get("template")), ingredient(json.get("base")), ingredient(json.get("addition"))), made.get(), count, Set.of());
			case "minecraft:smelting", "minecraft:smoking", "minecraft:campfire_cooking", "minecraft:blasting" -> new Entry(id, Kind.COOKING, List.of(),
				List.of(ingredient(json.get("ingredient"))), made.get(), count, Set.of(type.substring("minecraft:".length())));
			default -> null;
		};
	}

	private static Ingredient ingredient(final JsonElement element) {
		if (element.isJsonPrimitive()) {
			return new Ingredient(element.getAsString());
		}
		if (element.isJsonArray()) {
			return ingredient(element.getAsJsonArray().get(0));
		}
		JsonObject object = element.getAsJsonObject();
		return new Ingredient(object.has("tag") ? "#" + object.get("tag").getAsString() : object.get("item").getAsString());
	}

	/** The ways a cooking page names, in the order shown. */
	public static final List<String> COOKING = List.of("smelting", "smoking", "campfire_cooking", "blasting");
}
