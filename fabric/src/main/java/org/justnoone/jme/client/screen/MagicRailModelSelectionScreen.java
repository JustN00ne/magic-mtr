package org.justnoone.jme.client.screen;

import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongIterator;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mod.client.CustomResourceLoader;
import org.mtr.mod.resource.RailResource;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardListSelectorScreen;

import java.util.function.Consumer;

public class MagicRailModelSelectionScreen extends DashboardListSelectorScreen {

    private final Consumer<String> onSelect;
    private final String currentStyleId;
    private final boolean useBackwardsDirection;
    private final ObjectImmutableList<RailResource> allRails;

    private MagicRailModelSelectionScreen(ScreenExtension parent, ObjectImmutableList<RailResource> allRails, ObjectImmutableList<DashboardListItem> railsForList, LongArrayList selectedIds, String currentStyleId, Consumer<String> onSelect) {
        super(null, railsForList, selectedIds, true, false, parent);
        this.onSelect = onSelect;
        this.currentStyleId = currentStyleId == null || currentStyleId.isEmpty() ? MagicRailConstants.DEFAULT_STYLE : currentStyleId;
        this.useBackwardsDirection = this.currentStyleId.endsWith("_2");
        this.allRails = allRails;
    }

    @Override
    public void onClose2() {
        onSelect.accept(resolveSelectedStyleId());
        super.onClose2();
    }

    public static MagicRailModelSelectionScreen create(ScreenExtension parent, String currentStyleId, Consumer<String> onSelect) {
        final ObjectImmutableList<RailResource> allRails = CustomResourceLoader.getRails();
        final ObjectArrayList<DashboardListItem> railsForList = new ObjectArrayList<>();
        final LongArrayList selectedIds = new LongArrayList();
        final String styleId = currentStyleId == null || currentStyleId.isEmpty() ? MagicRailConstants.DEFAULT_STYLE : currentStyleId;
        final String styleWithoutDirection = RailResource.getIdWithoutDirection(styleId);

        for (int i = 0; i < allRails.size(); i++) {
            final RailResource railResource = allRails.get(i);
            railsForList.add(new DashboardListItem(i, railResource.getName(), railResource.getColor() | ARGB_BLACK));
            if (railResource.getId().equals(styleWithoutDirection)) {
                selectedIds.add(i);
            }
        }

        if (selectedIds.isEmpty()) {
            for (int i = 0; i < allRails.size(); i++) {
                if (CustomResourceLoader.DEFAULT_RAIL_ID.equals(allRails.get(i).getId())) {
                    selectedIds.add(i);
                    break;
                }
            }
        }

        return new MagicRailModelSelectionScreen(parent, allRails, new ObjectImmutableList<>(railsForList), selectedIds, styleId, onSelect);
    }

    private String resolveSelectedStyleId() {
        final LongIterator iterator = selectedIds.iterator();
        if (!iterator.hasNext()) {
            return currentStyleId;
        }

        final int selectedIndex = (int) iterator.nextLong();
        if (selectedIndex < 0 || selectedIndex >= allRails.size()) {
            return currentStyleId;
        }

        final String selectedBaseId = allRails.get(selectedIndex).getId();
        if (CustomResourceLoader.DEFAULT_RAIL_ID.equals(selectedBaseId)) {
            return useBackwardsDirection ? "default_2" : MagicRailConstants.DEFAULT_STYLE;
        }

        if (useBackwardsDirection && !selectedBaseId.endsWith("_2") && !selectedBaseId.endsWith("_1")) {
            return selectedBaseId + "_2";
        }

        return selectedBaseId;
    }
}
