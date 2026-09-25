package net.minetest.minetest;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import androidx.annotation.NonNull;
import java.io.File;
import java.util.Objects;

public class Utils {
    private static final String TAG = "Utils";

    @NonNull
    public static File createDirs(@NonNull File root, @NonNull String dir) {
        File f = new File(root, dir);
        if (!f.isDirectory()) {
            if (!f.mkdirs()) {
                Log.e(TAG, "Directory " + f.getAbsolutePath() + " cannot be created");
            }
        }
        return f;
    }

    @NonNull
    public static File getUserDataDirectory(@NonNull Context context) {
        // 1. Try public storage (/storage/emulated/0/ELI5)
        File primaryDir = new File(Environment.getExternalStorageDirectory(), "ELI5");
        if (primaryDir.exists() || primaryDir.mkdirs()) {
            return primaryDir;
        }

        // 2. Fallback to scoped app storage (Android/data/client.eli5.game/files/ELI5)
        File fallbackDir = context.getExternalFilesDir(null);
        if (fallbackDir != null) {
            return createDirs(fallbackDir, "ELI5");
        }

        // 3. Last resort internal storage
        return createDirs(context.getFilesDir(), "ELI5");
    }

    @NonNull
    public static File getCacheDirectory(@NonNull Context context) {
        return Objects.requireNonNull(
            context.getCacheDir(),
            "Cannot get cache directory"
        );
    }

    public static boolean isInstallValid(@NonNull Context context) {
        File userDataDirectory = getUserDataDirectory(context);
        return userDataDirectory.isDirectory() &&
            new File(userDataDirectory, "builtin").isDirectory() &&
            new File(userDataDirectory, "client").isDirectory() &&
            new File(userDataDirectory, "textures").isDirectory();
    }
}
