package io.github.foundationgames.splinecart.item;

import io.github.foundationgames.splinecart.Splinecart;
import io.github.foundationgames.splinecart.TrackType;
import io.github.foundationgames.splinecart.block.TrackTiesBlockEntity;
import io.github.foundationgames.splinecart.component.OriginComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TrackItem extends Item {
    public static final Map<TrackType, Item> ITEMS_BY_TYPE = new HashMap<>();

    public final TrackType track;

    public TrackItem(TrackType track, Settings settings) {
        super(settings);

        this.track = track;
        ITEMS_BY_TYPE.put(track, this);
    }

    @Override
    public ActionResult useOnBlock(ItemUsageContext context) {
        if (context.getPlayer() != null && !context.getPlayer().canModifyBlocks()) {
            return super.useOnBlock(context);
        }

        var world = context.getWorld();
        var pos = context.getBlockPos();
        var stack = context.getStack();

        if (world.getBlockEntity(pos) instanceof TrackTiesBlockEntity ties) {
            if (world.isClient()) {
                return ActionResult.SUCCESS;
            }

            var origin = stack.get(Splinecart.ORIGIN_POS);
            if (origin != null) {
                var oPos = origin.pos();
                if (!pos.equals(oPos) && world.getBlockEntity(oPos) instanceof TrackTiesBlockEntity oTies && oTies.next() == null && ties.prev() == null) {
                    oTies.setNext(pos, this.track);

                    world.playSound(null, pos, SoundEvents.ENTITY_IRON_GOLEM_REPAIR, SoundCategory.BLOCKS, 1.5f, 0.7f);
                }

                stack.remove(Splinecart.ORIGIN_POS);
            } else {
                stack.set(Splinecart.ORIGIN_POS, new OriginComponent(pos));
            }
        } else {
            var origin = stack.get(Splinecart.ORIGIN_POS);
            if (origin != null) {
                if (world.isClient()) {
                    return ActionResult.CONSUME;
                }

                stack.remove(Splinecart.ORIGIN_POS);
            }
        }

        return super.useOnBlock(context);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context, List<Text> tooltip, TooltipType type) {
        super.appendTooltip(stack, context, tooltip, type);

        var origin = stack.get(Splinecart.ORIGIN_POS);
        if (origin != null) {
            origin.appendTooltip(context, tooltip::add, type);
        }
    }
}
