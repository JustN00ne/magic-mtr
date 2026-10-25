package org.justnoone.jme.client.render;

import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectCollection;
import org.mtr.libraries.it.unimi.dsi.fastutil.objects.ObjectObjectImmutablePair;
import org.mtr.mapping.mapper.OptimizedModel;
import org.mtr.mapping.mapper.ResourceManagerHelper;
import org.mtr.mod.render.DynamicVehicleModel;
import org.mtr.mod.resource.CachedResource;
import org.mtr.mod.resource.OptimizedModelWrapper;
import org.mtr.mod.resource.StoredModelResourceBase;

import javax.annotation.Nullable;

/**
 * A MAGIC block model authored as OBJ ({@code assets/jme/models/obj/*.obj}), loaded and drawn through MTR's own OBJ
 * pipeline (the one used for resource-pack trains, rails and eye candy). That keeps the OBJ's per-vertex normals, UVs
 * and non axis-aligned faces, works on every Minecraft version MAGIC supports, and falls back to MTR's non-optimized
 * renderer automatically (eg with shader packs).
 * <p>
 * The block JSON models can only describe axis-aligned boxes, so blocks that only used them rendered as blocky
 * approximations of these OBJ files (the octagonal crossing lamp heads were diamonds, the axle counter lost its slanted
 * face and stepped profile).
 */
public final class ObjBlockModel implements StoredModelResourceBase {

    /**
     * The axle counter OBJ is placed with x measured from the block's west edge (Blockbench export), while z is already
     * centred; shift it so the model is centred on the block and can be rotated with the block's facing.
     */
    public static final ObjBlockModel AXLE_COUNTER = new ObjBlockModel("jme:models/obj/axle_counter.obj", "jme:textures/block/axle_counter.png", -0.5);

    /**
     * The whole level crossing, once per lamp texture: both lamps dark, lamp 1 lit, lamp 2 lit (see
     * {@code CrossingSignal#LAMP}). The OBJ is converted from the mesh elements of the Blockbench model
     * ({@code crossing_1.bbmodel} at repo root), which MTR's own bbmodel loader cannot read (it only supports cubes).
     */
    private static final String CROSSING_MODEL = "jme:models/obj/level_crossing.obj";
    private static final ObjBlockModel[] CROSSING = {
            new ObjBlockModel(CROSSING_MODEL, "jme:textures/block/level_crossing.png", 0),
            new ObjBlockModel(CROSSING_MODEL, "jme:textures/block/level_crossing_o1.png", 0),
            new ObjBlockModel(CROSSING_MODEL, "jme:textures/block/level_crossing_o2.png", 0),
    };

    private static final long CACHE_LIFESPAN_MILLIS = 60_000L;

    private final String modelResource;
    private final String textureResource;
    private final double offsetX;
    private volatile CachedResource<ObjectObjectImmutablePair<OptimizedModelWrapper, DynamicVehicleModel>> cachedModel;

    private ObjBlockModel(String modelResource, String textureResource, double offsetX) {
        this.modelResource = modelResource;
        this.textureResource = textureResource;
        this.offsetX = offsetX;
        cachedModel = createCache();
    }

    /**
     * Drops the loaded models after a resource reload, so they are rebuilt against the new resources.
     */
    public static void invalidateAll() {
        AXLE_COUNTER.cachedModel = AXLE_COUNTER.createCache();
        for (final ObjBlockModel model : CROSSING) {
            model.cachedModel = model.createCache();
        }
    }

    /**
     * @param lamp the crossing's {@code CrossingSignal#LAMP} value
     */
    public static ObjBlockModel getCrossing(int lamp) {
        return CROSSING[lamp >= 0 && lamp < CROSSING.length ? lamp : 0];
    }

    @Nullable
    @Override
    public OptimizedModelWrapper getOptimizedModel() {
        final ObjectObjectImmutablePair<OptimizedModelWrapper, DynamicVehicleModel> models = cachedModel.getData(false);
        return models == null ? null : models.left();
    }

    @Nullable
    @Override
    public DynamicVehicleModel getDynamicVehicleModel() {
        final ObjectObjectImmutablePair<OptimizedModelWrapper, DynamicVehicleModel> models = cachedModel.getData(false);
        return models == null ? null : models.right();
    }

    @Override
    public void preload() {
        cachedModel.getData(true);
    }

    @Override
    public void transform(ObjectCollection<OptimizedModel.ObjModel> objModels) {
        if (offsetX != 0) {
            objModels.forEach(objModel -> objModel.applyTranslation(offsetX, 0, 0));
        }
    }

    private CachedResource<ObjectObjectImmutablePair<OptimizedModelWrapper, DynamicVehicleModel>> createCache() {
        return new CachedResource<>(() -> load(modelResource, textureResource, false, 0, identifier -> {
            final String content = ResourceManagerHelper.readResource(identifier);
            return content == null ? "" : content;
        }), CACHE_LIFESPAN_MILLIS);
    }
}
