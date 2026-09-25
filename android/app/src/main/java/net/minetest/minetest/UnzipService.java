package net.minetest.minetest;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UnzipService extends IntentService {
    public static final String ACTION_UPDATE = "net.minetest.minetest.ACTION_UPDATE";
    public static final String ACTION_PROGRESS = "progress";
    public static final String ACTION_PROGRESS_MESSAGE = "message";
    public static final String ACTION_FAILURE = "failure";

    public static final int INDETERMINATE = -1;
    public static final int SUCCESS = -2;
    public static final int FAILURE = -3;

    private static boolean isRunning = false;

    public UnzipService() {
        super("UnzipService");
    }

    public static boolean getIsRunning() {
        return isRunning;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        isRunning = true;
        File targetDir = Utils.getUserDataDirectory(this);

        try (InputStream is = getAssets().open("assets.zip");
             ZipInputStream zis = new ZipInputStream(is)) {

            ZipEntry entry;
            byte[] buffer = new byte[8192];

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Strip leading "assets/" or top folder wrapper if present in zip
                if (name.startsWith("assets/")) {
                    name = name.substring(7);
                }
                if (name.isEmpty()) continue;

                File file = new File(targetDir, name);
                if (entry.isDirectory()) {
                    file.mkdirs();
                } else {
                    File parent = file.getParentFile();
                    if (parent != null && !parent.exists()) {
                        parent.mkdirs();
                    }
                    try (FileOutputStream fos = new FileOutputStream(file)) {
                        int count;
                        while ((count = zis.read(buffer)) != -1) {
                            fos.write(buffer, 0, count);
                        }
                    }
                }
                zis.closeEntry();
            }

            Intent doneIntent = new Intent(ACTION_UPDATE);
            doneIntent.putExtra(ACTION_PROGRESS, SUCCESS);
            sendBroadcast(doneIntent);

        } catch (Exception e) {
            Log.e("UnzipService", "Extraction failed", e);
            try {
                File crashFile = new File(getExternalFilesDir(null), "crash.txt");
                PrintWriter pw = new PrintWriter(crashFile);
                e.printStackTrace(pw);
                pw.close();
            } catch (Exception ignored) {}

            Intent failIntent = new Intent(ACTION_UPDATE);
            failIntent.putExtra(ACTION_PROGRESS, FAILURE);
            failIntent.putExtra(ACTION_FAILURE, e.getMessage());
            sendBroadcast(failIntent);
        } finally {
            isRunning = false;
        }
    }
}
