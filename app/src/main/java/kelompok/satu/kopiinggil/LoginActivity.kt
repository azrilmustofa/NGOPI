package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        applyEdgeToEdge(R.id.main)

        val tilIdentifier = findViewById<TextInputLayout>(R.id.tilIdentifier)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etIdentifier = findViewById<TextInputEditText>(R.id.etIdentifier)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
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
                    Toast.makeText(this@LoginActivity, "Login berhasil!", Toast.LENGTH_SHORT).show()
                    openMainAndClearStack()
                } catch (e: Exception) {
                    Toast.makeText(this@LoginActivity, e.localizedMessage ?: "Login gagal", Toast.LENGTH_LONG).show()
                } finally {
                    btnLogin.isEnabled = true
                }
            }
        }

        findViewById<android.view.View>(R.id.tvRegister).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
        findViewById<android.view.View>(R.id.tvForgot).setOnClickListener {
            Toast.makeText(this, R.string.forgot_todo, Toast.LENGTH_SHORT).show()
        }
    }
}
