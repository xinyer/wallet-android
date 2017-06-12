/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.wapi;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDoneException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;
import android.util.Log;

import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.OutPoint;
import com.mrd.bitlib.model.Transaction;
import com.mrd.bitlib.model.TransactionInput;
import com.mrd.bitlib.util.BitUtils;
import com.mrd.bitlib.util.HashUtils;
import com.mrd.bitlib.util.HexUtils;
import com.mrd.bitlib.util.Sha256Hash;
import com.mycelium.wallet.BuildConfig;
import com.mycelium.wallet.persistence.SQLiteQueryWithBlobs;
import com.mycelium.wapi.api.exception.DbCorruptedException;
import com.mycelium.wapi.model.TransactionEx;
import com.mycelium.wapi.model.TransactionOutputEx;
import com.mycelium.wapi.wallet.Bip44AccountBacking;
import com.mycelium.wapi.wallet.SingleAddressAccountBacking;
import com.mycelium.wapi.wallet.WalletManagerBacking;
import com.mycelium.wapi.wallet.bip44.Bip44AccountContext;
import com.mycelium.wapi.wallet.single.SingleAddressAccountContext;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.UUID;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.mycelium.wallet.persistence.SQLiteQueryWithBlobs.uuidToBytes;

public class SqliteWalletManagerBacking implements WalletManagerBacking {
   private static final String LOG_TAG = SqliteWalletManagerBacking.class.getCanonicalName();
   private static final String TABLE_KV = "kv";
   private static final int DEFAULT_SUB_ID = 0;
   private SQLiteDatabase _database;
   private Map<UUID, SqliteAccountBacking> _backings;
   private final SQLiteStatement _insertOrReplaceBip44Account;
   private final SQLiteStatement _updateBip44Account;
   private final SQLiteStatement _insertOrReplaceSingleAddressAccount;
   private final SQLiteStatement _updateSingleAddressAccount;
   private final SQLiteStatement _deleteSingleAddressAccount;
   private final SQLiteStatement _deleteBip44Account;
   private final SQLiteStatement _insertOrReplaceKeyValue;
   private final SQLiteStatement _deleteKeyValue;
   private final SQLiteStatement _deleteSubId;
   private final SQLiteStatement _getMaxSubId;
   private final SQLiteStatement _insertOrReplaceAddressTimestamp;
   private final SQLiteStatement _getTimestampForAddress;


   SqliteWalletManagerBacking(Context context) {
      OpenHelper _openHelper = new OpenHelper(context);
      _database = _openHelper.getWritableDatabase();

      _insertOrReplaceBip44Account = _database.compileStatement("INSERT OR REPLACE INTO bip44 VALUES (?,?,?,?,?,?,?,?,?,?)");
      _insertOrReplaceSingleAddressAccount = _database.compileStatement("INSERT OR REPLACE INTO single VALUES (?,?,?,?,?)");
      _updateBip44Account = _database.compileStatement("UPDATE bip44 SET archived=?,blockheight=?,lastExternalIndexWithActivity=?,lastInternalIndexWithActivity=?,firstMonitoredInternalIndex=?,lastDiscovery=?,accountType=?,accountSubId=? WHERE id=?");
      _updateSingleAddressAccount = _database.compileStatement("UPDATE single SET archived=?,blockheight=? WHERE id=?");
      _deleteSingleAddressAccount = _database.compileStatement("DELETE FROM single WHERE id = ?");
      _deleteBip44Account = _database.compileStatement("DELETE FROM bip44 WHERE id = ?");
      _insertOrReplaceKeyValue = _database.compileStatement("INSERT OR REPLACE INTO kv VALUES (?,?,?,?)");
      _getMaxSubId = _database.compileStatement("SELECT max(subId) FROM kv");
      _deleteKeyValue = _database.compileStatement("DELETE FROM kv WHERE k = ?");
      _deleteSubId = _database.compileStatement("DELETE FROM kv WHERE subId = ?");
      _insertOrReplaceAddressTimestamp = _database.compileStatement("INSERT OR REPLACE INTO addressTimestamp VALUES (?,?)");
      _getTimestampForAddress = _database.compileStatement("SELECT timestamp FROM addressTimestamp WHERE address=?");
      _backings = new HashMap<>();
      for (UUID id : getAccountIds(_database)) {
         _backings.put(id, new SqliteAccountBacking(id, _database));
      }
   }

   private List<UUID> getAccountIds(SQLiteDatabase db) {
      List<UUID> ids = new ArrayList<>();
      ids.addAll(getBip44AccountIds(db));
      ids.addAll(getSingleAddressAccountIds(db));
      return ids;
   }

   private List<UUID> getSingleAddressAccountIds(SQLiteDatabase db) {
      Cursor cursor = null;
      List<UUID> accounts = new ArrayList<>();
      try {
         SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(db);
         cursor = blobQuery.query(false, "single", new String[]{"id"}, null, null, null, null, null, null);
         while (cursor.moveToNext()) {
            UUID uuid = SQLiteQueryWithBlobs.uuidFromBytes(cursor.getBlob(0));
            accounts.add(uuid);
         }
         return accounts;
      } finally {
         if (cursor != null) {
            cursor.close();
         }
      }
   }

