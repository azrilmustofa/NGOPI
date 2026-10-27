package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class RegisterActivity : AppCompatActivity() {
    private val segments by lazy {
        listOf(R.id.strength1, R.id.strength2, R.id.strength3, R.id.strength4).map { findViewById<View>(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)
        applyEdgeToEdge(R.id.main)

        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilPhone = findViewById<TextInputLayout>(R.id.tilPhone)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val cbConsent = findViewById<MaterialCheckBox>(R.id.cbConsent)

        etName.doOnTextChanged { _, _, _, _ -> tilName.error = null }
        etPhone.doOnTextChanged { _, _, _, _ -> tilPhone.error = null }
        etPhone.doAfterTextChanged {
            val raw = it?.toString().orEmpty()
            val clean = raw.filter(Char::isDigit).removePrefix("62").trimStart('0')
            if (clean != raw) {
                it?.replace(0, it.length, clean)
            }
        }
        etEmail.doOnTextChanged { _, _, _, _ -> tilEmail.error = null }
        etPassword.doOnTextChanged { text, _, _, _ ->
            tilPassword.error = null
            renderStrength(text.toString())
        }
        renderStrength("")

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.tvLogin).setOnClickListener { finish() }

        val btnRegister = findViewById<View>(R.id.btnRegister)
        btnRegister.setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            val phone = etPhone.text?.toString().orEmpty().trim()
            val email = etEmail.text?.toString().orEmpty().trim()
            val password = etPassword.text?.toString().orEmpty()

            var valid = true
            if (name.isEmpty()) {
                tilName.error = getString(R.string.register_error_name)
                valid = false
            }
            if (phone.isNotEmpty() && !phone.isValidPhone()) {
                tilPhone.error = getString(R.string.register_error_phone)
                valid = false
            }
            if (email.isEmpty() || !email.isValidEmail()) {
                tilEmail.error = getString(R.string.register_error_email)
                valid = false
            }
            if (password.length < MIN_PASSWORD) {
                tilPassword.error = getString(R.string.register_error_password)
                valid = false
            }
            if (!cbConsent.isChecked) {
                Toast.makeText(this, R.string.register_error_consent, Toast.LENGTH_SHORT).show()
                valid = false
            }

            if (!valid) return@setOnClickListener

            btnRegister.isEnabled = false
            // ponytail: direct Auth call; extract to AuthViewModel/Repository when adding profile table sync
            lifecycleScope.launch {
                try {
                    supabase.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                        data = buildJsonObject {
                            put("full_name", name)
                            put("phone", phone)
                        }
                    }
                    setRememberMe(true)
                    Toast.makeText(this@RegisterActivity, R.string.register_success, Toast.LENGTH_SHORT).show()
                    openMainAndClearStack()
                } catch (e: Exception) {
                    Toast.makeText(this@RegisterActivity, e.toUserMessage(this@RegisterActivity, R.string.err_register_failed), Toast.LENGTH_LONG).show()
                } finally {
                    btnRegister.isEnabled = true
                }
            }
        }
    }

    private fun renderStrength(password: String) {
        var score = 0
        if (password.length >= MIN_PASSWORD) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        val on = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_primary))
        val off = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_border))
        segments.forEachIndexed { index, view -> view.backgroundTintList = if (index < score) on else off }

        findViewById<TextView>(R.id.tvStrengthHint).setText(
            when (score) {
                0, 1 -> R.string.strength_weak
                2 -> R.string.strength_fair
                3 -> R.string.strength_good
                else -> R.string.strength_great
            }
        )
    }

    private companion object {
        const val MIN_PASSWORD = 8
    }
}
