package me.desht.sensibletoolbox.blocks;

import me.desht.dhutils.Debugger;
import me.desht.sensibletoolbox.SensibleToolboxPlugin;
import me.desht.sensibletoolbox.gui.AccessControlGadget;
import me.desht.sensibletoolbox.gui.ButtonGadget;
import me.desht.sensibletoolbox.gui.InventoryGUI;
import me.desht.sensibletoolbox.gui.LevelMonitor;
import me.desht.sensibletoolbox.items.BaseSTBItem;
import me.desht.sensibletoolbox.items.PaintBrush;
import me.desht.sensibletoolbox.util.RelativePosition;
import me.desht.sensibletoolbox.util.STBUtil;
import org.apache.commons.lang.StringUtils;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.material.Dye;
import org.bukkit.material.MaterialData;
import org.bukkit.material.Wool;

import java.util.HashMap;

public class PaintCan extends BaseSTBBlock {
    public static final int MAX_PAINT_LEVEL = 200;
    private static final int PAINT_PER_DYE = 25;
    private static final int[] ITEM_SLOTS = new int[]{0, 1};
    private static final ItemStack MIX_TEXTURE = new ItemStack(Material.GOLD_SPADE);
    private static final ItemStack EMPTY_TEXTURE = STBUtil.makeColouredMaterial(Material.STAINED_GLASS, DyeColor.WHITE).toItemStack();
    private int paintLevel;
    private DyeColor colour;
    private int levelMonitorId;

    public PaintCan() {
        paintLevel = 0;
        colour = DyeColor.WHITE;
    }

    public PaintCan(ConfigurationSection conf) {
        super(conf);
        setPaintLevel(conf.getInt("paintLevel"));
        setColour(DyeColor.valueOf(conf.getString("paintColour")));
    }

