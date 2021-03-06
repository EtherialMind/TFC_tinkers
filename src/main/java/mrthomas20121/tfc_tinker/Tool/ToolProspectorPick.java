package mrthomas20121.tfc_tinker.tool;

import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import mrthomas20121.tfc_tinker.toolparts.Parts;
import slimeknights.tconstruct.library.materials.HandleMaterialStats;
import slimeknights.tconstruct.library.materials.HeadMaterialStats;
import slimeknights.tconstruct.library.materials.MaterialTypes;
import slimeknights.tconstruct.library.tinkering.Category;
import slimeknights.tconstruct.library.tinkering.PartMaterialType;
import slimeknights.tconstruct.library.tools.AoeToolCore;
import slimeknights.tconstruct.library.materials.Material;
import slimeknights.tconstruct.library.tools.ToolNBT;

import net.minecraft.block.Block;
import net.minecraft.block.SoundType;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.*;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.dries007.tfc.ConfigTFC;
import net.dries007.tfc.TerraFirmaCraft;
import net.dries007.tfc.api.capability.player.CapabilityPlayerData;
import net.dries007.tfc.api.types.Ore;
import net.dries007.tfc.util.skills.ProspectingSkill;
import net.dries007.tfc.util.skills.SkillType;
import net.dries007.tfc.world.classic.worldgen.vein.VeinRegistry;
import net.dries007.tfc.world.classic.worldgen.vein.VeinType;
import slimeknights.tconstruct.tools.TinkerTools;

public class ToolProspectorPick extends AoeToolCore {
    private static final int COOLDOWN = 10;
    private static final Random RANDOM = new Random();
    private static final int PROSPECT_RADIUS = 12;
    public static final float DURABILITY_MODIFIER = 1.1f;

    public ToolProspectorPick() {
        super(PartMaterialType.handle(TinkerTools.toolRod),
                PartMaterialType.head(Parts.proPickHead),
                PartMaterialType.extra(TinkerTools.binding));
        addCategory(Category.TOOL);
    }

    @Override
    public ToolNBT buildTagData(List<Material> materials) {
        HandleMaterialStats handle = materials.get(0).getStatsOrUnknown(MaterialTypes.HANDLE);
        HeadMaterialStats head = materials.get(1).getStatsOrUnknown(MaterialTypes.HEAD);
        ToolNBT data = new ToolNBT();
        data.head(head);
        data.handle(handle);

        data.harvestLevel = head.harvestLevel;
        data.durability *= DURABILITY_MODIFIER;

        return data;
    }

    @Override
    public float damagePotential() {
        return 0.7f;
    }
    @Override
    public double attackSpeed() {
        return 0.9F;
    }
    @Override
    public float getRepairModifierForPart(int index) {
        return DURABILITY_MODIFIER;
    }

    @Override
    @Nonnull
    public EnumActionResult onItemUse(EntityPlayer player, World worldIn, BlockPos pos, EnumHand hand, @Nullable EnumFacing facing, float hitX, float hitY, float hitZ)
    {
        IBlockState state = worldIn.getBlockState(pos);
        if (facing != null)
        {
            SoundType soundType = state.getBlock().getSoundType(state, worldIn, pos, player);
            worldIn.playSound(player, pos, soundType.getHitSound(), SoundCategory.BLOCKS, 1.0f, soundType.getPitch());

            if (!worldIn.isRemote)
            {
                float falseNegativeChance = 0.3f; //Classic value was random(100) >= (60 + rank)
                ProspectingSkill skill = CapabilityPlayerData.getSkill(player, SkillType.PROSPECTING);
                if (skill != null)
                {
                    falseNegativeChance = 0.3f - (0.1f * skill.getTier().ordinal());
                }

                // Damage item and set cooldown
                player.getHeldItem(hand).damageItem(1, player);
                player.getCooldownTracker().setCooldown(this, COOLDOWN);

                RANDOM.setSeed(pos.toLong());
                ItemStack targetStack = getOreStack(worldIn, pos, state, false);
                if (!targetStack.isEmpty())
                {
                    // Just clicked on an ore block
                    player.sendStatusMessage(new TextComponentTranslation("tfc.propick.found", targetStack.getDisplayName()), ConfigTFC.Client.TOOLTIP.propickOutputToActionBar);

                    // Increment skill
                    if (skill != null)
                    {
                        skill.addSkill(pos);
                    }
                }
                else if (RANDOM.nextFloat() < falseNegativeChance)
                {
                    // False negative
                    player.sendStatusMessage(new TextComponentTranslation("tfc.propick.found_nothing"), ConfigTFC.Client.TOOLTIP.propickOutputToActionBar);
                }
                else
                {
                    Collection<ProspectResult> results = scanSurroundingBlocks(worldIn, pos);
                    if (results.isEmpty())
                    {
                        // Found nothing
                        player.sendStatusMessage(new TextComponentTranslation("tfc.propick.found_nothing"), ConfigTFC.Client.TOOLTIP.propickOutputToActionBar);
                    }
                    else
                    {
                        // Found something
                        ProspectResult result = (ProspectResult) results.toArray()[RANDOM.nextInt(results.size())];

                        String translationKey;
                        if (result.score < 10)
                        {
                            translationKey = "tfc.propick.found_traces";
                        }
                        else if (result.score < 20)
                        {
                            translationKey = "tfc.propick.found_small";
                        }
                        else if (result.score < 40)
                        {
                            translationKey = "tfc.propick.found_medium";
                        }
                        else if (result.score < 80)
                        {
                            translationKey = "tfc.propick.found_large";
                        }
                        else
                        {
                            translationKey = "tfc.propick.found_very_large";
                        }

                        player.sendStatusMessage(new TextComponentTranslation(translationKey, result.ore.getDisplayName()), ConfigTFC.Client.TOOLTIP.propickOutputToActionBar);

                        if (ConfigTFC.General.DEBUG.enable)
                        {
                            for (ProspectResult debugResult : results)
                            {
                                TerraFirmaCraft.getLog().debug(debugResult.ore.getDisplayName() + ": " + String.format("%.02f", debugResult.score));
                            }
                        }
                    }
                }
            }
            else
            {
                //client side, add hit particles
                addHitBlockParticle(worldIn, pos, facing, state);
            }
        }

        return EnumActionResult.SUCCESS;
    }

