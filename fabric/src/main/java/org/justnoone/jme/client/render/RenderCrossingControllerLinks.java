package org.justnoone.jme.client.render;

import org.mtr.mapping.mapper.BlockEntityExtension;
import org.mtr.mapping.mapper.BlockEntityRenderer;
import org.mtr.mapping.mapper.GraphicsHolder;

/**
 * For a crossing controller drawn by its JSON block model: draws nothing, but lists the controller (it is called for
 * every loaded controller each frame) so the MTR brush shows its connections, like {@link RenderCrossing} does for
 * level crossings.
 */
public class RenderCrossingControllerLinks<T extends BlockEntityExtension> extends BlockEntityRenderer<T> {

    public RenderCrossingControllerLinks(Argument argument) {
        super(argument);
    }

    @Override
    public void render(T blockEntity, float tickDelta, GraphicsHolder graphicsHolder, int light, int overlay) {
        ClientCrossingLinks.onControllerRendered(blockEntity);
    }

    @Override
    public boolean rendersOutsideBoundingBox2(T blockEntity) {
        return true;
    }
}
