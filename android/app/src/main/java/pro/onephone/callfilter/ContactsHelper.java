package pro.onephone.callfilter;

import android.content.Context;

/**
 * ContactsHelper — thin wrapper around ContactsCacheManager for fast lookups.
 *
 * The real work is done by ContactsCacheManager which keeps an in-memory
 * HashMap refreshed in background every 5 minutes.
 * Each lookup is now < 0.1ms instead of 50-200ms.
 */
public class ContactsHelper {

    /** Returns true if the given number belongs to a device contact. */
    public static boolean isContactNumber(Context ctx, String number) {
        return ContactsCacheManager.getInstance(ctx).isContact(number);
    }

    /** Returns the contact display name, or null if not found. */
    public static String getContactName(Context ctx, String number) {
        return ContactsCacheManager.getInstance(ctx).getName(number);
    }

    /** Trigger a background refresh of the contacts cache. */
    public static void refreshCache(Context ctx) {
        ContactsCacheManager.getInstance(ctx).refreshAsync();
    }
}
