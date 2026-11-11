package com.mounsokdara.zyphvert.shizuku;

import android.os.ParcelFileDescriptor;

interface IFsService {
    void destroy() = 16777114;

    String list(String path) = 1;

    ParcelFileDescriptor open(String path) = 2;
}
