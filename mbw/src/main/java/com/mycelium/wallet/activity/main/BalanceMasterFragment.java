package com.mycelium.wallet.activity.main;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.mycelium.net.ServerEndpointType;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.event.SelectedAccountChanged;
import com.mycelium.wallet.event.TorStateChanged;
import com.squareup.otto.Subscribe;

public class BalanceMasterFragment extends Fragment {
    private TextView tvTor;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        View view = Preconditions.checkNotNull(inflater.inflate(R.layout.balance_master_fragment, container, false));
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.phFragmentAddress, new AddressFragment());
        fragmentTransaction.replace(R.id.phFragmentBalance, new BalanceFragment());
        fragmentTransaction.commitAllowingStateLoss();
        return view;
    }

    @Override
    public void onResume() {
        updateBuildText();
        Activity activity = getActivity();
        MbwManager mbwManager = MbwManager.getInstance(activity);
        tvTor = activity.findViewById(R.id.tvTorState);
        if (mbwManager.getTorMode() == ServerEndpointType.Types.ONLY_TOR && mbwManager.getTorManager() != null) {
            tvTor.setVisibility(View.VISIBLE);
            showTorState(mbwManager.getTorManager().getInitState());
        } else {
            tvTor.setVisibility(View.GONE);
        }
        updateAddressView();
        MbwManager.getInstance(this.getActivity()).getEventBus().register(this);
        super.onResume();
    }

    @Override
    public void onPause() {
        MbwManager.getInstance(this.getActivity()).getEventBus().unregister(this);
        super.onPause();
    }

    @Subscribe
    public void onTorState(TorStateChanged torState) {
        showTorState(torState.percentage);
    }

    @Subscribe
    public void selectedAccountChanged(SelectedAccountChanged event) {
        updateAddressView();
    }

    private void updateBuildText() {
        Activity activity = getActivity();
        PackageInfo pInfo;
        try {
            pInfo = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
            ((TextView) activity.findViewById(R.id.tvBuildText)).setText(getResources().getString(R.string.build_text,
                    pInfo.versionName));
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
    }

    private void updateAddressView() {
        FragmentTransaction fragmentTransaction = getFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.phFragmentAddress, new AddressFragment());
        fragmentTransaction.commitAllowingStateLoss();
    }

    private void showTorState(int percentage) {
        if (percentage == 0) {
            tvTor.setText("");
        } else if (percentage == 100) {
            tvTor.setText("");
        } else {
            tvTor.setText(getString(R.string.tor_state_init));
        }
    }

}
