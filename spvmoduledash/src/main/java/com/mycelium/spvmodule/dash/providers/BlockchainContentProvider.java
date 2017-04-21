package com.mycelium.spvmodule.dash.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.mycelium.spvmodule.dash.providers.DB.Addresses;
import com.mycelium.spvmodule.dash.providers.DB.Transactions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlockchainContentProvider extends ContentProvider {
    public static final String AUTHORITY = "com.mycelium.spvmodule.dash.providers.BlockchainContentProvider";

    private static final UriMatcher URI_MATCHER;

    private static final String ADDRESSES_TABLE_NAME = "addresses";
    private static final String TRANSACTIONS_TABLE_NAME = "transactions";

    private static final int ADDRESS_LIST = 1;
    private static final int ADDRESS_ID = 2;
    private static final int TRANSACTION_LIST = 3;
    private static final int TRANSACTION_ID = 4;

    private static final Logger log = LoggerFactory.getLogger(BlockchainContentProvider.class);

    private static class DatabaseHelper extends SQLiteOpenHelper {
        private static final String DATABASE_NAME = "spvModuleDash.db";
        private static final int DATABASE_VERSION = 2;

        DatabaseHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("DROP TABLE IF EXISTS " + ADDRESSES_TABLE_NAME + ";");
            db.execSQL("CREATE TABLE " + ADDRESSES_TABLE_NAME +
                    " (" + Addresses.ADDRESS + " TEXT PRIMARY KEY," +
                    Addresses.CREATION_DATE + " INTEGER," +
                    Addresses.SYNCED_TO_BLOCK + " INTEGER," +
                    Addresses.ACCOUNT + " TEXT);");
            db.execSQL("DROP TABLE IF EXISTS " + TRANSACTIONS_TABLE_NAME + ";");
            db.execSQL("CREATE TABLE " + TRANSACTIONS_TABLE_NAME +
                    " (" + Transactions.TRANSACTION_ID + " TEXT PRIMARY KEY," +
                    Transactions.TRANSACTION + " BLOB," +
                    Transactions.INCLUDED_IN_BLOCK + " INTEGER," +
                    Transactions.ACCOUNT + " TEXT);");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            log.warn("Upgrading database from version " + oldVersion + " to " + newVersion);
            if (oldVersion < 2) {
                onCreate(db);
            }
        }
    }

    private DatabaseHelper dbHelper;

    static {
        URI_MATCHER = new UriMatcher(UriMatcher.NO_MATCH);
        URI_MATCHER.addURI(AUTHORITY, ADDRESSES_TABLE_NAME, ADDRESS_LIST);
        URI_MATCHER.addURI(AUTHORITY, ADDRESSES_TABLE_NAME + "/#", ADDRESS_ID);
        URI_MATCHER.addURI(AUTHORITY, TRANSACTIONS_TABLE_NAME, TRANSACTION_LIST);
        URI_MATCHER.addURI(AUTHORITY, TRANSACTIONS_TABLE_NAME + "/#", TRANSACTION_ID);
    }

    @Override
    public boolean onCreate() {
        dbHelper = new DatabaseHelper(getContext());
        return true;
    }

    @Nullable
    @Override
    public Cursor query(@NonNull Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        int match = URI_MATCHER.match(uri);
        switch (match) {
            case ADDRESS_LIST:
            case TRANSACTION_LIST:
                break;
            case ADDRESS_ID:
            case TRANSACTION_ID:
                selection = selection + " _id=" + uri.getLastPathSegment();
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        qb.setTables(getTableFromMatch(match));
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = qb.query(db, projection, selection, selectionArgs, null, null, sortOrder);
        //noinspection ConstantConditions
        cursor.setNotificationUri(getContext().getContentResolver(), uri);
        return cursor;
    }

    private static String getTableFromMatch(int match) {
        switch (match) {
            case ADDRESS_LIST:
            case ADDRESS_ID:
                return ADDRESSES_TABLE_NAME;
            case TRANSACTION_LIST:
            case TRANSACTION_ID:
                return TRANSACTIONS_TABLE_NAME;
            default:
                throw new IllegalArgumentException("Unknown match " + match);
        }
    }

    @Nullable
    @Override
    public String getType(@NonNull Uri uri) {
        switch (URI_MATCHER.match(uri)) {
            case ADDRESS_LIST:
                return Addresses.CONTENT_TYPE;
            case ADDRESS_ID:
                return Addresses.CONTENT_ITEM_TYPE;
            case TRANSACTION_LIST:
                return Transactions.CONTENT_TYPE;
            case TRANSACTION_ID:
                return Transactions.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Nullable
    @Override
    public Uri insert(@NonNull Uri uri, ContentValues values) {
        // TODO: 9/14/16 validate data here
        if (values == null || values.size() < 1) {
            throw new IllegalArgumentException("Can't insert empty row!");
        }
        Uri contentUri;
        int match = URI_MATCHER.match(uri);
        switch (match) {
            case ADDRESS_LIST:
                contentUri = Addresses.CONTENT_URI;
                break;
            case TRANSACTION_LIST:
                contentUri = Transactions.CONTENT_URI;
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        long rowId = db.insert(getTableFromMatch(match), null, values);
        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(contentUri, rowId);
            notifyChange(noteUri);
            return noteUri;
        }
        throw new SQLException("Failed to insert row into " + uri);
    }

    @Override
    public int delete(@NonNull Uri uri, String where, String[] whereArgs) {
        // TODO: 9/14/16 delete should not allow to delete transactions of watched addresses
        // TODO: 9/14/16 deleting addresses should delete their transactions
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        switch (match) {
            case ADDRESS_LIST:
            case TRANSACTION_LIST:
                break;
            case ADDRESS_ID:
            case TRANSACTION_ID:
                where = where + " _id=" + uri.getLastPathSegment();
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        int count = db.delete(getTableFromMatch(match), where, whereArgs);
        if (count > 0) {
            notifyChange(uri);
        }
        return count;
    }

    @Override
    public int update(@NonNull Uri uri, ContentValues values, String where, String[] whereArgs) {
        // TODO: 9/14/16 updates should be limited to very few things
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        int match = URI_MATCHER.match(uri);
        int count = db.update(getTableFromMatch(match), values, where, whereArgs);
        if (count > 0) {
            notifyChange(uri);
        }
        return count;
    }

    private void notifyChange(Uri uri) {
        //crashing if context or contentResolver are null is appropriate here
        //noinspection ConstantConditions
        getContext().getContentResolver().notifyChange(uri, null);
    }
}
