package com.vernu.sms.helpers;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.telephony.TelephonyManager;
import android.util.Log;
import com.vernu.sms.AppConstants;

public class BundleManager {
    private static final String TAG = "BundleManager";

    private static String lastLog = "Aucune activité bundle";

    public static String getLastLog() {
        return lastLog;
    }

    private static void log(Context context, String message) {
        lastLog = message;
        android.util.Log.d(TAG, message);
        new Handler(Looper.getMainLooper()).post(() -> {
            Intent intent = new Intent("com.vernu.sms.BUNDLE_LOG");
            intent.putExtra("message", message);
            context.sendBroadcast(intent);
        });
    }

    public static boolean isBundleEnabled(Context context) {
        return SharedPreferenceHelper.getSharedPreferenceBoolean(context, AppConstants.SHARED_PREFS_BUNDLE_ENABLED_KEY, false);
    }

    public static boolean hasCredit(Context context) {
        return SharedPreferenceHelper.getSharedPreferenceInt(context, AppConstants.SHARED_PREFS_BUNDLE_REMAINING_KEY, 0) > 0;
    }

    public static void decrement(Context context) {
        int current = SharedPreferenceHelper.getSharedPreferenceInt(context, AppConstants.SHARED_PREFS_BUNDLE_REMAINING_KEY, 0);
        SharedPreferenceHelper.setSharedPreferenceInt(context, AppConstants.SHARED_PREFS_BUNDLE_REMAINING_KEY, Math.max(0, current - 1));
        Log.d(TAG, "Bundle: " + current + " → " + Math.max(0, current - 1));
    }

    public static void renewBundle(Context context) {
        int capacity = SharedPreferenceHelper.getSharedPreferenceInt(context, AppConstants.SHARED_PREFS_BUNDLE_CAPACITY_KEY, 0);
        SharedPreferenceHelper.setSharedPreferenceInt(context, AppConstants.SHARED_PREFS_BUNDLE_REMAINING_KEY, capacity);
        SharedPreferenceHelper.setSharedPreferenceBoolean(context, AppConstants.SHARED_PREFS_BUNDLE_IS_SUBSCRIBING_KEY, false);
        log(context, "Bundle renouvelé: " + capacity + " SMS");
    }

    public static boolean isSubscribing(Context context) {
        return SharedPreferenceHelper.getSharedPreferenceBoolean(context, AppConstants.SHARED_PREFS_BUNDLE_IS_SUBSCRIBING_KEY, false);
    }

    public static void setSubscribing(Context context, boolean value) {
        SharedPreferenceHelper.setSharedPreferenceBoolean(context, AppConstants.SHARED_PREFS_BUNDLE_IS_SUBSCRIBING_KEY, value);
    }

    public static int getRemaining(Context context) {
        return SharedPreferenceHelper.getSharedPreferenceInt(context, AppConstants.SHARED_PREFS_BUNDLE_REMAINING_KEY, 0);
    }

    public static void subscribeUssd(Context context) {
        String ussdCode = SharedPreferenceHelper.getSharedPreferenceString(
            context, AppConstants.SHARED_PREFS_BUNDLE_USSD_CODE_KEY, "");
        if (ussdCode.isEmpty()) {
            log(context, "ERREUR: Aucun code USSD configuré");
            setSubscribing(context, false);
            return;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            TelephonyManager tm = (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
            if (tm == null) {
                log(context, "ERREUR: TelephonyManager non disponible");
                setSubscribing(context, false);
                return;
            }
            log(context, "USSD envoyé: " + ussdCode);
            try {
                tm.sendUssdRequest(ussdCode, new TelephonyManager.UssdResponseCallback() {
                    @Override
                    public void onReceiveUssdResponse(TelephonyManager tm, String request, CharSequence response) {
                        log(context, "USSD OK: " + response);
                        renewBundle(context);
                    }
                    @Override
                    public void onReceiveUssdResponseFailed(TelephonyManager tm, String request, int failureCode) {
                        log(context, "USSD ECHEC code=" + failureCode + " request=" + request);
                        setSubscribing(context, false);
                    }
                }, new Handler(Looper.getMainLooper()));
            } catch (Exception e) {
                log(context, "EXCEPTION: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                setSubscribing(context, false);
            }
        } else {
            log(context, "Android < 8 - fallback Intent ACTION_CALL");
            try {
                String encoded = ussdCode.replace("#", "%23");
                Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + encoded));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(intent);
                new Handler(Looper.getMainLooper()).postDelayed(() -> renewBundle(context), 40_000);
            } catch (Exception e) {
                log(context, "EXCEPTION fallback: " + e.getMessage());
                setSubscribing(context, false);
            }
        }
    }
}
