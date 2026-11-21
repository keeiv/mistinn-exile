package life.shizune.mistinn.station;

import java.util.EnumSet;
import java.util.List;
import life.shizune.mistinn.training.TrainingGround;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A dock hand's round: walk to the next stop on their route, stand a while, go on. Leaving the
 * first stop they pick up a barrel and carry it to the next; coming back they are empty-handed.
 */
public final class RouteGoal extends Goal {
	private static final double SPEED = 0.7;
	private static final int PAUSE = 60;
	private static final int PAUSE_SPREAD = 60;
	private final StationHand hand;
	private int stop;
	private int resting;

	public RouteGoal(final StationHand hand) {
		this.hand = hand;
		this.setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	private List<BlockPos> route() {
		return TrainingGround.route(this.hand.role());
	}

	@Override
	public boolean canUse() {
		return this.route().size() > 1 && this.hand.level().dimension().equals(TrainingGround.KEY);
	}

	@Override
	public boolean canContinueToUse() {
		return this.canUse();
	}

	@Override
	public boolean requiresUpdateEveryTick() {
		return true;
	}

	@Override
	public void tick() {
		List<BlockPos> route = this.route();
		BlockPos target = route.get(this.stop % route.size());
		if (this.resting > 0) {
			this.resting--;
			return;
		}
		if (this.hand.distanceToSqr(target.getX() + 0.5, target.getY(), target.getZ() + 0.5) <= 2.25) {
			this.hand.getNavigation().stop();
			this.resting = PAUSE + this.hand.getRandom().nextInt(PAUSE_SPREAD);
			this.stop = (this.stop + 1) % route.size();
			// loaded leaving the first stop, empty-handed on the way back
			this.hand.setItemSlot(EquipmentSlot.MAINHAND, this.stop == 1 ? new ItemStack(Items.BARREL) : ItemStack.EMPTY);
			return;
		}
		if (this.hand.getNavigation().isDone()) {
			this.hand.getNavigation().moveTo(target.getX() + 0.5, target.getY(), target.getZ() + 0.5, SPEED);
		}
	}

	@Override
	public void stop() {
		this.hand.getNavigation().stop();
	}
}
