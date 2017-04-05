/*
 * Copyright 2013, 2014 Megion Research & Development GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mycelium.wapi.wallet;

import com.mrd.bitlib.model.Address;
import com.mycelium.wapi.wallet.bip44.Bip44AccountContext;
import com.mycelium.wapi.wallet.single.SingleAddressAccountContext;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface WalletManagerBacking extends SecureKeyValueStoreBacking {
   void beginTransaction();

   void setTransactionSuccessful();

   void endTransaction();

   boolean createBip44AccountContext(Bip44AccountContext context);

   List<Bip44AccountContext> loadBip44AccountContexts();

   Bip44AccountBacking getBip44AccountBacking(UUID accountId);

   boolean deleteBip44AccountContext(UUID accountId);

   boolean createSingleAddressAccountContext(SingleAddressAccountContext context);

   List<SingleAddressAccountContext> loadSingleAddressAccountContexts();

   SingleAddressAccountBacking getSingleAddressAccountBacking(UUID accountId);

   boolean deleteSingleAddressAccountContext(UUID accountId);

   Map<Address,Long> getAllAddressCreationTimes();

   Long getCreationTimeByAddress(Address address);

   boolean storeAddressCreationTime(Address address, long unixTimeSeconds);
}