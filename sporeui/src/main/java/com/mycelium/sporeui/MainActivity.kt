package com.mycelium.sporeui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.Menu
import android.view.MenuItem
import butterknife.BindView
import butterknife.ButterKnife
import com.mycelium.sporeui.di.DaggerService
import com.mycelium.sporeui.di.DaggerService.createComponent
import com.mycelium.sporeui.di.component.ActivityComponent
import com.mycelium.sporeui.di.component.DaggerActivityComponent
import com.mycelium.sporeui.di.module.ActivityModule
import com.mycelium.sporeui.presenter.ActionBarOwner
import flow.Flow
import flow.KeyDispatcher
import mortar.MortarScope
import mortar.MortarScope.buildChild
import mortar.MortarScope.findChild
import mortar.bundler.BundleServiceRunner
import javax.inject.Inject


class MainActivity : AppCompatActivity(), ActionBarOwner.Activity  {

    val SCANNER_RESULT_CODE = 0

    private var mortarScope: MortarScope? = null

    private var actionBarMenuActionList: List<ActionBarOwner.MenuAction>? = null

    @BindView(R.id.my_toolbar)
    lateinit var toolbar: Toolbar

    @Inject
    lateinit var actionBarOwner: ActionBarOwner


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        BundleServiceRunner.getBundleServiceRunner(this).onCreate(savedInstanceState);
        setContentView(R.layout.activity_main)
        initializeInjection(context)
        actionBarOwner.takeView(this)
        setSupportActionBar(toolbar)
        supportActionBar!!.hide()
    }

    private fun initializeInjection(context: Context) {
        DaggerService.getDaggerComponent<DaggerActivityComponent>(context).inject(this)
        ButterKnife.bind(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        BundleServiceRunner.getBundleServiceRunner(this).onSaveInstanceState(outState)
    }

    /**
     * Called when the main window associated with the activity has been
     * attached to the window manager.
     * See [View.onAttachedToWindow()][View.onAttachedToWindow]
     * for more information.
     * @see View.onAttachedToWindow
     */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        //actionBarOwner.takeView(this)
    }

    /**
     * Called when the main window associated with the activity has been
     * detached from the window manager.
     * See [View.onDetachedFromWindow()][View.onDetachedFromWindow]
     * for more information.
     * @see View.onDetachedFromWindow
     */
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        actionBarOwner.dropView(this)
    }

    override fun onDestroy() {
        actionBarOwner.dropView(this)
        if (isFinishing) {
            val activityScope = findChild(applicationContext, getScopeName())
            activityScope?.destroy()
        }

        super.onDestroy()
    }

    override fun attachBaseContext(newBase: Context?) {
        val baseContext = Flow.configure(newBase, this)
                .dispatcher(KeyDispatcher.configure(this, ScreenKeyChanger(this)).build())
                .defaultKey(MainScreen())
                .keyParceler(KeyParceler())
                .install()
        super.attachBaseContext(baseContext)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SCANNER_RESULT_CODE) {
            // TODO: Determine what to do with QR code data
            Flow.get(this).goBack()
        }
    }

    override fun onBackPressed() {
        supportActionBar!!.hide()
        if (!Flow.get(this).goBack()) {
            super.onBackPressed()
        }
    }

    override fun getSystemService(name: String): Any {
        var activityScope: MortarScope? = findChild(applicationContext, getScopeName())

        if (activityScope == null) {
            activityScope = buildChild(applicationContext) //
                    .withService(BundleServiceRunner.SERVICE_NAME, BundleServiceRunner())
                    .withService(DaggerService.SERVICE_NAME,
                            createComponent(ActivityComponent::class.java,
                                    MortarScope.getScope(applicationContext).getService(DaggerService.SERVICE_NAME),
                                    ActivityModule()))
                    .build(getScopeName())
        }

        return if (activityScope!!.hasService(name))
            activityScope.getService<Any>(name)
        else
            super.getSystemService(name)
    }

    private fun getScopeName(): String {
        return javaClass.name
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            supportActionBar!!.hide()
            return Flow.get(this).goBack()
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        if (actionBarMenuActionList != null && actionBarMenuActionList!!.isNotEmpty()) {
            for (menuAction in actionBarMenuActionList!!) {
                menu.add(menuAction.title)
                        .setIcon(menuAction.icon)
                        .setShowAsActionFlags(MenuItem.SHOW_AS_ACTION_ALWAYS or MenuItem.SHOW_AS_ACTION_WITH_TEXT)
                        .setOnMenuItemClickListener(MenuItem.OnMenuItemClickListener {
                            menuAction.action.call()
                            true
                        }).setEnabled(false)
            }
        }
        return true
    }

    override fun setMenu(menuActionList: MutableList<ActionBarOwner.MenuAction>?) {
        if (menuActionList !== actionBarMenuActionList) {
            actionBarMenuActionList = menuActionList
            invalidateOptionsMenu()
        }
    }

    override fun setToolbarTitle(title: CharSequence?) {
        supportActionBar!!.setDisplayShowTitleEnabled(true)
        supportActionBar!!.title = title
    }

    override fun setShowHomeEnabled(enabled: Boolean) {
        supportActionBar!!.setDisplayShowHomeEnabled(enabled)
    }

    override fun setUpButtonEnabled(enabled: Boolean) {
        supportActionBar!!.setDisplayHomeAsUpEnabled(enabled)
        supportActionBar!!.setHomeButtonEnabled(enabled)
    }

    override fun setVisibilityActionBar(enabled: Boolean) {
        if(enabled) {
            supportActionBar!!.show()
        } else {
            supportActionBar!!.hide()
        }
    }

    override fun getContext(): Context {
        return this
    }
}
