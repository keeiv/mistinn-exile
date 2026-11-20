package life.shizune.mistinn.bell;

import java.util.UUID;
import java.util.concurrent.TimeUnit;
import life.shizune.mistinn.registry.MistinnBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/** Stores the cooldown of a permanent bell or the claimant of a one-time bell. */
public final class BellBlockEntity extends BlockEntity {
	private static final long COOLDOWN_MS = TimeUnit.HOURS.toMillis(1);
	private long nextRingAt;
	private @Nullable UUID claimant;

	public BellBlockEntity(final BlockPos pos, final BlockState state) {
		super(MistinnBlockEntities.BELL, pos, state);
	}

	long remainingCooldown(final long now) {
		return Math.max(0L, this.nextRingAt - now);
	}

	void startCooldown(final long now) {
		this.nextRingAt = now + COOLDOWN_MS;
		this.setChanged();
	}

	public boolean claimed() {
		return this.claimant != null;
	}

	void claim(final UUID hunter) {
		this.claimant = hunter;
		this.setChanged();
	}

	boolean canReturn(final UUID hunter, final boolean legacyBinding) {
		return this.claimant == null ? legacyBinding : this.claimant.equals(hunter);
	}

	@Override
	protected void loadAdditional(final ValueInput input) {
		super.loadAdditional(input);
		this.nextRingAt = input.getLongOr("next_ring_at", 0L);
		this.claimant = input.read("claimant", UUIDUtil.CODEC).orElse(null);
	}

	@Override
	protected void saveAdditional(final ValueOutput output) {
		super.saveAdditional(output);
		if (this.nextRingAt != 0L) {
			output.putLong("next_ring_at", this.nextRingAt);
		}
		if (this.claimant != null) {
			output.store("claimant", UUIDUtil.CODEC, this.claimant);
		}
	}
}
