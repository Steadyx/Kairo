package com.kairo.reader.data.export;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

/** Disposable files owned by the instrumentation package. */
public class NoteExportFixtureProvider extends ContentProvider {
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String name = uri.getLastPathSegment();
        if (!"existing".equals(name) && !"empty".equals(name)
                && !"unreadable".equals(name) && !"writefailure".equals(name)) {
            throw new FileNotFoundException("Unknown fixture");
        }
        if ("r".equals(mode) && "unreadable".equals(name)) {
            throw new FileNotFoundException("unreadable");
        }
        try {
            if (!"r".equals(mode) && "writefailure".equals(name)) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                pipe[0].close();
                return pipe[1];
            }
            File file = new File(getContext().getCacheDir(), "note-export-fixture-" + name);
            if (!file.exists() && !file.createNewFile()) {
                throw new IOException("Could not create fixture");
            }
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode));
        } catch (IOException error) {
            FileNotFoundException failure = new FileNotFoundException(error.getMessage());
            failure.initCause(error);
            throw failure;
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        return new File(getContext().getCacheDir(), "note-export-fixture-" + uri.getLastPathSegment()).delete() ? 1 : 0;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "text/plain";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        return 0;
    }
}
