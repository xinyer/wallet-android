package com.mycelium.wallet.environment;

import com.mrd.bitlib.model.NetworkParameters;
import com.mycelium.net.HttpEndpoint;
import com.mycelium.net.HttpsEndpoint;
import com.mycelium.net.ServerEndpoints;


public class MbwRegTestEnvironment extends MbwEnvironment {
    private static final String myceliumThumbprint = "9c:8e:d7:ad:6c:28:db:d4:72:6a:71:93:d6:4d:cb:e7:c7:a0:2e:bc";

    public MbwRegTestEnvironment(String brand) {
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
            new HttpsEndpoint("https://localhost:4433/lttestnet", myceliumThumbprint),
    });

    @Override
    public ServerEndpoints getLtEndpoints() {
        return testnetLtEndpoints;
    }


    /**
     * Wapi
     */
    // run `adb reverse tcp:4433 tcp:4433` on your machine running vagrant
    private static final ServerEndpoints testnetWapiEndpoints = new ServerEndpoints(new HttpEndpoint[]{
            new HttpsEndpoint("https://localhost:4433/wapitestnet", myceliumThumbprint),
    });


    @Override
    public ServerEndpoints getWapiEndpoints() {
        return testnetWapiEndpoints;
    }

}
