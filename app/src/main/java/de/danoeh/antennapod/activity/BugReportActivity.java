package de.danoeh.antennapod.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import com.google.android.material.snackbar.Snackbar;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;


import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;


import de.danoeh.antennapod.error.CrashReportWriter;
import de.danoeh.antennapod.R;
import de.danoeh.antennapod.core.preferences.UserPreferences;
import de.danoeh.antennapod.core.util.IntentUtils;
import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Displays the 'crash report' screen
 */
public class BugReportActivity extends AppCompatActivity {
    private static final String TAG = "BugReportActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setTheme(UserPreferences.getTheme());
        super.onCreate(savedInstanceState);
        getSupportActionBar().setDisplayShowHomeEnabled(true);
        setContentView(R.layout.bug_report);

        String stacktrace = "No crash report recorded";
        try {
            File crashFile = CrashReportWriter.getFile();
            if (crashFile.exists()) {
                stacktrace = IOUtils.toString(new FileInputStream(crashFile), StandardCharsets.UTF_8);
            } else {
                Log.d(TAG, stacktrace);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        TextView crashDetailsTextView = findViewById(R.id.crash_report_logs);
        crashDetailsTextView.setText(CrashReportWriter.getSystemInfo() + "\n\n" + stacktrace);

        findViewById(R.id.btn_open_bug_tracker).setOnClickListener(v -> IntentUtils.openInBrowser(
                BugReportActivity.this, "https://github.com/AntennaPod/AntennaPod/issues"));

        findViewById(R.id.btn_copy_log).setOnClickListener(v -> {
            ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText(getString(R.string.bug_report_title), crashDetailsTextView.getText());
            clipboard.setPrimaryClip(clip);
            Snackbar.make(findViewById(android.R.id.content), R.string.copied_to_clipboard, Snackbar.LENGTH_SHORT).show();
        });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.bug_report_options, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == R.id.export_logcat) {
            AlertDialog.Builder alertBuilder = new AlertDialog.Builder(this);
            alertBuilder.setMessage(R.string.confirm_export_log_dialog_message);
            alertBuilder.setPositiveButton(R.string.confirm_label, (dialog, which) -> {
                exportLog();
                dialog.dismiss();
            });
            alertBuilder.setNegativeButton(R.string.cancel_label, null);
            alertBuilder.show();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void exportLog() {
        try {
            File filename = new File(UserPreferences.getDataFolder(null), "full-logs.txt");
            String cmd = "logcat -d -f " + filename.getAbsolutePath();
            Runtime.getRuntime().exec(cmd);
            //share file
            try {
                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("text/*");
                String authString = getString(de.danoeh.antennapod.core.R.string.provider_authority);
                Uri fileUri = FileProvider.getUriForFile(this, authString, filename);
                intent.putExtra(Intent.EXTRA_STREAM, fileUri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                String chooserTitle = getString(de.danoeh.antennapod.core.R.string.share_file_label);
                Intent chooser = Intent.createChooser(intent, chooserTitle);
                List<ResolveInfo> resInfos = getPackageManager()
                        .queryIntentActivities(chooser, PackageManager.MATCH_DEFAULT_ONLY);
                for (ResolveInfo resolveInfo : resInfos) {
                    String packageName = resolveInfo.activityInfo.packageName;
                    grantUriPermission(packageName, fileUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                }
                startActivity(chooser);
            } catch (Exception e) {
                e.printStackTrace();
                int strResId = R.string.log_file_share_exception;
                Snackbar.make(findViewById(android.R.id.content), strResId, Snackbar.LENGTH_LONG)
                        .show();
            }
        } catch (IOException e) {
            e.printStackTrace();
            Snackbar.make(findViewById(android.R.id.content), e.getMessage(), Snackbar.LENGTH_LONG).show();
        }
    }


}
