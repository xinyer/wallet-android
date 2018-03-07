package com.mycelium.wallet.environment;

import android.content.Context;

import com.mrd.bitlib.model.NetworkParameters;
import com.mycelium.net.ServerEndpoints;
import com.mycelium.wallet.R;


public abstract class MbwEnvironment {

   private String _brand;

   public static MbwEnvironment verifyEnvironment(Context applicationContext) {
      // Set up environment
      String network = applicationContext.getResources().getString(R.string.network);
      String brand = applicationContext.getResources().getString(R.string.brand);
      if(brand.equals("undefined")){
         throw new RuntimeException("No brand has been specified");
      }
      // todo proper IoC needed. it is not nice to refer to subclasses
      if (network.equals("prodnet")) {
         return new MbwProdEnvironment(brand);
      } else if (network.equals("testnet")) {
         return new MbwTestEnvironment(brand);
      } else if (network.equals("regtest")) {
         return new MbwRegTestEnvironment(brand);
      } else {
         throw new RuntimeException("No network has been specified");
      }
   }

   public MbwEnvironment(String brand) {
      _brand = brand;
   }

   public String getBrand() {
      return _brand;
   }

   public abstract NetworkParameters getNetwork();
   public abstract ServerEndpoints getLtEndpoints();
   public abstract ServerEndpoints getWapiEndpoints();

}
