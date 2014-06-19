package me.desht.sensibletoolbox.blocks.machines;

import me.desht.dhutils.Debugger;
import me.desht.dhutils.ItemNames;
import me.desht.sensibletoolbox.SensibleToolboxPlugin;
import me.desht.sensibletoolbox.gui.InventoryGUI;
import me.desht.sensibletoolbox.gui.ToggleButton;
import me.desht.sensibletoolbox.util.BukkitSerialization;
import me.desht.sensibletoolbox.util.STBUtil;
import org.apache.commons.lang.WordUtils;
import org.bukkit.*;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.material.MaterialData;
import org.bukkit.metadata.FixedMetadataValue;

import java.io.IOException;
import java.util.UUID;

public class BigStorageUnit extends AbstractProcessingMachine {
    private static final ItemStack LOCKED_BUTTON = InventoryGUI.makeTexture(new MaterialData(Material.EYE_OF_ENDER),
            ChatColor.UNDERLINE + "Locked", "Unit will remember its", "stored item, even when", "emptied");
    private static final ItemStack UNLOCKED_BUTTON = InventoryGUI.makeTexture(new MaterialData(Material.ENDER_PEARL),
            ChatColor.UNDERLINE + "Unlocked", "Unit will forget its stored", "item when emptied");
    private static final MaterialData md = STBUtil.makeLog(TreeSpecies.DARK_OAK);
    private static final String STB_LAST_BSU_INSERT = "STB_Last_BSU_Insert";
    private static final long DOUBLE_CLICK_TIME = 200L;
    private ItemStack stored;
    private int storageAmount;
    private int outputAmount;
    private int maxCapacity;
    private final String signLabel[] = new String[4];
    private int oldTotalAmount = -1;
    private boolean locked;

    public BigStorageUnit() {
        super();
        locked = false;
        setStoredItemType(null);
        signLabel[0] = makeItemLabel();
        oldTotalAmount = storageAmount = outputAmount = 0;
    }

    public BigStorageUnit(ConfigurationSection conf) {
        super(conf);
        signLabel[0] = makeItemLabel();
        try {
            Inventory inv = BukkitSerialization.fromBase64(conf.getString("stored"));
            setStoredItemType(inv.getItem(0));
        } catch (IOException e) {
            e.printStackTrace();
        }
        setStorageAmount(conf.getInt("amount"));
        locked = conf.getBoolean("locked", false);
        oldTotalAmount = storageAmount;
    }

    @Override
    public YamlConfiguration freeze() {
        YamlConfiguration conf = super.freeze();
        Inventory inv = Bukkit.createInventory(null, 9);
        inv.setItem(0, stored);
        conf.set("stored", BukkitSerialization.toBase64(inv, 1));
        conf.set("amount", storageAmount);
        conf.set("locked", locked);
        return conf;
    }

    public void setStorageAmount(int storageAmount) {
        this.storageAmount = Math.max(0, storageAmount);
    }

    public int getStorageAmount() {
        return storageAmount;
    }

    public int getOutputAmount() {
        return outputAmount;
    }

    public void setOutputAmount(int outputAmount) {
        this.outputAmount = outputAmount;
    }

    public int getTotalAmount() {
        return getStorageAmount() + getOutputAmount();
    }

    public ItemStack getStoredItemType() {
        return stored;
    }

    public boolean isLocked() {
        return locked;
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        updateSignQuantityLine();
        if (getTotalAmount() == 0 && !isLocked()) {
            setStoredItemType(null);
        }
        updateAttachedLabelSigns();
    }

    public void setStoredItemType(ItemStack stored) {
        Debugger.getInstance().debug(this + " set stored item = " + stored);
        if (stored != null) {
            this.stored = stored.clone();
            this.stored.setAmount(1);
        } else if (!isLocked()) {
            this.stored = null;
        }
        maxCapacity = getStackCapacity() * (this.stored == null ? 64 : this.stored.getMaxStackSize());
        updateSignItemLines();
    }

    private void updateSignQuantityLine() {
        if (isLocked()) {
            signLabel[1] = ChatColor.DARK_RED + Integer.toString(getTotalAmount());
        } else {
            signLabel[1] = getTotalAmount() > 0 ? Integer.toString(getTotalAmount()) : "";
        }
    }

    private void updateSignItemLines() {
        if (this.stored != null) {
            String[] lines = WordUtils.wrap(ItemNames.lookup(this.stored), 15).split("\\n");
            signLabel[2] = lines[0];
            if (lines.length > 1) {
                signLabel[3] = lines[1];
            }
        } else {
            signLabel[2] = ChatColor.ITALIC + "Empty";
            signLabel[3] = "";
        }
    }

