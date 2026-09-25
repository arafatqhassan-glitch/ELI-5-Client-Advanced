package net.minetest.minetest;

import android.annotation.SuppressLint;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.PrintWriter;

import static net.minetest.minetest.UnzipService.*;

public class MainActivity extends AppCompatActivity {
    public static final String NOTIFICATION_CHANNEL_ID = "Minetest channel";
    public static final int NOTIFICATION_ID_UNZIP = 1;
    public static final int NOTIFICATION_ID_GAME = 2;
    private static final int PERMISSION_REQUEST_CODE = 100;

    private final static int versionCode = BuildConfig.VERSION_CODE;
    private static final String SETTINGS = "MinetestSettings";
    private static final String TAG_VERSION_CODE = "versionCode";

    private ProgressBar mProgressBar;
    private TextView mTextView;
    private SharedPreferences sharedPreferences;

    private final BroadcastReceiver myReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            int progress = 0;
            @StringRes int message = 0;
            if (intent != null) {
                progress = intent.getIntExtra(ACTION_PROGRESS, 0);
                message = intent.getIntExtra(ACTION_PROGRESS_MESSAGE, 0);
            }

            if (progress == FAILURE) {
                Toast.makeText(MainActivity.this, intent.getStringExtra(ACTION_FAILURE), Toast.LENGTH_LONG).show();
                finish();
            } else if (progress == SUCCESS) {
                startNative();
            } else {
                if (mProgressBar != null) {
                    mProgressBar.setVisibility(View.VISIBLE);
                    if (progress == INDETERMINATE) {
                        mProgressBar.setIndeterminate(true);
                    } else {
                        mProgressBar.setIndeterminate(false);
                        mProgressBar.setProgress(progress);
                    }
                }
                mTextView.setVisibility(View.VISIBLE);
                if (message != 0)
                    mTextView.setText(message);
            }
        }
    };

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            try {
                File file = new File(getExternalFilesDir(null), "crash.txt");
                PrintWriter pw = new PrintWriter(file);
                throwable.printStackTrace(pw);
                pw.close();
            } catch (Exception ignored) {}
            System.exit(1);
        });

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        IntentFilter filter = new IntentFilter(ACTION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(myReceiver, filter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(myReceiver, filter);
        }

        mProgressBar = findViewById(R.id.progressBar);
        mTextView = findViewById(R.id.textView);
        sharedPreferences = getSharedPreferences(SETTINGS, Context.MODE_PRIVATE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            createNotificationChannel();

        if (checkStoragePermissions()) {
            checkAppVersion();
        } else {
            requestStoragePermissions();
        }
    }

    private boolean checkStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return android.os.Environment.isExternalStorageManager();
        } else {
            int write = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE);
            return write == android.content.pm.PackageManager.PERMISSION_GRANTED && read == android.content.pm.PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.setData(android.net.Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            } catch (Exception e) {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSION_REQUEST_CODE);
            }
        } else {
            androidx.core.app.ActivityCompat.requestPermissions(
                this,
                new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
                },
                PERMISSION_REQUEST_CODE
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                checkAppVersion();
            } else {
                Toast.makeText(this, "Storage permission is required to extract game files!", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (checkStoragePermissions()) {
                checkAppVersion();
            } else {
                Toast.makeText(this, "Storage permission is required to extract game files!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void checkAppVersion() {
        if (UnzipService.getIsRunning()) {
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setIndeterminate(true);
            mTextView.setVisibility(View.VISIBLE);
            return;
        }
        int stored = sharedPreferences.getInt(TAG_VERSION_CODE, 0);
        boolean valid = Utils.isInstallValid(this);
        if (stored == versionCode && valid) {
            startNative();
        } else {
            mProgressBar.setVisibility(View.VISIBLE);
            mProgressBar.setIndeterminate(true);
            mTextView.setVisibility(View.VISIBLE);

            Intent intent = new Intent(this, UnzipService.class);
            startService(intent);
        }
    }

    private void startNative() {
        sharedPreferences.edit().putInt(TAG_VERSION_CODE, versionCode).apply();
        Intent intent = new Intent(this, GameActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private void createNotificationChannel() {
        NotificationManager notifyManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (notifyManager == null) return;
        NotificationChannel notifyChannel = new NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        );
        notifyChannel.setDescription(getString(R.string.notification_channel_description));
        notifyChannel.setSound(null, null);
        notifyChannel.enableLights(false);
        notifyChannel.enableVibration(false);
        notifyManager.createNotificationChannel(notifyChannel);
    }

    @Override
    public void onBackPressed() {}

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(myReceiver);
    }
				}
	
