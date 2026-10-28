package co.aospa.glyph.Utils;

import static co.aospa.glyph.Constants.Constants.CONTEXT;

import androidx.appcompat.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;
import androidx.preference.PreferenceScreen;
import androidx.preference.PreferenceManager;

import android.view.View;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class InterfaceUtils {

    public static <T> void showToast(T msg) {
        Toast.makeText(CONTEXT, resolve(msg), Toast.LENGTH_SHORT).show();
    }

    private static String resolve(Object value) {
        return switch (value) {
            case null -> null;
            case Integer i -> CONTEXT.getString(i);
            case String s -> s;
            default -> value.toString();
        };
    }

    public static <T> void showDialog(Context ctx, T title, T message,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative,
                                      T neutralText, Runnable onNeutral) {
        new AlertDialog.Builder(ctx)
                .setTitle(resolve(title))
                .setMessage(resolve(message))
                .setPositiveButton(resolve(positiveText), (dialog, which) -> {
                    if (onPositive != null) onPositive.run();
                })
                .setNeutralButton(resolve(neutralText), (dialog, which) -> {
                    if (onNeutral != null) onNeutral.run();
                })
                .setNegativeButton(resolve(negativeText), (dialog, which) -> {
                    if (onNegative != null) onNegative.run();
                })
                .show();
    }

    public static <T> void showDialog(Context ctx, T title, T message,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative) {
        showDialog(ctx, title, message,
                positiveText, onPositive,
                negativeText, onNegative,
                (String) null, null);
    }

    public static <T> void showDialog(Context ctx, T title, View messageVew,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative,
                                      T neutralText, Runnable onNeutral) {
        new AlertDialog.Builder(ctx)
                .setTitle(resolve(title))
                .setView(messageVew)
                .setPositiveButton(resolve(positiveText), (dialog, which) -> {
                    if (onPositive != null) onPositive.run();
                })
                .setNeutralButton(resolve(neutralText), (dialog, which) -> {
                    if (onNeutral != null) onNeutral.run();
                })
                .setNegativeButton(resolve(negativeText), (dialog, which) -> {
                    if (onNegative != null) onNegative.run();
                })
                .show();
    }

    public static <T> void showDialog(Context ctx, T title, View messageView,
                                      T positiveText, Runnable onPositive,
                                      T negativeText, Runnable onNegative) {
        showDialog(ctx, title, messageView,
                positiveText, onPositive,
                negativeText, onNegative,
                (String) null, null);
    }

    public static void showMultiPickerDialog(Context context, Preference preference,
                                      String[] entries, String[] values,
                                      Runnable afterDismiss) {

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        Set<String> selected = prefs.getStringSet(preference.getKey(), new HashSet<>());

        boolean[] checked = new boolean[entries.length];
        for (int i = 0; i < values.length; i++) {
            checked[i] = selected.contains(values[i]);
        }

        new AlertDialog.Builder(context)
                .setTitle(preference.getTitle())
                .setMultiChoiceItems(entries, checked, (dialog, which, isChecked) -> {
                    checked[which] = isChecked;
                })
                .setOnDismissListener(dialog -> {
                    Set<String> newSelected = new HashSet<>();
                    for (int i = 0; i < checked.length; i++) {
                        if (checked[i]) newSelected.add(values[i]);
                    }
                    prefs.edit().putStringSet(preference.getKey(), newSelected).apply();
                    if (afterDismiss != null) {
                        afterDismiss.run();
                    }
                })
                .show();
    }

    public static void showMultiPickerDialog(Context context, Preference preference,
                                      String[] entries, String[] values) {
        showMultiPickerDialog(context, preference,
                entries, values, null);
    }

    public static void showMultiPickerDialog(Context context, Preference preference,
                                      List<String> entries, List<String> values) {
        showMultiPickerDialog(context, preference,
                entries.toArray(new String[0]), values.toArray(new String[0]), null);
    }

    public static void showMultiPickerDialog(Context context, Preference preference,
                                      List<String> entries, List<String> values, Runnable afterDismiss) {
        showMultiPickerDialog(context, preference,
                entries.toArray(new String[0]), values.toArray(new String[0]), afterDismiss);
    }


    public class Preferences {

        public static List<Preference> getAllPreferences(PreferenceGroup group) {
            List<Preference> preferences = new ArrayList<>();
            for (int i = 0; i < group.getPreferenceCount(); i++) {
                Preference pref = group.getPreference(i);
                preferences.add(pref);
                if (pref instanceof PreferenceGroup) {
                    preferences.addAll(getAllPreferences((PreferenceGroup) pref));
                }
            }
            return preferences;
        }

        public static List<Preference> getAllPreferences(PreferenceScreen screen) {
            return getAllPreferences((PreferenceGroup) screen);
        }

        public static List<Preference> getAllPreferences(PreferenceCategory category) {
            return getAllPreferences((PreferenceGroup) category);
        }
    }



}
