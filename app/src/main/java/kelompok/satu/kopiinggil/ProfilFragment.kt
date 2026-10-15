package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive

class ProfilFragment : Fragment(R.layout.fragment_profil) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvName = view.findViewById<TextView>(R.id.tvProfileName)
        val tvEmail = view.findViewById<TextView>(R.id.tvProfileEmail)
        val tvPhone = view.findViewById<TextView>(R.id.tvProfilePhone)

        val user = supabase.auth.currentUserOrNull()
        val email = user?.email.orEmpty()
        val metadata = user?.userMetadata

        val fullName = metadata?.get("full_name")?.jsonPrimitive?.content
        val phone = metadata?.get("phone")?.jsonPrimitive?.content

        if (!fullName.isNullOrBlank()) {
            tvName.text = fullName
        } else if (email.isNotEmpty()) {
            tvName.text = email.substringBefore("@")
        }

        if (email.isNotEmpty()) {
            tvEmail.text = email
        }

        if (!phone.isNullOrBlank()) {
            tvPhone.text = if (phone.startsWith("+")) phone else "+62 $phone"
            tvPhone.visibility = View.VISIBLE
        } else {
            tvPhone.visibility = View.GONE
        }

        view.findViewById<View>(R.id.rowEditPassword).setOnClickListener {
            val intent = Intent(requireContext(), ForgotPasswordActivity::class.java).apply {
                if (email.isNotEmpty()) {
                    putExtra(ForgotPasswordActivity.EXTRA_EMAIL, email)
                }
            }
            startActivity(intent)
        }

        view.findViewById<View>(R.id.rowHelpCenter).setOnClickListener {
            Toast.makeText(requireContext(), "Pusat bantuan: hubungi Kasir atau barista di meja", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.rowTerms).setOnClickListener {
            Toast.makeText(requireContext(), "NGOPI - Fine Coffee Roasted Beans v1.0", Toast.LENGTH_SHORT).show()
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
                        CartManager.clear()
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
}
