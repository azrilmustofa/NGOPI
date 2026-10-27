package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        applyEdgeToEdge(R.id.main)

        // Sembunyikan form sampai sesi tersimpan selesai dicek, supaya tidak berkedip.
        val root = findViewById<View>(R.id.main)
        root.visibility = View.INVISIBLE
        lifecycleScope.launch {
            try {
                supabase.auth.awaitInitialization()
                if (supabase.auth.currentSessionOrNull() != null) {
                    if (isRememberMe()) {
                        checkRoleAndNavigate()
                        return@launch
                    }
                    supabase.auth.signOut(SignOutScope.LOCAL)
                }
            } catch (_: Exception) {
                // Gagal baca sesi: tampilkan form login seperti biasa.
            }
            root.visibility = View.VISIBLE
        }

        val tilIdentifier = findViewById<TextInputLayout>(R.id.tilIdentifier)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etIdentifier = findViewById<TextInputEditText>(R.id.etIdentifier)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val cbRemember = findViewById<android.widget.CompoundButton>(R.id.cbRemember)
        val btnLogin = findViewById<android.view.View>(R.id.btnLogin)

        etIdentifier.doOnTextChanged { _, _, _, _ -> tilIdentifier.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> tilPassword.error = null }

        btnLogin.setOnClickListener {
            val identifier = etIdentifier.text?.toString().orEmpty().trim()
            val password = etPassword.text?.toString().orEmpty()

            var valid = true
            if (identifier.isEmpty() || !identifier.isValidEmail()) {
                tilIdentifier.error = getString(R.string.login_error_identifier)
                valid = false
            }
            if (password.isEmpty()) {
                tilPassword.error = getString(R.string.login_error_password)
                valid = false
            }

            if (!valid) return@setOnClickListener

            btnLogin.isEnabled = false
            // ponytail: direct Auth call; extract to AuthViewModel/Repository when adding social login/offline mode
            lifecycleScope.launch {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = identifier
                        this.password = password
                    }
                    setRememberMe(cbRemember.isChecked)
                    Toast.makeText(this@LoginActivity, R.string.login_success, Toast.LENGTH_SHORT).show()
                    checkRoleAndNavigate()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, e.toUserMessage(this@LoginActivity, R.string.err_login_failed), Toast.LENGTH_LONG).show()
                } finally {
                    btnLogin.isEnabled = true
                }
            }
        }

        findViewById<android.view.View>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        findViewById<android.view.View>(R.id.tvForgot).setOnClickListener {
            startActivity(
                Intent(this, ForgotPasswordActivity::class.java)
                    .putExtra(ForgotPasswordActivity.EXTRA_EMAIL, etIdentifier.text?.toString().orEmpty().trim())
            )
        }
    }

    private suspend fun checkRoleAndNavigate() {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser == null) {
            android.util.Log.d("LoginActivity", "currentUser is null -> openMain")
            openMainAndClearStack()
            return
        }

        android.util.Log.d("LoginActivity", "Checking role for UID: ${currentUser.id}")

        try {
            val staffList = supabase.postgrest["staff_user"].select {
                filter { eq("uid", currentUser.id) }
            }.decodeList<StaffUser>()

            android.util.Log.d("LoginActivity", "Query staff_user count: ${staffList.size}")

            // Bukan staf = pelanggan biasa, langsung ke alur pelanggan.
            if (staffList.isEmpty()) {
                openMainAndClearStack()
                return
            }

            val staff = staffList.first()
            android.util.Log.d("LoginActivity", "Found staff: name=${staff.name}, role=${staff.role}, isActive=${staff.isActive}")

            if (staff.isActive) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.staff_welcome, staff.name),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                openStaffDashboardAndClearStack()
            } else {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    android.widget.Toast.makeText(
                        this@LoginActivity,
                        getString(R.string.staff_inactive, staff.name),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                }
                openMainAndClearStack()
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "Error query staff_user", e)
            openMainAndClearStack()
        }
    }
}
