package me.johardt.heracles.client;

import me.johardt.heracles.core.QuestDefinition;

/**
 * Pure, shared geometry for one quest node. The node center is always the
 * authored graph position; the hit bounds also include backgrounds that extend
 * beyond the icon container.
 */
public record QuestNodeMetrics(
    int iconSize,
    int containerSize,
    int backgroundWidth,
    int backgroundHeight,
    int backgroundOffsetX,
    int backgroundOffsetY,
    int textureFrameWidth,
    int textureFrameHeight,
    QuestGraphLayout.NodeBounds containerBounds,
    QuestGraphLayout.NodeBounds backgroundBounds,
    QuestGraphLayout.NodeBounds bounds
) {
    public static final int DEFAULT_ICON_SIZE = 16;
    public static final int MIN_ICON_SIZE = 8;
    public static final int MAX_ICON_SIZE = 64;

    public static QuestNodeMetrics forQuest(
        double centerX,
        double centerY,
        int requestedIconSize,
        String background
    ) {
        int iconSize = clampIconSize(requestedIconSize);
        int containerSize = iconSize + 8;
        String path = background == null ? "" : background;
        boolean diamonds = path.endsWith("/diamonds.png");
        boolean hearts = path.endsWith("/hearts.png");
        boolean pentagons = path.endsWith("/pentagons.png");
        int textureFrameWidth = diamonds || hearts ? 32 : 24;
        int textureFrameHeight = diamonds || hearts ? 32 : 24;
        int backgroundWidth = diamonds || hearts ? iconSize * 2 : containerSize;
        int backgroundHeight = diamonds || hearts ? iconSize * 2 : containerSize;
        int backgroundOffsetX = diamonds || hearts ? scaledOffset(-4, iconSize) : 0;
        int backgroundOffsetY = diamonds ? scaledOffset(-4, iconSize)
            : hearts ? scaledOffset(-2, iconSize)
            : pentagons ? scaledOffset(-2, iconSize) : 0;

        QuestGraphLayout.NodeBounds container = QuestGraphLayout.NodeBounds.centered(
            centerX, centerY, containerSize, containerSize
        );
        QuestGraphLayout.NodeBounds backgroundBounds = new QuestGraphLayout.NodeBounds(
            container.x() + backgroundOffsetX,
            container.y() + backgroundOffsetY,
            backgroundWidth,
            backgroundHeight
        );
        QuestGraphLayout.NodeBounds bounds = union(container, backgroundBounds);
        return new QuestNodeMetrics(
            iconSize,
            containerSize,
            backgroundWidth,
            backgroundHeight,
            backgroundOffsetX,
            backgroundOffsetY,
            textureFrameWidth,
            textureFrameHeight,
            container,
            backgroundBounds,
            bounds
        );
    }

    public static QuestNodeMetrics forQuest(
        QuestDefinition quest,
        double centerX,
        double centerY
    ) {
        if (quest == null) return forQuest(centerX, centerY, DEFAULT_ICON_SIZE, "");
        return forQuest(
            centerX,
            centerY,
            quest.display().iconSize(),
            quest.display().iconBackground()
        );
    }

    public static int clampIconSize(int size) {
        return Math.max(MIN_ICON_SIZE, Math.min(MAX_ICON_SIZE, size));
    }

    /** Keeps minimap marks compact while preserving the two larger icon tiers. */
    public static int minimapMarkSize(int iconSize) {
        int safeSize = clampIconSize(iconSize);
        return safeSize > 48 ? 9 : safeSize > 16 ? 7 : 5;
    }

    public int minimapMarkSize() {
        return minimapMarkSize(iconSize);
    }

    public double centerX() {
        return containerBounds.x() + containerBounds.width() / 2.0;
    }

    public double centerY() {
        return containerBounds.y() + containerBounds.height() / 2.0;
    }

    public double iconX() {
        return containerBounds.x() + 4;
    }

    public double iconY() {
        return containerBounds.y() + 4;
    }

    private static int scaledOffset(int defaultOffset, int iconSize) {
        return (int) Math.round(defaultOffset * iconSize / (double) DEFAULT_ICON_SIZE);
    }

    private static QuestGraphLayout.NodeBounds union(
        QuestGraphLayout.NodeBounds first,
        QuestGraphLayout.NodeBounds second
    ) {
        double minX = Math.min(first.x(), second.x());
        double minY = Math.min(first.y(), second.y());
        double maxX = Math.max(first.maxX(), second.maxX());
        double maxY = Math.max(first.maxY(), second.maxY());
        return new QuestGraphLayout.NodeBounds(minX, minY, maxX - minX, maxY - minY);
    }
}