   private List<UUID> getBip44AccountIds(SQLiteDatabase db) {
      Cursor cursor = null;
      List<UUID> accounts = new ArrayList<>();
      try {
         SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(db);
         cursor = blobQuery.query(false, "bip44", new String[]{"id"}, null, null, null, null, null, null);
         while (cursor.moveToNext()) {
            UUID uuid = SQLiteQueryWithBlobs.uuidFromBytes(cursor.getBlob(0));
            accounts.add(uuid);
         }
         return accounts;
      } finally {
         if (cursor != null) {
            cursor.close();
         }
      }
   }

   private void beginTransaction() {
      synchronized (this) {
         _database.beginTransaction();
      }
   }

   private void setTransactionSuccessful() {
      synchronized (this) {
         _database.setTransactionSuccessful();
      }
   }

   private void endTransaction() {
      synchronized (this) {
         _database.endTransaction();
      }
   }

   @Override
   public List<Bip44AccountContext> loadBip44AccountContexts() {
      synchronized (this) {
         List<Bip44AccountContext> list = new ArrayList<>();
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_database);
            cursor = blobQuery.query(
                false, "bip44",
                new String[] {"id", "accountIndex", "archived", "blockheight",
                    "lastExternalIndexWithActivity", "lastInternalIndexWithActivity",
                    "firstMonitoredInternalIndex", "lastDiscovery", "accountType", "accountSubId"},
                null, null, null, null, "accountIndex", null);

            while (cursor.moveToNext()) {
               UUID id = SQLiteQueryWithBlobs.uuidFromBytes(cursor.getBlob(0));
               int accountIndex = cursor.getInt(1);
               boolean isArchived = cursor.getInt(2) == 1;
               int blockHeight = cursor.getInt(3);
               int lastExternalIndexWithActivity = cursor.getInt(4);
               int lastInternalIndexWithActivity = cursor.getInt(5);
               int firstMonitoredInternalIndex = cursor.getInt(6);
               long lastDiscovery = cursor.getLong(7);
               int accountType = cursor.getInt(8);
               int accountSubId = (int) cursor.getLong(9);

               list.add(new Bip44AccountContext(id, accountIndex, isArchived, blockHeight, lastExternalIndexWithActivity,
                   lastInternalIndexWithActivity, firstMonitoredInternalIndex, lastDiscovery, accountType, accountSubId));
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }
   }

