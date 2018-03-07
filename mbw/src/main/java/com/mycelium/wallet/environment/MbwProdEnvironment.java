package com.mycelium.wallet.environment;


import com.mrd.bitlib.model.NetworkParameters;
import com.mycelium.net.HttpEndpoint;
import com.mycelium.net.HttpsEndpoint;
import com.mycelium.net.ServerEndpoints;
import com.mycelium.net.TorHttpsEndpoint;


public class MbwProdEnvironment extends MbwEnvironment {
   /**
    * The legacy thumbprint of the Mycelium certificate.
    * We used one cert for pinning the server certificate - now we use different certs per endpoint.
    * For access via direct IP we still use the legacy cert to allow non updated wallets to continue
    * to work
    */
   private static final String myceliumLegacyThumbprint = "B3:42:65:33:40:F5:B9:1B:DA:A2:C8:7A:F5:4C:7C:5D:A9:63:C4:C3";


   public MbwProdEnvironment(String brand) {
      super(brand);
   }

   @Override
   public NetworkParameters getNetwork() {
      return NetworkParameters.productionNetwork;
   }

   /**
    * Local Trader API for prodnet
    */
   private static final ServerEndpoints prodnetLtEndpoints = new ServerEndpoints(new HttpEndpoint[]{
           new HttpsEndpoint("https://lt20.mycelium.com/ltprodnet", "EB:4C:27:A5:A3:8B:DF:E1:34:60:0A:97:57:3F:FA:FF:43:E0:EA:67"),
           new HttpsEndpoint("https://lt10.mycelium.com/ltprodnet", "9E:90:62:24:F7:71:83:FB:B6:B1:D6:4D:C2:78:4A:5D:29:3F:B5:BB"),

           new HttpsEndpoint("https://188.40.73.130/ltprodnet", myceliumLegacyThumbprint), // lt2
           new HttpsEndpoint("https://46.4.101.162/ltprodnet", myceliumLegacyThumbprint), // lt1

           new TorHttpsEndpoint("https://7c7yicf4e3brohwi.onion/ltprodnet", "4E:EE:C3:53:74:92:19:E6:37:EB:1A:2D:E8:21:9B:28:E8:8B:54:6C"),
           new TorHttpsEndpoint("https://az5zxxebeule5hmn.onion/ltprodnet", "62:D7:E2:92:A7:B9:7E:75:C7:B5:34:1E:ED:DB:DC:45:95:70:A0:9E"),
           new TorHttpsEndpoint("https://lodffvexeb72vf2f.onion/ltprodnet", "07:F1:79:DB:52:68:C8:B0:63:05:E8:87:64:D7:1B:57:53:4F:3E:D1"),
           new TorHttpsEndpoint("https://wmywc6g3mknihpq2.onion/ltprodnet", "D2:97:9A:9F:EB:F7:08:D1:89:1B:FC:B5:83:55:BE:1E:6D:B1:AE:E3"),

   }, 0);

   @Override
   public ServerEndpoints getLtEndpoints() {
      return prodnetLtEndpoints;
   }

   /**
    * Wapi
    */
   private static final HttpEndpoint[] prodnetWapiEndpoints = new HttpEndpoint[]{
           // mws 2,6,7,8
           new HttpsEndpoint("https://mws20.mycelium.com/wapi", "65:1B:FF:6B:8C:7F:C8:1C:8E:14:77:1E:74:9C:F7:E5:46:42:BA:E0"),
           new HttpsEndpoint("https://mws60.mycelium.com/wapi", "47:F1:F1:21:F3:90:39:05:D7:21:B6:1B:EB:79:B1:40:44:A1:6F:46"),
           new HttpsEndpoint("https://mws70.mycelium.com/wapi", "9E:90:62:24:F7:71:83:FB:B6:B1:D6:4D:C2:78:4A:5D:29:3F:B5:BB"),
           new HttpsEndpoint("https://mws80.mycelium.com/wapi", "EB:4C:27:A5:A3:8B:DF:E1:34:60:0A:97:57:3F:FA:FF:43:E0:EA:67"),


           // Also try to connect to the nodes via a hardcoded IP, in case the DNS has some problems
           new HttpsEndpoint("https://138.201.206.35/wapi", myceliumLegacyThumbprint),   // mws2
           new HttpsEndpoint("https://46.4.101.162/wapi", myceliumLegacyThumbprint),  // mws6
           new HttpsEndpoint("https://46.4.3.125/wapi", myceliumLegacyThumbprint),     // mws7
           new HttpsEndpoint("https://188.40.73.130/wapi", myceliumLegacyThumbprint),     // mws8

           // tor hidden services
           new TorHttpsEndpoint("https://n76y5k3le2zi73bw.onion/wapi", "8D:47:91:A1:EA:9B:CE:E5:A1:9E:38:5B:74:A7:45:0C:88:8F:57:E8"),
           new TorHttpsEndpoint("https://vtuao7psnrsot4tb.onion/wapi", "C5:09:C8:37:84:53:65:EE:8E:22:89:32:8F:86:70:49:AD:0A:53:4D"),
           new TorHttpsEndpoint("https://rztvro6qgydmujfv.onion/wapi", "A4:09:BC:3A:0E:2D:FE:BF:05:FB:9C:65:DC:82:EA:CF:5D:EE:4D:76"),
           new TorHttpsEndpoint("https://slacef5ylu6op7zc.onion/wapi", "EF:62:09:DE:A7:68:15:90:32:93:00:0A:4E:87:05:63:39:B5:87:85"),

   };

   private static final ServerEndpoints prodnetWapiServerEndpoints = new ServerEndpoints(prodnetWapiEndpoints);

   @Override
   public ServerEndpoints getWapiEndpoints() {
      return prodnetWapiServerEndpoints;
   }


}
