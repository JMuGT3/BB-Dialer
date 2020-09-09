package com.simplemobiletools.dialer.activities

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.SearchManager
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.graphics.drawable.LayerDrawable
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.*
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuItemCompat
import androidx.viewpager.widget.ViewPager
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.dialer.BuildConfig
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.adapters.ViewPagerAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.fragments.MyViewPagerFragment
import com.simplemobiletools.dialer.helpers.tabsList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_contacts.*
import kotlinx.android.synthetic.main.fragment_favorites.*
import kotlinx.android.synthetic.main.fragment_letters_layout.*
import kotlinx.android.synthetic.main.fragment_recents.*
import java.util.*

class MainActivity : SimpleActivity(), View.OnGenericMotionListener {
    private var storedTextColor = 0
    private var storedPrimaryColor = 0
    private var isFirstResume = true
    private var isSearchOpen = false
    private var searchMenuItem: MenuItem? = null
    private var lastX = 0f
    private var lastY = 0f
    private var horizontalThreshold = 200
    private var verticalThreshold = 50
    private var lastKeyCode = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        appLaunched(BuildConfig.APPLICATION_ID)
        setupTabColors()
        storeStateVariables()


        /*if (isDefaultDialer()) {
            checkContactPermissions()
        } else {
            launchSetDefaultDialerIntent()
        }*/
        baselayout.setOnGenericMotionListener(this)
        checkContactPermissions()
        initFragments()
    }

    override fun onResume() {
        super.onResume()
        val dialpadIcon = resources.getColoredDrawableWithColor(R.drawable.ic_dialpad_vector, getFABIconColor())
        main_dialpad_button.apply {
            setImageDrawable(dialpadIcon)
            background.applyColorFilter(getAdjustedPrimaryColor())
        }

        main_tabs_holder.setBackgroundColor(config.backgroundColor)

        val configTextColor = config.textColor
        if (storedTextColor != configTextColor) {
            getInactiveTabIndexes(viewpager.currentItem).forEach {
                main_tabs_holder.getTabAt(it)?.icon?.applyColorFilter(configTextColor)
            }

            getAllFragments().forEach {
                it?.textColorChanged(configTextColor)
            }
        }

        val configPrimaryColor = config.primaryColor
        if (storedPrimaryColor != configPrimaryColor) {
            main_tabs_holder.setSelectedTabIndicatorColor(getAdjustedPrimaryColor())
            main_tabs_holder.getTabAt(viewpager.currentItem)?.icon?.applyColorFilter(getAdjustedPrimaryColor())
            getAllFragments().forEach {
                it?.primaryColorChanged(configPrimaryColor)
            }
        }

        if (!isFirstResume && !isSearchOpen) {
            refreshItems()
        }

        checkShortcuts()
        isFirstResume = false
    }

    override fun onPause() {
        super.onPause()
        storeStateVariables()
    }

    override fun onDestroy() {
        super.onDestroy()
        config.lastUsedViewPagerPage = viewpager.currentItem
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.search).isVisible = viewpager.currentItem == 0

        setupSearch(menu)
        updateMenuItemColors(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.settings -> startActivity(Intent(applicationContext, SettingsActivity::class.java))
            R.id.about -> launchAbout()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, resultData: Intent?) {
        super.onActivityResult(requestCode, resultCode, resultData)
        // we dont really care about the result, the app can work without being the default Dialer too
        if (requestCode == REQUEST_CODE_SET_DEFAULT_DIALER) {
            checkContactPermissions()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        refreshItems()
    }

    private fun storeStateVariables() {
        config.apply {
            storedTextColor = textColor
            storedPrimaryColor = primaryColor
        }
    }

    private fun checkContactPermissions() {
        handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                handlePermission(PERMISSION_GET_ACCOUNTS) {
                    initFragments()
                }
            } else {
                initFragments()
            }
        }
    }

    private fun setupSearch(menu: Menu) {
        val searchManager = getSystemService(Context.SEARCH_SERVICE) as SearchManager
        searchMenuItem = menu.findItem(R.id.search)
        (searchMenuItem!!.actionView as SearchView).apply {
            setSearchableInfo(searchManager.getSearchableInfo(componentName))
            isSubmitButtonEnabled = false
            queryHint = getString(R.string.search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String): Boolean {
                    when(viewpager.currentItem) {
                        0 -> contacts_fragment.clickItem(0)
                        1 -> favorites_fragment.clickItem(0)
                        2 -> recents_fragment.clickItem(0)
                    }
                    return true
                }

                override fun onQueryTextChange(newText: String): Boolean {
                    if (isSearchOpen) {
                        contacts_fragment?.onSearchQueryChanged(newText)
                    }
                    return true
                }
            })
        }

        MenuItemCompat.setOnActionExpandListener(searchMenuItem, object : MenuItemCompat.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem?): Boolean {
                isSearchOpen = true
                main_dialpad_button.beGone()
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem?): Boolean {
                contacts_fragment?.onSearchClosed()
                isSearchOpen = false
                main_dialpad_button.beVisible()
                return true
            }
        })
    }

    @SuppressLint("NewApi")
    private fun checkShortcuts() {
        val appIconColor = config.appIconColor
        if (isNougatMR1Plus() && config.lastHandledShortcutColor != appIconColor) {
            val launchDialpad = getLaunchDialpadShortcut(appIconColor)

            try {
                shortcutManager.dynamicShortcuts = listOf(launchDialpad)
                config.lastHandledShortcutColor = appIconColor
            } catch (ignored: Exception) {
            }
        }
    }

    @SuppressLint("NewApi")
    private fun getLaunchDialpadShortcut(appIconColor: Int): ShortcutInfo {
        val newEvent = getString(R.string.dialpad)
        val drawable = resources.getDrawable(R.drawable.shortcut_dialpad)
        (drawable as LayerDrawable).findDrawableByLayerId(R.id.shortcut_dialpad_background).applyColorFilter(appIconColor)
        val bmp = drawable.convertToBitmap()

        val intent = Intent(this, DialpadActivity::class.java)
        intent.action = Intent.ACTION_VIEW
        return ShortcutInfo.Builder(this, "launch_dialpad")
            .setShortLabel(newEvent)
            .setLongLabel(newEvent)
            .setIcon(Icon.createWithBitmap(bmp))
            .setIntent(intent)
            .build()
    }

    private fun setupTabColors() {
        val lastUsedPage = getDefaultTab()
        main_tabs_holder.apply {
            background = ColorDrawable(config.backgroundColor)
            setSelectedTabIndicatorColor(getAdjustedPrimaryColor())
            getTabAt(lastUsedPage)?.select()
            getTabAt(lastUsedPage)?.icon?.applyColorFilter(getAdjustedPrimaryColor())

            getInactiveTabIndexes(lastUsedPage).forEach {
                getTabAt(it)?.icon?.applyColorFilter(config.textColor)
            }
        }
    }

    private fun getInactiveTabIndexes(activeIndex: Int) = (0 until tabsList.size).filter { it != activeIndex }

    private fun initFragments() {
        viewpager.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageScrollStateChanged(state: Int) {
                searchMenuItem?.collapseActionView()
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) {}

            override fun onPageSelected(position: Int) {
                main_tabs_holder.getTabAt(position)?.select()
                getAllFragments().forEach {
                    it?.finishActMode()
                }
                invalidateOptionsMenu()
            }
        })

        main_tabs_holder.onTabSelectionChanged(
            tabUnselectedAction = {
                it.icon?.applyColorFilter(config.textColor)
            },
            tabSelectedAction = {
                viewpager.currentItem = it.position
                it.icon?.applyColorFilter(getAdjustedPrimaryColor())
            }
        )

        main_tabs_holder.removeAllTabs()
        tabsList.forEachIndexed { index, value ->
            val tab = main_tabs_holder.newTab().setIcon(getTabIcon(index))
            main_tabs_holder.addTab(tab, index, getDefaultTab() == index)
        }

        // selecting the proper tab sometimes glitches, add an extra selector to make sure we have it right
        main_tabs_holder.onGlobalLayout {
            Handler().postDelayed({
                main_tabs_holder.getTabAt(getDefaultTab())?.select()
                invalidateOptionsMenu()
            }, 100L)
        }

        main_dialpad_button.setOnClickListener {
            Intent(applicationContext, DialpadActivity::class.java).apply {
                startActivity(this)
            }
        }
    }

    private fun getTabIcon(position: Int): Drawable {
        val drawableId = when (position) {
            0 -> R.drawable.ic_person_vector
            1 -> R.drawable.ic_star_on_vector
            else -> R.drawable.ic_clock_vector
        }

        return resources.getColoredDrawableWithColor(drawableId, config.textColor)
    }

    private fun refreshItems() {
        if (isDestroyed || isFinishing) {
            return
        }

        if (viewpager.adapter == null) {
            viewpager.offscreenPageLimit = tabsList.size - 1
            viewpager.adapter = ViewPagerAdapter(this)
            viewpager.currentItem = getDefaultTab()
        }

        contacts_fragment?.refreshItems()
        favorites_fragment?.refreshItems()
        recents_fragment?.refreshItems()
    }

    private fun getAllFragments() = arrayListOf(contacts_fragment, favorites_fragment, recents_fragment).toMutableList() as ArrayList<MyViewPagerFragment?>

    private fun getDefaultTab(): Int {
        return when (config.defaultTab) {
            TAB_LAST_USED -> config.lastUsedViewPagerPage
            TAB_CONTACTS -> 0
            TAB_FAVORITES -> 1
            else -> 2
        }
    }

    private fun launchAbout() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("Calculator")
        builder.setMessage("Simple dialer app with Blackberry optimizations\nOptmized for hardware keyboard use\n\nBased on Simple-Dialer by SimpleMobileTools - https://www.simplemobiletools.com")
        builder.setPositiveButton("OK", DialogInterface.OnClickListener { dialog, id ->
        })
        builder.show()
    }

    override fun onGenericMotion(view: View?, event: MotionEvent?): Boolean {
        if (event == null)
            return true


        //Log.i("gesture", event?.action.toString() + " @ " + event?.x.toString() + "/" + event?.y.toString() + "." + viewpager.currentItem)

        if(event.action == MotionEvent.ACTION_DOWN) {
            lastX = event.x;
            lastY = event.y
        }

        if(event.action == MotionEvent.ACTION_MOVE) {

            //swipe to right
            if(event.x > lastX + horizontalThreshold) {
                viewpager.setCurrentItem(viewpager.currentItem - 1, true)
                lastX = event.x;
                lastY = event.y
            }

            //siwpe to left
            if(event.x < lastX - horizontalThreshold) {
                viewpager.setCurrentItem(viewpager.currentItem + 1, true)
                lastX = event.x;
                lastY = event.y
            }

            //swipe up
            if(event.y < lastY - verticalThreshold) {
                when(viewpager.currentItem) {
                    0 -> contacts_fragment.scrollDown()
                    1 -> favorites_fragment.scrollDown()
                    2 -> recents_fragment.scrollDown()
                }
            }

            //swipe down
            if(event.y > lastY + verticalThreshold) {
                when(viewpager.currentItem) {
                    0 -> contacts_fragment.scrollUp()
                    1 -> favorites_fragment.scrollUp()
                    2 -> recents_fragment.scrollUp()
                }
            }
        }

        //viewpager.dispatchTouchEvent(event)

        return true;
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if(event.getAction() != KeyEvent.ACTION_UP)
            return super.dispatchKeyEvent(event);

        //Log.i("key up", java.lang.String.valueOf(event.keyCode) + " unicode char: " + event.unicodeChar)

        if(lastKeyCode == 57) {
            when(event.unicodeChar) {
                97 -> if(viewpager.currentItem == 0) { //alt-a add contact
                    fragment_fab.callOnClick();
                }
                112 -> main_dialpad_button.performClick() //alt-p dialpad
                119 -> when(viewpager.currentItem) { //select first entry
                    0 -> contacts_fragment.clickItem(0);
                    1 -> favorites_fragment.clickItem(0);
                    2 -> recents_fragment.clickItem(0);
                }
                101 -> when(viewpager.currentItem) { //select entry
                    0 -> contacts_fragment.clickItem(1);
                    1 -> favorites_fragment.clickItem(1);
                    2 -> recents_fragment.clickItem(1);
                }
                114 -> when(viewpager.currentItem) { //select entry
                    0 -> contacts_fragment.clickItem(2);
                    1 -> favorites_fragment.clickItem(2);
                    2 -> recents_fragment.clickItem(2);
                }
                115 -> when(viewpager.currentItem) { //select first entry
                    0 -> contacts_fragment.clickItem(3);
                    1 -> favorites_fragment.clickItem(3);
                    2 -> recents_fragment.clickItem(3);
                }
                100 -> when(viewpager.currentItem) { //select entry
                    0 -> contacts_fragment.clickItem(4);
                    1 -> favorites_fragment.clickItem(4);
                    2 -> recents_fragment.clickItem(4);
                }
                102 -> when(viewpager.currentItem) { //select entry
                    0 -> contacts_fragment.clickItem(5);
                    1 -> favorites_fragment.clickItem(5);
                    2 -> recents_fragment.clickItem(5);
                }
                122 -> when(viewpager.currentItem) { //select first entry
                    0 -> contacts_fragment.clickItem(6);
                    1 -> favorites_fragment.clickItem(6);
                    2 -> recents_fragment.clickItem(6);
                }
                120 -> when(viewpager.currentItem) { //select entry
                    0 -> contacts_fragment.clickItem(7);
                    1 -> favorites_fragment.clickItem(7);
                    2 -> recents_fragment.clickItem(7);
                }
                99 -> when(viewpager.currentItem) { //select entry
                    0 -> contacts_fragment.clickItem(8);
                    1 -> favorites_fragment.clickItem(8);
                    2 -> recents_fragment.clickItem(8);
                }
            }
        } else {
            if(event.unicodeChar >= 97 && event.unicodeChar <= 122) {
                searchMenuItem?.expandActionView()
                var v = searchMenuItem?.actionView as SearchView
                val pressedKey = event.unicodeChar.toChar()
                v.setQuery(pressedKey.toString(), false)
            }
        }

        lastKeyCode = event.keyCode;
        return super.dispatchKeyEvent(event)
    }

}
