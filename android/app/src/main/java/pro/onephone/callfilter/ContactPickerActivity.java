package pro.onephone.callfilter;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import java.util.*;

public class ContactPickerActivity extends AppCompatActivity {

    private LinearLayout container;
    private EditText searchInput;
    private TextView selectionCount, emptyView;

    private final List<ContactEntry> contacts = new ArrayList<>();
    private final List<ContactEntry> visible = new ArrayList<>();
    private final LinkedHashMap<String, String> selected = new LinkedHashMap<>();

    static class ContactEntry {
        String name; String number; String normalized;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contact_picker);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        container      = findViewById(R.id.contactsContainer);
        searchInput    = findViewById(R.id.contactSearchInput);
        selectionCount = findViewById(R.id.selectionCount);
        emptyView      = findViewById(R.id.emptyContactsView);

        ArrayList<String> initNums   = getIntent().getStringArrayListExtra("selected_numbers");
        ArrayList<String> initNames  = getIntent().getStringArrayListExtra("selected_names");
        if (initNums != null) {
            for (int i = 0; i < initNums.size(); i++) {
                String n = initNums.get(i);
                String name = initNames != null && i < initNames.size() ? initNames.get(i) : n;
                selected.put(normalize(n), name + "\u0000" + n);
            }
        }
        updateSelectionCount();

        findViewById(R.id.btnDone).setOnClickListener(v -> finishWithResult());

        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            public void onTextChanged(CharSequence s, int a, int b, int c) {}
            public void afterTextChanged(Editable s) { applyFilter(s.toString()); }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS)
            != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, 1001);
        } else {
            loadContacts();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadContacts();
            } else {
                Toast.makeText(this, "Contacts permission denied", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void loadContacts() {
        contacts.clear();
        Cursor c = null;
        try {
            c = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                new String[]{
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                },
                null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (c != null) {
                Set<String> seen = new HashSet<>();
                while (c.moveToNext()) {
                    String name = c.getString(0);
                    String num  = c.getString(1);
                    if (name == null) name = num;
                    if (num == null) continue;
                    String norm = normalize(num);
                    String key = name + "|" + norm;
                    if (seen.contains(key)) continue;
                    seen.add(key);
                    ContactEntry e = new ContactEntry();
                    e.name = name;
                    e.number = num;
                    e.normalized = norm;
                    contacts.add(e);
                }
            }
        } finally { if (c != null) c.close(); }
        applyFilter(searchInput.getText().toString());
    }

    private void applyFilter(String query) {
        visible.clear();
        if (query == null || query.trim().isEmpty()) {
            visible.addAll(contacts);
        } else {
            String q = query.toLowerCase(Locale.US);
            String qDigits = q.replaceAll("[^0-9]", "");
            for (ContactEntry e : contacts) {
                if (e.name.toLowerCase(Locale.US).contains(q)
                    || (!qDigits.isEmpty() && e.normalized.contains(qDigits))) {
                    visible.add(e);
                }
            }
        }
        renderList();
    }

    private void renderList() {
        container.removeAllViews();
        if (visible.isEmpty()) {
            emptyView.setVisibility(View.VISIBLE);
            return;
        }
        emptyView.setVisibility(View.GONE);
        LayoutInflater inf = LayoutInflater.from(this);
        for (final ContactEntry e : visible) {
            final View row = inf.inflate(R.layout.contact_pick_row, container, false);
            TextView nameView = row.findViewById(R.id.contactName);
            TextView numView  = row.findViewById(R.id.contactNumber);
            CheckBox check    = row.findViewById(R.id.contactCheck);
            nameView.setText(e.name);
            numView.setText(e.number);

            check.setOnCheckedChangeListener(null);
            boolean isSel = selected.containsKey(e.normalized);
            check.setChecked(isSel);
            check.setOnCheckedChangeListener((b, checked) -> {
                if (checked) selected.put(e.normalized, e.name + "\u0000" + e.number);
                else         selected.remove(e.normalized);
                updateSelectionCount();
            });

            row.setOnClickListener(v -> check.toggle());
            container.addView(row);
        }
    }

    private void updateSelectionCount() {
        int n = selected.size();
        selectionCount.setText(n == 0
            ? "No exceptions selected"
            : (n + (n == 1 ? " contact selected" : " contacts selected")));
    }

    private void finishWithResult() {
        ArrayList<String> nums = new ArrayList<>();
        ArrayList<String> names = new ArrayList<>();
        for (String pair : selected.values()) {
            int sep = pair.indexOf('\u0000');
            if (sep > 0) {
                names.add(pair.substring(0, sep));
                nums.add(pair.substring(sep + 1));
            }
        }
        Intent data = new Intent();
        data.putStringArrayListExtra("selected_numbers", nums);
        data.putStringArrayListExtra("selected_names",   names);
        setResult(RESULT_OK, data);
        finish();
    }

    private static String normalize(String n) {
        if (n == null) return "";
        return n.replaceAll("[^0-9+]", "");
    }
}
