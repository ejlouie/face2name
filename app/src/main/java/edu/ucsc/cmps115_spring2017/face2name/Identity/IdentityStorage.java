package edu.ucsc.cmps115_spring2017.face2name.Identity;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.provider.BaseColumns;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import edu.ucsc.cmps115_spring2017.face2name.Utils.Image;

/**
 * Created by micah on 4/29/17.
 */

public final class IdentityStorage extends SQLiteOpenHelper {
    /**
     * Asynchronous query callbacks.
     *
     * These callbacks are intended to allow the caller to handle the result of an asynchronous
     * query.
     *
     * @param <T> The return type of the query.
     */
    public abstract class AsyncQueryCallbacks<T> {
        /**
         * Called when an asynchronous query succeeds.
         *
         * @param result Result of the query.
         */
        protected void onSuccess(T result) {}

        /**
         * Called when an asynchronous query fails and an exception is thrown.
         *
         * @param ex thrown exception
         */
        protected void onError(Exception ex) {
            ex.printStackTrace();
        }
    }

    public IdentityStorage(Context context) {
        this(context, DBInfo.DB_NAME, null, DBInfo.VERSION);

        mImagesDir = new File(context.getFilesDir(), "faces");
        mImagesDir.mkdir();
    }

    private IdentityStorage(Context context, String dbName, SQLiteDatabase.CursorFactory factory, int dbVersion) {
        super(context, dbName, factory, dbVersion);
    }

    private IdentityStorage(Context context, String dbName, SQLiteDatabase.CursorFactory factory, int dbVersion, DatabaseErrorHandler errHandler) {
        super(context, dbName, factory, dbVersion, errHandler);
    }

