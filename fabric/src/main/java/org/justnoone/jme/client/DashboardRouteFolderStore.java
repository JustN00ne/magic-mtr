package org.justnoone.jme.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.justnoone.jme.config.JmeConfig;
import org.justnoone.jme.config.MagicConfigPaths;
import org.mtr.core.data.Platform;
import org.mtr.core.data.Route;
import org.mtr.core.data.RoutePlatformData;
import org.mtr.mod.screen.DashboardListItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DashboardRouteFolderStore {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = MagicConfigPaths.resolveConfigFile("dashboard_folders.json", "jme_dashboard_folders.json");
    private static final int MAX_FOLDER_DEPTH = 10;

    private static final Map<Long, List<FolderConfig>> routeFolders = new HashMap<>();
    private static final Map<Long, List<RowMetadata>> visibleRowsByRoute = new HashMap<>();
    private static boolean loaded;

    private DashboardRouteFolderStore() {
    }

    public static synchronized List<DashboardListItem> buildRows(Route route) {
        ensureLoaded();
        final List<DashboardListItem> listItems = new ArrayList<>();
        final List<RowMetadata> rowMetadataList = new ArrayList<>();

        if (route == null) {
            return listItems;
        }

        if (JmeConfig.dashboardRouteListMode() == JmeConfig.DashboardRouteListMode.FLAT) {
            final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
            for (int platformIndex = 0; platformIndex < routePlatforms.size(); platformIndex++) {
                final RoutePlatformData routePlatformData = routePlatforms.get(platformIndex);
                final Platform platform = routePlatformData.platform;
                if (platform == null) {
                    continue;
                }
                listItems.add(new DashboardListItem(platform.getId(), getPlatformLabel(routePlatformData), getStationColor(platform)));
                rowMetadataList.add(RowMetadata.platform(platformIndex, platform.getId()));
            }
            visibleRowsByRoute.put(route.getId(), rowMetadataList);
            return listItems;
        }

        jme$sanitizeFolders(route);

        final long routeId = route.getId();
        final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
        final Map<Long, List<Integer>> platformIndicesById = jme$getPlatformIndicesById(route);
        final Map<Long, Integer> nextIndexCursorByPlatformId = new HashMap<>();
        final LinkedHashSet<Integer> assignedRoutePlatformIndices = new LinkedHashSet<>();

        final List<FolderConfig> folders = routeFolders.getOrDefault(routeId, Collections.emptyList());
        for (final FolderConfig folderConfig : folders) {
            if (folderConfig.parentFolderId != null && !folderConfig.parentFolderId.isEmpty()) {
                continue;
            }
            jme$appendFolderRows(route, folderConfig, 0, listItems, rowMetadataList, platformIndicesById, nextIndexCursorByPlatformId, assignedRoutePlatformIndices);
        }

        for (int platformIndex = 0; platformIndex < routePlatforms.size(); platformIndex++) {
            final RoutePlatformData routePlatformData = routePlatforms.get(platformIndex);
            final Platform platform = routePlatformData.platform;
            if (platform == null || assignedRoutePlatformIndices.contains(platformIndex)) {
                continue;
            }

            listItems.add(new DashboardListItem(platform.getId(), getPlatformLabel(routePlatformData), getStationColor(platform)));
            rowMetadataList.add(RowMetadata.platform(platformIndex, platform.getId()));
        }

        visibleRowsByRoute.put(routeId, rowMetadataList);
        return listItems;
    }

    public static synchronized RowMetadata getRow(Route route, int visibleIndex) {
        if (route == null) {
            return null;
        }
        final List<RowMetadata> rows = visibleRowsByRoute.get(route.getId());
        if (rows == null || visibleIndex < 0 || visibleIndex >= rows.size()) {
            return null;
        }
        return rows.get(visibleIndex);
    }

    public static synchronized boolean toggleFolder(Route route, int visibleIndex) {
        final RowMetadata rowMetadata = getRow(route, visibleIndex);
        if (route == null || rowMetadata == null || !rowMetadata.folder) {
            return false;
        }

        final FolderConfig folderConfig = jme$getFolderConfig(route.getId(), rowMetadata.folderId);
        if (folderConfig == null) {
            return false;
        }

        folderConfig.expanded = !folderConfig.expanded;
        save();
        return true;
    }

    public static synchronized boolean createFolder(Route route) {
        return createFolder(route, null, -1);
    }

    public static synchronized boolean createFolder(Route route, String name, int iconColor) {
        return createFolder(route, (String) null, name, iconColor);
    }

    public static synchronized boolean createFolder(Route route, RowMetadata contextRow, String name, int iconColor) {
        if (contextRow == null) {
            return createFolder(route, (String) null, name, iconColor);
        }
        if (contextRow.folder || contextRow.child) {
            return createFolder(route, contextRow.folderId, name, iconColor);
        }
        if (contextRow.platformIndex >= 0) {
            return createFolderAtPlatform(route, contextRow.platformIndex, name, iconColor);
        }
        return createFolder(route, (String) null, name, iconColor);
    }

    public static synchronized boolean createFolder(Route route, String parentFolderId, String name, int iconColor) {
        ensureLoaded();
        if (route == null) {
            return false;
        }

        final long routeId = route.getId();
        final List<FolderConfig> folders = routeFolders.computeIfAbsent(routeId, ignored -> new ArrayList<>());
        String resolvedParentId = null;
        if (parentFolderId != null && !parentFolderId.isEmpty()) {
            final FolderConfig parentFolder = jme$getFolderConfig(routeId, parentFolderId);
            if (parentFolder == null) {
                return false;
            }
            final int parentDepth = jme$getFolderDepth(routeId, parentFolder.id);
            if (parentDepth < 0 || parentDepth >= MAX_FOLDER_DEPTH - 1) {
                return false;
            }
            resolvedParentId = parentFolder.id;
        }
        final String resolvedName = jme$sanitizeFolderName(name, folders);
        final FolderConfig folderConfig = new FolderConfig(UUID.randomUUID().toString(), resolvedName, true, new ArrayList<>(), jme$sanitizeIconColor(iconColor), resolvedParentId);
        if (resolvedParentId == null) {
            folders.add(folderConfig);
        } else {
            final int insertIndex = jme$getFolderInsertIndexAfterDescendants(folders, resolvedParentId);
            folders.add(Math.max(0, Math.min(insertIndex, folders.size())), folderConfig);
        }
        save();
        return true;
    }

    public static synchronized boolean createFolder(Route route, int visibleIndex) {
        final RowMetadata rowMetadata = getRow(route, visibleIndex);
        if (rowMetadata == null) {
            return createFolder(route);
        }
        if (rowMetadata.folder || rowMetadata.child) {
            return createFolder(route, rowMetadata.folderId, null, -1);
        }
        if (rowMetadata.platformIndex < 0) {
            return createFolder(route);
        }
        return createFolderAtPlatform(route, rowMetadata.platformIndex);
    }

    public static synchronized boolean createFolderAtPlatform(Route route, int platformIndex) {
        return createFolderAtPlatform(route, platformIndex, null, -1);
    }

    public static synchronized boolean createFolderAtPlatform(Route route, int platformIndex, String name, int iconColor) {
        ensureLoaded();
        if (route == null || platformIndex < 0 || platformIndex >= route.getRoutePlatforms().size()) {
            return false;
        }

        final Platform platform = route.getRoutePlatforms().get(platformIndex).platform;
        if (platform == null) {
            return false;
        }

        final long routeId = route.getId();
        final List<FolderConfig> folders = routeFolders.computeIfAbsent(routeId, ignored -> new ArrayList<>());
        final String resolvedName = jme$sanitizeFolderName(name, folders);
        final FolderConfig folder = new FolderConfig(UUID.randomUUID().toString(), resolvedName, true, new ArrayList<>(), jme$sanitizeIconColor(iconColor), null);
        folders.add(folder);
        jme$removePlatformFromAllFolders(routeId, platform.getId());
        folder.platformIds.add(platform.getId());
        save();
        return true;
    }

    public static synchronized boolean removeFolder(Route route, RowMetadata rowMetadata) {
        ensureLoaded();
        if (route == null || rowMetadata == null || !rowMetadata.folder) {
            return false;
        }

        final List<FolderConfig> folders = routeFolders.get(route.getId());
        if (folders == null) {
            return false;
        }

        final LinkedHashSet<String> folderIdsToRemove = new LinkedHashSet<>();
        jme$collectFolderAndDescendantIds(folders, rowMetadata.folderId, folderIdsToRemove);
        if (folderIdsToRemove.isEmpty()) {
            return false;
        }

        final boolean removed = folders.removeIf(folderConfig -> folderIdsToRemove.contains(folderConfig.id));
        if (!removed) {
            return false;
        }

        if (folders.isEmpty()) {
            routeFolders.remove(route.getId());
        }

        save();
        return true;
    }

    public static synchronized String getFolderName(Route route, RowMetadata rowMetadata) {
        if (route == null || rowMetadata == null || rowMetadata.folderId == null) {
            return "";
        }

        final FolderConfig folderConfig = jme$getFolderConfig(route.getId(), rowMetadata.folderId);
        return folderConfig == null ? "Folder" : folderConfig.name;
    }

    public static synchronized boolean renameFolder(Route route, RowMetadata rowMetadata, String newName) {
        if (route == null || rowMetadata == null || rowMetadata.folderId == null) {
            return false;
        }

        final FolderConfig folderConfig = jme$getFolderConfig(route.getId(), rowMetadata.folderId);
        if (folderConfig == null) {
            return false;
        }

        final String sanitized = (newName == null || newName.trim().isEmpty()) ? "Folder" : newName.trim();
        if (sanitized.equals(folderConfig.name)) {
            return false;
        }

        folderConfig.name = sanitized;
        save();
        return true;
    }

    public static synchronized int getFolderColor(Route route, RowMetadata rowMetadata) {
        if (route == null || rowMetadata == null || rowMetadata.folderId == null) {
            return -1;
        }
        final FolderConfig folderConfig = jme$getFolderConfig(route.getId(), rowMetadata.folderId);
        return folderConfig == null ? -1 : folderConfig.iconColor;
    }

    public static synchronized boolean setFolderAppearance(Route route, RowMetadata rowMetadata, String newName, int iconColor) {
        if (route == null || rowMetadata == null || rowMetadata.folderId == null) {
            return false;
        }

        final FolderConfig folderConfig = jme$getFolderConfig(route.getId(), rowMetadata.folderId);
        if (folderConfig == null) {
            return false;
        }

        final String resolvedName = newName == null || newName.trim().isEmpty() ? "Folder" : newName.trim();
        final int resolvedColor = jme$sanitizeIconColor(iconColor);
        final boolean nameChanged = !resolvedName.equals(folderConfig.name);
        final boolean colorChanged = resolvedColor != folderConfig.iconColor;
        if (!nameChanged && !colorChanged) {
            return false;
        }

        folderConfig.name = resolvedName;
        folderConfig.iconColor = resolvedColor;
        save();
        return true;
    }

    public static synchronized boolean removePlatformFromFolders(Route route, long platformId) {
        ensureLoaded();
        if (route == null || platformId == 0) {
            return false;
        }

        final boolean changed = jme$removePlatformFromAllFolders(route.getId(), platformId);
        if (changed) {
            save();
        }
        return changed;
    }

    public static synchronized boolean handleDrag(Route route, int fromVisibleIndex, int toVisibleIndex) {
        return handleDrag(route, fromVisibleIndex, toVisibleIndex, false);
    }

    public static synchronized boolean handleDrag(Route route, int fromVisibleIndex, int toVisibleIndex, boolean placeAfterTarget) {
        if (route == null || fromVisibleIndex == toVisibleIndex) {
            return false;
        }

        final RowMetadata source = getRow(route, fromVisibleIndex);
        RowMetadata target = getRow(route, toVisibleIndex);
        if (source == null || target == null || source.child) {
            return false;
        }

        if (source.folder && !target.folder) {
            final int resolvedTargetIndex = resolveFolderDropTarget(route, toVisibleIndex, placeAfterTarget);
            if (resolvedTargetIndex >= 0) {
                final RowMetadata resolvedTarget = getRow(route, resolvedTargetIndex);
                if (resolvedTarget != null) {
                    target = resolvedTarget;
                }
            } else {
                return false;
            }
        }

        if (source.folder && target.folder) {
            return jme$moveFolder(route, source.folderId, target.folderId, placeAfterTarget);
        }

        if (!source.folder && source.platformId != 0 && (target.folder || target.child)) {
            final String targetFolderId = target.folderId;
            if (targetFolderId == null) {
                return false;
            }

            final long routeId = route.getId();
            boolean changed = jme$removePlatformFromAllFolders(routeId, source.platformId);

            int targetInsertIndex = -1;
            if (target.child) {
                final FolderConfig targetFolder = jme$getFolderConfig(routeId, targetFolderId);
                if (targetFolder != null) {
                    targetInsertIndex = targetFolder.platformIds.indexOf(target.platformId);
                    if (targetInsertIndex >= 0 && placeAfterTarget) {
                        targetInsertIndex++;
                    }
                }
            } else if (target.folder) {
                final FolderConfig targetFolder = jme$getFolderConfig(routeId, targetFolderId);
                if (targetFolder != null) {
                    targetInsertIndex = placeAfterTarget ? targetFolder.platformIds.size() : 0;
                }
            }
            changed |= jme$addPlatformToFolder(routeId, targetFolderId, source.platformId, targetInsertIndex);

            if (changed) {
                save();
            }
            return changed;
        }

        if (!source.folder && source.platformIndex >= 0 && target.platformIndex >= 0) {
            boolean folderChanged = false;
            if (source.platformId != 0) {
                folderChanged = jme$removePlatformFromAllFolders(route.getId(), source.platformId);
            }
            final boolean moved = jme$movePlatform(route, source.platformIndex, target.platformIndex, placeAfterTarget);
            if (folderChanged) {
                save();
            }
            return folderChanged || moved;
        }

        return false;
    }

    public static synchronized boolean moveRouteGroup(Route route, int fromVisibleIndex, int toVisibleIndex) {
        return handleDrag(route, fromVisibleIndex, toVisibleIndex, false);
    }

    public static synchronized int resolveFolderDropTarget(Route route, int hoverVisibleIndex, boolean preferAfter) {
        if (route == null) {
            return -1;
        }

        final List<RowMetadata> rows = visibleRowsByRoute.get(route.getId());
        if (rows == null || rows.isEmpty()) {
            return -1;
        }

        int clampedIndex = Math.max(0, Math.min(rows.size() - 1, hoverVisibleIndex));
        if (rows.get(clampedIndex).folder) {
            return clampedIndex;
        }

        if (preferAfter) {
            for (int i = clampedIndex + 1; i < rows.size(); i++) {
                if (rows.get(i).folder) {
                    return i;
                }
            }
            for (int i = clampedIndex - 1; i >= 0; i--) {
                if (rows.get(i).folder) {
                    return i;
                }
            }
        } else {
            for (int i = clampedIndex - 1; i >= 0; i--) {
                if (rows.get(i).folder) {
                    return i;
                }
            }
            for (int i = clampedIndex + 1; i < rows.size(); i++) {
                if (rows.get(i).folder) {
                    return i;
                }
            }
        }

        return -1;
    }

    public static synchronized boolean sortFolder(Route route, RowMetadata rowMetadata) {
        if (route == null || rowMetadata == null || rowMetadata.folderId == null) {
            return false;
        }

        final FolderConfig folderConfig = jme$getFolderConfig(route.getId(), rowMetadata.folderId);
        if (folderConfig == null || folderConfig.platformIds == null || folderConfig.platformIds.size() < 2) {
            return false;
        }

        final Map<Long, String> labels = new HashMap<>();
        route.getRoutePlatforms().forEach(routePlatformData -> {
            if (routePlatformData.platform != null) {
                labels.putIfAbsent(routePlatformData.platform.getId(), getPlatformLabel(routePlatformData));
            }
        });

        final List<Long> sortedIds = new ArrayList<>(folderConfig.platformIds);
        sortedIds.sort(Comparator.comparing(platformId -> labels.getOrDefault(platformId, Long.toString(platformId)), String.CASE_INSENSITIVE_ORDER));
        if (sortedIds.equals(folderConfig.platformIds)) {
            return false;
        }

        folderConfig.platformIds = sortedIds;
        save();
        return true;
    }

    public static synchronized boolean sortRoutePlatforms(Route route) {
        if (route == null || route.getRoutePlatforms().size() < 2) {
            return false;
        }

        final List<RoutePlatformData> sorted = new ArrayList<>(route.getRoutePlatforms());
        sorted.sort(Comparator.comparing(DashboardRouteFolderStore::jme$getSortablePlatformLabel, String.CASE_INSENSITIVE_ORDER));

        boolean changed = false;
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i) != route.getRoutePlatforms().get(i)) {
                changed = true;
                break;
            }
        }

        if (!changed) {
            return false;
        }

        route.getRoutePlatforms().clear();
        route.getRoutePlatforms().addAll(sorted);
        return true;
    }

    public static synchronized Map<Long, List<Long>> getFolderAlternatives(Route route) {
        final Map<Long, List<Long>> alternatives = new LinkedHashMap<>();
        if (route == null) {
            return alternatives;
        }

        jme$sanitizeFolders(route);
        final LinkedHashSet<Long> validPlatformIds = new LinkedHashSet<>();
        route.getRoutePlatforms().forEach(routePlatformData -> {
            if (routePlatformData.platform != null) {
                validPlatformIds.add(routePlatformData.platform.getId());
            }
        });

        routeFolders.getOrDefault(route.getId(), Collections.emptyList()).forEach(folder -> {
            final List<Long> validMembers = new ArrayList<>();
            folder.platformIds.forEach(platformId -> {
                if (validPlatformIds.contains(platformId) && !validMembers.contains(platformId)) {
                    validMembers.add(platformId);
                }
            });
            if (validMembers.isEmpty()) {
                return;
            }

            final long primaryPlatformId = validMembers.get(0);
            final LinkedHashSet<Long> alternativeIds = new LinkedHashSet<>();
            for (int index = 1; index < validMembers.size(); index++) {
                final long alternativeId = validMembers.get(index);
                if (alternativeId != primaryPlatformId) {
                    alternativeIds.add(alternativeId);
                }
            }
            alternatives.put(primaryPlatformId, new ArrayList<>(alternativeIds));
        });

        return alternatives;
    }

    private static boolean jme$movePlatform(Route route, int fromPlatformIndex, int toPlatformIndex, boolean placeAfterTarget) {
        final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
        if (fromPlatformIndex < 0 || toPlatformIndex < 0 || fromPlatformIndex >= routePlatforms.size() || toPlatformIndex >= routePlatforms.size() || fromPlatformIndex == toPlatformIndex) {
            return false;
        }

        final RoutePlatformData moved = routePlatforms.remove(fromPlatformIndex);
        int targetIndex = toPlatformIndex;
        if (placeAfterTarget) {
            targetIndex++;
        }
        if (targetIndex > fromPlatformIndex) {
            targetIndex--;
        }
        targetIndex = Math.max(0, Math.min(targetIndex, routePlatforms.size()));
        routePlatforms.add(targetIndex, moved);
        return true;
    }

    private static boolean jme$moveFolder(Route route, String sourceFolderId, String targetFolderId, boolean placeAfterTarget) {
        if (route == null || sourceFolderId == null || targetFolderId == null || sourceFolderId.equals(targetFolderId)) {
            return false;
        }

        final List<FolderConfig> folders = routeFolders.get(route.getId());
        if (folders == null || folders.isEmpty()) {
            return false;
        }

        final FolderConfig sourceFolder = jme$getFolderConfig(route.getId(), sourceFolderId);
        final FolderConfig targetFolder = jme$getFolderConfig(route.getId(), targetFolderId);
        if (sourceFolder == null || targetFolder == null) {
            return false;
        }

        final String nextParentId = targetFolder.parentFolderId;
        if (sourceFolder.id.equals(nextParentId)) {
            return false;
        }
        if (nextParentId != null && jme$isFolderDescendant(route.getId(), nextParentId, sourceFolder.id)) {
            return false;
        }

        int sourceIndex = -1;
        int targetIndex = -1;
        for (int index = 0; index < folders.size(); index++) {
            final FolderConfig folder = folders.get(index);
            if (folder.id.equals(sourceFolderId)) {
                sourceIndex = index;
            }
            if (folder.id.equals(targetFolderId)) {
                targetIndex = index;
            }
        }

        if (sourceIndex < 0 || targetIndex < 0 || sourceIndex == targetIndex) {
            return false;
        }

        final FolderConfig movedFolder = folders.remove(sourceIndex);
        movedFolder.parentFolderId = nextParentId;
        if (sourceIndex < targetIndex) {
            targetIndex--;
        }
        int insertIndex = targetIndex;
        if (placeAfterTarget) {
            insertIndex = jme$getFolderSubtreeEndIndex(folders, targetFolderId, targetIndex) + 1;
        }
        insertIndex = Math.max(0, Math.min(folders.size(), insertIndex));
        folders.add(insertIndex, movedFolder);
        save();
        return true;
    }

    private static boolean jme$addPlatformToFolder(long routeId, String folderId, long platformId, int insertIndex) {
        if (folderId == null || platformId == 0) {
            return false;
        }

        final FolderConfig folderConfig = jme$getFolderConfig(routeId, folderId);
        if (folderConfig == null) {
            return false;
        }

        folderConfig.platformIds.remove(platformId);
        final int resolvedInsertIndex = insertIndex < 0 ? folderConfig.platformIds.size() : Math.max(0, Math.min(insertIndex, folderConfig.platformIds.size()));
        folderConfig.platformIds.add(resolvedInsertIndex, platformId);
        return true;
    }

    private static boolean jme$removePlatformFromAllFolders(long routeId, long platformId) {
        final List<FolderConfig> folders = routeFolders.get(routeId);
        if (folders == null || folders.isEmpty() || platformId == 0) {
            return false;
        }

        boolean changed = false;
        for (final FolderConfig folderConfig : folders) {
            if (folderConfig.platformIds.remove(platformId)) {
                changed = true;
            }
        }
        return changed;
    }

    private static Map<Long, List<Integer>> jme$getPlatformIndicesById(Route route) {
        final Map<Long, List<Integer>> platformIndicesById = new HashMap<>();
        final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
        for (int index = 0; index < routePlatforms.size(); index++) {
            final Platform platform = routePlatforms.get(index).platform;
            if (platform != null) {
                platformIndicesById.computeIfAbsent(platform.getId(), ignored -> new ArrayList<>()).add(index);
            }
        }
        return platformIndicesById;
    }

    private static void jme$appendFolderRows(
            Route route,
            FolderConfig folderConfig,
            int depth,
            List<DashboardListItem> listItems,
            List<RowMetadata> rowMetadataList,
            Map<Long, List<Integer>> platformIndicesById,
            Map<Long, Integer> nextIndexCursorByPlatformId,
            LinkedHashSet<Integer> assignedRoutePlatformIndices
    ) {
        if (route == null || folderConfig == null || depth >= MAX_FOLDER_DEPTH) {
            return;
        }

        final List<RoutePlatformData> routePlatforms = route.getRoutePlatforms();
        final List<Integer> resolvedFolderPlatformIndices = new ArrayList<>();
        new ArrayList<>(folderConfig.platformIds).forEach(folderPlatformId -> {
            if (folderPlatformId == null || folderPlatformId == 0) {
                return;
            }

            final List<Integer> candidateIndices = platformIndicesById.get(folderPlatformId);
            if (candidateIndices == null || candidateIndices.isEmpty()) {
                return;
            }

            final int cursor = nextIndexCursorByPlatformId.getOrDefault(folderPlatformId, 0);
            if (cursor >= candidateIndices.size()) {
                return;
            }

            final int platformIndex = candidateIndices.get(cursor);
            nextIndexCursorByPlatformId.put(folderPlatformId, cursor + 1);
            resolvedFolderPlatformIndices.add(platformIndex);
            assignedRoutePlatformIndices.add(platformIndex);
        });

        final int memberCount = resolvedFolderPlatformIndices.size();
        final int primaryPlatformIndex = memberCount > 0 ? resolvedFolderPlatformIndices.get(0) : -1;
        final Platform primaryPlatform = primaryPlatformIndex >= 0 && primaryPlatformIndex < routePlatforms.size() ? routePlatforms.get(primaryPlatformIndex).platform : null;
        final long primaryPlatformId = primaryPlatform == null ? 0 : primaryPlatform.getId();

        final String indentation = jme$indentation(depth);
        final String folderLabel = indentation + (folderConfig.expanded ? "▼ " : "▶ ") + "🗀 " + folderConfig.name;
        final int folderSeed = Long.hashCode(route.getId() * 31L) ^ folderConfig.id.hashCode();
        final int folderListId = folderSeed == Integer.MIN_VALUE ? Integer.MIN_VALUE + 1 : -Math.abs(folderSeed);
        final int folderColor = folderConfig.iconColor >= 0 ? folderConfig.iconColor : (primaryPlatform == null ? 0 : getStationColor(primaryPlatform));
        listItems.add(new DashboardListItem(folderListId, folderLabel, folderColor));
        rowMetadataList.add(RowMetadata.folder(folderConfig.id, folderConfig.parentFolderId, primaryPlatformId, primaryPlatformIndex, depth));

        if (!folderConfig.expanded) {
            return;
        }

        final List<FolderConfig> childFolders = jme$getChildFolders(route.getId(), folderConfig.id);
        for (final FolderConfig childFolder : childFolders) {
            jme$appendFolderRows(route, childFolder, depth + 1, listItems, rowMetadataList, platformIndicesById, nextIndexCursorByPlatformId, assignedRoutePlatformIndices);
        }

        for (int childOffset = 0; childOffset < memberCount; childOffset++) {
            final int platformIndex = resolvedFolderPlatformIndices.get(childOffset);
            final RoutePlatformData routePlatformData = routePlatforms.get(platformIndex);
            final Platform platform = routePlatformData.platform;
            if (platform == null) {
                continue;
            }

            final String prefix = indentation + (childOffset == 0 ? "  •★ " : "  • ");
            listItems.add(new DashboardListItem(platform.getId(), prefix + getPlatformLabel(routePlatformData), getStationColor(platform)));
            rowMetadataList.add(RowMetadata.childPlatform(folderConfig.id, folderConfig.parentFolderId, primaryPlatformId, platformIndex, platform.getId(), depth + 1));
        }
    }

    private static List<FolderConfig> jme$getChildFolders(long routeId, String parentFolderId) {
        final List<FolderConfig> folders = routeFolders.get(routeId);
        if (folders == null || folders.isEmpty()) {
            return Collections.emptyList();
        }
        final List<FolderConfig> childFolders = new ArrayList<>();
        for (final FolderConfig folderConfig : folders) {
            final String folderParentId = folderConfig.parentFolderId;
            final boolean isChild = (parentFolderId == null && folderParentId == null) || (parentFolderId != null && parentFolderId.equals(folderParentId));
            if (isChild) {
                childFolders.add(folderConfig);
            }
        }
        return childFolders;
    }

    private static String jme$indentation(int depth) {
        if (depth <= 0) {
            return "";
        }
        final StringBuilder stringBuilder = new StringBuilder(depth * 2);
        for (int index = 0; index < depth; index++) {
            stringBuilder.append("  ");
        }
        return stringBuilder.toString();
    }

    private static int jme$getFolderInsertIndexAfterDescendants(List<FolderConfig> folders, String parentFolderId) {
        int parentIndex = -1;
        for (int index = 0; index < folders.size(); index++) {
            if (folders.get(index).id.equals(parentFolderId)) {
                parentIndex = index;
                break;
            }
        }
        if (parentIndex < 0) {
            return folders.size();
        }
        return jme$getFolderSubtreeEndIndex(folders, parentFolderId, parentIndex) + 1;
    }

    private static int jme$getFolderSubtreeEndIndex(List<FolderConfig> folders, String rootFolderId, int rootIndex) {
        int index = rootIndex;
        while (index + 1 < folders.size()) {
            final FolderConfig nextFolder = folders.get(index + 1);
            if (!jme$isFolderDescendant(folders, nextFolder.id, rootFolderId)) {
                break;
            }
            index++;
        }
        return index;
    }

    private static boolean jme$isFolderDescendant(long routeId, String folderId, String ancestorFolderId) {
        final List<FolderConfig> folders = routeFolders.get(routeId);
        if (folders == null || folders.isEmpty()) {
            return false;
        }
        return jme$isFolderDescendant(folders, folderId, ancestorFolderId);
    }

    private static boolean jme$isFolderDescendant(List<FolderConfig> folders, String folderId, String ancestorFolderId) {
        if (folderId == null || ancestorFolderId == null) {
            return false;
        }
        final LinkedHashSet<String> visited = new LinkedHashSet<>();
        FolderConfig cursor = jme$getFolderConfig(folders, folderId);
        while (cursor != null && cursor.parentFolderId != null) {
            if (!visited.add(cursor.id)) {
                return false;
            }
            if (ancestorFolderId.equals(cursor.parentFolderId)) {
                return true;
            }
            cursor = jme$getFolderConfig(folders, cursor.parentFolderId);
        }
        return false;
    }

    private static int jme$getFolderDepth(long routeId, String folderId) {
        final List<FolderConfig> folders = routeFolders.get(routeId);
        if (folders == null || folders.isEmpty()) {
            return -1;
        }
        final LinkedHashSet<String> visited = new LinkedHashSet<>();
        FolderConfig cursor = jme$getFolderConfig(folders, folderId);
        if (cursor == null) {
            return -1;
        }
        int depth = 0;
        while (cursor != null && cursor.parentFolderId != null) {
            if (!visited.add(cursor.id)) {
                return -1;
            }
            depth++;
            if (depth > MAX_FOLDER_DEPTH * 2) {
                return -1;
            }
            cursor = jme$getFolderConfig(folders, cursor.parentFolderId);
        }
        return depth;
    }

    private static void jme$collectFolderAndDescendantIds(List<FolderConfig> folders, String folderId, LinkedHashSet<String> result) {
        if (folderId == null || result.contains(folderId)) {
            return;
        }
        result.add(folderId);
        for (final FolderConfig folderConfig : folders) {
            if (folderId.equals(folderConfig.parentFolderId)) {
                jme$collectFolderAndDescendantIds(folders, folderConfig.id, result);
            }
        }
    }

    private static void jme$sanitizeFolders(Route route) {
        final List<FolderConfig> folders = routeFolders.get(route.getId());
        if (folders == null || folders.isEmpty()) {
            return;
        }

        final LinkedHashSet<Long> validPlatformIds = new LinkedHashSet<>();
        route.getRoutePlatforms().forEach(routePlatformData -> {
            if (routePlatformData.platform != null) {
                validPlatformIds.add(routePlatformData.platform.getId());
            }
        });

        boolean changed = false;
        final LinkedHashSet<String> seenFolderIds = new LinkedHashSet<>();
        for (final FolderConfig folder : folders) {
            if (folder.id == null || folder.id.isEmpty() || !seenFolderIds.add(folder.id)) {
                folder.id = UUID.randomUUID().toString();
                changed = true;
            }
        }

        final LinkedHashSet<Long> assignedPlatformIds = new LinkedHashSet<>();
        for (final FolderConfig folder : folders) {
            if (folder.name == null || folder.name.trim().isEmpty()) {
                folder.name = "Folder";
                changed = true;
            }

            final String normalizedParent = folder.parentFolderId == null || folder.parentFolderId.trim().isEmpty() ? null : folder.parentFolderId.trim();
            if ((normalizedParent != null && folder.id.equals(normalizedParent)) || (normalizedParent != null && jme$getFolderConfig(route.getId(), normalizedParent) == null)) {
                folder.parentFolderId = null;
                changed = true;
            } else if ((folder.parentFolderId == null && normalizedParent != null) || (folder.parentFolderId != null && !folder.parentFolderId.equals(normalizedParent))) {
                folder.parentFolderId = normalizedParent;
                changed = true;
            }

            final int sanitizedColor = jme$sanitizeIconColor(folder.iconColor);
            if (sanitizedColor != folder.iconColor) {
                folder.iconColor = sanitizedColor;
                changed = true;
            }
            if (folder.platformIds == null) {
                folder.platformIds = new ArrayList<>();
                changed = true;
                continue;
            }

            final List<Long> sanitizedPlatformIds = new ArrayList<>();
            for (final Long platformId : folder.platformIds) {
                if (platformId == null || !validPlatformIds.contains(platformId)) {
                    changed = true;
                    continue;
                }
                if (!assignedPlatformIds.add(platformId)) {
                    changed = true;
                    continue;
                }
                sanitizedPlatformIds.add(platformId);
            }

            if (!sanitizedPlatformIds.equals(folder.platformIds)) {
                folder.platformIds = sanitizedPlatformIds;
                changed = true;
            }
        }

        for (final FolderConfig folder : folders) {
            if (folder.parentFolderId == null) {
                continue;
            }
            final int depth = jme$getFolderDepth(route.getId(), folder.id);
            if (depth < 0 || depth >= MAX_FOLDER_DEPTH) {
                folder.parentFolderId = null;
                changed = true;
            }
        }

        if (changed) {
            save();
        }
    }

    private static int getStationColor(Platform platform) {
        return platform.area == null ? 0 : platform.area.getColor();
    }

    private static String getPlatformLabel(RoutePlatformData routePlatformData) {
        final Platform platform = routePlatformData.platform;
        if (platform == null) {
            return "";
        }

        final String customDestinationPrefix = routePlatformData.getCustomDestination().isEmpty() ? "" : Route.destinationIsReset(routePlatformData.getCustomDestination()) ? "\"" : "*";
        if (platform.area == null) {
            return customDestinationPrefix + "(" + platform.getName() + ")";
        }
        return customDestinationPrefix + platform.area.getName() + " (" + platform.getName() + ")";
    }

    private static String jme$getNextFolderName(List<FolderConfig> folders) {
        int suffix = 1;
        while (true) {
            final String name = suffix == 1 ? "Folder" : "Folder " + suffix;
            final boolean exists = folders.stream().anyMatch(folder -> name.equalsIgnoreCase(folder.name));
            if (!exists) {
                return name;
            }
            suffix++;
        }
    }

    private static FolderConfig jme$getFolderConfig(long routeId, String folderId) {
        final List<FolderConfig> folders = routeFolders.get(routeId);
        if (folders == null || folderId == null) {
            return null;
        }

        return jme$getFolderConfig(folders, folderId);
    }

    private static FolderConfig jme$getFolderConfig(List<FolderConfig> folders, String folderId) {
        if (folders == null || folderId == null) {
            return null;
        }
        for (final FolderConfig folderConfig : folders) {
            if (folderId.equals(folderConfig.id)) {
                return folderConfig;
            }
        }
        return null;
    }

    private static String jme$getSortablePlatformLabel(RoutePlatformData routePlatformData) {
        if (routePlatformData == null || routePlatformData.platform == null) {
            return "";
        }
        return getPlatformLabel(routePlatformData);
    }

    private static String jme$sanitizeFolderName(String requestedName, List<FolderConfig> folders) {
        final String trimmed = requestedName == null ? "" : requestedName.trim();
        if (!trimmed.isEmpty()) {
            return trimmed;
        }
        return jme$getNextFolderName(folders);
    }

    private static int jme$sanitizeIconColor(int iconColor) {
        if (iconColor < 0) {
            return -1;
        }
        return iconColor & 0xFFFFFF;
    }

    private static void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }

    public static synchronized void reloadFromDisk() {
        loaded = true;
        load();
        visibleRowsByRoute.clear();
    }

    private static void load() {
        routeFolders.clear();
        if (!Files.exists(CONFIG_PATH)) {
            return;
        }

        try {
            final JsonElement parsed = new JsonParser().parse(new String(Files.readAllBytes(CONFIG_PATH), StandardCharsets.UTF_8));
            if (!parsed.isJsonObject()) {
                return;
            }

            final JsonObject root = parsed.getAsJsonObject();
            root.entrySet().forEach(entry -> {
                final long routeId;
                try {
                    routeId = Long.parseLong(entry.getKey());
                } catch (Exception ignored) {
                    return;
                }

                if (!entry.getValue().isJsonArray()) {
                    return;
                }

                final JsonArray array = entry.getValue().getAsJsonArray();
                final List<FolderConfig> folders = new ArrayList<>();
                array.forEach(folderElement -> {
                    if (!folderElement.isJsonObject()) {
                        return;
                    }

                    final JsonObject folderObject = folderElement.getAsJsonObject();
                    final String id = folderObject.has("id") ? folderObject.get("id").getAsString() : UUID.randomUUID().toString();
                    final String name = folderObject.has("name") ? folderObject.get("name").getAsString() : "Folder";
                    final boolean expanded = !folderObject.has("expanded") || folderObject.get("expanded").getAsBoolean();
                    final String parentFolderId = folderObject.has("parent_id") ? folderObject.get("parent_id").getAsString() : null;

                    final List<Long> platformIds = new ArrayList<>();
                    if (folderObject.has("platform_ids") && folderObject.get("platform_ids").isJsonArray()) {
                        folderObject.getAsJsonArray("platform_ids").forEach(idElement -> {
                            try {
                                platformIds.add(idElement.getAsLong());
                            } catch (Exception ignored) {
                            }
                        });
                    } else if (folderObject.has("primary_platform_id")) {
                        try {
                            platformIds.add(folderObject.get("primary_platform_id").getAsLong());
                        } catch (Exception ignored) {
                        }
                    }

                    final int iconColor;
                    if (folderObject.has("icon_color")) {
                        int parsedColor;
                        try {
                            parsedColor = folderObject.get("icon_color").getAsInt();
                        } catch (Exception ignored) {
                            parsedColor = -1;
                        }
                        iconColor = jme$sanitizeIconColor(parsedColor);
                    } else {
                        iconColor = -1;
                    }

                    folders.add(new FolderConfig(id, name, expanded, platformIds, iconColor, parentFolderId));
                });

                if (!folders.isEmpty()) {
                    routeFolders.put(routeId, folders);
                }
            });
        } catch (Exception ignored) {
        }
    }

    private static void save() {
        final JsonObject root = new JsonObject();
        routeFolders.forEach((routeId, folders) -> {
            final JsonArray array = new JsonArray();
            folders.forEach(folderConfig -> {
                final JsonObject folderObject = new JsonObject();
                folderObject.addProperty("id", folderConfig.id);
                folderObject.addProperty("name", folderConfig.name);
                folderObject.addProperty("expanded", folderConfig.expanded);
                folderObject.addProperty("icon_color", folderConfig.iconColor);
                if (folderConfig.parentFolderId != null && !folderConfig.parentFolderId.isEmpty()) {
                    folderObject.addProperty("parent_id", folderConfig.parentFolderId);
                }
                final JsonArray platformIds = new JsonArray();
                folderConfig.platformIds.forEach(platformIds::add);
                folderObject.add("platform_ids", platformIds);
                array.add(folderObject);
            });
            if (!folders.isEmpty()) {
                root.add(Long.toString(routeId), array);
            }
        });

        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            Files.write(CONFIG_PATH, GSON.toJson(root).getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    private static final class FolderConfig {
        private String id;
        private String name;
        private boolean expanded;
        private List<Long> platformIds;
        private int iconColor;
        private String parentFolderId;

        private FolderConfig(String id, String name, boolean expanded, List<Long> platformIds, int iconColor, String parentFolderId) {
            this.id = id;
            this.name = name;
            this.expanded = expanded;
            this.platformIds = platformIds;
            this.iconColor = iconColor;
            this.parentFolderId = parentFolderId;
        }
    }

    public static final class RowMetadata {
        public final boolean folder;
        public final boolean child;
        public final String folderId;
        public final String parentFolderId;
        public final long primaryPlatformId;
        public final int startIndex;
        public final int endIndex;
        public final int platformIndex;
        public final long platformId;
        public final int depth;

        private RowMetadata(boolean folder, boolean child, String folderId, String parentFolderId, long primaryPlatformId, int startIndex, int endIndex, int platformIndex, long platformId, int depth) {
            this.folder = folder;
            this.child = child;
            this.folderId = folderId;
            this.parentFolderId = parentFolderId;
            this.primaryPlatformId = primaryPlatformId;
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.platformIndex = platformIndex;
            this.platformId = platformId;
            this.depth = depth;
        }

        private static RowMetadata folder(String folderId, String parentFolderId, long primaryPlatformId, int primaryPlatformIndex, int depth) {
            return new RowMetadata(true, false, folderId, parentFolderId, primaryPlatformId, primaryPlatformIndex, primaryPlatformIndex, -1, primaryPlatformId, depth);
        }

        private static RowMetadata childPlatform(String folderId, String parentFolderId, long primaryPlatformId, int platformIndex, long platformId, int depth) {
            return new RowMetadata(false, true, folderId, parentFolderId, primaryPlatformId, platformIndex, platformIndex, platformIndex, platformId, depth);
        }

        private static RowMetadata platform(int platformIndex, long platformId) {
            return new RowMetadata(false, false, null, null, 0, platformIndex, platformIndex, platformIndex, platformId, 0);
        }
    }
}
