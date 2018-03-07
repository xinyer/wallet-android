package com.mycelium.wallet.activity.balance;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.HdDerivedAddress;
import com.mycelium.wallet.common.BitcoinUriWithAddress;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.widget.QrImageView;
import com.mycelium.wallet.event.AccountChanged;
import com.mycelium.wallet.event.BalanceChanged;
import com.mycelium.wallet.event.ReceivingAddressChanged;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

public class AddressFragment extends Fragment {

    private View root;
    private MbwManager mbwManager;
    private boolean showBip44Path;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root = Preconditions.checkNotNull(inflater.inflate(R.layout.address_view, container, false));
        QrImageView qrButton = (QrImageView) Preconditions.checkNotNull(root.findViewById(R.id.ivQR));
        qrButton.setTapToCycleBrightness(false);
        return root;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        mbwManager = MbwManager.getInstance(getActivity());
        showBip44Path = mbwManager.getMetadataStorage().getShowBip44Path();
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onResume() {
        getEventBus().register(this);
        updateUi();
        super.onResume();
    }

    @Override
    public void onPause() {
        getEventBus().unregister(this);
        super.onPause();
    }

    private Bus getEventBus() {
        return mbwManager.getEventBus();
    }

    private void updateUi() {
        if (!isAdded()) {
            return;
        }
        if (mbwManager.getSelectedAccount().isArchived()) {
            return;
        }

        // Update QR code
        QrImageView qrButton = (QrImageView) Preconditions.checkNotNull(root.findViewById(R.id.ivQR));

        Optional<Address> receivingAddress = getAddress();

        // Update address
        if (receivingAddress.isPresent()) {
            // Set address
            qrButton.setVisibility(View.VISIBLE);
            String address = BitcoinUriWithAddress.fromAddress(receivingAddress.get()).toString();
            qrButton.setQrCode(address);
            ((TextView) root.findViewById(R.id.tvAddress)).setText(address);
            if (showBip44Path && receivingAddress.get() instanceof HdDerivedAddress) {
                HdDerivedAddress hdAdr = (HdDerivedAddress) receivingAddress.get();
                ((TextView) root.findViewById(R.id.tvAddressPath)).setText(hdAdr.getBip32Path().toString());
            } else {
                ((TextView) root.findViewById(R.id.tvAddressPath)).setText("");
            }
        } else {
            // No address available
            qrButton.setVisibility(View.INVISIBLE);
            ((TextView) root.findViewById(R.id.tvAddress)).setText("");
            ((TextView) root.findViewById(R.id.tvAddressPath)).setText("");
        }

        // Show name of bitcoin address according to address book
        TextView tvAddressTitle = root.findViewById(R.id.tvAddressLabel);

        String name = mbwManager.getMetadataStorage().getLabelByAccount(mbwManager.getSelectedAccount().getId());
        if (name.length() == 0) {
            tvAddressTitle.setVisibility(View.GONE);
        } else {
            tvAddressTitle.setVisibility(View.VISIBLE);
            tvAddressTitle.setText(name);
        }

    }

    public Optional<Address> getAddress() {
        return mbwManager.getSelectedAccount().getReceivingAddress();
    }

    /**
     * We got a new Receiving Address, either because the selected Account changed,
     * or because our HD Account received Coins and changed the Address
     */
    @Subscribe
    public void receivingAddressChanged(ReceivingAddressChanged event) {
        updateUi();
    }

    @Subscribe
    public void accountChanged(AccountChanged event) {
        updateUi();
    }

    @Subscribe
    public void balanceChanged(BalanceChanged event) {
        updateUi();
    }

}
