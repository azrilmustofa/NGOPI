package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)
        applyEdgeToEdge(R.id.main)

        val tilIdentifier = findViewById<TextInputLayout>(R.id.tilIdentifier)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etIdentifier = findViewById<TextInputEditText>(R.id.etIdentifier)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)

        etIdentifier.doOnTextChanged { _, _, _, _ -> tilIdentifier.error = null }
        etPassword.doOnTextChanged { _, _, _, _ -> tilPassword.error = null }

        findViewById<android.view.View>(R.id.btnLogin).setOnClickListener {
            val identifier = etIdentifier.text.toString().trim()
            val password = etPassword.text.toString()

            val identifierOk = identifier.isValidEmail() || identifier.isValidPhone()
            tilIdentifier.error = if (identifierOk) null else getString(R.string.login_error_identifier)
            tilPassword.error = if (password.isNotEmpty()) null else getString(R.string.login_error_password)

            if (identifierOk && password.isNotEmpty()) {
                openMainAndClearStack()
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
