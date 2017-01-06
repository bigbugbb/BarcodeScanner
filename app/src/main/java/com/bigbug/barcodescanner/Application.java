package com.bigbug.barcodescanner;


import android.Manifest;
import android.app.Activity;
import android.os.Handler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class Application extends android.app.Application {
    /**
     * Request code for camera permissions.
     */
    public static final int REQUEST_CAMERA_PERMISSIONS = 1;

    /**
     * Permissions required to take a picture.
     */
    public static final String[] CAMERA_PERMISSIONS = {
            Manifest.permission.CAMERA
    };

    private Handler mHandler;

    private static Application sInstance;

    private Map<Class<? extends BaseUIListener>, Collection<? extends BaseUIListener>> mUiListeners;

    public Application() {
        sInstance = this;
        mHandler = new Handler();
        mUiListeners = new HashMap<>();
    }

    public static Application getInstance() {
        if (sInstance == null) {
            throw new IllegalStateException();
        }
        return sInstance;
    }

    @SuppressWarnings("unchecked")
    private <T extends BaseUIListener> Collection<T> getOrCreateUIListeners(Class<T> cls) {
        Collection<T> collection = (Collection<T>) mUiListeners.get(cls);
        if (collection == null) {
            collection = new ArrayList<>();
            mUiListeners.put(cls, collection);
        }
        return collection;
    }

    /**
     * @param cls Requested class of listeners.
     * @return List of registered UI listeners.
     */
    public <T extends BaseUIListener> Collection<T> getUIListeners(Class<T> cls) {
        return Collections.unmodifiableCollection(getOrCreateUIListeners(cls));
    }

    /**
     * Register new listener.
     * <p/>
     * Should be called from {@link Activity#onResume()}.
     */
    public <T extends BaseUIListener> void addUIListener(Class<T> cls, T listener) {
        getOrCreateUIListeners(cls).add(listener);
    }

    /**
     * Unregister listener.
     * <p/>
     * Should be called from {@link Activity#onPause()}.
     */
    public <T extends BaseUIListener> void removeUIListener(Class<T> cls, T listener) {
        getOrCreateUIListeners(cls).remove(listener);
    }

    public void runOnUiThread(final Runnable runnable) {
        mHandler.post(runnable);
    }
}
