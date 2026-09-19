package me.johardt.heracles.core;

import com.google.gson.JsonObject;
import java.io.IOException;

/** Persistence port for the complete per-player quest progress document. */
public interface ProgressStore {

    JsonObject load() throws IOException;

    void save(JsonObject progress) throws IOException;
}
