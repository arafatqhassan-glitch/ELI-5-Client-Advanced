// Luanti
// SPDX-License-Identifier: LGPL-2.1-or-later
// Copyright (C) 2014 celeron55, Perttu Ahola <celeron55@gmail.com>

#ifndef __ANDROID__
#error This file may only be compiled for android!
#endif

#include "util/numeric.h"
#include "porting.h"
#include "porting_android.h"
#include "threading/thread.h"
#include "config.h"
#include "filesys.h"
#include "log.h"
#include "settings.h"

#include <jni.h>
#define SDL_MAIN_HANDLED 1
#include <SDL.h>

#include <sstream>
#include <exception>
#include <cstdlib>

#ifdef GPROF
#include "prof.h"
#endif

extern int main(int argc, char *argv[]);

extern "C" JNIEXPORT void JNICALL
Java_net_minetest_minetest_GameActivity_saveSettings(JNIEnv* env, jobject /* this */) {
	if (!g_settings_path.empty())
		g_settings->updateConfigFile(g_settings_path.c_str());
}

namespace porting {
	void cleanupAndroid();
	std::string getLanguageAndroid();
	bool setSystemPaths();
}

extern "C" int SDL_Main(int _argc, char *_argv[])
{
	Thread::setName("Main");

	char *argv[] = {strdup(PROJECT_NAME), strdup("--verbose"), nullptr};
	int retval = main(ARRLEN(argv) - 1, argv);
	free(argv[0]);
	free(argv[1]);

	porting::cleanupAndroid();
	infostream << "Shutting down." << std::endl;
	exit(retval);
}

