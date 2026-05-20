package org.justnoone.jme.mixin;

import org.mtr.mod.screen.DashboardScreen;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.WidgetMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(value = DashboardScreen.class, remap = false)
public interface DashboardScreenAccessor {

    @Invoker("isValidRoutePlatformIndex")
    boolean jme$isValidRoutePlatformIndex();

    @Invoker("toggleButtons")
    void jme$toggleButtons();

    @Invoker("startEditingRouteDestination")
    void jme$startEditingRouteDestination(int index);

    @Invoker("onFind")
    void jme$onFind(DashboardListItem dashboardListItem, int index);

    @Invoker("onDrawArea")
    void jme$onDrawArea(DashboardListItem dashboardListItem, int index);

    @Invoker("onEdit")
    void jme$onEdit(DashboardListItem dashboardListItem, int index);

    @Invoker("onDelete")
    void jme$onDelete(DashboardListItem dashboardListItem, int index);

    @Accessor("widgetMap")
    WidgetMap jme$getWidgetMap();
}
