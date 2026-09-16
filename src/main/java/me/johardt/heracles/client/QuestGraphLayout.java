package me.johardt.heracles.client;

import java.util.Collection;
import java.util.Map;

/** Pure geometry for the quest graph's canvas, world, and viewport. */
public final class QuestGraphLayout {
    public static final double MIN_ZOOM = 0.15;
    public static final double MAX_ZOOM = 2.0;

    private QuestGraphLayout() {}

    public static Point worldToScreen(
        CanvasBounds canvas,
        ViewportState viewport,
        double worldX,
        double worldY
    ) {
        return new Point(
            canvas.centerX() + (worldX - viewport.centerWorldX()) * viewport.zoom(),
            canvas.centerY() + (worldY - viewport.centerWorldY()) * viewport.zoom()
        );
    }

    public static Point screenToWorld(
        CanvasBounds canvas,
        ViewportState viewport,
        double screenX,
        double screenY
    ) {
        return new Point(
            viewport.centerWorldX() + (screenX - canvas.centerX()) / viewport.zoom(),
            viewport.centerWorldY() + (screenY - canvas.centerY()) / viewport.zoom()
        );
    }

    public static ScreenBounds worldToScreen(
        CanvasBounds canvas,
        ViewportState viewport,
        NodeBounds node
    ) {
        Point topLeft = worldToScreen(canvas, viewport, node.x(), node.y());
        return new ScreenBounds(
            topLeft.x(),
            topLeft.y(),
            node.width() * viewport.zoom(),
            node.height() * viewport.zoom()
        );
    }

    public static WorldBounds visibleWorld(CanvasBounds canvas, ViewportState viewport) {
        Point topLeft = screenToWorld(canvas, viewport, canvas.x(), canvas.y());
        Point bottomRight = screenToWorld(
            canvas,
            viewport,
            canvas.maxX(),
            canvas.maxY()
        );
        return new WorldBounds(
            topLeft.x(),
            topLeft.y(),
            bottomRight.x(),
            bottomRight.y()
        );
    }

    public static WorldBounds boundsOf(Collection<NodeBounds> nodes, double padding) {
        if (nodes == null || nodes.isEmpty()) return WorldBounds.empty();

        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (NodeBounds node : nodes) {
            if (node == null) continue;
            minX = Math.min(minX, node.x());
            minY = Math.min(minY, node.y());
            maxX = Math.max(maxX, node.maxX());
            maxY = Math.max(maxY, node.maxY());
        }
        if (!Double.isFinite(minX) || !Double.isFinite(minY)) return WorldBounds.empty();

        double safePadding = Double.isFinite(padding) ? Math.max(0, padding) : 0;
        return new WorldBounds(
            minX - safePadding,
            minY - safePadding,
            maxX + safePadding,
            maxY + safePadding
        );
    }

    public static ViewportState fitViewport(CanvasBounds canvas, WorldBounds bounds) {
        if (canvas.isEmpty() || bounds == null || bounds.isEmpty()) {
            return ViewportState.DEFAULT;
        }

        double width = Math.max(1, bounds.width());
        double height = Math.max(1, bounds.height());
        double horizontal = canvas.width() / width;
        double vertical = canvas.height() / height;
        double zoom = Math.min(1.0, Math.min(horizontal, vertical));
        zoom = clampZoom(zoom);
        return new ViewportState(bounds.centerX(), bounds.centerY(), zoom);
    }

    public static boolean fitsAtZoomOne(CanvasBounds canvas, WorldBounds bounds) {
        if (bounds == null || bounds.isEmpty()) return true;
        return visibleWorld(canvas, ViewportState.DEFAULT).contains(bounds);
    }

    public static String hitTest(
        Map<String, NodeBounds> nodes,
        CanvasBounds canvas,
        ViewportState viewport,
        double screenX,
        double screenY
    ) {
        if (nodes == null) return null;
        Point world = screenToWorld(canvas, viewport, screenX, screenY);
        String hit = null;
        for (Map.Entry<String, NodeBounds> entry : nodes.entrySet()) {
            if (entry.getValue() != null && entry.getValue().contains(world.x(), world.y())) {
                hit = entry.getKey();
            }
        }
        return hit;
    }

    public static double clampZoom(double zoom) {
        if (!Double.isFinite(zoom)) return 1.0;
        return Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom));
    }

    public record Point(double x, double y) {}

    public record CanvasBounds(double x, double y, double width, double height) {
        public CanvasBounds {
            x = finiteOrZero(x);
            y = finiteOrZero(y);
            width = Math.max(0, finiteOrZero(width));
            height = Math.max(0, finiteOrZero(height));
        }

        public double centerX() {
            return x + width / 2.0;
        }

        public double centerY() {
            return y + height / 2.0;
        }

        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean isEmpty() {
            return width <= 0 || height <= 0;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < maxX() && pointY >= y && pointY < maxY();
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0;
        }
    }

    public record WorldBounds(double minX, double minY, double maxX, double maxY) {
        public WorldBounds {
            minX = finiteOrZero(minX);
            minY = finiteOrZero(minY);
            maxX = finiteOrZero(maxX);
            maxY = finiteOrZero(maxY);
        }

        public static WorldBounds empty() {
            return new WorldBounds(0, 0, 0, 0);
        }

        public double width() {
            return Math.max(0, maxX - minX);
        }

        public double height() {
            return Math.max(0, maxY - minY);
        }

        public double centerX() {
            return (minX + maxX) / 2.0;
        }

        public double centerY() {
            return (minY + maxY) / 2.0;
        }

        public double maxX() {
            return maxX;
        }

        public double maxY() {
            return maxY;
        }

        public boolean isEmpty() {
            return width() <= 0 || height() <= 0;
        }

        public boolean contains(WorldBounds other) {
            return other != null &&
                minX <= other.minX &&
                minY <= other.minY &&
                maxX >= other.maxX &&
                maxY >= other.maxY;
        }

        private static double finiteOrZero(double value) {
            return Double.isFinite(value) ? value : 0;
        }
    }

    public record NodeBounds(double x, double y, double width, double height) {
        public NodeBounds {
            x = Double.isFinite(x) ? x : 0;
            y = Double.isFinite(y) ? y : 0;
            width = Math.max(0, Double.isFinite(width) ? width : 0);
            height = Math.max(0, Double.isFinite(height) ? height : 0);
        }

        public static NodeBounds centered(double centerX, double centerY, double width, double height) {
            return new NodeBounds(
                centerX - width / 2.0,
                centerY - height / 2.0,
                width,
                height
            );
        }

        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < maxX() && pointY >= y && pointY < maxY();
        }
    }

    public record ScreenBounds(double x, double y, double width, double height) {
        public double maxX() {
            return x + width;
        }

        public double maxY() {
            return y + height;
        }

        public boolean contains(double pointX, double pointY) {
            return pointX >= x && pointX < maxX() && pointY >= y && pointY < maxY();
        }
    }

    public record ViewportState(double centerWorldX, double centerWorldY, double zoom) {
        public static final ViewportState DEFAULT = new ViewportState(0, 0, 1);

        public ViewportState {
            centerWorldX = Double.isFinite(centerWorldX) ? centerWorldX : 0;
            centerWorldY = Double.isFinite(centerWorldY) ? centerWorldY : 0;
            zoom = clampZoom(zoom);
        }
    }
}
