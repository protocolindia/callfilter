package pro.onephone.callfilter;

import android.Manifest;
import android.content.*;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.util.Log;
import androidx.core.content.ContextCompat;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ContactsCacheManager v2 — fast, cache-aware contacts lookup.
 *
 * Improvements over v1:
 *  1. First-call blocking: waits up to 800ms for initial load instead of returning wrong result
 *  2. ContentObserver: auto-invalidates when contacts change — no stale data, no polling
 *  3. Single background thread executor — no multiple concurrent loads
 *  4. Longer TTL (15 min) — reduces unnecessary reloads
 *  5. Warm-up method for early cache population on app start
 */
public class ContactsCacheManager {

    private static final String TAG    = "ContactsCache";
    private static final long   TTL_MS = 15 * 60_000L;  // 15 minutes
    private static final long   FIRST_LOAD_WAIT_MS = 800L; // wait up to 800ms on first lookup

    private static ContactsCacheManager instance;
    private final Context ctx;

    private final Map<String, String> nameMap   = new HashMap<>(2048);
    private final Set<String>         numberSet = new HashSet<>(2048);
    private final AtomicLong          loadedAt  = new AtomicLong(0L);
    private final AtomicBoolean       loading   = new AtomicBoolean(false);
    private final CountDownLatch      firstLoad = new CountDownLatch(1);

    // Single-thread executor — only one load at a time
    private final ExecutorService executor =
        Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "ContactsCache"); t.setDaemon(true); return t; });

    // ContentObserver to detect real contact changes
    private ContentObserver observer;

    public static synchronized ContactsCacheManager getInstance(Context ctx) {
        if (instance == null) instance = new ContactsCacheManager(ctx.getApplicationContext());
        return instance;
    }

    private ContactsCacheManager(Context ctx) {
        this.ctx = ctx;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Warm up the cache proactively (call from MainActivity.onCreate). */
    public void warmUp() {
        registerObserver();
        if (loadedAt.get() == 0L) refreshAsync();
    }

    /** Returns true if number is a contact. Blocks briefly on first call. */
    public boolean isContact(String number) {
        ensureFresh();
        return numberSet.contains(normalize(number));
    }

    /** Returns display name for number, or null if not a contact. */
    public String getName(String number) {
        ensureFresh();
        return nameMap.get(normalize(number));
    }

    /** Returns all contact numbers (for batch operations). */
    public synchronized Set<String> getAllNumbers() {
        ensureFresh();
        return new HashSet<>(numberSet);
    }

    /** Batch lookup — returns map of number → name for numbers that are contacts. */
    public Map<String, String> batchLookup(Collection<String> numbers) {
        ensureFresh();
        Map<String, String> result = new HashMap<>();
        synchronized (this) {
            for (String n : numbers) {
                String norm = normalize(n);
                if (numberSet.contains(norm)) {
                    result.put(n, nameMap.getOrDefault(norm, "Contact"));
                }
            }
        }
        return result;
    }

    /** True if cache has been loaded at least once. */
    public boolean isReady() { return loadedAt.get() > 0L; }

    /** Blocks until the first contacts load completes. MUST be called off the
     *  main thread. Triggers a load if one hasn't started. */
    public void awaitFirstLoad(long timeoutMs) {
        if (loadedAt.get() == 0L) refreshAsync();
        try { firstLoad.await(timeoutMs, TimeUnit.MILLISECONDS); }
        catch (InterruptedException ignored) {}
    }

    /** Trigger async background refresh. */
    public void refreshAsync() {
        if (loading.compareAndSet(false, true)) {
            executor.submit(() -> { load(); loading.set(false); });
        }
    }

    // ── Internal ──────────────────────────────────────────────────────────

    private void ensureFresh() {
        long age = System.currentTimeMillis() - loadedAt.get();
        if (loadedAt.get() == 0L) {
            // First load — trigger async and wait briefly
            refreshAsync();
            try { firstLoad.await(FIRST_LOAD_WAIT_MS, TimeUnit.MILLISECONDS); }
            catch (InterruptedException ignored) {}
        } else if (age > TTL_MS) {
            // Stale — refresh in background, use stale data in the meantime
            refreshAsync();
        }
    }

    private void load() {
        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            loadedAt.set(System.currentTimeMillis());
            firstLoad.countDown();
            return;
        }

        long start = System.currentTimeMillis();
        Map<String, String> newNames   = new HashMap<>(2048);
        Set<String>         newNumbers = new HashSet<>(2048);

        // Only fetch the columns we need
        String[] projection = {
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };

        try (Cursor cur = ctx.getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null, null,
                null)) {  // no sort — faster

            if (cur != null) {
                int iNorm = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER);
                int iRaw  = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int iName = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);

                while (cur.moveToNext()) {
                    String name = iName >= 0 ? cur.getString(iName) : null;

                    // Normalized number (+91...)
                    String norm = iNorm >= 0 ? cur.getString(iNorm) : null;
                    if (norm != null && !norm.isEmpty()) {
                        String key = normalize(norm);
                        newNumbers.add(key);
                        if (name != null) newNames.put(key, name);
                    }

                    // Raw number (handles devices that don't populate NORMALIZED_NUMBER)
                    String raw = iRaw >= 0 ? cur.getString(iRaw) : null;
                    if (raw != null && !raw.isEmpty()) {
                        String key = normalize(raw);
                        newNumbers.add(key);
                        if (name != null && !newNames.containsKey(key)) newNames.put(key, name);
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "load error: " + e.getMessage());
        }

        synchronized (this) {
            nameMap.clear();   nameMap.putAll(newNames);
            numberSet.clear(); numberSet.addAll(newNumbers);
        }
        loadedAt.set(System.currentTimeMillis());
        firstLoad.countDown();  // release any waiting callers
        Log.d(TAG, String.format("Loaded %d contacts in %dms",
            newNumbers.size(), System.currentTimeMillis() - start));
    }

    /** Register ContentObserver to auto-refresh when contacts DB changes. */
    private void registerObserver() {
        if (observer != null) return;
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                Log.d(TAG, "Contacts changed — scheduling refresh");
                // Invalidate and refresh in background
                loadedAt.set(0L);
                refreshAsync();
            }
        };
        ctx.getContentResolver().registerContentObserver(
            ContactsContract.Contacts.CONTENT_URI, true, observer);
        Log.d(TAG, "ContentObserver registered");
    }

    /** Normalize: keep last 10 digits for fuzzy match. */
    public static String normalize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(16);
        for (char ch : raw.toCharArray()) {
            if (Character.isDigit(ch)) sb.append(ch);
            else if (ch == '+' && sb.length() == 0) sb.append(ch);
        }
        String full = sb.toString();
        // Strip leading country code for matching flexibility
        return full.length() > 10 ? full.substring(full.length() - 10) : full;
    }
}
