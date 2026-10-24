package com.smartdialer.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.smartdialer.data.WhatsAppLink
import java.util.Locale

/**
 * Hands a contact over to other apps: the phone, WhatsApp, the contacts app. Orbit never reads anything
 * back from them.
 */
class ContactActions internal constructor(
    private val context: Context,
    private val requestCallPermission: (String) -> Unit,
) {
    /** Calls [number] directly, asking for the call permission the first time. */
    fun call(number: String) {
        if (hasCallPermission(context)) placeCall(context, number) else requestCallPermission(number)
    }

    fun whatsApp(number: String) {
        val digits = WhatsAppLink.internationalDigits(number, ::localToE164)
        if (digits == null) {
            toast("Can't make a WhatsApp link from this number")
            return
        }
        val pm = context.packageManager
        val installed = WhatsAppLink.PACKAGES.filter { isInstalled(pm, it) }.toSet()
        val pkg = WhatsAppLink.choosePackage(installed)
        if (pkg == null) {
            toast("WhatsApp is not installed")
            return
        }
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(WhatsAppLink.url(digits))).setPackage(pkg))
        } catch (_: ActivityNotFoundException) {
            toast("Could not open WhatsApp")
        }
    }

    /** Opens the phone's own contact page for the contact with this lookup key. */
    fun openContactCard(lookupKey: String) {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey)
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            toast("Could not open the contact")
        }
    }

    fun openDialer() {
        try {
            context.startActivity(Intent(Intent.ACTION_DIAL))
        } catch (_: ActivityNotFoundException) {
            toast("No dialer app found")
        }
    }

    private fun localToE164(number: String): String? {
        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as? TelephonyManager
        val country = listOfNotNull(tm?.simCountryIso, tm?.networkCountryIso, Locale.getDefault().country)
            .firstOrNull { it.isNotBlank() }
            ?.uppercase(Locale.ROOT)
            ?: return null
        return PhoneNumberUtils.formatNumberToE164(number, country)
    }

    @Suppress("DEPRECATION")
    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        runCatching { pm.getPackageInfo(pkg, 0) }.isSuccess

    private fun toast(text: String) = Toast.makeText(context, text, Toast.LENGTH_SHORT).show()

    internal companion object {
        fun hasCallPermission(context: Context) =
            ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

        fun placeCall(context: Context, number: String) {
            try {
                context.startActivity(Intent(Intent.ACTION_CALL, Uri.fromParts("tel", number, null)))
            } catch (_: SecurityException) {
                dial(context, number)
            }
        }

        /** The fallback when calling directly is not allowed: the dialer opens with the number filled in. */
        fun dial(context: Context, number: String) {
            try {
                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)))
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(context, "No dialer app found", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

/**
 * Creates the [ContactActions] for the current screen. The call permission is requested the first time a
 * call is placed; if it is denied, the dialer opens with the number filled in instead.
 */
@Composable
fun rememberContactActions(): ContactActions {
    val context = LocalContext.current
    var pendingNumber by remember { mutableStateOf<String?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val number = pendingNumber
        pendingNumber = null
        if (number != null) {
            if (granted) ContactActions.placeCall(context, number) else ContactActions.dial(context, number)
        }
    }
    return remember(context) {
        ContactActions(context) { number ->
            pendingNumber = number
            launcher.launch(Manifest.permission.CALL_PHONE)
        }
    }
}