    /**
     * Loops through every block in a 25x25x25 cube around the center
     *
     * @param world  The world
     * @param center The center position
     * @return the collection of results
     */
    @Nonnull
    private Collection<ProspectResult> scanSurroundingBlocks(World world, BlockPos center)
    {
        Map<String, ProspectResult> results = new HashMap<>();
        for (BlockPos.MutableBlockPos pos : BlockPos.MutableBlockPos.getAllInBoxMutable(center.add(-PROSPECT_RADIUS, -PROSPECT_RADIUS, -PROSPECT_RADIUS), center.add(PROSPECT_RADIUS, PROSPECT_RADIUS, PROSPECT_RADIUS)))
        {
            ItemStack stack = getOreStack(world, pos, world.getBlockState(pos), true);
            if (!stack.isEmpty())
            {
                String oreName = stack.getDisplayName();
                if (results.containsKey(oreName))
                {
                    results.get(oreName).score += 1;
                }
                else
                {
                    results.put(oreName, new ProspectResult(stack, 1));
                }
            }
        }
        return results.values();
    }

    @Nonnull
    private ItemStack getOreStack(World world, BlockPos pos, IBlockState state, boolean ignoreGrade)
    {
        for (VeinType vein : VeinRegistry.INSTANCE.getVeins().values())
        {
            if (vein.isOreBlock(state))
            {
                Block block = state.getBlock();
                if (vein.getOre() != null && vein.getOre().isGraded() && !ignoreGrade)
                {
                    ItemStack result = block.getPickBlock(state, null, world, pos, null);
                    result.setItemDamage(Ore.Grade.NORMAL.getMeta()); // ignore grade
                    return result;
                }
                else
                {
                    return block.getPickBlock(state, null, world, pos, null);
                }
            }
        }
        return ItemStack.EMPTY;
    }
    private void addHitBlockParticle(World world, BlockPos pos, EnumFacing side, IBlockState state)
    {
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        AxisAlignedBB axisalignedbb = state.getBoundingBox(world, pos);
        for (int i = 0; i < 2; i++)
        {
            double xOffset = x + RANDOM.nextDouble() * (axisalignedbb.maxX - axisalignedbb.minX - 0.2D) + 0.1D + axisalignedbb.minX;
            double yOffset = y + RANDOM.nextDouble() * (axisalignedbb.maxY - axisalignedbb.minY - 0.2D) + 0.1D + axisalignedbb.minY;
            double zOffset = z + RANDOM.nextDouble() * (axisalignedbb.maxZ - axisalignedbb.minZ - 0.2D) + 0.1D + axisalignedbb.minZ;

            switch (side)
            {
                case WEST:
                    xOffset = x + axisalignedbb.minX - 0.1D;
                    break;
                case EAST:
                    xOffset = x + axisalignedbb.maxX + 0.1D;
                    break;
                case DOWN:
                    yOffset = y + axisalignedbb.minY - 0.1D;
                    break;
                case UP:
                    yOffset = y + axisalignedbb.maxY + 0.1D;
                    break;
                case NORTH:
                    zOffset = z + axisalignedbb.minZ - 0.1D;
                    break;
                case SOUTH:
                    zOffset = z + axisalignedbb.maxZ + 0.1D;
                    break;
            }

            world.spawnParticle(EnumParticleTypes.BLOCK_CRACK, xOffset, yOffset, zOffset, 0.0D, 0.0D, 0.0D, Block.getStateId(state));
        }
    }
    private static final class ProspectResult
    {
        private final ItemStack ore;
        private double score;

        ProspectResult(ItemStack itemStack, double num)
        {
            ore = itemStack;
            score = num;
        }
    }
}