    @Override
    public int[] getInputSlots() {
        return new int[]{10};
    }

    @Override
    public int[] getOutputSlots() {
        return new int[]{14};
    }

    @Override
    public int[] getUpgradeSlots() {
        // no upgrades at this time (maybe in future)
        return new int[0];
    }

    @Override
    public int getUpgradeLabelSlot() {
        return -1;
    }

    @Override
    public int getInventoryGUISize() {
        return 36;
    }

    @Override
    protected void playActiveParticleEffect() {
        // nothing
    }

    @Override
    public MaterialData getMaterialData() {
        return md;
    }

    @Override
    public String getItemName() {
        return "BSU";
    }

    @Override
    public String[] getLore() {
        return new String[]{"Big Storage Unit", "Stores up to " + getStackCapacity() + " stacks", "of a single item type"};
    }

    @Override
    public String[] getExtraLore() {
        if (isLocked() && getStoredItemType() != null) {
            return new String[]{ChatColor.WHITE + "Locked: " + ChatColor.YELLOW + ItemNames.lookup(getStoredItemType())};
        } else {
            return new String[0];
        }
    }

    @Override
    public Recipe getRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(toItemStack());
        recipe.shape("LSL", "L L", "LLL");
        recipe.setIngredient('L', STBUtil.makeWildCardMaterialData(Material.LOG));
        recipe.setIngredient('S', STBUtil.makeWildCardMaterialData(Material.WOOD_STEP));
        return recipe;
    }

    @Override
    public Recipe[] getExtraRecipes() {
        ShapedRecipe recipe = new ShapedRecipe(toItemStack());
        recipe.shape("LSL", "L L", "LLL");
        recipe.setIngredient('L', STBUtil.makeWildCardMaterialData(Material.LOG_2));
        recipe.setIngredient('S', STBUtil.makeWildCardMaterialData(Material.WOOD_STEP));
        return new Recipe[]{recipe};
    }

    @Override
    public boolean acceptsEnergy(BlockFace face) {
        return false;
    }

    @Override
    public boolean suppliesEnergy(BlockFace face) {
        return false;
    }

    @Override
    public int getMaxCharge() {
        return 0;
    }

    @Override
    public int getChargeRate() {
        return 0;
    }

    public int getStackCapacity() {
        return 128;
    }

    @Override
    public int getTickRate() {
        return 5;
    }

    @Override
    protected InventoryGUI createGUI() {
        InventoryGUI gui = super.createGUI();

        gui.addGadget(new ToggleButton(gui, isLocked(), LOCKED_BUTTON, UNLOCKED_BUTTON, new ToggleButton.ToggleListener() {
            @Override
            public boolean run(int slot, boolean newValue) {
                setLocked(newValue);
                return true;
            }
        }), 26);
        return gui;
    }

    @Override
    public void onServerTick() {
        // 1. move items from input to storage
        int inputSlot = getInputSlots()[0];
        ItemStack stackIn = getInventoryItem(inputSlot);
        if (stackIn != null && (stored == null || stackIn.isSimilar(stored) && !isFull())) {
            double chargeNeeded = getChargePerOperation(stackIn.getAmount());
            if (getCharge() >= chargeNeeded) {
                if (stored == null) {
                    setStoredItemType(stackIn);
                }
                int toPull = Math.min(stackIn.getAmount(), maxCapacity - getStorageAmount());
                setStorageAmount(getStorageAmount() + toPull);
                stackIn.setAmount(stackIn.getAmount() - toPull);
                setInventoryItem(inputSlot, stackIn);
                setCharge(getCharge() - chargeNeeded);
                if (stackIn.getAmount() == 0) {
                    // workaround to avoid leaving ghost items in the input slot
                    STBUtil.forceInventoryRefresh(getInventory());
                }
            }
        }

        ItemStack stackOut = getOutputItem();
        int newAmount = stackOut == null ? 0 : stackOut.getAmount();
        if (getOutputAmount() != newAmount) {
            setOutputAmount(newAmount);
        }

        // 2. top up the output stack from storage
        if (stored != null) {
            int toPush = Math.min(getStorageAmount(), stored.getMaxStackSize() - getOutputAmount());
            if (toPush > 0) {
                if (stackOut == null) {
                    stackOut = stored.clone();
                    stackOut.setAmount(toPush);
                } else {
                    stackOut.setAmount(stackOut.getAmount() + toPush);
                }
                setOutputItem(stackOut);
                setOutputAmount(stackOut.getAmount());
                setStorageAmount(getStorageAmount() - toPush);
            }
        }

        // 3. perform any necessary updates if storage has changed
        if (getTotalAmount() != oldTotalAmount) {
            updateSignQuantityLine();
            if (getTotalAmount() == 0) {
                setStoredItemType(null);
            }
            Debugger.getInstance().debug(2, this + " amount changed! " + oldTotalAmount + " -> " + getTotalAmount());
            getProgressMeter().setMaxProgress(maxCapacity);
            setProcessing(stored);
            setProgress(maxCapacity - getStorageAmount());
            updateBlock(false);
            updateAttachedLabelSigns();
            oldTotalAmount = getTotalAmount();
        }

        super.onServerTick();
    }

    protected void setOutputItem(ItemStack stackOut) {
        setInventoryItem(getOutputSlots()[0], stackOut);
    }

    @Override
    public void setLocation(Location loc) {
        if (loc == null && getProcessing() != null) {
            if (dropsItemsOnBreak()) {
                // dump contents on floor (could make a big mess)
                Location current = getLocation();
                storageAmount = Math.min(4096, storageAmount);  // max 64 stacks will be dropped
                while (storageAmount > 0) {
                    ItemStack stack = stored.clone();
                    stack.setAmount(Math.min(storageAmount, stored.getMaxStackSize()));
                    current.getWorld().dropItemNaturally(current, stack);
                    storageAmount -= stored.getMaxStackSize();
                }
                setStorageAmount(0);
            }
        }
        super.setLocation(loc);
        if (loc != null) {
            getProgressMeter().setMaxProgress(maxCapacity);
            setProcessing(stored);
            setProgress(maxCapacity - storageAmount);
            ItemStack output = getOutputItem();
            outputAmount = output == null ? 0 : output.getAmount();
        }
    }

    @Override
    public void onInteractBlock(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = player.getItemInHand();
        if (event.getAction() == Action.LEFT_CLICK_BLOCK && getStoredItemType() != null &&
                hasAccessRights(player) && hasOKItem(player)) {
            // try to extract items from the output stack
            int wanted = player.isSneaking() ? 1 : getStoredItemType().getMaxStackSize();
            int nExtracted = Math.min(wanted, getOutputAmount());
            if (nExtracted > 0) {
                Location loc = event.getClickedBlock().getRelative(event.getBlockFace()).getLocation().add(0.5, 0.5, 0.5);
                ItemStack stack = getStoredItemType().clone();
                stack.setAmount(nExtracted);
                loc.getWorld().dropItem(loc, stack);
                setOutputAmount(getOutputAmount() - nExtracted);
                stack.setAmount(getOutputAmount());
                setOutputItem(stack);
            }
            event.setCancelled(true);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK && !player.isSneaking() &&
                hasAccessRights(player)) {
            Long lastInsert = (Long) STBUtil.getMetadataValue(player, STB_LAST_BSU_INSERT);
            long now = System.currentTimeMillis();
            if (inHand.getType() == Material.AIR && lastInsert != null && now - lastInsert < DOUBLE_CLICK_TIME) {
                rightClickFullInsert(player);
                event.setCancelled(true);
            } else if (inHand.isSimilar(getStoredItemType())) {
                rightClickInsert(player, player.getInventory().getHeldItemSlot());
                event.setCancelled(true);
            } else {
                super.onInteractBlock(event);
            }
        } else {
            super.onInteractBlock(event);
        }
    }

    private boolean hasOKItem(Player player) {
        switch (player.getItemInHand().getType()) {
            case SIGN:
            case WOOD_AXE:
            case STONE_AXE:
            case IRON_AXE:
            case DIAMOND_AXE:
                return false;
            default:
                return true;
        }
    }

    private void rightClickFullInsert(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack != null && stack.isSimilar(getStoredItemType()) && rightClickInsert(player, slot) == 0) {
                break;
            }
        }
        player.updateInventory();
    }

    private int rightClickInsert(Player player, int slot) {
        ItemStack stack = player.getInventory().getItem(slot);
        int toInsert = Math.min(stack.getAmount(), maxCapacity - getStorageAmount());
        if (toInsert == 0) {
            STBUtil.complain(player, getItemName() + " is full.");
            return 0;
        }
        double chargeNeeded = getChargePerOperation(toInsert);
        if (getCharge() >= chargeNeeded) {
            setStorageAmount(getStorageAmount() + toInsert);
            if (getStoredItemType() == null) {
                setStoredItemType(stack);
            }
            stack.setAmount(stack.getAmount() - toInsert);
            player.getInventory().setItem(slot, stack.getAmount() == 0 ? null : stack);
            setCharge(getCharge() - chargeNeeded);
            player.setMetadata(STB_LAST_BSU_INSERT,
                    new FixedMetadataValue(SensibleToolboxPlugin.getInstance(), System.currentTimeMillis()));
            return toInsert;
        } else {
            STBUtil.complain(player, getItemName() + " has insufficient charge to accept items.");
            return 0;
        }
    }

    protected boolean dropsItemsOnBreak() {
        return true;
    }

    @Override
    public int getProgressItemSlot() {
        return 12;
    }

    @Override
    public int getProgressCounterSlot() {
        return 3;
    }

    @Override
    public Material getProgressIcon() {
        return Material.DIAMOND_CHESTPLATE;
    }

    public boolean isFull() {
        return stored != null && storageAmount >= getStackCapacity() * stored.getMaxStackSize();
    }

    @Override
    public String getProgressMessage() {
        return ChatColor.YELLOW + "Stored: " + getStorageAmount() + "/" + maxCapacity;
    }

    @Override
    public boolean acceptsItemType(ItemStack stack) {
        return stored == null || stored.isSimilar(stack);
    }

    @Override
    protected String[] getSignLabel() {
        return signLabel;
    }

    public ItemStack getOutputItem() {
        return getInventoryItem(getOutputSlots()[0]);
    }

    @Override
    public int insertItems(ItemStack item, BlockFace face, boolean sorting, UUID uuid) {
        if (!hasAccessRights(uuid)) {
            return 0;
        }
        double chargeNeeded = getChargePerOperation(item.getAmount());
        if (!isRedstoneActive() || getCharge() < chargeNeeded) {
            return 0;
        } else if (stored == null) {
            setStoredItemType(item);
            setStorageAmount(item.getAmount());
            setCharge(getCharge() - chargeNeeded);
            return item.getAmount();
        } else if (item.isSimilar(stored)) {
            int toInsert = Math.min(item.getAmount(), maxCapacity - getStorageAmount());
            setStorageAmount(getStorageAmount() + toInsert);
            setCharge(getCharge() - chargeNeeded);
            return toInsert;
        } else {
            return 0;
        }
    }

    @Override
    public ItemStack extractItems(BlockFace face, ItemStack receiver, int amount, UUID uuid) {
        if (!hasAccessRights(uuid)) {
            return null;
        }
        double chargeNeeded = getChargePerOperation(amount);
        if (!isRedstoneActive() || getStorageAmount() == 0 && getOutputAmount() == 0 || getCharge() < chargeNeeded) {
            return null;
        }

        if (receiver != null) {
            amount = Math.min(amount, receiver.getMaxStackSize() - receiver.getAmount());
            if (getStorageAmount() > 0 && !receiver.isSimilar(getStoredItemType())) {
                return null;
            }
            if (amount > getStorageAmount() && getOutputAmount() > 0 && !receiver.isSimilar(getOutputItem())) {
                return null;
            }
        }

        int fromStorage = Math.min(getStorageAmount(), amount);
        if (fromStorage > 0) {
            amount -= fromStorage;
            setStorageAmount(getStorageAmount() - fromStorage);
        }
        int fromOutput = 0;
        if (amount > 0) {
            fromOutput = Math.min(getOutputAmount(), amount);
            if (fromOutput > 0) {
                setOutputAmount(getOutputAmount() - fromOutput);
                ItemStack output = getOutputItem();
                output.setAmount(getOutputAmount());
                setOutputItem(output.getAmount() > 0 ? output : null);
            }
        }

        ItemStack tmpStored = getStoredItemType();
        if (getTotalAmount() == 0) {
            setStoredItemType(null);
        }

        setCharge(getCharge() - chargeNeeded);

        if (receiver == null) {
            ItemStack returned = tmpStored.clone();
            returned.setAmount(fromStorage + fromOutput);
            return returned;
        } else {
            receiver.setAmount(receiver.getAmount() + fromStorage + fromOutput);
            return receiver;
        }
    }

    @Override
    public Inventory showOutputItems() {
        Inventory inv = Bukkit.createInventory(this, 9);
        inv.setItem(0, getOutputItem());
        return inv;
    }

    @Override
    public void updateOutputItems(Inventory inventory) {
        setOutputItem(inventory.getItem(0));
        setOutputAmount(getOutputItem() == null ? 0 : getOutputItem().getAmount());
    }

    /**
     * Return the SCU cost for processing some items; either inserting or
     * extracting them.
     *
     * @param nItems the number of items to check for
     * @return the SCU cost
     */
    public double getChargePerOperation(int nItems) {
        return 0.0;
    }
}
