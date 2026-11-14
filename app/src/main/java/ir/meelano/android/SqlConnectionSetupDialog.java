package ir.meelano.android;

import android.app.Activity;
import android.app.AlertDialog;
import android.text.InputType;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** First-run and recovery setup for direct SQL Server connectivity. */
final class SqlConnectionSetupDialog {
    private SqlConnectionSetupDialog() { }

    static void show(Activity activity, Runnable onSaved) {
        if (activity == null || activity.isFinishing()) return;
        SqlConnectionConfig.Profile draft = SqlConnectionConfig.draft(activity);
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(activity, 18);
        form.setPadding(pad, pad, pad, dp(activity, 8));

        TextView intro = new TextView(activity);
        intro.setText("سرور را با نام یا IP وارد کنید، دیتابیس‌ها را دریافت و یک مورد را انتخاب کنید. پورت پیش‌فرض ۱۴۳۳ است؛ برای پورت فورواردشده مقدار دیگری مانند ۵۰۵۰ را وارد کنید.");
        intro.setTextSize(13);
        intro.setGravity(Gravity.RIGHT);
        form.addView(intro, lp());

        EditText host = field(activity, "نام یا IP سرور", draft.host,
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        EditText port = field(activity, "پورت SQL (پیش‌فرض ۱۴۳۳)",
                draft.port == SqlConnectionConfig.DEFAULT_PORT ? "1433" : String.valueOf(draft.port),
                InputType.TYPE_CLASS_NUMBER);
        EditText username = field(activity, "نام کاربری SQL", SqlConnectionConfig.savedUsername(activity),
                InputType.TYPE_CLASS_TEXT);
        EditText password = field(activity, "رمز SQL (فقط یک‌بار روی این دستگاه)",
                SqlConnectionConfig.savedPassword(activity),
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(host, lp());
        form.addView(port, lp());
        form.addView(username, lp());
        form.addView(password, lp());

        TextView routeLabel = new TextView(activity);
        routeLabel.setText("نوع آدرس (برای ذخیره در پروفایل درست)");
        routeLabel.setTextSize(12);
        form.addView(routeLabel, lp());
        Spinner route = new Spinner(activity);
        ArrayAdapter<String> routes = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item,
                new String[]{"تشخیص خودکار", "سرور داخل شبکه", "IP عمومی / خارج شبکه"});
        routes.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        route.setAdapter(routes);
        route.setSelection("wan".equals(draft.kind) ? 2 : 0);
        form.addView(route, lp());

        Spinner databases = new Spinner(activity);
        List<String> dbNames = new ArrayList<>();
        dbNames.add("— ابتدا فهرست دیتابیس‌ها را دریافت کنید —");
        ArrayAdapter<String> dbAdapter = new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_item, dbNames);
        dbAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        databases.setAdapter(dbAdapter);
        form.addView(databases, lp());

        TextView status = new TextView(activity);
        status.setTextSize(12);
        status.setGravity(Gravity.RIGHT);
        form.addView(status, lp());

        Button scan = new Button(activity);
        scan.setText("پیدا کردن سرورهای شبکهٔ محلی");
        form.addView(scan, lp());
        Button list = new Button(activity);
        list.setText("اتصال و نمایش دیتابیس‌ها");
        form.addView(list, lp());

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(activity)
                .setTitle("تنظیم اتصال مستقیم به SQL Server")
                .setView(scroll)
                .setNegativeButton("انصراف", null)
                .setPositiveButton("آزمون و ذخیره", null)
                .create();
        ExecutorService worker = Executors.newSingleThreadExecutor();
        final List<String> loadedNames = new ArrayList<>();
        dialog.setOnDismissListener(ignored -> worker.shutdownNow());
        dialog.setOnShowListener(ignored -> {
            Button save = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            save.setOnClickListener(v -> {
                if (loadedNames.isEmpty() || databases.getSelectedItemPosition() <= 0) {
                    status.setText("ابتدا دیتابیس را از فهرست انتخاب کنید.");
                    return;
                }
                String h = host.getText().toString().trim();
                int p = SqlConnectionConfig.parsePort(port.getText().toString());
                String u = username.getText().toString().trim();
                String secret = password.getText().toString();
                String db = loadedNames.get(databases.getSelectedItemPosition() - 1);
                String routeValue = route.getSelectedItemPosition() == 1 ? "lan"
                        : route.getSelectedItemPosition() == 2 ? "wan" : "auto";
                if (!SqlConnectionConfig.validHost(h) || u.isEmpty() || secret.isEmpty()) {
                    status.setText("نام سرور، نام کاربری و رمز را کامل و معتبر وارد کنید.");
                    return;
                }
                save.setEnabled(false);
                status.setText("در حال آزمون دیتابیس و ذخیرهٔ محلی...");
                worker.execute(() -> {
                    boolean ok = false;
                    try {
                        ok = SqlConnectionConfig.testDatabase(h, p, db, u, secret);
                        if (ok) ok = SqlConnectionConfig.saveVerified(activity, h, p, db, u, secret, routeValue);
                    } catch (Exception ignored1) { }
                    final boolean saved = ok;
                    activity.runOnUiThread(() -> {
                        if (activity.isFinishing()) return;
                        save.setEnabled(true);
                        if (!saved) {
                            status.setText("اتصال یا ذخیره انجام نشد. پورت را بررسی کنید؛ در صورت بسته بودن ۱۴۳۳، پورت فورواردشده را وارد و دوباره فهرست را دریافت کنید.");
                            return;
                        }
                        dialog.dismiss();
                        if (onSaved != null) onSaved.run();
                    });
                });
            });
        });
        scan.setOnClickListener(v -> {
            scan.setEnabled(false);
            status.setText("در حال جستجوی پورت‌های SQL در شبکهٔ محلی...");
            worker.execute(() -> {
                List<SqlConnectionConfig.ServerCandidate> found = SqlConnectionConfig.discoverLocalServers();
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || !dialog.isShowing()) return;
                    scan.setEnabled(true);
                    if (found.isEmpty()) {
                        status.setText("سروری روی ۱۴۳۳ یا ۵۰۵۰ در شبکهٔ محلی پیدا نشد؛ نام یا IP را دستی وارد کنید.");
                        return;
                    }
                    String[] labels = new String[found.size()];
                    for (int i = 0; i < found.size(); i++) {
                        SqlConnectionConfig.ServerCandidate candidate = found.get(i);
                        labels[i] = candidate.label + ":" + candidate.port;
                    }
                    new AlertDialog.Builder(activity).setTitle("سرورهای پیدا‌شده")
                            .setItems(labels, (pick, which) -> {
                                SqlConnectionConfig.ServerCandidate candidate = found.get(which);
                                host.setText(candidate.host);
                                port.setText(String.valueOf(candidate.port));
                                route.setSelection(1);
                                status.setText("سرور انتخاب شد؛ اکنون فهرست دیتابیس‌ها را دریافت کنید.");
                            }).setNegativeButton("بستن", null).show();
                });
            });
        });
        list.setOnClickListener(v -> {
            String h = host.getText().toString().trim();
            int p = SqlConnectionConfig.parsePort(port.getText().toString());
            String u = username.getText().toString().trim();
            String secret = password.getText().toString();
            if (!SqlConnectionConfig.validHost(h) || u.isEmpty() || secret.isEmpty()) {
                status.setText("نام یا IP سرور، نام کاربری و رمز را وارد کنید.");
                return;
            }
            list.setEnabled(false);
            status.setText("در حال آزمون TCP و دریافت فهرست دیتابیس‌ها...");
            worker.execute(() -> {
                List<String> result = new ArrayList<>();
                String error = "";
                try {
                    result = SqlConnectionConfig.listDatabases(h, p, u, secret).databases;
                    if (result.isEmpty()) error = "اتصال برقرار شد اما دیتابیسی برای نمایش در دسترس نیست.";
                } catch (Exception e) {
                    error = p == SqlConnectionConfig.DEFAULT_PORT
                            ? "پورت ۱۴۳۳ در دسترس نیست یا ورود SQL موفق نشد؛ اگر پورت دیگری به ۱۴۳۳ فوروارد شده، آن را وارد کنید."
                            : "اتصال روی پورت انتخاب‌شده برقرار نشد؛ آدرس، پورت و دسترسی مشاهدهٔ دیتابیس‌ها را بررسی کنید.";
                }
                final List<String> names = result;
                final String message = error;
                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || !dialog.isShowing()) return;
                    list.setEnabled(true);
                    loadedNames.clear();
                    dbNames.clear();
                    dbNames.add("— دیتابیس را انتخاب کنید —");
                    loadedNames.addAll(names);
                    dbNames.addAll(names);
                    dbAdapter.notifyDataSetChanged();
                    if (!SqlConnectionConfig.savedDatabase(activity).isEmpty()) {
                        int old = names.indexOf(SqlConnectionConfig.savedDatabase(activity));
                        if (old >= 0) databases.setSelection(old + 1);
                    }
                    status.setText(message.isEmpty()
                            ? "فهرست آماده است؛ دیتابیس موردنظر را انتخاب کنید."
                            : message);
                });
            });
        });
        dialog.show();
    }

    private static EditText field(Activity activity, String hint, String value, int inputType) {
        EditText edit = new EditText(activity);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(inputType);
        if (value != null && !value.isEmpty()) edit.setText(value);
        return edit;
    }

    private static LinearLayout.LayoutParams lp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = 7;
        return p;
    }

    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
