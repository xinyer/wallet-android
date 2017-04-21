package com.mycelium.sporeui.view

import android.content.Context
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import butterknife.BindView
import butterknife.ButterKnife
import com.mycelium.sporeui.Layout
import com.mycelium.sporeui.R
import com.mycelium.sporeui.data.CurrencyValue
import com.mycelium.sporeui.di.DaggerService
import com.mycelium.sporeui.di.component.DaggerActivityComponent
import com.mycelium.sporeui.presenter.SendScreenPresenter
import com.mycelium.sporeui.presenter.ToolbarPresenter
import com.mycelium.sporeui.screen.SendScreen
import com.mycelium.sporeui.ui.ReceiverRecyclerViewAdapter
import com.mycelium.sporeui.ui.ReceiverRecyclerViewItem
import com.mycelium.sporeui.ui.SendRecyclerViewAdapter
import com.mycelium.sporeui.ui.SenderRecyclerViewItem
import com.pawegio.kandroid.find
import com.wefika.horizontalpicker.HorizontalPicker
import eu.davidea.flexibleadapter.FlexibleAdapter
import eu.davidea.flexibleadapter.SelectableAdapter
import flow.Flow
import java.util.*
import javax.inject.Inject
import kotlin.reflect.jvm.internal.impl.resolve.scopes.receivers.Receiver


@Layout(R.layout.screen_send)
class SendView constructor(context: Context = null!!, attrs: AttributeSet = null!!) : LinearLayout(context, attrs) {

    @Inject
    lateinit var sendScreenPresenter: SendScreenPresenter

    @Inject
    lateinit var toolbarPresenter: ToolbarPresenter

    //@Inject
    //lateinit var bitcoinService: BitcoinService

    //var walletManager : WalletManager

    private val marker = SendView::class.qualifiedName

    @BindView(R.id.sender_debitcard_choose_menu)
    lateinit var senderDebitcardChooseMenu: LinearLayout

    @BindView(R.id.sender_debitcard_add_menu)
    lateinit var senderDebitcardAddMenu: LinearLayout

    @BindView(R.id.sender_btc_accounts_menu)
    lateinit var senderBtcAccountsMenu: LinearLayout

    @BindView(R.id.sender_btc_accounts_add_menu)
    lateinit var senderBtcAccountsAddMenu: LinearLayout

    @BindView(R.id.receiver_btc_contacts_menu)
    lateinit var receiverBtcContactsMenu: LinearLayout

    @BindView(R.id.receiver_bankaccount_add_menu)
    lateinit var receiverBankaccountAddMenu: LinearLayout

    @BindView(R.id.receiver_btc_accounts_menu)
    lateinit var receiverBtcAccountsMenu: LinearLayout

    @BindView(R.id.receiver_btc_contacts_add_menu)
    lateinit var receiverBtcContactsAddMenu: LinearLayout

    @BindView(R.id.currency_edit_text)
    lateinit var currencyEditText: EditText

    @BindView(R.id.send_currency_symbol)
    lateinit var sendCurrencySymbol: TextView


    init {
        initializeInjection(context)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        sendScreenPresenter.takeView(this)
    }

    override fun onDetachedFromWindow() {
        sendScreenPresenter.dropView(this)
        super.onDetachedFromWindow()
    }


    var senderFirstItemWidth: Float = 0.toFloat()
    var senderPadding: Float = 0.toFloat()
    var senderItemWidth: Float = 0.toFloat()
    var senderAllPixels: Int = 0
    var senderFinalWidth: Int = 0

    val senderAccounts : MutableList<SenderRecyclerViewItem> = ArrayList()


    var receiverFirstItemWidth: Float = 0.toFloat()
    var receiverPadding: Float = 0.toFloat()
    var receiverItemWidth: Float = 0.toFloat()
    var receiverAllPixels: Int = 0
    var receiverFinalWidth: Int = 0

    val receiverAccounts : MutableList<ReceiverRecyclerViewItem> = ArrayList()


    override fun onFinishInflate() {
        super.onFinishInflate()
        ButterKnife.bind(this)
        val screen: SendScreen = Flow.getKey<SendScreen?>(this)!!
/*
        val scanButton = find<RoundButtonWithText>(R.id.send_qrcode_scan)
        scanButton.setOnClickListener {
            Flow.get(scanButton).set(ScanActivity())
        }
*/
        currencyEditText.setText(screen.currencyValue.value.toEngineeringString());
        sendCurrencySymbol.setText(screen.currencyValue.currencyCode)
        sendCurrencySymbol.setOnClickListener(
                object : View.OnClickListener {
                    override fun onClick(v: View?) {
                        sendScreenPresenter.changeCurrency(
                                SendScreenPresenter.CURRENCY.valueOf(sendCurrencySymbol.text.toString()),
                                currencyEditText.text.toString().toLong())
                    }

                }
        )

        val senderAccountsRecyclerView = findViewById(R.id.senderAccountsRecyclerView) as RecyclerView

        val senderVTO : ViewTreeObserver = senderAccountsRecyclerView.getViewTreeObserver();
        senderVTO.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener{
            override fun onPreDraw(): Boolean {
                senderAccountsRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this)
                senderFinalWidth = senderAccountsRecyclerView.getMeasuredWidth()
                senderItemWidth = resources.getDimension(R.dimen.item_dob_width)
                senderPadding = (senderFinalWidth - senderItemWidth) / 2
                senderFirstItemWidth = senderPadding
                senderAllPixels = 0

                val layoutManager = LinearLayoutManager(context)
                layoutManager.orientation = LinearLayoutManager.HORIZONTAL
                senderAccountsRecyclerView.setLayoutManager(layoutManager)
                senderAccountsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        synchronized(this) {
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                calculatePositionAndScroll(recyclerView as RecyclerView)
                            }
                        }
                    }