    @Override
    public void onCreate(final SQLiteDatabase db) {
        db.execSQL(Queries.CreateTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    /**
     * Stores an identity.
     *
     * @param identity Identity object
     */
    public void storeIdentity(Identity identity) {
        SQLiteDatabase db = getWritableDatabase();

        ContentValues queryValues = new ContentValues();
        queryValues.put(DBInfo._ID, identity.key);
        queryValues.put("name", identity.name);

        db.insertWithOnConflict(DBInfo.TABLE_NAME, null, queryValues, SQLiteDatabase.CONFLICT_REPLACE);

        storeIdentityFace(identity);
    }

    /**
     * Asynchronous version of {@link #storeIdentity(Identity)}
     *
     * @param identity
     * @param callbacks
     */
    public void storeIdentity(final Identity identity, final AsyncQueryCallbacks<Void> callbacks) {
        AsyncQuery<Void> query = new AsyncQuery<Void>(callbacks) {
            @Override
            protected Void onExecute() {
                storeIdentity(identity);

                return null;
            }
        };
        query.execute();
    }

    /**
     * Returns a {@link List} containing every stored identity.
     *
     * @return Identity list
     */
    public List<Identity> dumpIdentities() {
        SQLiteDatabase db = getReadableDatabase();

        Cursor queryResult = db.rawQuery(Queries.DumpIdentities, null);

        int rowCount = queryResult.getCount();
        List<Identity> ret = new ArrayList<>(rowCount);

        if (queryResult.moveToFirst()) {
            do {
                long key = getKey(queryResult);
                String name = getName(queryResult);

                Identity identity = new Identity(key, name, null);
                identity.image = getIdentityFace(identity);

                ret.add(identity);
            } while (queryResult.moveToNext());
        }
        queryResult.close();

        return ret;
    }

    /**
     * Asynchronous version of {@link #dumpIdentities()}.
     *
     * @param callbacks Asynchronous callback object
     */
    public void dumpIdentities(final AsyncQueryCallbacks<List<Identity>> callbacks) {
        AsyncQuery<List<Identity>> query = new AsyncQuery<List<Identity>>(callbacks) {
            @Override
            protected List<Identity> onExecute() {
                return dumpIdentities();
            }
        };
        query.execute();
    }

    /**
     * Fetches an identity from the database.
     *
     * {@link Identity} objects are not required to contain every piece of information all at once
     * (e.g. an object may have its {@code name} field be {@code null}, etc.). Such an object is
     * considered 'incomplete.' This method may be used to 'complete' {@link Identity} objects.
     *
     * @param identity Identity object whose {@code .key} field is the search key
     * @return A valid {@link Identity} object if a matching entry exists, {@code null} otherwise
     */
    public Identity getIdentity(Identity identity) {
        SQLiteDatabase db = getReadableDatabase();
        String[] queryParams = new String[] {
                Long.toString(identity.key)
        };
        Cursor queryResult = db.rawQuery(Queries.GetIdentity, queryParams);
        Identity result = null;

        if (queryResult.moveToFirst()) {
            String name = getName(queryResult);

            result = new Identity(identity.key, name, null);
            result.image = getIdentityFace(identity);
        }
        queryResult.close();

        return result;
    }

    /**
     * Asynchronous version of {@link #getIdentity(Identity)}.
     *
     * @param identity
     * @param callbacks
     */
    public void getIdentity(final Identity identity, final AsyncQueryCallbacks<Identity> callbacks) {
        AsyncQuery<Identity> query = new AsyncQuery<Identity>(callbacks) {
            @Override
            protected Identity onExecute() {
                return getIdentity(identity);
            }
        };
        query.execute();
    }

    /**
     * Returns whether or not a particular identity is being stored within the database.
     *
     * @param identity Target identity
     * @return true if the identity is being stored, false otherwise.
     */
    public boolean hasIdentity(Identity identity) {
        SQLiteDatabase db = getReadableDatabase();

        String[] queryParams = new String[] {
                Long.toString(identity.key)
        };
        Cursor queryResult = db.rawQuery(Queries.HasIdentity, queryParams);
        queryResult.moveToFirst();
        int identityCount = queryResult.getInt(0);
        queryResult.close();

        return identityCount > 0;
    }

    /**
     * Asynchronous version of {@link #hasIdentity(Identity)}.
     *
     * @param identity
     * @param callbacks
     */
    public void hasIdentity(final Identity identity, final AsyncQueryCallbacks<Boolean> callbacks) {
        AsyncQuery<Boolean> query = new AsyncQuery<Boolean>() {
            @Override
            protected Boolean onExecute() {
                return hasIdentity(identity);
            }
        };
        query.execute();
    }

    /**
     * Returns the current number of identities being stored by the database.
     *
     * @return The number of identities stored within the database.
     */
    public int countIdentities() {
        SQLiteDatabase db = getReadableDatabase();

        Cursor queryResult = db.rawQuery(Queries.CountIdentities, null);
        queryResult.moveToFirst();
        int identityCount = queryResult.getInt(0);
        queryResult.close();

        return identityCount;
    }

    /**
     * Asynchronous version of {@link #countIdentities()}.
     *
     * @param callbacks
     */
    public void countIdentities(AsyncQueryCallbacks<Integer> callbacks) {
        AsyncQuery<Integer> query = new AsyncQuery<Integer>() {
            @Override
            protected Integer onExecute() {
                return countIdentities();
            }
        };
        query.execute();
    }

    /**
     * Removes an identity from the database.
     *
     * Does nothing if the identity was not being stored in the first place.
     *
     * @param identity target identity
     */
    public void removeIdentity(Identity identity) {
        SQLiteDatabase db = getWritableDatabase();

        String[] queryParams = new String[] {
                Long.toString(identity.key)
        };
        db.rawQuery(Queries.RemoveIdentity, queryParams);
        getFaceFile(identity).delete();
    }

    /**
     * Asynchronous version of {@link #removeIdentity(Identity)}.
     *
     * @param identity
     * @param callbacks
     */
    public void removeIdentity(final Identity identity, final AsyncQueryCallbacks<Void> callbacks) {
        AsyncQuery<Void> query = new AsyncQuery<Void>(callbacks) {
            @Override
            protected Void onExecute() {
                removeIdentity(identity);

                return null;
            }
        };
        query.execute();
    }

    /**
     * Deletes all identities, leaving the database empty.
     */
    public void clearIdentities() {
        SQLiteDatabase db = getWritableDatabase();

        db.execSQL(Queries.ClearIdentities);
        mImagesDir.delete();
    }

    /**
     * Asynchronous version of {@link #clearIdentities()}.
     *
     * @param callbacks
     */
    public void clearIdentities(final AsyncQueryCallbacks<Void> callbacks) {
        AsyncQuery<Void> query = new AsyncQuery<Void>(callbacks) {
            @Override
            protected Void onExecute() {
                clearIdentities();

                return null;
            }
        };
        query.execute();
    }

    private static long getKey(Cursor queryResult) {
        int keyIndex = queryResult.getColumnIndex(DBInfo._ID);
        return queryResult.getLong(keyIndex);
    }

    private static String getName(Cursor queryResult) {
        int nameIndex = queryResult.getColumnIndex("name");
        return !queryResult.isNull(nameIndex) ? queryResult.getString(nameIndex) : null;
    }

    private File getFaceFile(Identity identity) {
        return new File(mImagesDir, Long.toString(identity.key) + "_face.jpg");
    }

    private void storeIdentityFace(Identity identity) {
        if (identity.image == null) return;

        File imageFile = getFaceFile(identity);
        FileOutputStream outStream = null;

        try {
            outStream = new FileOutputStream(imageFile);
        } catch (FileNotFoundException ex) {
            throw new RuntimeException("Could not store face image.");
        }
        identity.image.getBitmap().compress(Bitmap.CompressFormat.JPEG, 80, outStream);
    }

    private Image getIdentityFace(Identity identity) {
        File imageFile = getFaceFile(identity);

        if (imageFile.exists()) {
            return new Image(BitmapFactory.decodeFile(imageFile.getAbsolutePath()));
        }
        return null;
    }

    private class AsyncQueryResult<T> {
        public T value;
        public Exception err;
    }

    private abstract class AsyncQuery<T> extends AsyncTask<Void, Void, AsyncQueryResult<T>> {
        AsyncQuery() {
            super();
        }
        AsyncQuery(AsyncQueryCallbacks<T> callbacks) {
            super();

            mCallbacks = callbacks;
        }

        protected abstract T onExecute();

        protected void onSuccess(T result) {
            if (mCallbacks == null) return;

            mCallbacks.onSuccess(result);
        }

        protected void onError(Exception ex) {
            if (mCallbacks == null) return;

            mCallbacks.onError(ex);
        }

        protected void onStart() {

        }

        protected void onComplete() {

        }

        @Override
        protected void onPreExecute() {
            onStart();
        }

        @Override
        protected AsyncQueryResult<T> doInBackground(Void... nothing) {
            AsyncQueryResult<T> ret = new AsyncQueryResult<>();

            try {
                ret.value = onExecute();
            } catch (Exception ex) {
                ret.err = ex;
            }
            return ret;
        }

        @Override
        protected void onPostExecute(AsyncQueryResult<T> result) {
            onComplete();

            if (result.value != null) {
                onSuccess(result.value);
            } else {
                onError(result.err);
            }
        }

        private AsyncQueryCallbacks<T> mCallbacks = new AsyncQueryCallbacks<T>() {
            @Override
            protected void onSuccess(T result) {}
        };
    }

    private static class DBInfo implements BaseColumns {
        final static int VERSION = 1;
        final static String DB_NAME = "Face2Name";
        final static String TABLE_NAME = "identities";
    }

    private static class Queries {
        final static String CreateTable = "CREATE TABLE IF NOT EXISTS " + DBInfo.TABLE_NAME +
                                            "(" + DBInfo._ID + " INTEGER PRIMARY KEY NOT NULL," +
                                            "name TEXT)";
        final static String DumpIdentities = "SELECT * FROM " + DBInfo.TABLE_NAME;
        final static String GetIdentity = "SELECT * FROM " + DBInfo.TABLE_NAME + " WHERE " + DBInfo._ID + "=?";
        final static String RemoveIdentity = "DELETE FROM " + DBInfo.TABLE_NAME + " WHERE " + DBInfo._ID + "=?";
        final static String ClearIdentities = "DELETE FROM " + DBInfo.TABLE_NAME;
        final static String HasIdentity = "SELECT COUNT(*) FROM " + DBInfo.TABLE_NAME + " WHERE " + DBInfo._ID + "=?";
        final static String CountIdentities = "SELECT COUNT(*) FROM " + DBInfo.TABLE_NAME;
    }
    private File mImagesDir;
}
