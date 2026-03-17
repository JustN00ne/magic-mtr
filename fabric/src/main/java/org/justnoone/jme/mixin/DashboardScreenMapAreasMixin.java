package org.justnoone.jme.mixin;

import org.justnoone.jme.client.DashboardMapAreaStore;
import org.justnoone.jme.config.JmeConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = org.mtr.mod.screen.DashboardScreen.class, remap = false)
public abstract class DashboardScreenMapAreasMixin {

    @Unique
    private int jme$autoSaveTickCounter;

    @Inject(method = "init2", at = @At("TAIL"), remap = false)
    private void jme$restoreMapAreaOnOpen(CallbackInfo ci) {
        if (!JmeConfig.dashboardMapAutoSaveEnabled()) {
            return;
        }

        final DashboardScreenAccessor accessor = (DashboardScreenAccessor) this;
        DashboardMapAreaStore.restoreLatest(accessor.jme$getWidgetMap());
        jme$autoSaveTickCounter = 0;
    }

    @Inject(method = "tick2", at = @At("TAIL"), remap = false)
    private void jme$autoSaveMapArea(CallbackInfo ci) {
        if (!JmeConfig.dashboardMapAutoSaveEnabled()) {
            return;
        }

        jme$autoSaveTickCounter++;
        if (jme$autoSaveTickCounter < 40) {
            return;
        }

        jme$autoSaveTickCounter = 0;
        final DashboardScreenAccessor accessor = (DashboardScreenAccessor) this;
        DashboardMapAreaStore.autoSaveCurrent(accessor.jme$getWidgetMap());
    }

    @Inject(method = "onClose2", at = @At("HEAD"), remap = false)
    private void jme$saveMapAreaOnClose(CallbackInfo ci) {
        if (!JmeConfig.dashboardMapAutoSaveEnabled()) {
            return;
        }

        final DashboardScreenAccessor accessor = (DashboardScreenAccessor) this;
        DashboardMapAreaStore.autoSaveCurrent(accessor.jme$getWidgetMap());
    }
}