                    override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        senderAllPixels += dx
                    }
                })
                val myItems : List<SenderRecyclerViewItem> = sendScreenPresenter.getSenderAccountsList()
                senderAccounts.addAll(myItems)
                val senderAccountsRecyclerViewAdapter : SendRecyclerViewAdapter =
                        SendRecyclerViewAdapter(senderAccounts.toTypedArray(),
                                senderFirstItemWidth.toInt(),
                                object : SendRecyclerViewAdapter.ViewHolder.ViewHolderClickListener {
                                    override fun onClick(adapter : SendRecyclerViewAdapter, position: Int) {
                                        //adapter.setSelecteditem(position)
                                        if(position == adapter.getSelectedPosition()) {
                                            when (position) {
                                                0 -> openOrCloseMenu(senderBtcAccountsAddMenu, true)
                                                1 -> openOrCloseMenu(senderBtcAccountsMenu, true)
                                                2 -> openOrCloseMenu(senderDebitcardAddMenu, true)
                                                3 -> openOrCloseMenu(senderDebitcardChooseMenu, true)
                                                else -> {
                                                    Log.e(marker, "selected an item not hardcoded in the senderAccountsHorizontalPicker onItemClicked listener.")
                                                }
                                            }
                                        }
                                        adapter.notifyDataSetChanged()
                                    }
                                })
                senderAccountsRecyclerView.setAdapter(senderAccountsRecyclerViewAdapter)
                setSelectedItem(senderAccountsRecyclerView.adapter)
                return true
            }
        })
        senderAccountsRecyclerView.setHasFixedSize(true)


        /* --- Receiver RecyclerView --- */


        val receiverAccountsRecyclerView = findViewById(R.id.receiverAccountsRecyclerView) as RecyclerView

        val receiverVTO : ViewTreeObserver = receiverAccountsRecyclerView.getViewTreeObserver();
        receiverVTO.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener{
            override fun onPreDraw(): Boolean {
                receiverAccountsRecyclerView.getViewTreeObserver().removeOnPreDrawListener(this)
                receiverFinalWidth = receiverAccountsRecyclerView.getMeasuredWidth()
                receiverItemWidth = resources.getDimension(R.dimen.item_dob_width)
                receiverPadding = (receiverFinalWidth - receiverItemWidth) / 2
                receiverFirstItemWidth = receiverPadding
                receiverAllPixels = 0

                val layoutManager = LinearLayoutManager(context)
                layoutManager.orientation = LinearLayoutManager.HORIZONTAL
                receiverAccountsRecyclerView.setLayoutManager(layoutManager)
                receiverAccountsRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrollStateChanged(recyclerView: RecyclerView?, newState: Int) {
                        super.onScrollStateChanged(recyclerView, newState)
                        synchronized(this) {
                            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                                calculatePositionAndScroll(recyclerView as RecyclerView)
                            }
                        }
                    }

                    override fun onScrolled(recyclerView: RecyclerView?, dx: Int, dy: Int) {
                        super.onScrolled(recyclerView, dx, dy)
                        receiverAllPixels += dx
                    }
                })
                val myItems : List<ReceiverRecyclerViewItem> = sendScreenPresenter.getReceiverAccountsList()
                receiverAccounts.addAll(myItems)
                val receiverAccountsRecyclerViewAdapter : ReceiverRecyclerViewAdapter =
                        ReceiverRecyclerViewAdapter(receiverAccounts.toTypedArray(),
                                receiverFirstItemWidth.toInt(),
                                object : ReceiverRecyclerViewAdapter.ViewHolder.ViewHolderClickListener {
                                    override fun onClick(adapter : ReceiverRecyclerViewAdapter,
                                                         position: Int) {
                                        //adapter.setSelecteditem(position)
                                        if(position == adapter.getSelectedPosition()) {
                                            when (position) {
                                                1 -> openOrCloseMenu(receiverBtcAccountsMenu, true)
                                                2 -> openOrCloseMenu(receiverBtcContactsMenu, true)
                                                3 -> openOrCloseMenu(receiverBtcContactsAddMenu, true)
                                                4 -> openOrCloseMenu(receiverBankaccountAddMenu, true)
                                                else -> {
                                                    Log.e(marker, "selected an item not hardcoded in the receiverAccountsHorizontalPicker onItemClicked listener.")
                                                }
                                            }
                                        }
                                        adapter.notifyDataSetChanged()
                                    }
                                })
                receiverAccountsRecyclerView.setAdapter(receiverAccountsRecyclerViewAdapter)
                setSelectedItem(receiverAccountsRecyclerView.adapter)
                return true
            }
        })

        receiverAccountsRecyclerView.setHasFixedSize(true)
    }

    /* this if most important, if expectedPosition < 0 recyclerView will return to nearest item*/

    private fun calculatePositionAndScroll(recyclerView: RecyclerView) {
        if(recyclerView.adapter is SendRecyclerViewAdapter) {
            var expectedPosition =
                    Math.round(
                            (senderAllPixels + senderPadding -
                                    senderFirstItemWidth) / senderItemWidth)

            if (expectedPosition == -1) {
                expectedPosition = 0
            } else if (expectedPosition >= recyclerView.adapter.itemCount - 2) {
                expectedPosition--
            }
            scrollListToPosition(recyclerView, expectedPosition)
        } else if (recyclerView.adapter is ReceiverRecyclerViewAdapter) {
            var expectedPosition =
                    Math.round(
                            (receiverAllPixels + receiverPadding -
                                    receiverFirstItemWidth) / receiverItemWidth)

            if (expectedPosition == -1) {
                expectedPosition = 0
            } else if (expectedPosition >= recyclerView.adapter.itemCount - 2) {
                expectedPosition--
            }
            scrollListToPosition(recyclerView, expectedPosition)
        }
    }

    /* this if most important, if expectedPosition < 0 recyclerView will return to nearest item*/
    private fun scrollListToPosition(recyclerView: RecyclerView, expectedPosition: Int) {
        if(recyclerView.adapter is SendRecyclerViewAdapter) {
            val targetScrollPos =
                    expectedPosition * senderItemWidth + senderFirstItemWidth - senderPadding
            val missingPx = targetScrollPos - senderAllPixels
            if (missingPx != 0f) {
                recyclerView.smoothScrollBy(missingPx.toInt(), 0)
            }
            setSelectedItem(recyclerView.adapter)
        } else if (recyclerView.adapter is ReceiverRecyclerViewAdapter) {
            val targetScrollPos =
                    expectedPosition * receiverItemWidth + receiverFirstItemWidth - receiverPadding
            val missingPx = targetScrollPos - receiverAllPixels
            if (missingPx != 0f) {
                recyclerView.smoothScrollBy(missingPx.toInt(), 0)
            }
            setSelectedItem(recyclerView.adapter)
        }
    }

    private fun setSelectedItem(adapter: RecyclerView.Adapter<RecyclerView.ViewHolder>) {
        if(adapter is SendRecyclerViewAdapter) {
            val expectedPositionColor =
                    Math.round((senderAllPixels + senderPadding - senderFirstItemWidth)
                            / senderItemWidth)
            val setColor = expectedPositionColor + 1
            adapter.setSelecteditem(setColor)
        } else if (adapter is ReceiverRecyclerViewAdapter) {
            val expectedPositionColor =
                    Math.round((receiverAllPixels + receiverPadding - receiverFirstItemWidth)
                            / receiverItemWidth)
            val setColor = expectedPositionColor + 1
            adapter.setSelecteditem(setColor)
        }
    }

    fun setNewCurrencyValue(currencyValue: CurrencyValue) {
        currencyEditText.setText(currencyValue.value.toEngineeringString())
        sendCurrencySymbol.setText(currencyValue.currencyCode)
    }

    private fun initializeInjection(context: Context) {
        DaggerService.getDaggerComponent<DaggerActivityComponent>(context).inject(this)
    }

    private fun closeSenderMenus() {
        senderDebitcardChooseMenu.visibility = View.GONE
        find<LinearLayout>(R.id.sender_debitcard_add_menu).visibility = View.GONE
        find<LinearLayout>(R.id.sender_btc_accounts_menu).visibility = View.GONE
        find<LinearLayout>(R.id.sender_btc_accounts_add_menu).visibility = View.GONE
        showSendButton()
    }

    private fun closeReceiverMenus() {
        find<LinearLayout>(R.id.receiver_bankaccount_add_menu).visibility = View.GONE
        find<LinearLayout>(R.id.receiver_btc_accounts_menu).visibility = View.GONE
        find<LinearLayout>(R.id.receiver_btc_contacts_add_menu).visibility = View.GONE
        find<LinearLayout>(R.id.receiver_btc_contacts_menu).visibility = View.GONE
        showSendButton()
    }

    private fun openOrCloseMenu(menu : LinearLayout, sender : Boolean) {
        if(menu.visibility == View.VISIBLE) {
            menu.visibility = View.GONE
            showSendButton()
        } else {
            if(sender) {
                closeSenderMenus()
            } else {
                closeReceiverMenus()
            }
            menu.visibility = View.VISIBLE
            hideSendButton()
        }
    }

    private fun hideSendButton() {
        //sendButton.visibility = View.GONE
    }

    private fun showSendButton() {
        //sendButton.visibility = View.VISIBLE
    }
}