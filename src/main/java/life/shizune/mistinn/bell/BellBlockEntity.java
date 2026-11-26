package life.shizune.mistinn.bell;

import com.mojang.serialization.Codec;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import life.shizune.mistinn.registry.MistinnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * What a golden bell remembers, for each hunter on their own: when a permanent bell will ring for
 * them again, and who has claimed a one-time bell (and who has already been brought back by it).
 */
public final class BellBlockEntity extends BlockEntity {
	static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(1);
	private static final Codec<Map<UUID, Long>> COOLDOWNS = Codec.unboundedMap(UUIDUtil.STRING_CODEC, Codec.LONG);
	private static final Codec<List<UUID>> HUNTERS = UUIDUtil.CODEC.listOf();

	/** Each hunter's next ring, as real time: a permanent bell cools for each of them apart. */
	private final Map<UUID, Long> nextRingAt = new HashMap<>();
	/** Hunters a one-time bell will still bring back. */
	private final Set<UUID> claimants = new LinkedHashSet<>();
	/** Hunters a one-time bell has already brought back: it will not take them again. */
	private final Set<UUID> spent = new LinkedHashSet<>();

	public BellBlockEntity(final BlockPos pos, final BlockState state) {
		super(MistinnBlockEntities.BELL, pos, state);
	}

	long remainingCooldown(final UUID hunter, final long now) {
		return Math.max(0L, this.nextRingAt.getOrDefault(hunter, 0L) - now);
	}

	void startCooldown(final UUID hunter, final long now) {
		this.nextRingAt.put(hunter, now + COOLDOWN_MS);
		this.setChanged();
	}

	/** Whether any hunter has claimed this one-time bell: such a bell cannot be picked up to reset it. */
	public boolean claimed() {
		return !this.claimants.isEmpty() || !this.spent.isEmpty();
	}

	/** Whether the hunter may still claim this one-time bell: once each, never again after it brought them back. */
	boolean mayClaim(final UUID hunter) {
		return !this.claimants.contains(hunter) && !this.spent.contains(hunter);
	}

	void claim(final UUID hunter) {
		this.claimants.add(hunter);
		this.setChanged();
	}

	boolean canReturn(final UUID hunter, final boolean legacyBinding) {
		return this.claimed() ? this.claimants.contains(hunter) : legacyBinding;
	}

	/** The bell brought the hunter back: their claim is spent. True when no one else holds one, and it cracks. */
	boolean spend(final UUID hunter) {
		this.claimants.remove(hunter);
		this.spent.add(hunter);
		this.setChanged();
		return this.claimants.isEmpty();
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.nextRingAt.clear();
		this.nextRingAt.putAll(input.read("cooldowns", COOLDOWNS).orElse(Map.of()));
		this.claimants.clear();
		this.claimants.addAll(input.read("claimants", HUNTERS).orElse(List.of()));
		// from before bells kept each hunter apart: the one who had claimed it still has it
		input.read("claimant", UUIDUtil.CODEC).ifPresent(this.claimants::add);
		this.spent.clear();
		this.spent.addAll(input.read("spent", HUNTERS).orElse(List.of()));
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		long now = System.currentTimeMillis();
		this.nextRingAt.values().removeIf(at -> at <= now);
		if (!this.nextRingAt.isEmpty()) {
			output.store("cooldowns", COOLDOWNS, Map.copyOf(this.nextRingAt));
		}
		if (!this.claimants.isEmpty()) {
			output.store("claimants", HUNTERS, List.copyOf(this.claimants));
		}
		if (!this.spent.isEmpty()) {
			output.store("spent", HUNTERS, List.copyOf(this.spent));
		}
	}
}
