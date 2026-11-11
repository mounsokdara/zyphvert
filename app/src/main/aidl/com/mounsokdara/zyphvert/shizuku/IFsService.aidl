package com.mounsokdara.zyphvert.shizuku;

import android.os.ParcelFileDescriptor;

/** Runs as the adb shell user through Shizuku, which can see Android/data and Android/obb. */
interface IFsService {
    /** Shizuku calls this to stop the service process. */
    void destroy() = 16777114;

    /** The entries of a folder as a JSON array (n = name, d = folder, s = size, m = modified), or "null" if it can't be read. */
    String list(String path) = 1;

    /** A read-only handle to a file, for copying it into the app. */
    ParcelFileDescriptor open(String path) = 2;
}
