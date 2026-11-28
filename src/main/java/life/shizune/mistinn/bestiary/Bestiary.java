package life.shizune.mistinn.bestiary;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import life.shizune.mistinn.registry.MistinnEntities;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import org.jspecify.annotations.Nullable;

/**
 * 獵物圖鑑: the creatures a hunter's guide keeps a page for, and what a hunter knows of each. A
 * creature met (it struck them, they struck it, it wound up a blow at them) shows its name, its
 * picture and what it is; one brought down tells its weakness; {@link #TELL_KILLS} tell its tell.
 * A keeper or other great foe brought down once tells everything.
 */
public final class Bestiary {
	/** How many of an ordinary creature must be brought down before its tell is known. */
	public static final int TELL_KILLS = 5;

	/** One creature's page: its type, and whether it is a great foe (a keeper, a herald, an elder). */
	public record Entry(EntityType<?> type, boolean great) {
		/** Its registry id, as the record keeps it. */
		public String id() {
			return EntityType.getKey(this.type).toString();
		}

		/** The last part of its id, for its words in the guide. */
		public String key() {
			return EntityType.getKey(this.type).getPath();
		}
	}

	/** How much of a creature's page is known. */
	public enum Detail {
		/** Met: its name, its picture and what it is. */
		NAME,
		/** One brought down: its weakness. */
		WEAK,
		/** Five brought down (one, for a great foe): its tell. */
		TELL
	}

	/** In the order the guide lists them: the shore, its keepers, the sea, the rift, Terra, and the dead and beasts of everywhere. */
	public static final List<Entry> ENTRIES = List.of(
		new Entry(MistinnEntities.SALT_CORPSE, false),
		new Entry(MistinnEntities.LAMP_THRALL, false),
		new Entry(MistinnEntities.MIST_SHADE, false),
		new Entry(MistinnEntities.SNUFFER, true),
		new Entry(MistinnEntities.SUNKEN_BELL, true),
		new Entry(MistinnEntities.RUST_WHEEL, true),
		new Entry(MistinnEntities.PIRATE, false),
		new Entry(MistinnEntities.RIFT_HOUND, false),
		new Entry(MistinnEntities.RIFT_WALKER, false),
		new Entry(MistinnEntities.RIFT_HERALD, true),
		new Entry(MistinnEntities.CRYSTAL_LIZARD, false),
		new Entry(MistinnEntities.CRYSTAL_DRAKE, false),
		new Entry(MistinnEntities.GATE_SENTRY, true),
		new Entry(MistinnEntities.CRYSTAL_ELDER, true),
		new Entry(EntityTypes.ZOMBIE, false),
		new Entry(EntityTypes.HUSK, false),
		new Entry(EntityTypes.DROWNED, false),
		new Entry(EntityTypes.SKELETON, false),
		new Entry(EntityTypes.SPIDER, false),
		new Entry(EntityTypes.CREEPER, false),
		new Entry(EntityTypes.ENDERMAN, false)
	);

	private Bestiary() {
	}

	/** The page for a creature of this type, or null when the guide keeps none. */
	public static @Nullable Entry of(final EntityType<?> type) {
		for (Entry entry : ENTRIES) {
			if (entry.type() == type) {
				return entry;
			}
		}
		return null;
	}

	/** Whether this much of a creature's page is known. */
	public static boolean knows(final Record record, final Entry entry, final Detail detail) {
		int kills = record.kills(entry.id());
		return switch (detail) {
			case NAME -> kills >= 0;
			case WEAK -> kills >= 1;
			case TELL -> kills >= (entry.great() ? 1 : TELL_KILLS);
		};
	}

	/**
	 * What a hunter has met and brought down: each creature's id, with how many they have brought
	 * down (0: met, none yet). A creature not in it has not been met.
	 */
	public record Record(Map<String, Integer> hunted) {
		public static final Record EMPTY = new Record(Map.of());
		public static final Codec<Record> CODEC = Codec.unboundedMap(Codec.STRING, Codec.INT).xmap(Record::new, Record::hunted);
		public static final StreamCodec<ByteBuf, Record> STREAM_CODEC = ByteBufCodecs.<ByteBuf, String, Integer, Map<String, Integer>>map(HashMap::new,
			ByteBufCodecs.STRING_UTF8, ByteBufCodecs.VAR_INT).map(Record::new, record -> new HashMap<>(record.hunted()));

		public Record {
			hunted = Map.copyOf(hunted);
		}

		/** How many brought down: -1 when not met at all. */
		public int kills(final String id) {
			Integer kills = this.hunted.get(id);
			return kills == null ? -1 : kills;
		}

		public boolean seen(final String id) {
			return this.hunted.containsKey(id);
		}

		/** How many creatures have been met. */
		public int size() {
			return this.hunted.size();
		}

		/** This record with the creature met and {@code more} more brought down. */
		public Record with(final String id, final int more) {
			Map<String, Integer> next = new HashMap<>(this.hunted);
			next.merge(id, more, Integer::sum);
			return new Record(next);
		}

		/** This record with at least {@code kills} of the creature brought down. */
		public Record atLeast(final String id, final int kills) {
			if (this.kills(id) >= kills) {
				return this;
			}
			Map<String, Integer> next = new HashMap<>(this.hunted);
			next.put(id, kills);
			return new Record(next);
		}
	}
}
