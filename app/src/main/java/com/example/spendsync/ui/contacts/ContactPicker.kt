package com.example.spendsync.ui.contacts

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** One person the user chose from their contacts. Only the piece asked for (phone or email) is filled in. */
data class PickedContact(val name: String, val phone: String? = null, val email: String? = null)

class ContactPickerLauncher internal constructor(private val pickPhoneFn: () -> Unit, private val pickEmailFn: () -> Unit) {
    fun pickPhone() = pickPhoneFn()
    fun pickEmail() = pickEmailFn()
}

/**
 * Opens the system contact picker. Android hands back temporary read access to the ONE contact the user taps, so the
 * app needs no READ_CONTACTS permission and never sees anyone else in the address book.
 */
@Composable
fun rememberContactPicker(onPicked: (PickedContact) -> Unit): ContactPickerLauncher {
    val context = LocalContext.current
    var wantEmail = remember { booleanArrayOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri -> read(context, uri, wantEmail[0])?.let(onPicked) }
        }
    }
    return remember(launcher) {
        ContactPickerLauncher(
            pickPhoneFn = {
                wantEmail[0] = false
                runCatching { launcher.launch(Intent(Intent.ACTION_PICK).apply { type = ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE }) }
            },
            pickEmailFn = {
                wantEmail[0] = true
                runCatching { launcher.launch(Intent(Intent.ACTION_PICK).apply { type = ContactsContract.CommonDataKinds.Email.CONTENT_TYPE }) }
            },
        )
    }
}

private fun read(context: Context, uri: Uri, email: Boolean): PickedContact? = runCatching {
    val valueColumn = if (email) ContactsContract.CommonDataKinds.Email.ADDRESS else ContactsContract.CommonDataKinds.Phone.NUMBER
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME, valueColumn), null, null, null)?.use { c ->
        if (!c.moveToFirst()) return@use null
        val name = c.getString(0).orEmpty()
        val value = c.getString(1)?.trim().orEmpty()
        if (value.isEmpty()) null else PickedContact(name, phone = value.takeIf { !email }, email = value.takeIf { email })
    }
}.getOrNull()
