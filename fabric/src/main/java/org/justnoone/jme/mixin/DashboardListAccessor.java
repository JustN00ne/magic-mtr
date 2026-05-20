package org.justnoone.jme.mixin;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.mod.screen.DashboardList;
import org.mtr.mod.screen.DashboardListItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = DashboardList.class, remap = false)
public interface DashboardListAccessor {

    @Accessor("dataSorted")
    ObjectArrayList<DashboardListItem> jme$getDataSorted();

    @Accessor("hasFind")
    void jme$setHasFind(boolean value);

    @Accessor("hasDrawArea")
    void jme$setHasDrawArea(boolean value);

    @Accessor("hasEdit")
    void jme$setHasEdit(boolean value);

    @Accessor("hasSort")
    void jme$setHasSort(boolean value);

    @Accessor("hasAdd")
    void jme$setHasAdd(boolean value);

    @Accessor("hasDelete")
    void jme$setHasDelete(boolean value);
}
