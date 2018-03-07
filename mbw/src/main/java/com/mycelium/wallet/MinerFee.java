package com.mycelium.wallet;

import android.content.Context;

import com.megiontechnologies.Bitcoins;
import com.mycelium.wapi.api.lib.FeeEstimation;

public enum MinerFee {
    LOWPRIO("LOWPRIO", 20, R.string.miner_fee_lowprio_name, R.string.miner_fee_lowprio_desc),
    ECONOMIC("ECONOMIC", 10, R.string.miner_fee_economic_name, R.string.miner_fee_economic_desc),
    NORMAL("NORMAL", 3, R.string.miner_fee_normal_name, R.string.miner_fee_normal_desc),
    PRIORITY("PRIORITY", 1, R.string.miner_fee_priority_name, R.string.miner_fee_priority_desc);

    public final String tag;
    private final int nBlocks;
    private final int idTag;
    private final int idLongDesc;

    MinerFee(String tag, int nBlocks, int idTag, int idLongDesc) {
        this.tag = tag;
        this.nBlocks = nBlocks;
        this.idTag = idTag;
        this.idLongDesc = idLongDesc;
    }

    @Override
    public String toString() {
        return tag;
    }

    public static MinerFee fromString(String string) {
        for (MinerFee fee : values()) {
            if (fee.tag.equals(string)) {
                return fee;
            }
        }
        return NORMAL;
    }

    //simply returns the next fee in order of declaration, starts with the first after reaching the last
    //useful for cycling through them in sending for example
    public MinerFee getNext() {
        return values()[(ordinal() + 1) % values().length];
    }

    public MinerFee getPrevious() {
        return values()[(ordinal() - 1 + values().length) % values().length];
    }

    public Bitcoins getFeePerKb(FeeEstimation feeEstimation) {
        return feeEstimation.getEstimation(nBlocks);
    }

    public String getMinerFeeName(Context context) {
        return context.getString(idTag);
    }

    public String getMinerFeeDescription(Context context) {
        return context.getString(idLongDesc);
    }

    public int getNBlocks() {
        return nBlocks;
    }

}
