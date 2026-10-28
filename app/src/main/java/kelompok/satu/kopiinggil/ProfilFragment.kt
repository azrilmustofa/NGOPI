package kelompok.satu.kopiinggil

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Profil: nama & WhatsApp diedit per field (tombol Edit -> Batal) lalu disimpan sekaligus.
// Email hanya tampil (identitas login). Ubah kata sandi punya layar sendiri (UbahKataSandiActivity).
class ProfilFragment : Fragment(R.layout.fragment_profil) {

    private lateinit var etName: EditText
    private lateinit var etPhone: EditText
    private lateinit var etEmail: EditText
    private lateinit var btnEditName: TextView
    private lateinit var btnEditPhone: TextView
    private lateinit var btnSave: View
    private lateinit var btnLogout: View
    private lateinit var llEditActions: View

    private var editingName = false
    private var editingPhone = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        etName = view.findViewById(R.id.tvProfileName)
        etPhone = view.findViewById(R.id.tvProfilePhone)
        etEmail = view.findViewById(R.id.tvProfileEmail)
        btnEditName = view.findViewById(R.id.btnEditName)
        btnEditPhone = view.findViewById(R.id.btnEditPhone)
        btnSave = view.findViewById(R.id.btnSaveProfile)
        btnLogout = view.findViewById(R.id.btnLogout)
        llEditActions = view.findViewById(R.id.llEditActions)

        etEmail.setEditable(false)
        bindUser()

        btnEditName.setOnClickListener {
            if (editingName) cancelEdit(name = true) else startEdit(etName, name = true)
        }
        btnEditPhone.setOnClickListener {
            if (editingPhone) cancelEdit(phone = true) else startEdit(etPhone, phone = true)
        }
        btnSave.setOnClickListener { saveProfile() }
        view.findViewById<View>(R.id.btnCancelEdit).setOnClickListener { cancelEdit(name = true, phone = true) }

        view.findViewById<View>(R.id.rowEditPassword).setOnClickListener {
            startActivity(Intent(requireContext(), UbahKataSandiActivity::class.java))
        }

        view.findViewById<View>(R.id.rowHelpCenter).setOnClickListener {
            Toast.makeText(requireContext(), R.string.profile_help_info, Toast.LENGTH_LONG).show()
        }

        view.findViewById<View>(R.id.rowTerms).setOnClickListener {
            Toast.makeText(requireContext(), R.string.profile_terms_info, Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnLogout).setOnClickListener {
            AlertDialog.Builder(requireContext())
                .setTitle(R.string.profile_logout)
                .setMessage(R.string.profile_logout_confirm)
                .setPositiveButton("Keluar") { _, _ ->
                    lifecycleScope.launch {
                        try {
                            supabase.auth.signOut()
                        } catch (_: Exception) {
                        }
                        CartManager.clearAll()
                        OrderManager.clear()
                        val intent = Intent(requireContext(), LoginActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        requireActivity().finish()
                    }
                }
                .setNegativeButton("Batal", null)
                .show()
        }
    }

    // Mengisi field dari akun yang sedang login (nama & telepon dari user metadata).
    private fun bindUser() {
        val user = supabase.auth.currentUserOrNull()
        val email = user?.email.orEmpty()
        val metadata = user?.userMetadata

        val fullName = metadata?.get("full_name")?.jsonPrimitive?.content
        val phone = metadata?.get("phone")?.jsonPrimitive?.content

        val name = if (!fullName.isNullOrBlank()) fullName else email.substringBefore("@")
        etName.setText(name)
        etPhone.setText(phone.orEmpty().cleanPhone())
        etEmail.setText(email)

        view?.findViewById<TextView>(R.id.tvAvatarInitials)?.text = initialsOf(name)
        setEditable(name = false, phone = false)
    }

    private fun startEdit(field: EditText, name: Boolean = false, phone: Boolean = false) {
        setEditable(name = name || editingName, phone = phone || editingPhone)
        field.requestFocus()
        field.setSelection(field.text.length)
        (requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(field, InputMethodManager.SHOW_IMPLICIT)
    }

    // Batal: kembalikan isi field ke data akun.
    private fun cancelEdit(name: Boolean = false, phone: Boolean = false) {
        val metadata = supabase.auth.currentUserOrNull()?.userMetadata
        if (name) {
            val saved = metadata?.get("full_name")?.jsonPrimitive?.content
                ?: supabase.auth.currentUserOrNull()?.email.orEmpty().substringBefore("@")
            etName.setText(saved)
        }
        if (phone) etPhone.setText(metadata?.get("phone")?.jsonPrimitive?.content.orEmpty().cleanPhone())
        setEditable(name = editingName && !name, phone = editingPhone && !phone)
        hideKeyboard()
    }

    private fun setEditable(name: Boolean, phone: Boolean) {
        editingName = name
        editingPhone = phone
        etName.setEditable(name)
        etPhone.setEditable(phone)
        view?.findViewById<View>(R.id.layoutInputName)
            ?.setBackgroundResource(if (name) R.drawable.bg_input_active else R.drawable.bg_input_default)
        view?.findViewById<View>(R.id.layoutInputPhone)
            ?.setBackgroundResource(if (phone) R.drawable.bg_input_active else R.drawable.bg_input_default)
        btnEditName.setText(if (name) R.string.profile_cancel else R.string.profile_edit)
        btnEditPhone.setText(if (phone) R.string.profile_cancel else R.string.profile_edit)
        val editing = name || phone
        llEditActions.visibility = if (editing) View.VISIBLE else View.GONE
        btnLogout.visibility = if (editing) View.GONE else View.VISIBLE
    }

    private fun saveProfile() {
        val name = etName.text.toString().trim()
        val phone = etPhone.text.toString().cleanPhone()

        var valid = true
        if (name.isEmpty()) {
            etName.error = getString(R.string.profile_error_name)
            valid = false
        }
        if (phone.isNotEmpty() && !phone.isValidPhone()) {
            etPhone.error = getString(R.string.profile_error_phone)
            valid = false
        }
        if (!valid) return

        btnSave.isEnabled = false
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                supabase.auth.updateUser {
                    data = buildJsonObject {
                        put("full_name", name)
                        put("phone", phone)
                    }
                }
                hideKeyboard()
                bindUser()
                Toast.makeText(requireContext(), R.string.profile_saved, Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(
                    requireContext(),
                    e.toUserMessage(requireContext(), R.string.err_profile_save_failed),
                    Toast.LENGTH_LONG,
                ).show()
            } finally {
                btnSave.isEnabled = true
            }
        }
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(requireView().windowToken, 0)
        etName.clearFocus()
        etPhone.clearFocus()
    }

    private fun initialsOf(name: String): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> "${parts[0].first()}${parts[1].first()}".uppercase()
        }
    }

    // Sama seperti Register: hanya angka, tanpa awalan 62/0 (prefix +62 sudah tampil di UI).
    private fun String.cleanPhone(): String = filter(Char::isDigit).removePrefix("62").trimStart('0')

    private fun EditText.setEditable(editable: Boolean) {
        isFocusable = editable
        isFocusableInTouchMode = editable
        isCursorVisible = editable
        isLongClickable = editable
        if (!editable) error = null
    }
}
