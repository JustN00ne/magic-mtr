package org.justnoone.jme.client.screen;

import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.network.PacketByteBuf;
import org.justnoone.jme.network.MagicNetworkingCompat;
import org.justnoone.jme.rail.AlternativePlatformRegistry;
import org.justnoone.jme.rail.MagicRailConstants;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongIterator;
import org.mtr.libraries.it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectImmutableList;
import org.mtr.mapping.mapper.ScreenExtension;
import org.mtr.mod.screen.DashboardListItem;
import org.mtr.mod.screen.DashboardListSelectorScreen;

public class AlternativePlatformSelectorScreen extends DashboardListSelectorScreen {

    private final long routeId;
    private final long primaryPlatformId;
    private final LongArrayList candidatePlatformIds;
    private final LongOpenHashSet originalExplicitSelection;
    private final boolean originalWildcardEnabled;

    private AlternativePlatformSelectorScreen(
            ScreenExtension parent,
            long routeId,
            long primaryPlatformId,
            ObjectImmutableList<DashboardListItem> allData,
            LongArrayList selectedIds,
            LongArrayList candidatePlatformIds,
            LongOpenHashSet originalExplicitSelection,
            boolean originalWildcardEnabled
    ) {
        super(allData, selectedIds, false, false, parent);
        this.routeId = routeId;
        this.primaryPlatformId = primaryPlatformId;
        this.candidatePlatformIds = candidatePlatformIds;
        this.originalExplicitSelection = originalExplicitSelection == null ? new LongOpenHashSet() : originalExplicitSelection;
        this.originalWildcardEnabled = originalWildcardEnabled;
    }

    public static AlternativePlatformSelectorScreen create(ScreenExtension parent, Route route, Platform primaryPlatform) {
        final ObjectArrayList<DashboardListItem> listItems = new ObjectArrayList<>();
        final LongArrayList selectedIds = new LongArrayList();
        final LongArrayList candidateIds = new LongArrayList();

        if (!AlternativePlatformRegistry.isEnabled()) {
            return new AlternativePlatformSelectorScreen(parent, route.getId(), primaryPlatform.getId(), new ObjectImmutableList<>(listItems), selectedIds, candidateIds, new LongOpenHashSet(), false);
        }

        final LongOpenHashSet configuredSet = new LongOpenHashSet(AlternativePlatformRegistry.getAlternatives(route.getId(), primaryPlatform.getId()));
        final boolean wildcardEnabled = configuredSet.contains(-1L);
        final LongOpenHashSet explicitSet = new LongOpenHashSet();
        configuredSet.forEach(id -> {
            if (id != 0 && id != -1L) {
                explicitSet.add(id);
            }
        });

        if (primaryPlatform.area != null) {
            primaryPlatform.area.savedRails.forEach(savedRail -> {
                if (!(savedRail instanceof Platform)) {
                    return;
                }

                final Platform platform = (Platform) savedRail;
                if (platform.getId() == primaryPlatform.getId()) {
                    return;
                }

                final long platformId = platform.getId();
                candidateIds.add(platformId);
                listItems.add(new DashboardListItem(platformId, jme$getPlatformLabel(platform), jme$getPlatformColor(platform)));
                if (wildcardEnabled || explicitSet.contains(platformId)) {
                    selectedIds.add(platformId);
                }
            });
        }

        return new AlternativePlatformSelectorScreen(parent, route.getId(), primaryPlatform.getId(), new ObjectImmutableList<>(listItems), selectedIds, candidateIds, explicitSet, wildcardEnabled);
    }

    @Override
    public void onClose2() {
        if (!AlternativePlatformRegistry.isEnabled()) {
            super.onClose2();
            return;
        }

        final LongOpenHashSet newSelection = new LongOpenHashSet();
        final LongIterator iterator = selectedIds.iterator();
        while (iterator.hasNext()) {
            newSelection.add(iterator.nextLong());
        }

        boolean allCandidatesSelected = !candidatePlatformIds.isEmpty();
        final LongIterator allCandidateIter = candidatePlatformIds.iterator();
        while (allCandidateIter.hasNext()) {
            if (!newSelection.contains(allCandidateIter.nextLong())) {
                allCandidatesSelected = false;
                break;
            }
        }

        // If the user selected everything, store the wildcard (-1) instead of enumerating every platform ID.
        // This keeps configs small and makes the "all platforms in station" workflow achievable from the UI.
        if (allCandidatesSelected && !originalWildcardEnabled) {
            final boolean changed = AlternativePlatformRegistry.setAlternative(routeId, primaryPlatformId, -1L, true);
            if (changed) {
                final PacketByteBuf wildcardPacket = PacketByteBufs.create();
                wildcardPacket.writeLong(routeId);
                wildcardPacket.writeLong(primaryPlatformId);
                wildcardPacket.writeLong(-1L);
                wildcardPacket.writeBoolean(true);
                MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ALTERNATIVE_PLATFORM_PACKET_ID, wildcardPacket);
            }
            super.onClose2();
            return;
        }

        // If the wildcard (-1) is enabled and the user kept everything selected, keep the config unchanged.
        if (originalWildcardEnabled && allCandidatesSelected) {
            super.onClose2();
            return;
        }

        // If the wildcard was enabled but the user deselected something, turn it off first. We'll then
        // materialize the remaining selection as explicit alternatives.
        if (originalWildcardEnabled) {
            final boolean changed = AlternativePlatformRegistry.setAlternative(routeId, primaryPlatformId, -1L, false);
            if (changed) {
                final PacketByteBuf wildcardPacket = PacketByteBufs.create();
                wildcardPacket.writeLong(routeId);
                wildcardPacket.writeLong(primaryPlatformId);
                wildcardPacket.writeLong(-1L);
                wildcardPacket.writeBoolean(false);
                MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ALTERNATIVE_PLATFORM_PACKET_ID, wildcardPacket);
            }
        }

        final LongIterator candidateIterator = candidatePlatformIds.iterator();
        while (candidateIterator.hasNext()) {
            final long candidateId = candidateIterator.nextLong();
            final boolean enabled = newSelection.contains(candidateId);
            if (originalExplicitSelection.contains(candidateId) == enabled) {
                continue;
            }

            AlternativePlatformRegistry.setAlternative(routeId, primaryPlatformId, candidateId, enabled);
            final PacketByteBuf packet = PacketByteBufs.create();
            packet.writeLong(routeId);
            packet.writeLong(primaryPlatformId);
            packet.writeLong(candidateId);
            packet.writeBoolean(enabled);
            MagicNetworkingCompat.sendToServer(MagicRailConstants.SET_ALTERNATIVE_PLATFORM_PACKET_ID, packet);
        }

        super.onClose2();
    }

    private static String jme$getPlatformLabel(Platform platform) {
        if (platform.area == null) {
            return platform.getName();
        }
        return platform.area.getName() + " (" + platform.getName() + ")";
    }

    private static int jme$getPlatformColor(Platform platform) {
        return platform.area == null ? 0xFFFFFFFF : (platform.area.getColor() & 0xFFFFFF) | 0xFF000000;
    }
}
