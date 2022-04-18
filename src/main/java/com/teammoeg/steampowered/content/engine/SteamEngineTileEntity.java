/*
 * Copyright (c) 2021 TeamMoeg
 *
 * This file is part of Steam Powered.
 *
 * Steam Powered is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, version 3.
 *
 * Steam Powered is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Steam Powered. If not, see <https://www.gnu.org/licenses/>.
 */

package com.teammoeg.steampowered.content.engine;

import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.simibubi.create.content.contraptions.components.flywheel.FlywheelBlock;
import com.simibubi.create.content.contraptions.components.flywheel.FlywheelTileEntity;
import com.simibubi.create.content.contraptions.components.flywheel.engine.EngineBlock;
import com.simibubi.create.content.contraptions.components.flywheel.engine.EngineTileEntity;
import com.simibubi.create.content.contraptions.goggles.IHaveGoggleInformation;
import com.teammoeg.steampowered.FluidRegistry;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.capability.CapabilityFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.IFluidHandler.FluidAction;
import net.minecraftforge.fluids.capability.templates.FluidTank;

public abstract class SteamEngineTileEntity extends EngineTileEntity implements IHaveGoggleInformation {

	private FluidTank tank;
	private LazyOptional<IFluidHandler> holder = LazyOptional.of(() -> tank);
	private int heatup = 0;

	public SteamEngineTileEntity(BlockEntityType<? extends SteamEngineTileEntity> type, BlockPos pos, BlockState state) {
		super(type, pos, state);
		this.refreshCapability();
		this.tank = new FluidTank(this.getSteamStorage(), fluidStack -> {
				return fluidStack.getFluid() == FluidRegistry.steam.get();
		});
	}

	@Override
	public void tick() {
		super.tick();
		if (level != null && !level.isClientSide) {
			BlockState state = this.level.getBlockState(this.worldPosition);
			if (this.poweredWheel == null || this.poweredWheel.isRemoved()) {
				this.level.setBlockAndUpdate(this.worldPosition, state.setValue(SteamEngineBlock.LIT, false));
				this.refreshWheelSpeed();
				heatup = 0;
				tank.drain(this.getSteamConsumptionPerTick(), IFluidHandler.FluidAction.EXECUTE);
			} else {
				if (!tank.isEmpty() && tank.drain(this.getSteamConsumptionPerTick(), IFluidHandler.FluidAction.EXECUTE)
						.getAmount() >= this.getSteamConsumptionPerTick()) {
					this.level.setBlockAndUpdate(this.worldPosition, state.setValue(SteamEngineBlock.LIT, true));
					if (heatup >= 60) {
						this.appliedCapacity = this.getGeneratingCapacity();
						this.appliedSpeed = this.getGeneratingSpeed();
						this.refreshWheelSpeed();
					} else
						heatup++;
				} else {
					if (heatup > 0)
						heatup--;
					this.level.setBlockAndUpdate(this.worldPosition, state.setValue(SteamEngineBlock.LIT, false));
					this.appliedCapacity = 0;
					this.appliedSpeed = 0;
					this.refreshWheelSpeed();
				}
			}
			this.level.sendBlockUpdated(this.getBlockPos(), this.getBlockState(), this.getBlockState(), 3);
			this.setChanged();
		}
	}

	public boolean addToGoggleTooltip(List<Component> tooltip, boolean isPlayerSneaking) {
		if (!this.getBlockState().getValue(SteamEngineBlock.LIT)) {
			tooltip.add(componentSpacing.plainCopy()
					.append(new TranslatableComponent("tooltip.steampowered.steam_engine.not_enough_steam")
							.withStyle(ChatFormatting.RED)));
		} else if (heatup < 60) {
			tooltip.add(componentSpacing.plainCopy()
					.append(new TranslatableComponent("tooltip.steampowered.steam_engine.heating")
							.withStyle(ChatFormatting.YELLOW)));
		} else {
			tooltip.add(componentSpacing.plainCopy()
					.append(new TranslatableComponent("tooltip.steampowered.steam_engine.running")
							.withStyle(ChatFormatting.GREEN)));
		}
		return this.containedFluidTooltip(tooltip, isPlayerSneaking,
				getCapability(CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY));
	}

	@Override
	protected void read(CompoundTag compound, boolean clientPacket) {
		super.read(compound, clientPacket);
		tank.readFromNBT(compound.getCompound("TankContent"));
		heatup = compound.getInt("heatup");
	}

	@Override
	public void write(CompoundTag compound, boolean clientPacket) {
		super.write(compound, clientPacket);
		compound.put("TankContent", tank.writeToNBT(new CompoundTag()));
		compound.putInt("heatup", heatup);
		
	}

	@Override
	@Nonnull
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> capability, @Nullable Direction facing) {
		if (!this.holder.isPresent()) {
			this.refreshCapability();
		}
		return capability == CapabilityFluidHandler.FLUID_HANDLER_CAPABILITY ? holder.cast()
				: super.getCapability(capability, facing);
	}

	private void refreshCapability() {
		LazyOptional<IFluidHandler> oldCap = this.holder;
		this.holder = LazyOptional.of(() -> {
			return this.tank;
		});
		oldCap.invalidate();
	}

	public void attachWheel() {
		Direction engineFacing = (Direction) this.getBlockState().getValue(EngineBlock.FACING);
		BlockPos wheelPos = this.worldPosition.relative(engineFacing, 2);
		BlockState wheelState = this.level.getBlockState(wheelPos);
		if (this.getFlywheel() == wheelState.getBlock()) {
			Direction wheelFacing = (Direction) wheelState.getValue(FlywheelBlock.HORIZONTAL_FACING);
			if (wheelFacing.getAxis() == engineFacing.getClockWise().getAxis()) {
				if (!FlywheelBlock.isConnected(wheelState)
						|| FlywheelBlock.getConnection(wheelState) == engineFacing.getOpposite()) {
					BlockEntity te = this.level.getBlockEntity(wheelPos);
					if (!te.isRemoved()) {
						if (te instanceof FlywheelTileEntity) {
							if (!FlywheelBlock.isConnected(wheelState)) {
								FlywheelBlock.setConnection(this.level, te.getBlockPos(), te.getBlockState(),
										engineFacing.getOpposite());
							}

							this.poweredWheel = (FlywheelTileEntity) te;
							this.refreshWheelSpeed();
						}

					}
				}
			}
		}
	}

	public abstract Block getFlywheel();

	public abstract float getGeneratingCapacity();

	public abstract float getGeneratingSpeed();

	public abstract int getSteamConsumptionPerTick();

	public abstract int getSteamStorage();
}