namespace porting {
JNIEnv      *jnienv = nullptr;
jobject      activity;
jclass       activityClass;

void osSpecificInit()
{
	jnienv = (JNIEnv*)SDL_AndroidGetJNIEnv();
	activity = (jobject)SDL_AndroidGetActivity();
	activityClass = jnienv->GetObjectClass(activity);

	auto lang = getLanguageAndroid();
	unsetenv("LANGUAGE");
	setenv("LANG", lang.c_str(), 1);

#ifdef GPROF
	warningstream << "Initializing GPROF profiler" << std::endl;
	monstartup("libluanti.so");
#endif
}

void cleanupAndroid()
{
#ifdef GPROF
	warningstream << "Shutting down GPROF profiler" << std::endl;
	setenv("CPUPROFILE", (path_user + DIR_DELIM + "gmon.out").c_str(), 1);
	moncleanup();
#endif
}

static std::string readJavaString(jstring j_str)
{
	const char *c_str = jnienv->GetStringUTFChars(j_str, nullptr);
	std::string str(c_str);
	jnienv->ReleaseStringUTFChars(j_str, c_str);
	return str;
}

bool setSystemPaths()
{
	// Base default paths
	path_user = "/storage/emulated/0/ELI5";
	path_share = "/storage/emulated/0/ELI5";
	path_cache = "/storage/emulated/0/ELI5/cache";

	// JNI resolution directly from GameActivity
	if (jnienv != nullptr && activityClass != nullptr && activity != nullptr) {
		jmethodID getUserDataPath = jnienv->GetMethodID(activityClass, "getUserDataPath", "()Ljava/lang/String;");
		if (getUserDataPath != nullptr) {
			jobject result = jnienv->CallObjectMethod(activity, getUserDataPath);
			if (result != nullptr) {
				std::string str = readJavaString((jstring)result);
				if (!str.empty()) {
					path_user = str;
					path_share = str;
				}
			}
		}

		jmethodID getCachePath = jnienv->GetMethodID(activityClass, "getCachePath", "()Ljava/lang/String;");
		if (getCachePath != nullptr) {
			jobject result = jnienv->CallObjectMethod(activity, getCachePath);
			if (result != nullptr) {
				std::string str = readJavaString((jstring)result);
				if (!str.empty()) {
					path_cache = str;
				}
			}
		}
	}

	return true;
}

void showTextInputDialog(const std::string &hint, const std::string &current, int editType)
{
	jmethodID showdialog = jnienv->GetMethodID(activityClass, "showTextInputDialog",
			"(Ljava/lang/String;Ljava/lang/String;I)V");
	FATAL_ERROR_IF(showdialog == nullptr, "unable to find showTextInputDialog");

	jstring jhint = jnienv->NewStringUTF(hint.c_str());
	jstring jcurrent = jnienv->NewStringUTF(current.c_str());
	jnienv->CallVoidMethod(activity, showdialog, jhint, jcurrent, (jint)editType);
}

void showComboBoxDialog(const std::string *optionList, s32 listSize, s32 selectedIdx)
{
	jmethodID showdialog = jnienv->GetMethodID(activityClass, "showSelectionInputDialog",
			"([Ljava/lang/String;I)V");
	FATAL_ERROR_IF(showdialog == nullptr, "unable to find showSelectionInputDialog");

	jclass jStringClass = jnienv->FindClass("java/lang/String");
	jobjectArray jOptionList = jnienv->NewObjectArray(listSize, jStringClass, NULL);

	for (s32 i = 0; i < listSize; i++) {
		jnienv->SetObjectArrayElement(jOptionList, i, jnienv->NewStringUTF(optionList[i].c_str()));
	}

	jnienv->CallVoidMethod(activity, showdialog, jOptionList, (jint)selectedIdx);
}

void openURIAndroid(const char *url)
{
	jmethodID url_open = jnienv->GetMethodID(activityClass, "openURI", "(Ljava/lang/String;)V");
	FATAL_ERROR_IF(url_open == nullptr, "unable to find openURI");
	jnienv->CallVoidMethod(activity, url_open, jnienv->NewStringUTF(url));
}

void shareFileAndroid(const std::string &path)
{
	jmethodID url_open = jnienv->GetMethodID(activityClass, "shareFile", "(Ljava/lang/String;)V");
	FATAL_ERROR_IF(url_open == nullptr, "unable to find shareFile");
	jnienv->CallVoidMethod(activity, url_open, jnienv->NewStringUTF(path.c_str()));
}

void setPlayingNowNotification(bool show)
{
	jmethodID play_notification = jnienv->GetMethodID(activityClass, "setPlayingNowNotification", "(Z)V");
	FATAL_ERROR_IF(play_notification == nullptr, "unable to find setPlayingNowNotification");
	jnienv->CallVoidMethod(activity, play_notification, (jboolean)show);
}

AndroidDialogType getLastInputDialogType()
{
	jmethodID lastdialogtype = jnienv->GetMethodID(activityClass, "getLastDialogType", "()I");
	FATAL_ERROR_IF(lastdialogtype == nullptr, "unable to find getLastDialogType");
	return static_cast<AndroidDialogType>(jnienv->CallIntMethod(activity, lastdialogtype));
}

AndroidDialogState getInputDialogState()
{
	jmethodID inputdialogstate = jnienv->GetMethodID(activityClass, "getInputDialogState", "()I");
	FATAL_ERROR_IF(inputdialogstate == nullptr, "unable to find getInputDialogState");
	return static_cast<AndroidDialogState>(jnienv->CallIntMethod(activity, inputdialogstate));
}

std::string getInputDialogMessage()
{
	jmethodID dialogvalue = jnienv->GetMethodID(activityClass, "getDialogMessage", "()Ljava/lang/String;");
	FATAL_ERROR_IF(dialogvalue == nullptr, "unable to find getDialogMessage");
	return readJavaString((jstring)jnienv->CallObjectMethod(activity, dialogvalue));
}

int getInputDialogSelection()
{
	jmethodID dialogvalue = jnienv->GetMethodID(activityClass, "getDialogSelection", "()I");
	FATAL_ERROR_IF(dialogvalue == nullptr, "unable to find getDialogSelection");
	return jnienv->CallIntMethod(activity, dialogvalue);
}

float getDisplayDensity()
{
	static float value = 0;
	if (value == 0) {
		jmethodID getDensity = jnienv->GetMethodID(activityClass, "getDensity", "()F");
		FATAL_ERROR_IF(getDensity == nullptr, "unable to find getDensity");
		value = jnienv->CallFloatMethod(activity, getDensity);
	}
	return value;
}

v2u32 getDisplaySize()
{
	static v2u32 retval;
	if (retval.X == 0) {
		jmethodID getDisplayWidth = jnienv->GetMethodID(activityClass, "getDisplayWidth", "()I");
		jmethodID getDisplayHeight = jnienv->GetMethodID(activityClass, "getDisplayHeight", "()I");
		FATAL_ERROR_IF(getDisplayWidth == nullptr || getDisplayHeight == nullptr, "unable to find display bounds");
		retval.X = jnienv->CallIntMethod(activity, getDisplayWidth);
		retval.Y = jnienv->CallIntMethod(activity, getDisplayHeight);
	}
	return retval;
}

std::string getLanguageAndroid()
{
	jmethodID getLanguage = jnienv->GetMethodID(activityClass, "getLanguage", "()Ljava/lang/String;");
	FATAL_ERROR_IF(getLanguage == nullptr, "unable to find getLanguage");
	return readJavaString((jstring)jnienv->CallObjectMethod(activity, getLanguage));
}

bool hasPhysicalKeyboardAndroid()
{
	jmethodID hasPhysicalKeyboard = jnienv->GetMethodID(activityClass, "hasPhysicalKeyboard", "()Z");
	FATAL_ERROR_IF(hasPhysicalKeyboard == nullptr, "unable to find hasPhysicalKeyboard");
	return jnienv->CallBooleanMethod(activity, hasPhysicalKeyboard);
}

}
