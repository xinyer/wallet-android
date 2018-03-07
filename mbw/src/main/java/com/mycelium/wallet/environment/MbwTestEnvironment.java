package com.mycelium.wallet.environment;

import com.mrd.bitlib.model.NetworkParameters;
import com.mycelium.net.HttpEndpoint;
import com.mycelium.net.HttpsEndpoint;
import com.mycelium.net.ServerEndpoints;
import com.mycelium.net.TorHttpsEndpoint;


public class MbwTestEnvironment extends MbwEnvironment {
    public MbwTestEnvironment(String brand) {
        super(brand);
    }

    @Override
    public NetworkParameters getNetwork() {
        return NetworkParameters.testNetwork;
    }


    /**
     * Local Trader API for testnet
     */
    private static final ServerEndpoints testnetLtEndpoints = new ServerEndpoints(new HttpEndpoint[]{
            new HttpsEndpoint("https://mws30.mycelium.com/lttestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB"),
            new TorHttpsEndpoint("https://grrhi6bwwpiarsfl.onion/lttestnet", "D0:09:70:40:98:71:E0:0E:62:08:1A:36:4C:BC:C7:2E:51:40:50:4C"),
    });

    @Override
    public ServerEndpoints getLtEndpoints() {
        return testnetLtEndpoints;
    }


    /**
     * Wapi
     */
    private static final ServerEndpoints testnetWapiEndpoints = new ServerEndpoints(new HttpEndpoint[]{
            new HttpsEndpoint("https://mws30.mycelium.com/wapitestnet", "ED:C2:82:16:65:8C:4E:E1:C7:F6:A2:2B:15:EC:30:F9:CD:48:F8:DB"),
            new TorHttpsEndpoint("https://ti4v3ipng2pqutby.onion/wapitestnet", "75:3E:8A:87:FA:95:9F:C6:1A:DB:2A:09:43:CE:52:74:27:B1:80:4B"),
    });

    @Override
    public ServerEndpoints getWapiEndpoints() {
        return testnetWapiEndpoints;
    }

}
