package com.mycelium.wallet.util;

import android.content.Context;

import com.google.common.base.Optional;
import com.mycelium.wallet.widget.EnterTextDialog;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.event.AccountChanged;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.squareup.otto.Bus;

import java.util.UUID;

public class EnterLabelUtil {

   public static void enterAccountLabel(Context context, UUID account, String defaultName, MetadataStorage storage) {
      String hintText = context.getResources().getString(R.string.name);
      String currentName = storage.getLabelByAccount(account);
      int title_id;
      if (currentName.length() == 0) {
         title_id = R.string.enter_account_label_title;
         currentName = defaultName;
      } else {
         title_id = R.string.edit_account_label_title;
      }
      String invalidOkToastMessage = context.getResources().getString(R.string.account_label_not_unique);
      Bus bus = MbwManager.getInstance(context).getEventBus();
      EnterAccountLabelHandler handler = new EnterAccountLabelHandler(account, invalidOkToastMessage, storage, bus);
      EnterTextDialog.show(context, title_id, hintText, currentName, true, handler);

   }

   private static class EnterAccountLabelHandler extends EnterTextDialog.EnterTextHandler {

      private UUID account;
      private String invalidOkToastMessage;
      private MetadataStorage storage;
      private Bus bus;

      public EnterAccountLabelHandler(UUID account, String invalidOkToastMessage, MetadataStorage storage, Bus bus) {
         this.account = account;
         this.invalidOkToastMessage = invalidOkToastMessage;
         this.storage = storage;
         this.bus = bus;
      }

      @Override
      public boolean validateTextOnChange(String newText, String oldText) {
         return true;
      }

      @Override
      public boolean validateTextOnOk(String newText, String oldText) {
         Optional<UUID> existing = storage.getAccountByLabel(newText);
         return !existing.isPresent() || existing.get().equals(account);
      }

      @Override
      public boolean getVibrateOnInvalidOk(String newText, String oldText) {
         return true;
      }

      @Override
      public String getToastTextOnInvalidOk(String newText, String oldText) {
         return invalidOkToastMessage;
      }

      @Override
      public void onNameEntered(String newText, String oldText) {
         storage.storeAccountLabel(account, newText);
         bus.post(new AccountChanged(account));
      }
   }

}
