package avalanche.core.ingestion.models.json;

import android.support.annotation.NonNull;

import org.json.JSONException;

import avalanche.core.ingestion.models.Log;
import avalanche.core.ingestion.models.LogContainer;

public interface LogSerializer {

    String serializeLog(@NonNull Log log) throws JSONException;

    Log deserializeLog(@NonNull String json) throws JSONException;

    String serializeContainer(@NonNull LogContainer container) throws JSONException;

    LogContainer deserializeContainer(@NonNull String json) throws JSONException;

    void addLogFactory(@NonNull String logType, @NonNull LogFactory logFactory);
}