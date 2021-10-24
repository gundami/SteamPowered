package com.teammoeg.steampowered.tileentity.burner;

import net.minecraft.tileentity.TileEntityType;

public class BronzeBurnerTileEntity extends BurnerTileEntity {

    public BronzeBurnerTileEntity(TileEntityType<?> type) {
        super(type);
    }

    @Override
    protected int getHuPerTick() {
        return 120;
    }
}
