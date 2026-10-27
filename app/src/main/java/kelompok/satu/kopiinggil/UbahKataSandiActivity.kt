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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

// Ubah kata sandi dari Profil: kata sandi sekarang -> baru -> ulangi baru.
// Kata sandi sekarang diperiksa dengan login ulang ke Supabase sebelum diganti.
class UbahKataSandiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ubah_kata_sandi)
        applyEdgeToEdge(R.id.main)

        val email = supabase.auth.currentUserOrNull()?.email.orEmpty()

        val tilCurrent = findViewById<TextInputLayout>(R.id.tilCurrent)
        val tilNew = findViewById<TextInputLayout>(R.id.tilNew)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirm)
        val etCurrent = findViewById<TextInputEditText>(R.id.etCurrent)
        val etNew = findViewById<TextInputEditText>(R.id.etNew)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirm)
        val btnSave = findViewById<View>(R.id.btnSave)

        etCurrent.doOnTextChanged { _, _, _, _ -> tilCurrent.error = null }
        etNew.doOnTextChanged { _, _, _, _ -> tilNew.error = null }
        etConfirm.doOnTextChanged { _, _, _, _ -> tilConfirm.error = null }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.tvForgotCurrent).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).putExtra(ForgotPasswordActivity.EXTRA_EMAIL, email))
        }

        btnSave.setOnClickListener {
            val current = etCurrent.text?.toString().orEmpty()
            val newPassword = etNew.text?.toString().orEmpty()
            val confirm = etConfirm.text?.toString().orEmpty()

            var valid = true
            if (current.isEmpty()) {
                tilCurrent.error = getString(R.string.chpw_error_current_empty)
                valid = false
            }
            if (newPassword.length < MIN_PASSWORD) {
                tilNew.error = getString(R.string.register_error_password)
                valid = false
            } else if (newPassword == current) {
                tilNew.error = getString(R.string.chpw_error_same)
                valid = false
            }
            if (confirm != newPassword) {
                tilConfirm.error = getString(R.string.forgot_error_confirm)
                valid = false
            }
            if (!valid) return@setOnClickListener
            if (email.isEmpty()) {
                Toast.makeText(this, R.string.chpw_error_session, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnSave.isEnabled = false
            lifecycleScope.launch {
                try {
                    // Login ulang = bukti pengguna tahu kata sandi sekarang.
                    try {
                        supabase.auth.signInWith(Email) {
                            this.email = email
                            this.password = current
                        }
                    } catch (e: Exception) {
                        if (e.message?.contains("invalid", ignoreCase = true) == true) {
                            tilCurrent.error = getString(R.string.chpw_error_current_wrong)
                        } else {
                            Toast.makeText(this@UbahKataSandiActivity, e.toUserMessage(this@UbahKataSandiActivity, R.string.err_password_check_failed), Toast.LENGTH_LONG).show()
                        }
                        return@launch
                    }
                    supabase.auth.updateUser { password = newPassword }
                    Toast.makeText(this@UbahKataSandiActivity, R.string.forgot_success, Toast.LENGTH_SHORT).show()
                    finish()
                } catch (e: Exception) {
                    Toast.makeText(this@UbahKataSandiActivity, e.toUserMessage(this@UbahKataSandiActivity, R.string.err_password_save_failed), Toast.LENGTH_LONG).show()
                } finally {
                    btnSave.isEnabled = true
                }
            }
        }
    }

    private companion object {
        const val MIN_PASSWORD = 8
    }
}