    public static int getMaxPaintLevel() {
        return MAX_PAINT_LEVEL;
    }

    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        conf.set("paintColour", getColour().toString());
        conf.set("paintLevel", getPaintLevel());
        return conf;
    }

    public int getPaintLevel() {
        return paintLevel;
    }

    public void setPaintLevel(int paintLevel) {
        int oldLevel = this.paintLevel;
        this.paintLevel = paintLevel;
        updateBlock(oldLevel == 0 && paintLevel != 0 || oldLevel != 0 && paintLevel == 0);
        updateAttachedLabelSigns();
        if (getPaintLevelMonitor() != null) {
            getPaintLevelMonitor().repaint();
        }
    }

    public DyeColor getColour() {
        return colour;
    }

    public void setColour(DyeColor colour) {
        DyeColor oldColour = this.colour;
        this.colour = colour;
        if (this.colour != oldColour) {
            updateBlock(true);
            updateAttachedLabelSigns();
            if (getPaintLevelMonitor() != null) {
                getPaintLevelMonitor().repaint();
            }
        }
    }

    @Override
    public MaterialData getMaterialData() {
        return getPaintLevel() > 0 ? new Wool(colour) : STBUtil.makeColouredMaterial(Material.STAINED_GLASS, colour);
    }

    @Override
    public String getItemName() {
        return "Paint Can";
    }

    @Override
    public String[] getLore() {
        return new String[]{
                "R-click block with Paint Brush",
                " to refill the brush",
                "R-click block with anything else",
                " to open mixer; place milk bucket and",
                " a dye inside to mix some paint"
        };
    }

    @Override
    public Recipe getRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(toItemStack());
        recipe.shape("ISI", "I I", "III");
        recipe.setIngredient('S', Material.WOOD_STEP);
        recipe.setIngredient('I', Material.IRON_INGOT);
        return recipe;
    }

    @Override
    public void onInteractBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack stack = player.getItemInHand();
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            PaintBrush brush = BaseSTBItem.fromItemStack(stack, PaintBrush.class);
            if (brush == null) {
                // refilling a paintbrush/roller from the can is handled in the PaintBrush object
                getGUI().show(player);
                getPaintLevelMonitor().repaint();
            }
            event.setCancelled(true);
        } else {
            super.onInteractBlock(event);
        }
    }

    @Override
    public InventoryGUI createGUI() {
        InventoryGUI gui = new InventoryGUI(this, 9, ChatColor.DARK_RED + getItemName());
        for (int slot : ITEM_SLOTS) {
            gui.setSlotType(slot, InventoryGUI.SlotType.ITEM);
        }
        gui.addGadget(new ButtonGadget(gui, 3, "Mix/Dye", null, MIX_TEXTURE, new Runnable() {
            @Override
            public void run() {
                if (tryMix()) {
                    Location loc = getLocation();
                    loc.getWorld().playSound(loc, Sound.SPLASH, 1.0f, 1.0f);
                }
            }
        }));
        gui.addGadget(new ButtonGadget(gui, 4, "! Empty Paint !", null, EMPTY_TEXTURE, new Runnable() {
            @Override
            public void run() {
                emptyPaintCan();
            }
        }));
        levelMonitorId = gui.addMonitor(new LevelMonitor(gui, new LevelMonitor.LevelReporter() {
            @Override
            public int getLevel() {
                return getPaintLevel();
            }

            @Override
            public int getMaxLevel() {
                return MAX_PAINT_LEVEL;
            }

            @Override
            public Material getIcon() {
                return Material.DIAMOND_LEGGINGS;
            }

            @Override
            public int getSlot() {
                return 6;
            }

            @Override
            public String getMessage() {
                ChatColor cc = STBUtil.dyeColorToChatColor(getColour());
                return ChatColor.WHITE + "Paint Level: " + getPaintLevel() + "/" + MAX_PAINT_LEVEL + " " + cc + getColour();
            }
        }));
        gui.addGadget(new AccessControlGadget(gui, 8));
        return gui;
    }

    private void emptyPaintCan() {
        setPaintLevel(0);
        Location loc = getLocation();
        loc.getWorld().playSound(loc, Sound.SPLASH2, 1.0f, 1.0f);
    }

    private LevelMonitor getPaintLevelMonitor() {
        return getGUI() == null ? null : (LevelMonitor) getGUI().getMonitor(levelMonitorId);
    }

    private boolean validItem(ItemStack item) {
        return STBUtil.isColorable(item.getType()) ||
                item.getType() == Material.MILK_BUCKET || item.getType() == Material.INK_SACK ||
                item.getType() == Material.GLASS || item.getType() == Material.THIN_GLASS;
    }

    @Override
    public boolean onSlotClick(HumanEntity player, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        return onCursor.getType() == Material.AIR || validItem(onCursor);
    }

    @Override
    public boolean onPlayerInventoryClick(HumanEntity player, int slot, ClickType click, ItemStack inSlot, ItemStack onCursor) {
        return true;
    }

    @Override
    public int onShiftClickInsert(HumanEntity player, int slot, ItemStack toInsert) {
        if (!validItem(toInsert)) {
            return 0;
        } else {
            HashMap<Integer, ItemStack> excess = getGUI().getInventory().addItem(toInsert);
            int inserted = toInsert.getAmount();
            for (ItemStack stack : excess.values()) {
                inserted -= stack.getAmount();
            }
            return inserted;
        }
    }

    @Override
    public boolean onShiftClickExtract(HumanEntity player, int slot, ItemStack toExtract) {
        return true;
    }

    @Override
    public boolean onClickOutside(HumanEntity player) {
        return false;
    }

    @Override
    public void onGUIClosed(HumanEntity player) {
        if (getGUI().getViewers().size() == 1) {
            // last player closing inventory - eject any remaining items
            Location loc = getLocation();
            for (int slot : ITEM_SLOTS) {
                ItemStack item = getGUI().getInventory().getItem(slot);
                if (item != null) {
                    loc.getWorld().dropItemNaturally(getLocation(), item);
                    getGUI().getInventory().setItem(slot, null);
                }
            }
        }
    }

    @Override
    public String getDisplaySuffix() {
        return getPaintLevel() + " " + STBUtil.dyeColorToChatColor(getColour()) + getColour();
    }

    @Override
    public RelativePosition[] getBlockStructure() {
        return new RelativePosition[]{new RelativePosition(0, 1, 0)};
    }

    @Override
    public void onBlockPlace(BlockPlaceEvent event) {
        super.onBlockPlace(event);
        if (!event.isCancelled()) {
            Block above = event.getBlock().getRelative(BlockFace.UP);
            Skull skull = STBUtil.setSkullHead(above, "MHF_OakLog", event.getPlayer());
            skull.update();
        }
    }

    @Override
    protected String[] getSignLabel() {
        String res[] = new String[4];
        ChatColor cc = STBUtil.dyeColorToChatColor(getColour());
        res[0] = getItemName();
        res[1] = cc.toString() + getColour();
        res[2] = getPaintLevel() + "/" + getMaxPaintLevel();
        res[3] = cc + StringUtils.repeat("\u25fc", (getPaintLevel() * 13) / getMaxPaintLevel());
        return res;
    }

    /**
     * Attempt to refill the can from the contents of the can's inventory.  The mixer needs to find
     * a milk bucket and at least one dye.  If mixing is successful, the bucket is replaced with an
     * empty bucket and dye is removed, and the method returns true.
     *
     * @return true if mixing was successful, false otherwise
     */
    public boolean tryMix() {
        int bucketSlot = -1;
        int dyeSlot = -1;
        int dyeableSlot = -1;

        Inventory inventory = getGUI().getInventory();

        // first try to find a milk bucket, dye and/or wool
        for (int slot : ITEM_SLOTS) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null) {
                if (stack.getType() == Material.MILK_BUCKET && bucketSlot == -1) {
                    bucketSlot = slot;
                } else if (stack.getType() == Material.INK_SACK && dyeSlot == -1) {
                    dyeSlot = slot;
                } else if (validItem(stack) && dyeableSlot == -1) {
                    dyeableSlot = slot;
                } else {
                    // not an item we want - eject it
                    getLocation().getWorld().dropItemNaturally(getLocation(), stack);
                    inventory.setItem(slot, null);
                }
            }
        }
        Debugger.getInstance().debug(this + ": dyeable=" + dyeableSlot + " dye=" + dyeSlot + " milk=" + bucketSlot);

        if (bucketSlot >= 0 && dyeSlot >= 0) {
            // we have milk & some dye - mix it up!
            ItemStack dyeStack = inventory.getItem(dyeSlot);
            Dye dye = (Dye) dyeStack.getData();
            DyeColor newColour = dye.getColor();
            int dyeAmount = dyeStack.getAmount();
            int paintPerDye = SensibleToolboxPlugin.getInstance().getConfig().getInt("paint_per_dye", PAINT_PER_DYE);
            int toUse = Math.min((getMaxPaintLevel() - getPaintLevel()) / paintPerDye, dyeAmount);
            if (toUse == 0) {
                // not enough room for any mixing
                return false;
            }
            if (getColour() != newColour && getPaintLevel() > 0) {
                // two different colours - do they mix?
                DyeColor mixedColour = mixDyes(getColour(), newColour);
                if (mixedColour == null) {
                    // no - just replace the can's contents with the new colour
                    toUse = Math.min(getMaxPaintLevel() / paintPerDye, dyeAmount);
                    setColour(newColour);
                    setPaintLevel(paintPerDye * toUse);
                } else {
                    // yes, they mix
                    setColour(mixedColour);
                    setPaintLevel(Math.min(getMaxPaintLevel(), getPaintLevel() + paintPerDye * toUse));
                }
            } else {
                // either adding to an empty can, or adding more of the same colour
                setColour(newColour);
                setPaintLevel(Math.min(getMaxPaintLevel(), getPaintLevel() + paintPerDye * toUse));
            }
            Debugger.getInstance().debug(this + ": paint mixed! now " + getPaintLevel() + " " + getColour());

            Location loc = getLocation();
            loc.getWorld().playSound(loc, Sound.SPLASH, 1.0f, 1.0f);

            inventory.setItem(bucketSlot, new ItemStack(Material.BUCKET));
            dyeStack.setAmount(dyeStack.getAmount() - toUse);
            inventory.setItem(dyeSlot, dyeStack.getAmount() > 0 ? dyeStack : null);

            return true;
        } else if (dyeableSlot >= 0 && getPaintLevel() > 0) {
            // soak up some paint with the dyeable item(s)
            int toDye = inventory.getItem(dyeableSlot).getAmount();
            Debugger.getInstance().debug(this + ": dyeing " + inventory.getItem(dyeableSlot));
            int canDye = Math.min(toDye, getPaintLevel());
            ItemStack undyed = inventory.getItem(dyeableSlot).getData().toItemStack(inventory.getItem(dyeableSlot).getAmount());
            ItemStack dyed = STBUtil.makeColouredMaterial(undyed.getType(), getColour()).toItemStack(Math.min(canDye, undyed.getAmount()));
            undyed.setAmount(undyed.getAmount() - dyed.getAmount());
            inventory.setItem(0, dyed.getAmount() > 0 ? dyed : null);
            inventory.setItem(1, undyed.getAmount() > 0 ? undyed : null);
            setPaintLevel(getPaintLevel() - canDye);
            Location loc = getLocation();
            loc.getWorld().playSound(loc, Sound.SPLASH, 1.0f, 1.0f);
            return true;
        } else {
            return false;
        }
    }

    private DyeColor mixDyes(DyeColor dye1, DyeColor dye2) {
        if (dye1.compareTo(dye2) > 0) {
            DyeColor tmp = dye2;
            dye2 = dye1;
            dye1 = tmp;
        } else if (dye1 == dye2) {
            return dye1;
        }
        Debugger.getInstance().debug(this + ": try mixing: " + dye1 + " " + dye2);
        if (dye1 == DyeColor.YELLOW && dye2 == DyeColor.RED) {
            return DyeColor.ORANGE;
        } else if (dye1 == DyeColor.WHITE && dye2 == DyeColor.RED) {
            return DyeColor.PINK;
        } else if (dye1 == DyeColor.BLUE && dye2 == DyeColor.GREEN) {
            return DyeColor.CYAN;
        } else if (dye1 == DyeColor.BLUE && dye2 == DyeColor.RED) {
            return DyeColor.PURPLE;
        } else if (dye1 == DyeColor.WHITE && dye2 == DyeColor.BLACK) {
            return DyeColor.GRAY;
        } else if (dye1 == DyeColor.WHITE && dye2 == DyeColor.BLUE) {
            return DyeColor.LIGHT_BLUE;
        } else if (dye1 == DyeColor.WHITE && dye2 == DyeColor.GREEN) {
            return DyeColor.LIME;
        } else if (dye1 == DyeColor.PINK && dye2 == DyeColor.PURPLE) {
            return DyeColor.MAGENTA;
        } else if (dye1 == DyeColor.WHITE && dye2 == DyeColor.GRAY) {
            return DyeColor.SILVER;
        } else {
            // colours don't mix
            return null;
        }
    }
}
