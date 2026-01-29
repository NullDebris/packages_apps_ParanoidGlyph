package co.aospa.glyph.Services;

import android.telephony.TelephonyCallback;

final class CallStateCallback extends TelephonyCallback
        implements TelephonyCallback.CallStateListener {

    private final int mSubId;
    private final int mSlotIndex;
    private final OnIncomingCallListener mListener;

    CallStateCallback(
            int subId,
            int slotIndex,
            OnIncomingCallListener listener) {

        mSubId = subId;
        mSlotIndex = slotIndex;
        mListener = listener;
    }

    @Override
    public void onCallStateChanged(int state) {
        if (mListener != null) {
            mListener.onIncomingCall(mSlotIndex, mSubId, state);
        }
    }
}

