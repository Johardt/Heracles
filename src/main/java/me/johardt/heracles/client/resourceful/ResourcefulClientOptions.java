package me.johardt.heracles.client.resourceful;

import com.teamresourceful.resourcefulconfig.api.annotations.Config;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigEntry;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigInfo;
import com.teamresourceful.resourcefulconfig.api.annotations.ConfigOption;
import com.teamresourceful.resourcefulconfig.api.loader.Configurator;
import com.teamresourceful.resourcefulconfig.api.types.entries.Observable;
import me.johardt.heracles.Heracles;
import me.johardt.heracles.client.HeraclesClientOptions;

/** Resourceful Config view of the versioned Heracles client preferences. */
@Config(value = "heracles_options", version = HeraclesClientOptions.CURRENT_SCHEMA_VERSION)
@ConfigInfo(
    icon = "settings",
    title = "Heracles Options",
    description = "Client-side display and editor preferences for Heracles."
)
public final class ResourcefulClientOptions {
    @ConfigEntry(id = "maxEditorHistory")
    @ConfigOption.Range(min = 10, max = 1000)
    public static Observable<Integer> maxEditorHistory = Observable.of(
        HeraclesClientOptions.DEFAULT_MAX_EDITOR_HISTORY
    );

    @ConfigEntry(id = "minimapMode")
    public static Observable<HeraclesClientOptions.MinimapMode> minimapMode = Observable.of(
        HeraclesClientOptions.MinimapMode.FLOATING
    );

    @ConfigEntry(id = "minimapX")
    @ConfigOption.Range(min = 0, max = 1)
    @ConfigOption.Slider
    public static Observable<Double> minimapX = Observable.of(1.0);

    @ConfigEntry(id = "minimapY")
    @ConfigOption.Range(min = 0, max = 1)
    @ConfigOption.Slider
    public static Observable<Double> minimapY = Observable.of(1.0);

    @ConfigEntry(id = "showGrid")
    public static Observable<Boolean> showGrid = Observable.of(false);

    @ConfigEntry(id = "snapToGrid")
    public static Observable<Boolean> snapToGrid = Observable.of(false);

    @ConfigEntry(id = "trackerAnchor")
    public static Observable<HeraclesClientOptions.TrackerAnchor> trackerAnchor = Observable.of(
        HeraclesClientOptions.TrackerAnchor.TOP_RIGHT
    );

    @ConfigEntry(id = "tutorialAutoShow")
    public static Observable<Boolean> tutorialAutoShow = Observable.of(true);

    @ConfigEntry(id = "tutorialSeen")
    public static Observable<Boolean> tutorialSeen = Observable.of(false);

    private static Configurator configurator;
    private static boolean applying;

    private ResourcefulClientOptions() {}

    public static void register() {
        if (configurator != null) return;

        setFromPreferences(HeraclesClientOptions.preferences());
        addListeners();
        applying = true;
        try {
            configurator = new Configurator(Heracles.MOD_ID);
            configurator.register(ResourcefulClientOptions.class);
        } finally {
            applying = false;
        }

        HeraclesClientOptions.applyExternalPreferences(currentPreferences());
        saveFromPreferences(HeraclesClientOptions.preferences());
    }

    public static void saveFromPreferences(HeraclesClientOptions.Preferences preferences) {
        if (configurator == null || preferences == null) return;

        applying = true;
        try {
            setFromPreferences(preferences);
            configurator.saveConfig(ResourcefulClientOptions.class);
        } finally {
            applying = false;
        }
    }

    private static void addListeners() {
        maxEditorHistory.addListener((previous, current) -> changed());
        minimapMode.addListener((previous, current) -> changed());
        minimapX.addListener((previous, current) -> changed());
        minimapY.addListener((previous, current) -> changed());
        showGrid.addListener((previous, current) -> changed());
        snapToGrid.addListener((previous, current) -> changed());
        trackerAnchor.addListener((previous, current) -> changed());
        tutorialAutoShow.addListener((previous, current) -> changed());
        tutorialSeen.addListener((previous, current) -> changed());
    }

    private static void changed() {
        if (!applying) HeraclesClientOptions.applyExternalPreferences(currentPreferences());
    }

    private static HeraclesClientOptions.Preferences currentPreferences() {
        return new HeraclesClientOptions.Preferences(
            HeraclesClientOptions.CURRENT_SCHEMA_VERSION,
            maxEditorHistory.get(),
            minimapMode.get(),
            minimapX.get(),
            minimapY.get(),
            showGrid.get(),
            snapToGrid.get(),
            trackerAnchor.get(),
            tutorialAutoShow.get(),
            tutorialSeen.get()
        );
    }

    private static void setFromPreferences(HeraclesClientOptions.Preferences preferences) {
        maxEditorHistory = update(maxEditorHistory, preferences.maxEditorHistory());
        minimapMode = update(minimapMode, preferences.minimapMode());
        minimapX = update(minimapX, preferences.minimapX());
        minimapY = update(minimapY, preferences.minimapY());
        showGrid = update(showGrid, preferences.showGrid());
        snapToGrid = update(snapToGrid, preferences.snapToGrid());
        trackerAnchor = update(trackerAnchor, preferences.trackerAnchor());
        tutorialAutoShow = update(tutorialAutoShow, preferences.tutorialAutoShow());
        tutorialSeen = update(tutorialSeen, preferences.tutorialSeen());
    }

    private static <T> Observable<T> update(Observable<T> current, T value) {
        current.accept(value);
        return current;
    }
}
