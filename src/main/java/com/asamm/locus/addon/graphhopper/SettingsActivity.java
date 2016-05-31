package com.asamm.locus.addon.graphhopper;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.File;
import java.util.List;

import locus.api.utils.Logger;

/**
 * Created by menion on 10. 7. 2014.
 * Class is part of Locus project
 */
public class SettingsActivity extends Activity {

	// tag for logger
	private static final String TAG = "SettingsActivity";

	// request code for obtaining permission
	private static final int REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE		= 1001;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		// refresh content of page
		refreshPageContent();

		// check also permissions on Android 6.0+
		if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
			int granted = ContextCompat.checkSelfPermission(this,
					Manifest.permission.READ_EXTERNAL_STORAGE);
			if (granted != PackageManager.PERMISSION_GRANTED) {
				ActivityCompat.requestPermissions(this,
						new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
						REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE);
			}
		}
	}

	@Override
	public void onRequestPermissionsResult(int requestCode,
			@NonNull String permissions[], @NonNull int[] grantResults) {
		if (requestCode == REQUEST_CODE_PERMISSION_READ_EXTERNAL_STORAGE) {
			refreshPageContent();
		} else {
			super.onRequestPermissionsResult(requestCode, permissions, grantResults);
		}
	}

	/**
	 * Refresh content of layout.
	 */
	private void refreshPageContent() {
		// get directories
		List<File> dirs = null;
		try {
			dirs = Utils.getAvailableData(this);
		} catch (Exception e) {
			Logger.logE(TAG, "onCreate()", e);
		}

		// set empty view
		if (!Utils.existsValidLocus(this)) {
			displayWarningMessage("No valid Locus installation!");
		} else if (dirs == null || dirs.size() == 0) {
			StringBuilder sb = new StringBuilder();
			sb.append("No content!").append("\n\n");
			sb.append("Put GraphHopper data to ").
					append(Utils.getRootDirectory(this)).
					append(" directory");
			displayWarningMessage(sb);
		} else {
			displayDataFiles(dirs);
		}
	}

    /**
     * Display warning on a display.
     * @param text text to display
     */
	private void displayWarningMessage(CharSequence text) {
		TextView tv = new TextView(this);
		tv.setText(text);
        tv.setPadding(20, 20, 20, 20);
		setContentView(tv);
	}

	private void displayDataFiles(final List<File> dirs) {
		setContentView(R.layout.settings_data_activity);
		// find spinner
		Spinner sp = (Spinner) findViewById(R.id.spinner_data);

		// prepare data
		int selectedIndex = 0;
		File currentItem = Utils.getCurrentRoutingItem(this);
		String[] data = new String[dirs.size()];
		for (int i = 0, m = dirs.size(); i < m; i++) {
			data[i] = dirs.get(i).getName().replace("-gh", "").replace("_", " ").trim();
			if (dirs.get(i).equals(currentItem)) {
				selectedIndex = i;
			}
		}

		// set spinner chooser
		ArrayAdapter<String> spinnerArrayAdapter = new ArrayAdapter<>(this,
				android.R.layout.simple_spinner_item, data);
		spinnerArrayAdapter.setDropDownViewResource(
				android.R.layout.simple_spinner_dropdown_item);
		sp.setAdapter(spinnerArrayAdapter);
		sp.setSelection(selectedIndex);
		sp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {

			@Override
			public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
				File newDir = dirs.get(position);
				Utils.setCurrentRoutingItem(SettingsActivity.this, newDir);
			}

			@Override
			public void onNothingSelected(AdapterView<?> parent) {}
		});
	}
}
