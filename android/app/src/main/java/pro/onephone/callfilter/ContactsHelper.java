package pro.onephone.callfilter;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import androidx.core.content.ContextCompat;

public class ContactsHelper {

    /** True if the given number matches any contact in the device address book. */
    public static boolean isContactNumber(Context ctx, String number) {
        if (number == null || number.isEmpty()) return false;
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) return false;
        Cursor c = null;
        try {
            Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                                            Uri.encode(number));
            c = ctx.getContentResolver().query(uri,
                new String[]{ContactsContract.PhoneLookup._ID}, null, null, null);
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }
}
