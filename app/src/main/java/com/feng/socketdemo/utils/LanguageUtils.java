package com.feng.socketdemo.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.os.LocaleList;
import android.preference.PreferenceManager;
import android.text.TextUtils;

import com.feng.socketdemo.R;

import java.util.Locale;

public class LanguageUtils {

    // 支持的语种
    public static final String FOLLOW_SYSTEM = "follow_system";
    public static final String ENGLISH = "en";
    public static final String SIMPLIFIED_CHINESE = "zh";
    public static final String TRADITIONAL_CHINESE = "zh_Hant";

    // 存储键名
    private static final String KEY_APP_LANGUAGE = "key_app_language";

    /**
     * 获取当前应用语言
     */
    public static String getAppLanguage(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context)
                .getString(KEY_APP_LANGUAGE, FOLLOW_SYSTEM);
    }

    /**
     * 保存语言设置
     */
    public static void saveAppLanguage(Context context, String language) {
        PreferenceManager.getDefaultSharedPreferences(context)
                .edit()
                .putString(KEY_APP_LANGUAGE, language)
                .apply();
    }

    /**
     * 应用语言设置
     */
    public static void applyLanguage(Context context) {
        String language = getAppLanguage(context);
        updateResources(context, language);
    }

    /**
     * 更新资源
     */
    public static Context updateResources(Context context, String language) {
        Resources resources = context.getResources();
        Configuration configuration = resources.getConfiguration();
        Locale locale = getLocaleByLanguage(language);

        // 7.0及以上
        configuration.setLocale(locale);
        configuration.setLocales(new LocaleList(locale));
        return context.createConfigurationContext(configuration);
    }

    /**
     * 根据语言代码获取Locale
     */
    private static Locale getLocaleByLanguage(String language) {
        if (TextUtils.isEmpty(language) || FOLLOW_SYSTEM.equals(language)) {
            // 跟随系统
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return Resources.getSystem().getConfiguration().getLocales().get(0);
            } else {
                return Resources.getSystem().getConfiguration().locale;
            }
        }

        switch (language) {
            case ENGLISH:
                return Locale.ENGLISH;
            case SIMPLIFIED_CHINESE:
                return Locale.SIMPLIFIED_CHINESE;
            case TRADITIONAL_CHINESE:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    return Locale.forLanguageTag("zh-Hant");
                } else {
                    return Locale.TRADITIONAL_CHINESE;
                }
            default:
                return new Locale(language);
        }
    }

    /**
     * 切换语言并重启应用
     */
    public static void changeAppLanguage(Activity activity, String language) {
        saveAppLanguage(activity, language);
        restartApp(activity);
    }

    /**
     * 重启应用
     */
    public static void restartApp(Activity activity) {
        Intent intent = activity.getPackageManager()
                .getLaunchIntentForPackage(activity.getPackageName());
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK);
            activity.startActivity(intent);
        }
        activity.finish();
    }

    /**
     * 获取当前显示的语言名称
     */
    public static String getCurrentLanguageName(Context context) {
        String language = getAppLanguage(context);
        switch (language) {
            case FOLLOW_SYSTEM:
                return context.getString(R.string.follow_system);
            case SIMPLIFIED_CHINESE:
                return context.getString(R.string.simplified_chinese);
            case TRADITIONAL_CHINESE:
                return context.getString(R.string.traditional_chinese);
            default:
                return context.getString(R.string.follow_system);
        }
    }
}
