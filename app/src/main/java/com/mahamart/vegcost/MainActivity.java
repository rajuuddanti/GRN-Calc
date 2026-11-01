package com.mahamart.vegcost;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.AutoCompleteTextView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    private final int green = Color.rgb(12, 92, 68);
    private final int pale = Color.rgb(230, 244, 237);
    private final int ink = Color.rgb(28, 43, 37);
    private final int muted = Color.rgb(105, 124, 115);
    private final DecimalFormat money = new DecimalFormat("#,##0.00");
    private final List<ProductRow> products = new ArrayList<>();
    private LinearLayout rowsContainer;
    private ScrollView contentScroll;
    private EditText expensesInput;
    private TextView purchaseTotal, expenseTotal, grandTotal, status;
    private SharedPreferences prefs;
    private boolean restoring = false;

    private static class ProductRow {
        EditText name, qty, cp;
        EditText amount;
        TextView finalCp;
        LinearLayout row;
        boolean amountManuallyEntered = false;
        // Tracks which field was calculated from the other two inputs.
        String derivedField = "";
    }

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Pan the whole app window upward when a bottom input receives focus.
        // This keeps the tapped row and the text being entered above the keyboard,
        // instead of relying on a ScrollView resize that was not working on-device.
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN
                | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        prefs = getSharedPreferences("veg_cost_offline", MODE_PRIVATE);
        buildUi();
        if (!restoreCurrent()) {
            addProduct("Alugadda", "", "");
            addProduct("Ulligadda W", "", "");
            addProduct("Ulligadda R", "", "");
            addProduct("Allam", "", "");
            addProduct("Yelligadda", "", "");
            expensesInput.setText("0");
        }
        recalculate();
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(242, 247, 244));
        root.setPadding(dp(12), dp(10), dp(12), dp(8));
        setContentView(root);

        // Keep the top of the screen focused on the one value the user needs to enter: expenses.
        // Branding and redundant offline subtitles are intentionally omitted to save space.
        contentScroll = new ScrollView(this);
        contentScroll.setFillViewport(false);
        contentScroll.setClipToPadding(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, 0, 0, dp(8));
        contentScroll.addView(content);

        // Product list, expense entry and totals share one scrollable area.
        rowsContainer = new LinearLayout(this); rowsContainer.setOrientation(LinearLayout.VERTICAL);
        rowsContainer.setBackgroundColor(Color.WHITE);
        rowsContainer.setClipToPadding(false);

        LinearLayout expenseCard = new LinearLayout(this);
        expenseCard.setOrientation(LinearLayout.VERTICAL);
        expenseCard.setPadding(dp(16), dp(12), dp(16), dp(12));
        expenseCard.setBackground(makeRounded(Color.WHITE, 18));
        expenseCard.setElevation(dp(2));
        LinearLayout.LayoutParams expenseCardLp = new LinearLayout.LayoutParams(-1, -2);
        expenseCardLp.topMargin = dp(2);
        expenseCardLp.bottomMargin = dp(10);
        content.addView(expenseCard, expenseCardLp);
        content.addView(rowsContainer, new LinearLayout.LayoutParams(-1, -2));

        TextView expenseTitle = new TextView(this);
        expenseTitle.setText("PURCHASE EXPENSES"); expenseTitle.setTextSize(11);
        expenseTitle.setTypeface(null, Typeface.BOLD); expenseTitle.setTextColor(green);
        expenseTitle.setLetterSpacing(0.08f);
        expenseCard.addView(expenseTitle);
        LinearLayout expenseLine = new LinearLayout(this);
        expenseLine.setGravity(Gravity.CENTER_VERTICAL);
        expenseLine.setPadding(0, dp(7), 0, dp(2));
        TextView expLabel = new TextView(this); expLabel.setText("Total expenses");
        expLabel.setTextColor(ink); expLabel.setTextSize(15); expLabel.setTypeface(null, Typeface.BOLD);
        expenseLine.addView(expLabel, new LinearLayout.LayoutParams(0, -2, 1));
        expensesInput = new EditText(this); expensesInput.setSingleLine(true); expensesInput.setTextSize(17);
        expensesInput.setTypeface(null, Typeface.BOLD);
        expensesInput.setTextColor(ink);
        expensesInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
        expensesInput.setHint("0.00"); expensesInput.setText("0"); expensesInput.setSelectAllOnFocus(true);
        expensesInput.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        expensesInput.setPadding(dp(12), 0, dp(12), 0);
        expensesInput.setBackground(makeRounded(pale, 12));
        expensesInput.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));
        expenseLine.addView(expensesInput, new LinearLayout.LayoutParams(dp(125), dp(46)));
        expenseCard.addView(expenseLine);
        expensesInput.addTextChangedListener(watcher(() -> { recalculate(); persistCurrent(); }));
        // Scroll only as much as needed to keep the focused field above the keyboard.
        expensesInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) keepFieldVisible(expensesInput);
        });

        LinearLayout totals = new LinearLayout(this); totals.setOrientation(LinearLayout.VERTICAL);
        totals.setBackground(makeRounded(green, 18));
        totals.setPadding(dp(16), dp(13), dp(16), dp(13));
        totals.setElevation(dp(3));
        LinearLayout.LayoutParams totalsLp = new LinearLayout.LayoutParams(-1, -2);
        totalsLp.topMargin = dp(12);
        content.addView(totals, totalsLp);
        purchaseTotal = totalLine(totals, "TOTAL AMOUNT", "₹0.00");
        expenseTotal = totalLine(totals, "EXPENSES", "₹0.00");
        grandTotal = totalLine(totals, "GRAND TOTAL", "₹0.00");
        root.addView(contentScroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, dp(10), 0, 0);
        Button add = button("+ Add item");
        add.setOnClickListener(v -> { addProduct("", "", ""); recalculate(); persistCurrent(); contentScroll.post(() -> contentScroll.fullScroll(View.FOCUS_DOWN)); });
        Button save = button("Save");
        save.setOnClickListener(v -> savePurchase());
        Button history = button("History");
        history.setOnClickListener(v -> showHistory());
        buttons.addView(add, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams saveLp = new LinearLayout.LayoutParams(0, dp(46), 0.75f); saveLp.leftMargin = dp(6);
        buttons.addView(save, saveLp);
        LinearLayout.LayoutParams historyLp = new LinearLayout.LayoutParams(0, dp(46), 0.75f); historyLp.leftMargin = dp(6);
        buttons.addView(history, historyLp);
        root.addView(buttons);
        Button clear = button("New purchase");
        clear.setTextColor(green); clear.setBackground(makeRounded(Color.rgb(224, 239, 231), 14));
        clear.setElevation(dp(1));
        clear.setOnClickListener(v -> confirmNewPurchase());
        LinearLayout clearRow = new LinearLayout(this); clearRow.setPadding(0, dp(4), 0, 0);
        clearRow.addView(clear, new LinearLayout.LayoutParams(-1, dp(40)));
        root.addView(clearRow);

        status = new TextView(this); status.setText("Auto-saved on this phone"); status.setTextSize(11);
        status.setTextColor(muted); status.setGravity(Gravity.CENTER); status.setPadding(0, dp(6), 0, 0);
        root.addView(status);


    }

    /**
     * Scrolls the focused input above the visible window area (including the
     * area reduced by the soft keyboard). Retries after layout/IME animation,
     * because the first focus event often happens before Android resizes the view.
     */
    private void keepFieldVisible(View field) {
        if (field == null || contentScroll == null) return;
        Runnable reveal = () -> {
            if (!field.isShown() || contentScroll.getHeight() == 0) return;

            // First let Android scroll the descendant into the ScrollView viewport.
            Rect childRect = new Rect(0, 0, field.getWidth(), field.getHeight());
            field.requestRectangleOnScreen(childRect, true);

            // Then ensure the focused field is above the keyboard, even on devices
            // where the IME resize arrives a little later than the focus event.
            int[] fieldLocation = new int[2];
            int[] scrollLocation = new int[2];
            field.getLocationOnScreen(fieldLocation);
            contentScroll.getLocationOnScreen(scrollLocation);
            Rect visibleFrame = new Rect();
            getWindow().getDecorView().getWindowVisibleDisplayFrame(visibleFrame);

            int fieldBottom = fieldLocation[1] + field.getHeight();
            int scrollBottom = scrollLocation[1] + contentScroll.getHeight();
            int usableBottom = Math.min(scrollBottom, visibleFrame.bottom);
            int overlap = fieldBottom + dp(24) - usableBottom;
            if (overlap > 0) {
                contentScroll.post(() -> contentScroll.smoothScrollBy(0, overlap));
            }
        };
        // Run after focus, after the IME begins opening, and after resize/layout settles.
        field.post(reveal);
        field.postDelayed(reveal, 150);
        field.postDelayed(reveal, 300);
        field.postDelayed(reveal, 500);
        field.postDelayed(reveal, 750);
    }

    private android.graphics.drawable.GradientDrawable makeRounded(int color, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(color); d.setCornerRadius(dp(radiusDp)); return d;
    }

    private TextView totalLine(LinearLayout parent, String label, String value) {
        LinearLayout line = new LinearLayout(this); line.setGravity(Gravity.CENTER_VERTICAL);
        TextView l = new TextView(this); l.setText(label); l.setTextColor(Color.rgb(218, 239, 227)); l.setTextSize(11); l.setTypeface(null, Typeface.BOLD);
        TextView r = new TextView(this); r.setText(value); r.setTextColor(Color.WHITE); r.setTextSize(14); r.setTypeface(null, Typeface.BOLD); r.setGravity(Gravity.RIGHT);
        line.addView(l, new LinearLayout.LayoutParams(0, -2, 1)); line.addView(r, new LinearLayout.LayoutParams(dp(125), -2));
        parent.addView(line); return r;
    }

    private void addProduct(String name, String qty, String cp) {
        ProductRow p = new ProductRow();
        p.row = new LinearLayout(this); p.row.setOrientation(LinearLayout.VERTICAL);
        p.row.setPadding(dp(12), dp(8), dp(12), dp(9));
        p.row.setBackground(makeRounded(products.size() % 2 == 0 ? Color.WHITE : Color.rgb(248, 251, 249), 14));
        p.row.setElevation(dp(1));

        if (products.isEmpty()) {
            LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.VERTICAL);
            header.setBackground(makeRounded(Color.rgb(12, 92, 68), 14)); header.setPadding(dp(12), dp(8), dp(12), dp(8));
            header.setElevation(dp(2));
            TextView nameHeader = headerCell("NAME", 100); nameHeader.setGravity(Gravity.LEFT | Gravity.CENTER_VERTICAL);
            nameHeader.setWidth(-1); header.addView(nameHeader);
            LinearLayout subHeader = new LinearLayout(this); subHeader.setOrientation(LinearLayout.HORIZONTAL);
            subHeader.addView(headerFlexCell("QTY"), flexCellParams()); subHeader.addView(headerFlexCell("CP"), flexCellParams());
            subHeader.addView(headerFlexCell("AMOUNT"), flexCellParams()); subHeader.addView(headerFlexCell("FINAL CP"), flexCellParams());
            header.addView(subHeader); rowsContainer.addView(header);
        }

        AutoCompleteTextView nameInput = new AutoCompleteTextView(this);
        nameInput.setSingleLine(true); nameInput.setTextSize(15); nameInput.setTypeface(null, Typeface.BOLD); nameInput.setTextColor(ink);
        nameInput.setHint("Choose or type vegetable");
        nameInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        nameInput.setThreshold(0); nameInput.setSelectAllOnFocus(false);
        nameInput.setFocusable(true); nameInput.setFocusableInTouchMode(true); nameInput.setCursorVisible(true);
        nameInput.setPadding(dp(10), 0, dp(10), 0); nameInput.setBackground(makeRounded(Color.rgb(241, 247, 243), 10));
        String[] defaults = {"Alugadda", "Ulligadda W", "Ulligadda R", "Allam", "Yelligadda"};
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_dropdown_item_1line, defaults);
        nameInput.setAdapter(adapter);
        nameInput.setText(name, false);
        // The five starter SKU rows are selection-only; added rows remain editable.
        // This prevents accidentally renaming a predefined SKU while entering quantities.
        final boolean predefinedRow = products.size() < 5;
        if (predefinedRow) {
            // Lock the five predefined product names against typing/paste.
            // They can only be changed by choosing one of the dropdown options.
            nameInput.setKeyListener(null);
            nameInput.setInputType(InputType.TYPE_NULL);
            nameInput.setCursorVisible(false);
            nameInput.setLongClickable(false);
            nameInput.setTextIsSelectable(false);
            nameInput.setFocusable(true);
            nameInput.setFocusableInTouchMode(true);
            nameInput.setOnClickListener(v -> {
                nameInput.dismissDropDown();
                nameInput.showDropDown();
            });
        }
        nameInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                if (!predefinedRow) nameInput.setSelection(nameInput.getText().length());
                keepFieldVisible(p.row);
            }
        });
        p.name = nameInput;

        LinearLayout values = new LinearLayout(this); values.setOrientation(LinearLayout.HORIZONTAL);
        values.setGravity(Gravity.CENTER_VERTICAL);
        p.qty = input(qty, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, "0");
        p.cp = input(cp, InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, "0.00");
        p.qty.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) keepFieldVisible(p.row);
        });
        p.cp.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) keepFieldVisible(p.row);
        });
        p.amount = input("", InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL, "0.00");
        p.amount.setTextSize(12);
        p.amount.setGravity(Gravity.CENTER);
        p.amount.setHint("Amount");
        p.finalCp = output("—", 1);
        values.addView(p.qty, flexCellParams()); values.addView(p.cp, flexCellParams());
        values.addView(p.amount, flexCellParams()); values.addView(p.finalCp, flexCellParams());
        p.row.addView(p.name, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout.LayoutParams valuesLp = new LinearLayout.LayoutParams(-1, dp(42));
        valuesLp.topMargin = dp(5);
        p.row.addView(values, valuesLp);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(-1, -2);
        rowLp.setMargins(dp(2), dp(3), dp(2), dp(3));
        rowsContainer.addView(p.row, rowLp); products.add(p);
        View divider = new View(this); divider.setBackgroundColor(Color.TRANSPARENT);
        rowsContainer.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        p.name.addTextChangedListener(watcher(() -> persistCurrent()));
        p.qty.addTextChangedListener(watcher(() -> { if (!restoring) { updateRowFromEdited(p, "qty"); recalculate(); persistCurrent(); } }));
        p.cp.addTextChangedListener(watcher(() -> { if (!restoring) { updateRowFromEdited(p, "cp"); recalculate(); persistCurrent(); } }));
        p.amount.addTextChangedListener(watcher(() -> { if (!restoring) { p.amountManuallyEntered = !p.amount.getText().toString().trim().isEmpty(); updateRowFromEdited(p, "amount"); recalculate(); persistCurrent(); } }));
        p.amount.setOnFocusChangeListener((v, hasFocus) -> { if (hasFocus) keepFieldVisible(p.row); });
    }

    private TextView headerFlexCell(String text) {
        TextView t = new TextView(this); t.setText(text); t.setTextColor(Color.WHITE); t.setTextSize(10);
        t.setTypeface(null, Typeface.BOLD); t.setGravity(Gravity.CENTER); return t;
    }
    private LinearLayout.LayoutParams flexCellParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(40), 1f);
        lp.setMargins(dp(1), 0, dp(1), 0); return lp;
    }

    private TextView headerCell(String s, int width) {
        TextView t = new TextView(this); t.setText(s); t.setTextColor(Color.WHITE); t.setTextSize(11);
        t.setTypeface(null, Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setWidth(dp(width)); return t;
    }
    private EditText input(String value, int type, String hint) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(13); e.setTextColor(ink);
        e.setInputType(type); e.setHint(hint); e.setSelectAllOnFocus(true); e.setPadding(dp(5), 0, dp(5), 0);
        e.setBackground(makeRounded(Color.rgb(242, 247, 244), 9)); e.setText(value); return e;
    }
    private TextView output(String value, int width) {
        TextView t = new TextView(this); t.setText(value); t.setTextSize(12); t.setTextColor(green);
        t.setTypeface(null, Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setPadding(dp(2), dp(6), dp(2), dp(6));
        t.setBackground(makeRounded(pale, 9)); if (width > 1) t.setWidth(dp(width)); t.setTextSize(11); return t;
    }
    private LinearLayout.LayoutParams cellParams(int width) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(width), dp(48));
        lp.setMargins(dp(1), 0, dp(1), 0); return lp;
    }
    private Button button(String text) {
        Button b = new Button(this); b.setText(text); b.setTextSize(12); b.setAllCaps(false);
        b.setTypeface(null, Typeface.BOLD); b.setTextColor(Color.WHITE);
        b.setBackground(makeRounded(green, 13)); b.setElevation(dp(2));
        return b;
    }
    private TextWatcher watcher(Runnable action) {
        return new TextWatcher() { public void beforeTextChanged(CharSequence s,int st,int c,int a) {}
            public void onTextChanged(CharSequence s,int st,int before,int count) { if (!restoring) action.run(); }
            public void afterTextChanged(Editable e) {} };
    }

    private double number(EditText e) {
        try { String s = e.getText().toString().trim().replace(",", ""); return s.isEmpty() ? 0 : Double.parseDouble(s); }
        catch (Exception ex) { return 0; }
    }
    private double expenseValue() { return number(expensesInput); }

    // QTY, CP and AMOUNT are three equivalent inputs: entering any two derives the third.
    // If one of the two source values is cleared, clear the value that depended on it.
    private void updateRowFromEdited(ProductRow p, String edited) {
        if (restoring) return;
        String qText = p.qty.getText().toString().trim();
        String cpText = p.cp.getText().toString().trim();
        String amountText = p.amount.getText().toString().trim();
        double q = number(p.qty), cp = number(p.cp), amount = number(p.amount);

        // A source value was erased: remove the stale calculated value immediately.
        boolean editedIsBlank = ("qty".equals(edited) && qText.isEmpty())
                || ("cp".equals(edited) && cpText.isEmpty())
                || ("amount".equals(edited) && amountText.isEmpty());
        if (editedIsBlank) {
            restoring = true;
            try {
                if (!p.derivedField.isEmpty() && !p.derivedField.equals(edited)) {
                    if ("qty".equals(p.derivedField)) p.qty.setText("");
                    else if ("cp".equals(p.derivedField)) p.cp.setText("");
                    else if ("amount".equals(p.derivedField)) p.amount.setText("");
                }
                if ("amount".equals(edited)) p.amountManuallyEntered = false;
                p.derivedField = "";
            } finally { restoring = false; }
            return;
        }

        restoring = true;
        try {
            if ("amount".equals(edited)) {
                p.amountManuallyEntered = !amountText.isEmpty();
                p.derivedField = "";
                if (q > 0 && !qText.isEmpty()) {
                    p.cp.setText(money.format(amount / q));
                    p.derivedField = "cp";
                } else if (cp > 0 && !cpText.isEmpty()) {
                    p.qty.setText(money.format(amount / cp));
                    p.derivedField = "qty";
                }
            } else if (p.amountManuallyEntered && !amountText.isEmpty()) {
                if ("qty".equals(edited) && q > 0) {
                    p.cp.setText(money.format(amount / q));
                    p.derivedField = "cp";
                } else if ("cp".equals(edited) && cp > 0) {
                    p.qty.setText(money.format(amount / cp));
                    p.derivedField = "qty";
                }
            } else if (!qText.isEmpty() && !cpText.isEmpty()) {
                p.amount.setText(money.format(q * cp));
                p.amountManuallyEntered = false;
                p.derivedField = "amount";
            } else {
                p.derivedField = "";
            }
        } finally { restoring = false; }
    }

    private void recalculate() {
        double total = 0;
        for (ProductRow p : products) total += number(p.amount);
        double expenses = expenseValue(), grand = 0;
        for (ProductRow p : products) {
            double q = number(p.qty), amount = number(p.amount);
            double allocated = total > 0 ? (amount / total) * expenses : 0;
            double landed = amount + allocated; grand += landed;
            p.finalCp.setText(q > 0 && amount > 0 ? money.format(landed / q) : "—");
        }
        purchaseTotal.setText("₹" + money.format(total));
        expenseTotal.setText("₹" + money.format(expenses));
        grandTotal.setText("₹" + money.format(grand));
    }

    private void persistCurrent() {
        if (restoring || prefs == null || expensesInput == null) return;
        try {
            JSONArray arr = new JSONArray();
            for (ProductRow p : products) {
                JSONObject o = new JSONObject(); o.put("name", p.name.getText().toString());
                o.put("qty", p.qty.getText().toString()); o.put("cp", p.cp.getText().toString()); o.put("amount", p.amount.getText().toString()); arr.put(o);
            }
            prefs.edit().putString("current_rows", arr.toString()).putString("expenses", expensesInput.getText().toString()).apply();
            if (status != null) status.setText("Saved on this phone • Offline");
        } catch (Exception ignored) { }
    }

    private boolean restoreCurrent() {
        String raw = prefs.getString("current_rows", null);
        if (raw == null) return false;
        restoring = true;
        try {
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                addProduct(o.optString("name", ""), o.optString("qty", ""), o.optString("cp", ""));
                if (!o.optString("amount", "").isEmpty()) { ProductRow rp = products.get(products.size() - 1); rp.amount.setText(o.optString("amount", "")); rp.amountManuallyEntered = true; }
            }
            String[] defaults = {"Alugadda", "Ulligadda W", "Ulligadda R", "Allam", "Yelligadda"};
            // Migrate older saved drafts: ensure all five predefined products are
            // actually visible as populated rows, not merely autocomplete choices.
            while (products.size() < defaults.length) {
                int i = products.size();
                addProduct(defaults[i], "", "");
            }
            for (int i = 0; i < defaults.length; i++) {
                if (products.get(i).name.getText().toString().trim().isEmpty()) {
                    products.get(i).name.setText(defaults[i]);
                }
            }
            expensesInput.setText(prefs.getString("expenses", "0"));
            return true;
        } catch (Exception e) {
            products.clear();
            rowsContainer.removeAllViews();
            return false;
        } finally { restoring = false; }
    }

    private JSONObject currentPurchaseSnapshot() throws Exception {
        JSONArray arr = new JSONArray();
        for (ProductRow p : products) {
            JSONObject o = new JSONObject();
            o.put("name", p.name.getText().toString().trim());
            o.put("qty", p.qty.getText().toString().trim());
            o.put("cp", p.cp.getText().toString().trim());
            o.put("amount", p.amount.getText().toString().trim());
            arr.put(o);
        }
        JSONObject snapshot = new JSONObject();
        snapshot.put("savedAt", System.currentTimeMillis());
        snapshot.put("expenses", expensesInput.getText().toString().trim());
        snapshot.put("rows", arr);
        snapshot.put("purchaseTotal", purchaseTotal.getText().toString());
        snapshot.put("grandTotal", grandTotal.getText().toString());
        return snapshot;
    }

    private void savePurchase() {
        try {
            boolean hasItem = false;
            for (ProductRow p : products) {
                if (!p.name.getText().toString().trim().isEmpty() && number(p.qty) > 0) { hasItem = true; break; }
            }
            if (!hasItem) {
                Toast.makeText(this, "Enter at least one product with a quantity first.", Toast.LENGTH_LONG).show();
                return;
            }
            JSONArray history = new JSONArray(prefs.getString("purchase_history", "[]"));
            history.put(currentPurchaseSnapshot());
            prefs.edit().putString("purchase_history", history.toString()).apply();
            status.setText("Purchase saved on this phone • Offline");
            Toast.makeText(this, "Purchase saved on this phone", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't save purchase", Toast.LENGTH_SHORT).show();
        }
    }

    private void showHistory() {
        try {
            JSONArray history = new JSONArray(prefs.getString("purchase_history", "[]"));
            if (history.length() == 0) {
                new AlertDialog.Builder(this).setTitle("Purchase history")
                        .setMessage("No saved purchases yet. Tap Save purchase after entering your items.")
                        .setPositiveButton("OK", null).show();
                return;
            }
            String[] labels = new String[history.length()];
            for (int i = history.length() - 1, j = 0; i >= 0; i--, j++) {
                JSONObject item = history.getJSONObject(i);
                java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("dd MMM yyyy, hh:mm a", java.util.Locale.getDefault());
                labels[j] = fmt.format(new java.util.Date(item.optLong("savedAt"))) + "  •  " + item.optString("grandTotal", "");
            }
            new AlertDialog.Builder(this).setTitle("Saved purchases — tap to open")
                    .setItems(labels, (dialog, which) -> {
                        int index = history.length() - 1 - which;
                        try { loadPurchase(history.getJSONObject(index)); }
                        catch (Exception e) { Toast.makeText(this, "Couldn't open this purchase", Toast.LENGTH_SHORT).show(); }
                    }).setNegativeButton("Close", null).show();
        } catch (Exception e) {
            Toast.makeText(this, "Couldn't read purchase history", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadPurchase(JSONObject item) throws Exception {
        restoring = true;
        try {
            products.clear();
            rowsContainer.removeAllViews();
            JSONArray arr = item.getJSONArray("rows");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject row = arr.getJSONObject(i);
                addProduct(row.optString("name", ""), row.optString("qty", ""), row.optString("cp", ""));
                if (!row.optString("amount", "").isEmpty()) { ProductRow rp = products.get(products.size() - 1); rp.amount.setText(row.optString("amount", "")); rp.amountManuallyEntered = true; }
            }
            if (products.isEmpty()) addProduct("", "", "");
            expensesInput.setText(item.optString("expenses", "0"));
        } finally { restoring = false; }
        recalculate();
        persistCurrent();
        status.setText("Loaded saved purchase • Offline");
        Toast.makeText(this, "Saved purchase loaded", Toast.LENGTH_SHORT).show();
    }

    private void confirmNewPurchase() {
        new AlertDialog.Builder(this).setTitle("Start a new purchase?")
                .setMessage("This clears the current entries and expenses from the screen. The app is offline and does not upload your data.")
                .setNegativeButton("Cancel", (d,w) -> d.dismiss())
                .setPositiveButton("New purchase", (d,w) -> {
                    products.clear(); rowsContainer.removeAllViews();
                    addProduct("Alugadda", "", ""); addProduct("Ulligadda W", "", "");
                    addProduct("Ulligadda R", "", ""); addProduct("Allam", "", ""); addProduct("Yelligadda", "", "");
                    expensesInput.setText("0"); recalculate(); persistCurrent();
                }).show();
    }
    private int dp(float v) { return (int)(v * getResources().getDisplayMetrics().density + 0.5f); }
}
