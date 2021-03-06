package io.o2mc.sdk.business.batch;

import android.support.annotation.NonNull;
import android.util.Log;
import com.google.gson.Gson;
import io.o2mc.sdk.BuildConfig;
import io.o2mc.sdk.domain.Batch;
import java.io.IOException;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Dispatches events in JSON format.
 * Singleton class, guaranteed to have only one instance in the apps lifecycle.
 * Sends events in a thread-safe manner.
 */
public class BatchDispatcher {

  private static final String TAG = "BatchDispatcher";

  private static Gson gson;
  private static BatchManager batchManager;

  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
  private static final OkHttpClient client =
      new OkHttpClient().newBuilder().retryOnConnectionFailure(false).build();

  // ==========================================
  // region Start singleton technicalities
  // ==========================================
  private static BatchDispatcher instance;

  public static synchronized BatchDispatcher getInstance() {
    if (instance == null) {
      instance = new BatchDispatcher();
      gson = new Gson();
    }
    return instance;
  }
  // ==========================================
  // endregion Start singleton technicalities
  // ==========================================

  /**
   * Sends analytical events to backend specified by URL in JSON format.
   *
   * @param url url of backend to send events to
   * @param batch list of events with meta data
   */
  public void post(String url, Batch batch) {
    try {
      String json = batchToJson(batch);

      RequestBody body = RequestBody.create(JSON, json);
      Request request = new Request.Builder().url(url).post(body).build();
      client.newCall(request).enqueue(new Callback() {
        @Override
        public void onFailure(@NonNull Call call, @NonNull IOException e) {
          if (BuildConfig.DEBUG) {
            Log.e(TAG, String.format("Unable to post data: '%s'", e.getMessage()));
          }

          BatchDispatcher.getInstance().failureCallback();
        }

        @SuppressWarnings("ConstantConditions") // invalid warning because it's checked for
        @Override
        public void onResponse(@NonNull Call call, @NonNull Response response) {
          if (response.isSuccessful()) {
            // Http response indicates success, inform user and SDK
            BatchDispatcher.getInstance().successCallback();
            if (response.body() == null) {
              if (BuildConfig.DEBUG) {
                Log.w(TAG, "onResponse: empty http response from backend");
              }
            }
          } else {
            try {
              // Http response indicates failure, inform user and SDK
              BatchDispatcher.getInstance().failureCallback();

              if (BuildConfig.DEBUG) {
                Log.w(TAG, String.format("onResponse: Http response indicates failure: '%s'",
                    response.body().string()));
              }
            } catch (NullPointerException | IOException ex) {
              if (BuildConfig.DEBUG) {
                Log.e(TAG, "onResponse: Response string is null", ex);
              }
            }
          }
        }
      });
    } catch (IllegalArgumentException | NullPointerException e) {
      BatchDispatcher.getInstance().failureCallback();

      if (BuildConfig.DEBUG) {
        Log.e(TAG, "post: Failed to dispatch events", e);
      }
    }
  }

  /**
   * Transforms a list of events to an array of JsonObjects in Json format
   *
   * @param batch list of events with meta data
   * @return list of JsonObjects in JSON format, as String
   */
  private String batchToJson(Batch batch) {
    return gson.toJson(batch);
  }

  public void setBatchManager(BatchManager batchManager) {
    BatchDispatcher.batchManager = batchManager;

    if (BuildConfig.DEBUG) {
      Log.d(TAG, "Set o2mc field.");
    }
  }

  /**
   * Called upon successful HTTP post
   */
  private void successCallback() {
    if (batchManager == null) {
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "O2mc variable is null.");
      }
      return;
    }
    batchManager.dispatchSuccess();
  }

  /**
   * Called upon failure of HTTP post
   */
  private void failureCallback() {
    if (batchManager == null) {
      if (BuildConfig.DEBUG) {
        Log.d(TAG, "O2mc variable is null.");
      }
      return;
    }
    batchManager.dispatchFailure();
  }
}
