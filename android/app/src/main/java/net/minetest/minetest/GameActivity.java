package net.minetest.minetest;

import android.os.Bundle;
import org.libsdl.app.SDLActivity;

public class GameActivity extends SDLActivity {

    @Override
    protected String[] getLibraries() {
        return new String[]{
            "c++_shared",
            "luanti"
        };
    }

    // JNI path getters called by setSystemPaths() in C++
    public String getUserDataPath() {
        return "/storage/emulated/0/ELI5";
    }

    public String getCachePath() {
        return "/storage/emulated/0/ELI5/cache";
    }

    public float getDensity() {
        return getResources().getDisplayMetrics().density;
    }

    public int getDisplayWidth() {
        return getResources().getDisplayMetrics().widthPixels;
    }

    public int getDisplayHeight() {
        return getResources().getDisplayMetrics().heightPixels;
    }

    public String getLanguage() {
        return java.util.Locale.getDefault().getLanguage();
    }

    public boolean hasPhysicalKeyboard() {
        return getResources().getConfiguration().keyboard == android.content.res.Configuration.KEYBOARD_QWERTY;
    }
}
