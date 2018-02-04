package com.asamm.locus.addon.graphhopper;

import android.content.Context;
import android.preference.PreferenceManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import locus.api.android.ActionTools;
import locus.api.android.utils.LocusInfo;
import locus.api.android.utils.LocusUtils;
import locus.api.android.utils.exceptions.RequiredVersionMissingException;
import locus.api.utils.Logger;

/**
 * Created by menion on 29. 7. 2014.
 * Class is part of Locus project
 */
public class Utils {

    // tag for logger
    private static final String TAG = "Utils";

    // path to currently selected item
    private static final String KEY_S_DATA_ITEM_PATH = "KEY_S_DATA_ITEM_PATH";

    /**
     * Check if in system is installed valid Locus version.
     * @param ctx current context
     * @return <code>true</code> if Locus is available
     */
    static boolean existsValidLocus(Context ctx) {
        return LocusUtils.isLocusAvailable(ctx, LocusUtils.VersionCode.UPDATE_11);
    }

    // SOURCE FOR DATA

    /**
     * Get valid directory with GraphHopped routing items.
     * @param ctx current context
     * @return File as root GH directory or null if not defined or any problem happen
     */
    static File getRootDirectory(Context ctx) {
        // check Locus version
        if (!existsValidLocus(ctx)) {
            Logger.logW(TAG, "getAvailableData(" + ctx + "), " +
                    "Valid Locus version not available");
            return null;
        }

        // get data
        try {
            // get list of available routes
            LocusUtils.LocusVersion lv = LocusUtils.getActiveVersion(ctx);
            LocusInfo li = ActionTools.getLocusInfo(ctx, lv);

            // check Locus root directory
            if (li.getRootDirectory() == null) {
                Logger.logW(TAG, "getAvailableData(" + ctx + "), " +
                        "Locus ROOT directory do not exists");
                return null;
            }

            // get data directory
            return new File(li.getRootDirMapsVector());
        } catch (RequiredVersionMissingException e) {
            Logger.logE(TAG, "getRootDirectory(" + ctx + ")", e);
            return null;
        }
    }

    /**
     * Get all available data from root defined directory. This directory is currently hard-coded
     * to Locus/mapsVector directory.
     * @return list of all available routing items
     * @throws locus.api.android.utils.exceptions.RequiredVersionMissingException
     */
    static List<File> getAvailableData(Context ctx) throws RequiredVersionMissingException {
        // container for data
        List<File> res = new ArrayList<>();

        // get data directory
        File fileRoot = getRootDirectory(ctx);
        if (fileRoot == null) {
            return res;
        }
        getAvailableData(fileRoot, res);

        // return directories
        return res;
    }

    /**
     * Get full list of all available routing items.
     * @param item item (directory/file) we wants to test
     * @param container container for files
     */
    private static void getAvailableData(File item, List<File> container) {
        // check parameter
        if (item == null) {
            return;
        }

//        // handle files
//        if (item.isFile()) {
//            // check compressed routing files
//            if (item.getName().toLowerCase().endsWith(".osm.ghz")) {
//                container.add(item);
//            }
//        } else

        if (item.isDirectory()) {
            // check GraphHopper directory
            if (item.getName().endsWith("-gh")) {
                container.add(item);
                return;
            }

            // get content of directory
            File[] files = item.listFiles();
            if (files == null || files.length == 0) {
                return;
            }

            // iterate over content
            for (File file : files) {
                getAvailableData(file, container);
            }
        }
    }

    // ACTIVE ROUTING ITEM (DIR/FILE)

    /**
     * Get current stored routing item (dir/file).
     * @param ctx current context
     * @return defined path to routing item
     */
	static File getCurrentRoutingItem(Context ctx) {
		// get and check dir
		String dir = PreferenceManager.getDefaultSharedPreferences(ctx).
				getString(KEY_S_DATA_ITEM_PATH, "");
		if (dir.length() == 0) {
			return null;
		}

		// create valid directory
		File fileDir = new File(dir);
		if (fileDir.exists()) {
			return fileDir;
		}
		return null;
	}

    /**
     * Set selected routing item that should be used by this add-on.
     * @param ctx current context
     * @param file selected item
     */
	static void setCurrentRoutingItem(Context ctx, File file) {
		PreferenceManager.getDefaultSharedPreferences(ctx).
				edit().
				putString(KEY_S_DATA_ITEM_PATH, file.getAbsolutePath()).
				apply();
	}
}
