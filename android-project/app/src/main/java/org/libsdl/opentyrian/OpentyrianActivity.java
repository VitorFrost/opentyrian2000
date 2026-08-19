package org.libsdl.opentyrian;

import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;

import org.libsdl.app.SDLActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class OpentyrianActivity extends SDLActivity
{
    private static final String TAG = "OpentyrianActivity";

    // Bumped automatically by the build: changes whenever assets are re-staged.
    // Stored in SharedPreferences so we only re-copy when the APK's data
    // differs from what's already in the per-app data directory.
    private static final String PREFS_NAME = "tyrian_assets";
    private static final String KEY_INSTALLED_VERSION = "installed_version";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            installDataFiles();
        } catch (IOException e) {
            Log.e(TAG, "Failed to install Tyrian data files", e);
        }
        super.onCreate(savedInstanceState);
        hideSystemBars();

        // SDL may update its window style shortly after its native thread starts.
        // Hiding insets again is intentionally non-invasive: unlike changing the
        // decor-fit/cutout geometry it does not tear down SDL's SurfaceView.
        getWindow().getDecorView().postDelayed(this::hideSystemBars, 500);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            hideSystemBars();
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        hideSystemBars();
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars() {
        Window window = getWindow();
        if (window == null) {
            return;
        }

        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = window.getInsetsController();
            if (controller != null) {
                controller.setSystemBarsBehavior(
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
                controller.hide(WindowInsets.Type.systemBars());
            }
        } else {
            window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    // Copy bundled assets/data/* into <filesDir>/data/ and ensure the save
    // directory exists. Files are skipped when already present and the
    // installed-version marker matches the current APK build, so this is
    // effectively a no-op after the first launch.
    private void installDataFiles() throws IOException {
        File filesDir = getFilesDir();
        File dataDir = new File(filesDir, "data");
        File saveDir = new File(filesDir, "save");

        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IOException("Cannot create " + dataDir);
        }
        if (!saveDir.exists() && !saveDir.mkdirs()) {
            throw new IOException("Cannot create " + saveDir);
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long installedVersion = prefs.getLong(KEY_INSTALLED_VERSION, -1);
        long currentVersion;
        try {
            currentVersion = getPackageManager()
                .getPackageInfo(getPackageName(), 0).lastUpdateTime;
        } catch (Exception e) {
            currentVersion = 0;
        }

        boolean forceCopy = installedVersion != currentVersion;
        copyAssetDir("data", dataDir, forceCopy);

        prefs.edit().putLong(KEY_INSTALLED_VERSION, currentVersion).apply();
    }

    private void copyAssetDir(String assetPath, File outDir, boolean force) throws IOException {
        AssetManager am = getAssets();
        String[] names = am.list(assetPath);
        if (names == null || names.length == 0) {
            Log.w(TAG, "No assets under '" + assetPath + "'");
            return;
        }
        for (String name : names) {
            String childAsset = assetPath + "/" + name;
            File outFile = new File(outDir, name);
            String[] sub = am.list(childAsset);
            if (sub != null && sub.length > 0) {
                if (!outFile.exists() && !outFile.mkdirs()) {
                    throw new IOException("Cannot create " + outFile);
                }
                copyAssetDir(childAsset, outFile, force);
            } else {
                if (!force && outFile.exists()) {
                    continue;
                }
                copyAssetFile(childAsset, outFile);
            }
        }
    }

    private void copyAssetFile(String assetPath, File outFile) throws IOException {
        try (InputStream in = getAssets().open(assetPath);
             OutputStream out = new FileOutputStream(outFile)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }
}
