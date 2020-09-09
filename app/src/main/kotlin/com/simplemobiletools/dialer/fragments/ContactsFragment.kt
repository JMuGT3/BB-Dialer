package com.simplemobiletools.dialer.fragments

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import android.util.AttributeSet
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.recyclerview.widget.LinearLayoutManager
import com.reddit.indicatorfastscroll.FastScrollItemIndicator
import com.simplemobiletools.commons.adapters.MyRecyclerViewAdapter
import com.simplemobiletools.commons.extensions.*
import com.simplemobiletools.commons.helpers.*
import com.simplemobiletools.commons.models.SimpleContact
import com.simplemobiletools.dialer.R
import com.simplemobiletools.dialer.activities.SimpleActivity
import com.simplemobiletools.dialer.adapters.ContactsAdapter
import com.simplemobiletools.dialer.extensions.config
import com.simplemobiletools.dialer.interfaces.RefreshItemsListener
import kotlinx.android.synthetic.main.fragment_letters_layout.view.*
import java.math.BigDecimal
import java.util.*

class ContactsFragment(context: Context, attributeSet: AttributeSet) : MyViewPagerFragment(context, attributeSet), RefreshItemsListener {
    private var allContacts = ArrayList<SimpleContact>()
    private var SCROLL_BY = 450;

    override fun setupFragment() {
        val placeholderResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.no_contacts_found
        } else {
            R.string.could_not_access_contacts
        }

        fragment_placeholder.text = context.getString(placeholderResId)

        val placeholderActionResId = if (context.hasPermission(PERMISSION_READ_CONTACTS)) {
            R.string.create_new
        } else {
            R.string.request_access
        }

        fragment_placeholder_2.apply {
            text = context.getString(placeholderActionResId)
            setTextColor(context.config.primaryColor)
            underlineText()
            setOnClickListener {
                requestReadContactsPermission()
            }
        }

        letter_fastscroller.textColor = context.config.textColor.getColorStateList()
        letter_fastscroller_thumb.setupWithFastScroller(letter_fastscroller)
        letter_fastscroller_thumb.textColor = context.config.primaryColor.getContrastColor()

        fragment_fab.setOnClickListener {
            Intent(Intent.ACTION_INSERT).apply {
                data = ContactsContract.Contacts.CONTENT_URI

                if (resolveActivity(context.packageManager) != null) {
                    activity?.startActivity(this)
                } else {
                    context.toast(R.string.no_app_found)
                }
            }
        }
    }

    override fun textColorChanged(color: Int) {
        (fragment_list?.adapter as? MyRecyclerViewAdapter)?.updateTextColor(color)
        letter_fastscroller?.textColor = color.getColorStateList()
    }

    override fun primaryColorChanged(color: Int) {
        letter_fastscroller_thumb?.thumbColor = color.getColorStateList()
        letter_fastscroller_thumb?.textColor = color.getContrastColor()
        fragment_fab.background.applyColorFilter(context.getAdjustedPrimaryColor())
    }

    override fun refreshItems() {
        val privateCursor = context?.getMyContactsCursor()?.loadInBackground()
        SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
            allContacts = contacts

            val privateContacts = MyContactsContentProvider.getSimpleContacts(context, privateCursor)
            if (privateContacts.isNotEmpty()) {
                allContacts.addAll(privateContacts)
                allContacts.sort()
            }

            activity?.runOnUiThread {
                gotContacts(contacts)
            }
        }
    }

    private fun gotContacts(contacts: ArrayList<SimpleContact>) {
        setupLetterFastscroller(contacts)
        if (contacts.isEmpty()) {
            fragment_placeholder.beVisible()
            fragment_placeholder_2.beVisible()
            fragment_list.beGone()
        } else {
            fragment_placeholder.beGone()
            fragment_placeholder_2.beGone()
            fragment_list.beVisible()

            val currAdapter = fragment_list.adapter
            if (currAdapter == null) {
                ContactsAdapter(activity as SimpleActivity, contacts, fragment_list, this) {
                    val lookupKey = SimpleContactsHelper(activity!!).getContactLookupKey((it as SimpleContact).rawId.toString())
                    val publicUri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)

                    // handle private contacts differently, only Simple Contacts Pro can open them
                    val simpleContacts = "com.simplemobiletools.contacts.pro"
                    val simpleContactsDebug = "com.simplemobiletools.contacts.pro.debug"
                    if (lookupKey.isEmpty() && it.rawId > 1000000 && it.contactId > 1000000 && it.rawId == it.contactId &&
                        (context.isPackageInstalled(simpleContacts) || context.isPackageInstalled(simpleContactsDebug))) {
                        Intent().apply {
                            action = Intent.ACTION_VIEW
                            putExtra(CONTACT_ID, it.rawId)
                            putExtra(IS_PRIVATE, true)
                            `package` = if (context.isPackageInstalled(simpleContacts)) simpleContacts else simpleContactsDebug
                            setDataAndType(publicUri, "vnd.android.cursor.dir/person")
                            if (resolveActivity(context.packageManager) != null) {
                                activity?.startActivity(this)
                            } else {
                                context.toast(R.string.no_app_found)
                            }
                        }
                    } else {
                        activity!!.launchViewContactIntent(publicUri)
                    }
                }.apply {
                    fragment_list.adapter = this
                }
            } else {
                (currAdapter as ContactsAdapter).updateItems(contacts)
            }
        }
    }

    private fun setupLetterFastscroller(contacts: ArrayList<SimpleContact>) {
        letter_fastscroller.setupWithRecyclerView(fragment_list, { position ->
            try {
                val name = contacts[position].name
                val character = if (name.isNotEmpty()) name.substring(0, 1) else ""
                FastScrollItemIndicator.Text(character.toUpperCase(Locale.getDefault()))
            } catch (e: Exception) {
                FastScrollItemIndicator.Text("")
            }
        })
    }

    fun clickItem(item: Int) {
        if(fragment_list == null)
            return
        var position = (fragment_list.layoutManager as LinearLayoutManager?)!!.findFirstVisibleItemPosition()
        position += item;
        fragment_list.findViewHolderForAdapterPosition(position)?.itemView?.performClick()
        //fragment_list.findViewHolderForAdapterPosition(item)?.itemView?.performClick()
    }

    fun setFastScroller(keycode: BigDecimal) {
        letter_fastscroller.performClick();
    }

    fun scrollDown() {
        if(fragment_list == null)
            return
        fragment_list.smoothScrollBy(0, SCROLL_BY);
    }

    fun scrollUp() {
        if(fragment_list == null)
            return
        fragment_list.smoothScrollBy(0, -SCROLL_BY);
    }

    fun onSearchClosed() {
        (fragment_list.adapter as? ContactsAdapter)?.updateItems(allContacts)
        setupLetterFastscroller(allContacts)
    }

    fun onSearchQueryChanged(text: String) {
        val contacts = allContacts.filter {
            it.name.contains(text, true) || it.doesContainPhoneNumber(text)
        }.toMutableList() as ArrayList<SimpleContact>

        (fragment_list.adapter as? ContactsAdapter)?.updateItems(contacts, text)
        setupLetterFastscroller(contacts)
    }

    private fun requestReadContactsPermission() {
        activity?.handlePermission(PERMISSION_READ_CONTACTS) {
            if (it) {
                fragment_placeholder.text = context.getString(R.string.no_contacts_found)
                fragment_placeholder_2.text = context.getString(R.string.create_new)

                SimpleContactsHelper(context).getAvailableContacts(false) { contacts ->
                    activity?.runOnUiThread {
                        gotContacts(contacts)
                    }
                }
            }
        }
    }


}