   @Override
   public boolean createBip44AccountContext(Bip44AccountContext context) {
      synchronized (this) {
         beginTransaction();
         try {
            // Create backing tables
            SqliteAccountBacking backing = _backings.get(context.getId());
            if (backing == null) {
               backing = new SqliteAccountBacking(context.getId(), _database);
               _backings.put(context.getId(), backing);
            }

            // Create context
            _insertOrReplaceBip44Account.bindBlob(1, uuidToBytes(context.getId()));
            _insertOrReplaceBip44Account.bindLong(2, context.getAccountIndex());
            _insertOrReplaceBip44Account.bindLong(3, context.isArchived() ? 1 : 0);
            _insertOrReplaceBip44Account.bindLong(4, context.getBlockHeight());
            _insertOrReplaceBip44Account.bindLong(5, context.getLastExternalIndexWithActivity());
            _insertOrReplaceBip44Account.bindLong(6, context.getLastInternalIndexWithActivity());
            _insertOrReplaceBip44Account.bindLong(7, context.getFirstMonitoredInternalIndex());
            _insertOrReplaceBip44Account.bindLong(8, context.getLastDiscovery());
            _insertOrReplaceBip44Account.bindLong(9, context.getAccountType());
            _insertOrReplaceBip44Account.bindLong(10, context.getAccountSubId());
            boolean result = _insertOrReplaceBip44Account.executeInsert() != -1;
            setTransactionSuccessful();
            return result;
         } catch (SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }
   }


   private boolean updateBip44AccountContext(Bip44AccountContext context) {
      //UPDATE bip44 SET archived=?,blockheight=?,lastExternalIndexWithActivity=?,lastInternalIndexWithActivity=?,firstMonitoredInternalIndex=?,lastDiscovery=?,accountType=?,accountSubId=? WHERE id=?
      beginTransaction();
      try {
         _updateBip44Account.bindLong(1, context.isArchived() ? 1 : 0);
         _updateBip44Account.bindLong(2, context.getBlockHeight());
         _updateBip44Account.bindLong(3, context.getLastExternalIndexWithActivity());
         _updateBip44Account.bindLong(4, context.getLastInternalIndexWithActivity());
         _updateBip44Account.bindLong(5, context.getFirstMonitoredInternalIndex());
         _updateBip44Account.bindLong(6, context.getLastDiscovery());
         _updateBip44Account.bindLong(7, context.getAccountType());
         _updateBip44Account.bindLong(8, context.getAccountSubId());
         _updateBip44Account.bindBlob(9, uuidToBytes(context.getId()));
         boolean result = _updateBip44Account.executeUpdateDelete() == 1;
         setTransactionSuccessful();
         return result;
      } catch(SQLException sqlException) {
         return false;
      } finally {
         endTransaction();
      }
   }

   @Override
   public List<SingleAddressAccountContext> loadSingleAddressAccountContexts() {
      synchronized (this) {
         List<SingleAddressAccountContext> list = new ArrayList<>();
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_database);
            cursor = blobQuery.query(false, "single", new String[] {"id", "address", "addressstring", "archived", "blockheight"}, null, null,
                null, null, null, null);
            while (cursor.moveToNext()) {
               UUID id = SQLiteQueryWithBlobs.uuidFromBytes(cursor.getBlob(0));
               byte[] addressBytes = cursor.getBlob(1);
               String addressString = cursor.getString(2);
               Address address = new Address(addressBytes, addressString);
               boolean isArchived = cursor.getInt(3) == 1;
               int blockHeight = cursor.getInt(4);
               list.add(new SingleAddressAccountContext(id, address, isArchived, blockHeight));
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }
   }

   @Override
   public boolean createSingleAddressAccountContext(SingleAddressAccountContext context) {
      synchronized (this) {
         beginTransaction();
         try {
            // Create backing tables
            SqliteAccountBacking backing = _backings.get(context.getId());
            if (backing == null) {
               backing = new SqliteAccountBacking(context.getId(), _database);
               _backings.put(context.getId(), backing);
            }

            // Create context
            _insertOrReplaceSingleAddressAccount.bindBlob(1, uuidToBytes(context.getId()));
            _insertOrReplaceSingleAddressAccount.bindBlob(2, context.getAddress().getAllAddressBytes());
            _insertOrReplaceSingleAddressAccount.bindString(3, context.getAddress().toString());
            _insertOrReplaceSingleAddressAccount.bindLong(4, context.isArchived() ? 1 : 0);
            _insertOrReplaceSingleAddressAccount.bindLong(5, context.getBlockHeight());
            boolean result = _insertOrReplaceSingleAddressAccount.executeInsert() != -1;
            setTransactionSuccessful();
            return result;
         } catch (SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }
   }

   private boolean updateSingleAddressAccountContext(SingleAddressAccountContext context) {
      // "UPDATE single SET archived=?,blockheight=? WHERE id=?"
      beginTransaction();
      try {
         _updateSingleAddressAccount.bindLong(1, context.isArchived() ? 1 : 0);
         _updateSingleAddressAccount.bindLong(2, context.getBlockHeight());
         _updateSingleAddressAccount.bindBlob(3, uuidToBytes(context.getId()));
         boolean result = _updateSingleAddressAccount.executeUpdateDelete() == 1;
         setTransactionSuccessful();
         return result;
      } catch(SQLException e) {
         logException(e);
         return false;
      } finally {
         endTransaction();
      }
   }

   @Override
   public boolean deleteSingleAddressAccountContext(UUID accountId) {
      synchronized (this) {
         // "DELETE FROM single WHERE id = ?"
         beginTransaction();
         try {
            SqliteAccountBacking sqliteAccountBacking = _backings.get(accountId);
            if (sqliteAccountBacking == null) {
               return false;
            }
            _deleteSingleAddressAccount.bindBlob(1, uuidToBytes(accountId));
            boolean result = _deleteSingleAddressAccount.executeUpdateDelete() == 1;
            sqliteAccountBacking.dropTables();
            _backings.remove(accountId);
            setTransactionSuccessful();
            return result;
         } catch (SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }
   }

   @Override
   public Map<Address, Long> getAllAddressCreationTimes() {
      synchronized (this) {
         Map<Address, Long> map = new HashMap<>();
         Cursor cursor = _database.rawQuery("SELECT address, timestamp FROM addressTimestamp;", null);
         while (cursor.moveToNext()) {
            map.put(Address.fromString(cursor.getString(0)), cursor.getLong(1));
         }
         cursor.close();
         return map;
      }
   }


   @Override
   public boolean deleteBip44AccountContext(UUID accountId) {
      synchronized (this) {
         // "DELETE FROM bip44 WHERE id = ?"
         beginTransaction();
         try {
            SqliteAccountBacking backing = _backings.get(accountId);
            if (backing == null) {
               return false;
            }
            _deleteBip44Account.bindBlob(1, uuidToBytes(accountId));
            boolean result = _deleteBip44Account.executeUpdateDelete() == 1;
            backing.dropTables();
            _backings.remove(accountId);
            setTransactionSuccessful();
            return result;
         } catch (SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }
   }

   private void logException(SQLException e) {
      if(BuildConfig.DEBUG) {
         Log.e(LOG_TAG, e.getLocalizedMessage(), e);
      }
   }

   @Override
   public Bip44AccountBacking getBip44AccountBacking(UUID accountId) {
      synchronized (this) {
         SqliteAccountBacking backing = _backings.get(accountId);
         checkNotNull(backing);
         return backing;
      }
   }

   @Override
   public SingleAddressAccountBacking getSingleAddressAccountBacking(UUID accountId) {
      synchronized (this) {
         SqliteAccountBacking backing = _backings.get(accountId);
         checkNotNull(backing);
         return backing;
      }
   }

   @Override
   public byte[] getValue(byte[] id) {
      synchronized (this) {
         return getValue(id, DEFAULT_SUB_ID);
      }
   }

   @Override
   public byte[] getValue(byte[] id, int subId) {
      synchronized (this) {
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_database);
            blobQuery.bindBlob(1, id);
            blobQuery.bindLong(2, (long) subId);
            cursor = blobQuery.query(false, TABLE_KV, new String[] {"v", "checksum"}, "k = ? and subId = ?", null, null, null,
                null, null);
            if (cursor.moveToNext()) {
               byte[] retVal = cursor.getBlob(0);
               byte[] checkSumDb = cursor.getBlob(1);

               // checkSumDb might be null for older data, where we hadn't had a checksum
               if (checkSumDb != null && !Arrays.equals(checkSumDb, calcChecksum(id, retVal))) {
                  // mismatch in checksum - the DB might be corrupted
                  Log.e(LOG_TAG, "Checksum failed - SqliteDB might be corrupted");
                  throw new DbCorruptedException("Checksum failed while reading from DB. Your file storage might be corrupted");
               }

               return retVal;
            }
            return null;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }
   }

   @Override
   public void setValue(byte[] key, byte[] value) {
      synchronized (this) {
         setValue(key, DEFAULT_SUB_ID, value);
      }
   }

   @Override
   public int getMaxSubId() {
      synchronized (this) {
         return (int) _getMaxSubId.simpleQueryForLong();
      }
   }

   @Override
   public void setValue(byte[] key, int subId, byte[] value) {
      synchronized (this) {
         _insertOrReplaceKeyValue.bindBlob(1, key);
         SQLiteQueryWithBlobs.bindBlobWithNull(_insertOrReplaceKeyValue, 2, value);
         _insertOrReplaceKeyValue.bindBlob(3, calcChecksum(key, value));
         _insertOrReplaceKeyValue.bindLong(4, subId);

         _insertOrReplaceKeyValue.executeInsert();
      }
   }

   private byte[] calcChecksum(byte[] key, byte[] value) {
      synchronized (this) {
         byte toHash[] = BitUtils.concatenate(key, value);
         return HashUtils.sha256(toHash).firstNBytes(8);
      }
   }

   @Override
   public void deleteValue(byte[] id) {
      synchronized (this) {
         _deleteKeyValue.bindBlob(1, id);
         _deleteKeyValue.execute();
      }
   }

   @Override
   public void deleteSubStorageId(int subId) {
      synchronized (this) {
         _deleteSubId.bindLong(1, subId);
         _deleteSubId.execute();
      }
   }

   private static String uuidToTableSuffix(UUID uuid) {
      return HexUtils.toHex(uuidToBytes(uuid));
   }

   private static String getUtxoTableName(String tableSuffix) {
      return "utxo_" + tableSuffix;
   }

   private static String getPtxoTableName(String tableSuffix) {
      return "ptxo_" + tableSuffix;
   }

   private static String getTxRefersPtxoTableName(String tableSuffix) {
      return "txtoptxo_" + tableSuffix;
   }

   private static String getTxTableName(String tableSuffix) {
      return "tx_" + tableSuffix;
   }

   private static String getOutgoingTxTableName(String tableSuffix) {
      return "outtx_" + tableSuffix;
   }

   private class SqliteAccountBacking implements Bip44AccountBacking, SingleAddressAccountBacking {
      private UUID _id;
      private final String utxoTableName;
      private final String ptxoTableName;
      private final String txTableName;
      private final String outTxTableName;
      private final String txRefersParentTxTableName;
      private final SQLiteStatement _insertOrReplaceUtxo;
      private final SQLiteStatement _deleteUtxo;
      private final SQLiteStatement _deleteAllUtxo;
      private final SQLiteStatement _insertOrReplacePtxo;
      private final SQLiteStatement _insertOrReplaceTx;
      private final SQLiteStatement _deleteTx;
      private final SQLiteStatement _insertOrReplaceOutTx;
      private final SQLiteStatement _deleteOutTx;
      private final SQLiteStatement _insertTxRefersParentTx;
      private final SQLiteStatement _deleteTxRefersParentTx;
      private final SQLiteDatabase _db;

      private SqliteAccountBacking(UUID id, SQLiteDatabase db) {
         _id = id;
         _db = db;
         String tableSuffix = uuidToTableSuffix(id);
         utxoTableName = "txo";
         ptxoTableName = "txo";
         txTableName = "tx";
         outTxTableName = "outtx";
         txRefersParentTxTableName = "txtoptxo";

         _insertOrReplaceUtxo = db.compileStatement("INSERT OR REPLACE INTO " + utxoTableName + " VALUES (?,?,?,?,?,?,?)");
         _deleteUtxo = db.compileStatement("DELETE FROM " + utxoTableName + " WHERE outpoint = ?");
         _deleteAllUtxo = db.compileStatement("DELETE FROM " + utxoTableName + " WHERE transactionType = \'utxo\'");

         _insertOrReplacePtxo = db.compileStatement("INSERT OR REPLACE INTO " + ptxoTableName + " VALUES (?,?,?,?,?,?, ?)");

         _insertOrReplaceTx = db.compileStatement("INSERT OR REPLACE INTO " + txTableName + " VALUES (?,?,?,?,?)");
         _deleteTx = db.compileStatement("DELETE FROM " + txTableName + " WHERE id = ?");

         _insertOrReplaceOutTx = db.compileStatement("INSERT OR REPLACE INTO " + outTxTableName + " VALUES (?,?,?)");
         _deleteOutTx = db.compileStatement("DELETE FROM " + outTxTableName + " WHERE id = ?");

         _insertTxRefersParentTx = db.compileStatement("INSERT OR REPLACE INTO " + txRefersParentTxTableName + " VALUES (?,?,?)");
         _deleteTxRefersParentTx = db.compileStatement("DELETE FROM " + txRefersParentTxTableName + " WHERE txid = ?");
      }

      private void dropTables() {
         String tableSuffix = uuidToTableSuffix(_id);
         _db.execSQL("DROP TABLE IF EXISTS " + getUtxoTableName(tableSuffix));
         _db.execSQL("DROP TABLE IF EXISTS " + getPtxoTableName(tableSuffix));
         _db.execSQL("DROP TABLE IF EXISTS " + getTxTableName(tableSuffix));
         _db.execSQL("DROP TABLE IF EXISTS " + getOutgoingTxTableName(tableSuffix));
         _db.execSQL("DROP TABLE IF EXISTS " + getTxRefersPtxoTableName(tableSuffix));
      }

      void beginTransaction() {
         SqliteWalletManagerBacking.this.beginTransaction();
      }

      void setTransactionSuccessful() {
         SqliteWalletManagerBacking.this.setTransactionSuccessful();
      }

      void endTransaction() {
         SqliteWalletManagerBacking.this.endTransaction();
      }

      @Override
      public void clear() {
         _db.execSQL("DELETE FROM " + utxoTableName);
         _db.execSQL("DELETE FROM " + ptxoTableName);
         _db.execSQL("DELETE FROM " + txTableName);
         _db.execSQL("DELETE FROM " + outTxTableName);
         _db.execSQL("DELETE FROM " + txRefersParentTxTableName);
      }

      @Override
      public synchronized boolean putUnspentOutput(TransactionOutputEx output) {
         beginTransaction();
         try {
            _insertOrReplaceUtxo.bindBlob(1, SQLiteQueryWithBlobs.outPointToBytes(output.outPoint));
            _insertOrReplaceUtxo.bindLong(2, output.height);
            _insertOrReplaceUtxo.bindLong(3, output.value);
            _insertOrReplaceUtxo.bindLong(4, output.isCoinBase ? 1 : 0);
            _insertOrReplaceUtxo.bindBlob(5, output.script);
            _insertOrReplaceUtxo.bindString(6, _id.toString());
            _insertOrReplaceUtxo.bindString(7, "utxo");

            boolean result = _insertOrReplaceUtxo.executeInsert() != -1;
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public Collection<TransactionOutputEx> getAllUnspentOutputs() {
         Cursor cursor = null;
         List<TransactionOutputEx> list = new LinkedList<>();
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            cursor = blobQuery.query(false, utxoTableName, new String[]{"outpoint", "height", "value", "isCoinbase",
                "script"}, "transactionType = 'utxo' AND accountID = ?", new String[] {_id.toString()}, null, null, null, null);
            while (cursor.moveToNext()) {
               TransactionOutputEx tex = new TransactionOutputEx(SQLiteQueryWithBlobs.outPointFromBytes(cursor
                     .getBlob(0)), cursor.getInt(1), cursor.getLong(2), cursor.getBlob(4), cursor.getInt(3) != 0);
               list.add(tex);
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public TransactionOutputEx getUnspentOutput(OutPoint outPoint) {
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            blobQuery.bindBlob(1, SQLiteQueryWithBlobs.outPointToBytes(outPoint));
            cursor = blobQuery.query(false, utxoTableName, new String[]{"height", "value", "isCoinbase", "script"},
                "outpoint = ? AND transactionType = 'utxo' AND accountID = ?", new String[] {_id.toString()}, null, null, null, null);
            if (cursor.moveToNext()) {
               return new TransactionOutputEx(outPoint, cursor.getInt(0), cursor.getLong(1),
                     cursor.getBlob(3), cursor.getInt(2) != 0);
            }
            return null;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public boolean deleteUnspentOutput(OutPoint outPoint) {
         beginTransaction();
         try {
            _deleteUtxo.bindBlob(1, SQLiteQueryWithBlobs.outPointToBytes(outPoint));
            boolean result = _deleteUtxo.executeUpdateDelete() == 1;
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean deleteAllUnspentOutput() {
         beginTransaction();
         try {
            boolean result = _deleteAllUtxo.executeUpdateDelete() >= 0;
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean putParentTransactionOutput(TransactionOutputEx output) {
         beginTransaction();
         try {
            _insertOrReplacePtxo.bindBlob(1, SQLiteQueryWithBlobs.outPointToBytes(output.outPoint));
            _insertOrReplacePtxo.bindLong(2, output.height);
            _insertOrReplacePtxo.bindLong(3, output.value);
            _insertOrReplacePtxo.bindLong(4, output.isCoinBase ? 1 : 0);
            _insertOrReplacePtxo.bindBlob(5, output.script);
            _insertOrReplacePtxo.bindString(6, _id.toString());
            _insertOrReplacePtxo.bindString(7, "ptxo");
            boolean result = _insertOrReplacePtxo.executeInsert() >= -1;
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean putTxRefersParentTransaction(Sha256Hash txId, List<OutPoint> refersOutputs) {
         beginTransaction();
         try {
            boolean result = true;
            for (OutPoint output : refersOutputs) {
               _insertTxRefersParentTx.bindBlob(1, txId.getBytes());
               _insertTxRefersParentTx.bindBlob(2, SQLiteQueryWithBlobs.outPointToBytes(output));
               _insertTxRefersParentTx.bindString(3, _id.toString());
               if(_insertTxRefersParentTx.executeInsert() == -1) {
                  result = false;
               }
            }
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean deleteTxRefersParentTransaction(Sha256Hash txId) {
         beginTransaction();
         try {
            _deleteTxRefersParentTx.bindBlob(1, txId.getBytes());
            boolean result = _deleteTxRefersParentTx.executeUpdateDelete() == 1;
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public Collection<Sha256Hash> getTransactionsReferencingOutPoint(OutPoint outPoint) {
         Cursor cursor = null;
         List<Sha256Hash> list = new LinkedList<>();
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            blobQuery.bindBlob(1, SQLiteQueryWithBlobs.outPointToBytes(outPoint));
            cursor = blobQuery.query(false, txRefersParentTxTableName, new String[]{"txid"},
                "input = ? AND accountID = \'" + _id.toString() +"\'", null, null, null, null, null);
            while (cursor.moveToNext()) {
               list.add(new Sha256Hash(cursor.getBlob(0)));
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public TransactionOutputEx getParentTransactionOutput(OutPoint outPoint) {
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            blobQuery.bindBlob(1, SQLiteQueryWithBlobs.outPointToBytes(outPoint));
            cursor = blobQuery.query(false, ptxoTableName, new String[]{"height", "value", "isCoinbase", "script"},
                  "outpoint = ? AND accountID = \'" + _id.toString() +"\'", null, null, null, null, null);
            if (cursor.moveToNext()) {
               return new TransactionOutputEx(outPoint, cursor.getInt(0), cursor.getLong(1),
                     cursor.getBlob(3), cursor.getInt(2) != 0);
            }
            return null;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public boolean putTransaction(TransactionEx tx) {
         Log.d(LOG_TAG, "testNelson, putTransaction, transaction ID = " + tx.txid);
         beginTransaction();
         try {
            _insertOrReplaceTx.bindBlob(1, tx.txid.getBytes());
            _insertOrReplaceTx.bindLong(2, tx.height == -1 ? Integer.MAX_VALUE : tx.height);
            _insertOrReplaceTx.bindLong(3, tx.time);
            _insertOrReplaceTx.bindBlob(4, tx.binary);
            _insertOrReplaceTx.bindString(5, _id.toString());
            boolean result = _insertOrReplaceTx.executeInsert() != -1;
            if(result) {
               if (result = putReferencedOutputs(tx.binary)) {
                  setTransactionSuccessful();
               }
            }
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public long getOldestTransactionTimestamp() {
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            cursor = blobQuery.query(false, txTableName, new String[]{"time"}, "id = ? AND accountID = \'" + _id.toString() +"\'", null,
                null, null, "time ASC", "1");
            //"SELECT time FROM tx ORDER BY time ASC LIMIT 1"
            if (cursor.moveToNext()) {
               return cursor.getInt(0);
            }
            return 0;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

     private boolean putReferencedOutputs(byte[] rawTx) {
         try {
            final Transaction transaction = Transaction.fromBytes(rawTx);
            final List<OutPoint> refersOutpoint = new ArrayList<>();
            for (TransactionInput input : transaction.inputs) {
               refersOutpoint.add(input.outPoint);
            }
            return putTxRefersParentTransaction(transaction.getHash(), refersOutpoint);
         } catch (Transaction.TransactionParsingException e) {
            Log.w(LOG_TAG, "Unable to decode transaction: " + e.getMessage());
            return false;
         }
      }

      @Override
      public TransactionEx getTransaction(Sha256Hash hash) {
         Cursor cursor = null;
         Log.d(LOG_TAG, "testNelson, getTransaction, transaction ID = " + hash);
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            blobQuery.bindBlob(1, hash.getBytes());
            cursor = blobQuery.query(false, txTableName, new String[]{"height", "time", "binary"}, "id = ? AND accountID = \'" + _id.toString() +"\'", null,
                  null, null, null, null);
            if (cursor.moveToNext()) {
               int height = cursor.getInt(0);
               if (height == Integer.MAX_VALUE) {
                  height = -1;
               }
               return new TransactionEx(hash, height, cursor.getInt(1), cursor.getBlob(2));
            }
            return null;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public boolean deleteTransaction(Sha256Hash hash) {
         beginTransaction();
         try {
            _deleteTx.bindBlob(1, hash.getBytes());
            boolean result = _deleteTx.executeUpdateDelete() == 1;
            // also delete all output references for this tx
            deleteTxRefersParentTransaction(hash);
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean hasTransaction(Sha256Hash hash) {
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            blobQuery.bindBlob(1, hash.getBytes());
            cursor = blobQuery.query(false, txTableName, new String[]{"height"}, "id = ?", null, null, null, null,
                  null);
            return cursor.moveToNext();
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public Collection<TransactionEx> getUnconfirmedTransactions() {
         Cursor cursor = null;
         List<TransactionEx> list = new LinkedList<>();
         try {
            // 2147483647 == Integer.MAX_VALUE
            cursor = _db.rawQuery("SELECT id, time, binary FROM " + txTableName + " WHERE height = 2147483647",
                  new String[]{});
            while (cursor.moveToNext()) {
               TransactionEx tex = new TransactionEx(new Sha256Hash(cursor.getBlob(0)), -1, cursor.getInt(1),
                     cursor.getBlob(2));
               list.add(tex);
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public Collection<TransactionEx> getYoungTransactions(int maxConfirmations, int blockChainHeight) {
         int maxHeight = blockChainHeight - maxConfirmations + 1;
         Cursor cursor = null;
         List<TransactionEx> list = new LinkedList<>();
         try {
            // return all transaction younger than maxConfirmations or have no confirmations at all
            cursor = _db.rawQuery("SELECT id, height, time, binary FROM " + txTableName + " WHERE height >= ? OR height = -1 ",
                  new String[]{Integer.toString(maxHeight)});
            while (cursor.moveToNext()) {
               int height = cursor.getInt(1);
               if (height == Integer.MAX_VALUE) {
                  height = -1;
               }
               TransactionEx tex = new TransactionEx(new Sha256Hash(cursor.getBlob(0)), height, cursor.getInt(2),
                     cursor.getBlob(3));
               list.add(tex);
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public boolean putOutgoingTransaction(Sha256Hash txid, byte[] rawTransaction) {
         beginTransaction();
         try {
            _insertOrReplaceOutTx.bindBlob(1, txid.getBytes());
            _insertOrReplaceOutTx.bindBlob(2, rawTransaction);
            _insertOrReplaceOutTx.bindString(3, _id.toString());
            boolean result = _insertOrReplaceOutTx.executeInsert() != -1;

            putReferencedOutputs(rawTransaction);
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public Map<Sha256Hash, byte[]> getOutgoingTransactions() {
         Cursor cursor = null;
         HashMap<Sha256Hash, byte[]> list = new HashMap<>();
         try {
            cursor = _db.rawQuery("SELECT id, raw FROM " + outTxTableName, new String[]{});
            while (cursor.moveToNext()) {
               list.put(new Sha256Hash(cursor.getBlob(0)), cursor.getBlob(1));
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public boolean deleteOutgoingTransaction(Sha256Hash txid) {
         beginTransaction();
         try {
            _deleteOutTx.bindBlob(1, txid.getBytes());
            boolean result =_deleteOutTx.executeUpdateDelete() == 1;
            setTransactionSuccessful();
            return result;
         } catch(SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean isOutgoingTransaction(Sha256Hash txid) {
         Cursor cursor = null;
         try {
            SQLiteQueryWithBlobs blobQuery = new SQLiteQueryWithBlobs(_db);
            blobQuery.bindBlob(1, txid.getBytes());
            cursor = blobQuery.query(false, outTxTableName, new String[]{}, "id = ?", null, null, null, null,
                  null);
            return cursor.moveToNext();
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public List<TransactionEx> getTransactionHistory(int offset, int limit) {
         Cursor cursor = null;
         List<TransactionEx> list = new LinkedList<>();
         try {
            cursor = _db.rawQuery("SELECT id, height, time, binary, accountID FROM " + txTableName
                        + " WHERE accountID = \'" + _id.toString() + "\' ORDER BY height DESC LIMIT ? OFFSET ?",
                  new String[]{Integer.toString(limit), Integer.toString(offset)});
            while (cursor.moveToNext()) {
               TransactionEx tex = new TransactionEx(new Sha256Hash(cursor.getBlob(0)), cursor.getInt(1),
                     cursor.getInt(2), cursor.getBlob(3));
               Log.d(LOG_TAG, "getTransactionHistory => accountID = " + cursor.getString(4)
                   + ", tex = " + tex.toString());
               list.add(tex);
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public List<TransactionEx> getTransactionsSince(long since) {
         Cursor cursor = null;
         List<TransactionEx> list = new LinkedList<>();
         try {
            cursor = _db.rawQuery("SELECT id, height, time, binary FROM " + txTableName
                        + " WHERE time >= ?"
                        + " ORDER BY height DESC",
                  new String[]{Long.toString(since / 1000)});
            while (cursor.moveToNext()) {
               TransactionEx tex = new TransactionEx(new Sha256Hash(cursor.getBlob(0)), cursor.getInt(1),
                     cursor.getInt(2), cursor.getBlob(3));
               list.add(tex);
            }
            return list;
         } finally {
            if (cursor != null) {
               cursor.close();
            }
         }
      }

      @Override
      public boolean updateAccountContext(Bip44AccountContext context) {
         return updateBip44AccountContext(context);
      }

      @Override
      public Long getCreationTimeByAddress(Address address) {
         _getTimestampForAddress.bindString(1, address.toString());
         return _getTimestampForAddress.simpleQueryForLong();
      }

      @Override
      public boolean storeAddressCreationTime(Address address, long unixTimeSeconds) {
         beginTransaction();
         try {
            _insertOrReplaceAddressTimestamp.bindString(1, address.toString());
            _insertOrReplaceAddressTimestamp.bindLong(2, unixTimeSeconds);
            boolean result = _insertOrReplaceAddressTimestamp.executeInsert() != -1;
            setTransactionSuccessful();
            return result;
         } catch (SQLException e) {
            logException(e);
            return false;
         } finally {
            endTransaction();
         }
      }

      @Override
      public boolean updateAccountContext(SingleAddressAccountContext context) {
         return updateSingleAddressAccountContext(context);
      }
   }

   private class OpenHelper extends SQLiteOpenHelper {
      private static final String DATABASE_NAME = "walletbacking.db";
      private static final int DATABASE_VERSION = 5;

      OpenHelper(Context context) {
         super(context, DATABASE_NAME, null, DATABASE_VERSION);
      }

      @Override
      public void onCreate(SQLiteDatabase db) {
         db.execSQL("CREATE TABLE single (id TEXT PRIMARY KEY, address BLOB, addressstring TEXT, archived INTEGER, "
             + "blockheight INTEGER);");
         db.execSQL("CREATE TABLE bip44 (id TEXT PRIMARY KEY, accountIndex INTEGER, archived INTEGER, "
             + "blockheight INTEGER, lastExternalIndexWithActivity INTEGER, lastInternalIndexWithActivity INTEGER, "
             + "firstMonitoredInternalIndex INTEGER, lastDiscovery, accountType INTEGER, accountSubId INTEGER);");
         db.execSQL("CREATE TABLE kv (k BLOB NOT NULL, v BLOB, checksum BLOB, subId INTEGER NOT NULL, "
             + "PRIMARY KEY (k, subId) );");
         db.execSQL("CREATE TABLE addressTimestamp (address TEXT PRIMARY KEY, timestamp INTEGER DEFAULT 0);");

         db.execSQL("CREATE TABLE txo (outpoint BLOB PRIMARY KEY, height INTEGER, value INTEGER, " +
             "isCoinbase INTEGER, script BLOB, accountID TEXT, transactionType TEXT);");
         db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON txo (accountID);");
         db.execSQL("CREATE INDEX IF NOT EXISTS transactionTypeIndex ON txo (transactionType);");

         db.execSQL("CREATE TABLE tx  (id BLOB PRIMARY KEY, height INTEGER, time INTEGER, binary BLOB, accountID TEXT);");
         db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON tx (accountID);");
         db.execSQL("CREATE INDEX IF NOT EXISTS heightIndex ON tx (height);");

         db.execSQL("CREATE TABLE IF NOT EXISTS outtx (id BLOB PRIMARY KEY, raw BLOB, TEXT accountID);");
         db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON outtx (accountID);");

         db.execSQL("CREATE TABLE IF NOT EXISTS txtoptxo (txid BLOB, input BLOB, TEXT accountID, PRIMARY KEY (txid, input) );");
         db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON  txtoptxo (accountID);");

      }

      @Override
      public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
         if (oldVersion < 2) {
            db.execSQL("ALTER TABLE kv ADD COLUMN checksum BLOB");
         }
         if (oldVersion < 3) {
            // add column to the secure kv table to indicate sub-stores
            // use a temporary table to migrate the table, as sqlite does not allow to change primary keys constraints
            db.execSQL("CREATE TABLE kv_new (k BLOB NOT NULL, v BLOB, checksum BLOB, subId INTEGER NOT NULL, PRIMARY KEY (k, subId) );");
            db.execSQL("INSERT INTO kv_new SELECT k, v, checksum, 0 FROM kv");
            db.execSQL("ALTER TABLE kv RENAME TO kv_old");
            db.execSQL("ALTER TABLE kv_new RENAME TO kv");
            db.execSQL("DROP TABLE kv_old");

            // add column to store what account type it is
            db.execSQL("ALTER TABLE bip44 ADD COLUMN accountType INTEGER DEFAULT 0");
            db.execSQL("ALTER TABLE bip44 ADD COLUMN accountSubId INTEGER DEFAULT 0");
         }
         if (oldVersion < 4) {
            db.execSQL("CREATE TABLE IF NOT EXISTS addressTimestamp (address TEXT PRIMARY KEY, "
                + "timestamp INTEGER DEFAULT 0);");
         }
         if (oldVersion < 5) {
            //TODO in Progress Nelson
            List<UUID> accountIDs = getAccountIds(db);
            ListIterator<UUID> list = accountIDs.listIterator();
            String selectStatement = "";
            while (list.hasNext()) {
               UUID accountID  = list.next();
               String utxoTableName = getUtxoTableName(uuidToTableSuffix(accountID));
               selectStatement += "SELECT '" + accountID.toString() + "' AS accountID, "
                   + " 'utxo' AS transactionType, "
                   + utxoTableName + ".* FROM "
                   + utxoTableName;
               if(list.hasNext()) {
                  selectStatement += " UNION ";
               }
            }
            list = accountIDs.listIterator();
            if(list.hasNext()) {
               selectStatement += " UNION ";
            }
            while (list.hasNext()) {
               UUID accountID  = list.next();
               String ptxoTableName = getPtxoTableName(uuidToTableSuffix(accountID));
               selectStatement += "SELECT '" + accountID.toString() + "' AS accountID, "
                   + " 'ptxo' AS transactionType, "
                   + ptxoTableName + ".* FROM " + ptxoTableName;
               if(list.hasNext()) {
                  selectStatement += " UNION ";
               }
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS " + "txo"
                + " AS " + selectStatement);
            db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON " + "txo" + " (accountID);");
            db.execSQL("CREATE INDEX IF NOT EXISTS transactionTypeIndex ON txo (transactionType);");

            selectStatement = "";
            list = accountIDs.listIterator();
            while (list.hasNext()) {
               UUID accountID  = list.next();
               String txTableName = getTxTableName(uuidToTableSuffix(accountID));
               selectStatement += "SELECT '" + accountID.toString() + "' AS accountID, "
                   + txTableName + ".* FROM " + txTableName;
               if(list.hasNext()) {
                  selectStatement += " UNION ";
               }
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS tx AS " + selectStatement);
            db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON " + "tx" + " (accountID);");

            selectStatement = "";
            list = accountIDs.listIterator();
            while (list.hasNext()) {
               UUID accountID  = list.next();
               String tableName = getOutgoingTxTableName(uuidToTableSuffix(accountID));
               selectStatement += "SELECT '" + accountID.toString() + "' AS accountID, "
                   + tableName + ".* FROM " + tableName;
               if(list.hasNext()) {
                  selectStatement += " UNION ";
               }
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS outtx AS " + selectStatement);
            db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON outtx (accountID);");


            selectStatement = "";
            list = accountIDs.listIterator();
            while (list.hasNext()) {
               UUID accountID  = list.next();
               String tableName = getTxRefersPtxoTableName(uuidToTableSuffix(accountID));
               selectStatement += "SELECT '" + accountID.toString() + "' AS accountID, "
                   + tableName + ".* FROM " + tableName;
               if(list.hasNext()) {
                  selectStatement += " UNION ";
               }
            }
            db.execSQL("CREATE TABLE IF NOT EXISTS txtoptxo AS " + selectStatement);
            db.execSQL("CREATE INDEX IF NOT EXISTS accountIDIndex ON txtoptxo (accountID);");
         }
      }
   }
}
