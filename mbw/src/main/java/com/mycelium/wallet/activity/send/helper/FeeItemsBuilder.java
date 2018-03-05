package com.mycelium.wallet.activity.send.helper;

import android.support.annotation.NonNull;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.MinerFee;
import com.mycelium.wallet.activity.send.adapter.FeeViewAdapter;
import com.mycelium.wallet.activity.send.model.FeeItem;
import com.mycelium.wapi.api.lib.FeeEstimation;
import com.mycelium.wapi.wallet.currency.CurrencyValue;
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue;

import java.util.ArrayList;
import java.util.List;

/**
 * se have 4 dynamic values from server LOWPRIO, ECO, NORMAL, PRIO
 * FeeItemsBuilder divide values  MIN_NON_ZIRO_FEE_PER_KB..LOWPRIO..ECO..NORMAL..PRIO..1.5*PRIO
 * on 8 part,
 * LOWPRIO tab
 * lower part - MIN_NON_ZIRO_FEE_PER_KB..LOWPRIO
 * upper part - LOWPRIO..(LOWPRIO+ECO)/2
 * ECO tab
 * lower part - (LOWPRIO+ECO)/2..ECO
 * upper part - ECO..(ECO+NORMAL)/2
 * NORMAL tab
 * lower part - (ECO+NORMAL)/2..NORMAL
 * uppper part - NORMAL..(NORMAL+PRIO)/2
 * PRIO
 * lower part - (NORMAL+PRIO)/2..PRIO
 * upper part - PRIO..(1.5*PRIO)
 */
public class FeeItemsBuilder {
    private static final int MIN_NON_ZIRO_FEE_PER_KB = 3000;

    private MbwManager _mbwManager;
    private FeeEstimation feeEstimation;

    public FeeItemsBuilder(MbwManager _mbwManager) {
        this._mbwManager = _mbwManager;
        this.feeEstimation = _mbwManager.getWalletManager().getLastFeeEstimations();
    }

    public List<FeeItem> getFeeItemList(MinerFee feeLvl, int txSize) {
        long min = MIN_NON_ZIRO_FEE_PER_KB;
        long current = feeLvl.getFeePerKb(feeEstimation).getLongValue();

        if (feeLvl != MinerFee.LOWPRIO) {
            long prevValue = feeLvl.getPrevious().getFeePerKb(feeEstimation).getLongValue();
            min = (current + prevValue) / 2;
        }
        long max = 3 * MinerFee.PRIORITY.getFeePerKb(feeEstimation).getLongValue() / 2;
        if (feeLvl != MinerFee.PRIORITY) {
            max = (feeLvl.getNext().getFeePerKb(feeEstimation).getLongValue() + current) / 2;
        }

        FeeItemsAlgorithm algorithmLower = new LinearAlgorithm(min, 0, current, 4);
        FeeItemsAlgorithm algorithmUpper = new LinearAlgorithm(current, 4, max, 9);
        if (feeLvl == MinerFee.LOWPRIO) {
            algorithmLower = new ExponentialLowPrioAlgorithm(min, current);
            algorithmUpper = new LinearAlgorithm(current, algorithmLower.getMaxPosition()
                    , max, algorithmLower.getMaxPosition() + 3);
        }

        List<FeeItem> feeItems = new ArrayList<>();
        feeItems.add(new FeeItem(0, null, null, FeeViewAdapter.VIEW_TYPE_PADDING));
        addItemsInRange(feeItems, algorithmLower, txSize);
        addItemsInRange(feeItems, algorithmUpper, txSize);
        feeItems.add(new FeeItem(0, null, null, FeeViewAdapter.VIEW_TYPE_PADDING));

        return feeItems;
    }

    private void addItemsInRange(List<FeeItem> feeItems, FeeItemsAlgorithm algorithm, int txSize) {
        for (int i = algorithm.getMinPosition(); i < algorithm.getMaxPosition(); i++) {
            FeeItem feeItem = createFeeItem(txSize, algorithm.computeValue(i));
            if (feeItems.size() == 0 || feeItems.get(feeItems.size() - 1).feePerKb < feeItem.feePerKb) { // avoid duplication
                feeItems.add(feeItem);
            }
        }
    }

    @NonNull
    private FeeItem createFeeItem(int txSize, long feePerKb) {
        ExactBitcoinValue bitcoinValue = ExactBitcoinValue.from(txSize * feePerKb / 1000);
        CurrencyValue fiatFee = CurrencyValue.fromValue(bitcoinValue,
                _mbwManager.getFiatCurrency(), _mbwManager.getExchangeRateManager());
        return new FeeItem(feePerKb, bitcoinValue.getAsBitcoin(), fiatFee, FeeViewAdapter.VIEW_TYPE_ITEM);
    }
}
