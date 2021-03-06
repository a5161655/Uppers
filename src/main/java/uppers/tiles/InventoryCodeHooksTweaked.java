package uppers.tiles;

import javax.annotation.Nullable;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.IHopper;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import uppers.blocks.BlockUpper;

public class InventoryCodeHooksTweaked
{
    /**
     * Copied from TileEntityUpper#captureDroppedItems and added capability support
     * @return Null if we did nothing {no IItemHandler}, True if we moved an item, False if we moved no items
     */
    @Nullable
    public static Boolean extractHook(IHopper dest)
    {
        Pair<IItemHandler, Object> itemHandlerResult = getItemHandler(dest, EnumFacing.DOWN);
        if (itemHandlerResult == null)
            return null;

        IItemHandler handler = itemHandlerResult.getKey();

        for (int i = 0; i < handler.getSlots(); i++)
        {
            ItemStack extractItem = handler.extractItem(i, 1, true);
            if (!extractItem.isEmpty())
            {
                for (int j = 0; j < dest.getSizeInventory(); j++)
                {
                    ItemStack destStack = dest.getStackInSlot(j);
                    if (dest.isItemValidForSlot(j, extractItem) && (destStack.isEmpty() || destStack.getCount() < destStack.getMaxStackSize() && destStack.getCount() < dest.getInventoryStackLimit() && ItemHandlerHelper.canItemStacksStack(extractItem, destStack)))
                    {
                        extractItem = handler.extractItem(i, 1, false);
                        if (destStack.isEmpty())
                            dest.setInventorySlotContents(j, extractItem);
                        else
                        {
                            destStack.grow(1);
                            dest.setInventorySlotContents(j, destStack);
                        }
                        dest.markDirty();
                        return true;
                    }
                }
            }
        }

        return false;
    }

    /**
     * Copied from TileEntityUpper#transferItemsOut and added capability support
     */
    public static boolean insertHook(TileEntityUpper tileEntityUpper)
    {
        EnumFacing upperFacing = BlockUpper.getFacing(tileEntityUpper.getBlockMetadata());
        Pair<IItemHandler, Object> destinationResult = getItemHandler(tileEntityUpper, upperFacing);
        if (destinationResult == null)
        {
            return false;
        }
        else
        {
            IItemHandler itemHandler = destinationResult.getKey();
            Object destination = destinationResult.getValue();
            if (isFull(itemHandler))
            {
                return false;
            }
            else
            {
                for (int i = 0; i < tileEntityUpper.getSizeInventory(); ++i)
                {
                    if (!tileEntityUpper.getStackInSlot(i).isEmpty())
                    {
                        ItemStack originalSlotContents = tileEntityUpper.getStackInSlot(i).copy();
                        ItemStack insertStack = tileEntityUpper.decrStackSize(i, 1);
                        ItemStack remainder = putStackInInventoryAllSlots(tileEntityUpper, destination, itemHandler, insertStack);

                        if (remainder.isEmpty())
                        {
                            return true;
                        }

                        tileEntityUpper.setInventorySlotContents(i, originalSlotContents);
                    }
                }

                return false;
            }
        }
    }

    private static ItemStack putStackInInventoryAllSlots(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack)
    {
        for (int slot = 0; slot < destInventory.getSlots() && !stack.isEmpty(); slot++)
        {
            stack = insertStack(source, destination, destInventory, stack, slot);
        }
        return stack;
    }

    /**
     * Copied from TileEntityUpper#insertStack and added capability support
     */
    private static ItemStack insertStack(TileEntity source, Object destination, IItemHandler destInventory, ItemStack stack, int slot)
    {
        ItemStack itemstack = destInventory.getStackInSlot(slot);

        if (destInventory.insertItem(slot, stack, true).isEmpty())
        {
            boolean insertedItem = false;
            boolean inventoryWasEmpty = isEmpty(destInventory);

            if (itemstack.isEmpty())
            {
                destInventory.insertItem(slot, stack, false);
                stack = ItemStack.EMPTY;
                insertedItem = true;
            }
            else if (ItemHandlerHelper.canItemStacksStack(itemstack, stack))
            {
                int originalSize = stack.getCount();
                stack = destInventory.insertItem(slot, stack, false);
                insertedItem = originalSize < stack.getCount();
            }

            if (insertedItem)
            {
                if (inventoryWasEmpty && destination instanceof TileEntityUpper)
                {
                    TileEntityUpper destinationUpper = (TileEntityUpper)destination;

                    if (!destinationUpper.mayTransfer())
                    {
                        int k = 0;

                        if (source instanceof TileEntityUpper)
                        {
                            if (destinationUpper.getLastUpdateTime() >= ((TileEntityUpper) source).getLastUpdateTime())
                            {
                                k = 1;
                            }
                        }

                        destinationUpper.setTransferCooldown(8 - k);
                    }
                }
            }
        }

        return stack;
    }

    @Nullable
    private static Pair<IItemHandler, Object> getItemHandler(IHopper upper, EnumFacing upperFacing)
    {
        double x = upper.getXPos() + (double) upperFacing.getFrontOffsetX();
        double y = upper.getYPos() + (double) upperFacing.getFrontOffsetY();
        double z = upper.getZPos() + (double) upperFacing.getFrontOffsetZ();
        return getItemHandler(upper.getWorld(), x, y, z, upperFacing.getOpposite());
    }

    private static boolean isFull(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.isEmpty() || stackInSlot.getCount() != stackInSlot.getMaxStackSize())
            {
                return false;
            }
        }
        return true;
    }

    private static boolean isEmpty(IItemHandler itemHandler)
    {
        for (int slot = 0; slot < itemHandler.getSlots(); slot++)
        {
            ItemStack stackInSlot = itemHandler.getStackInSlot(slot);
            if (stackInSlot.getCount() > 0)
            {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public static Pair<IItemHandler, Object> getItemHandler(World worldIn, double x, double y, double z, final EnumFacing side)
    {
        Pair<IItemHandler, Object> destination = null;
        int i = MathHelper.floor(x);
        int j = MathHelper.floor(y);
        int k = MathHelper.floor(z);
        BlockPos blockpos = new BlockPos(i, j, k);
        net.minecraft.block.state.IBlockState state = worldIn.getBlockState(blockpos);
        Block block = state.getBlock();

        if (block.hasTileEntity(state))
        {
            TileEntity tileentity = worldIn.getTileEntity(blockpos);
            if (tileentity != null)
            {
                if (tileentity.hasCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side))
                {
                    IItemHandler capability = tileentity.getCapability(CapabilityItemHandler.ITEM_HANDLER_CAPABILITY, side);
                    destination = ImmutablePair.<IItemHandler, Object>of(capability, tileentity);
                }
            }
        }

        return destination;
    }
}