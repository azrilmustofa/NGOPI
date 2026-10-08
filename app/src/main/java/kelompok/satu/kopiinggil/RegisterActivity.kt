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
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

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
        etPhone.doAfterTextChanged { if (it?.startsWith("0") == true) it.delete(0, 1) }
        etEmail.doOnTextChanged { _, _, _, _ -> tilEmail.error = null }
        etPassword.doOnTextChanged { text, _, _, _ ->
            tilPassword.error = null
            renderStrength(text.toString())
        }
        renderStrength("")

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.tvLogin).setOnClickListener { finish() }

        findViewById<View>(R.id.btnRegister).setOnClickListener {
            val nameOk = etName.text.toString().isNotBlank()
            val phoneOk = etPhone.text.toString().isValidPhone()
            val emailOk = etEmail.text.toString().trim().isValidEmail()
            val passwordOk = etPassword.text.toString().length >= MIN_PASSWORD

            tilName.error = if (nameOk) null else getString(R.string.register_error_name)
            tilPhone.error = if (phoneOk) null else getString(R.string.register_error_phone)
            tilEmail.error = if (emailOk) null else getString(R.string.register_error_email)
            tilPassword.error = if (passwordOk) null else getString(R.string.register_error_password)

            if (!(nameOk && phoneOk && emailOk && passwordOk)) return@setOnClickListener
            if (!cbConsent.isChecked) {
                Toast.makeText(this, R.string.register_error_consent, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            openMainAndClearStack()
        }
    }

    private fun renderStrength(password: String) {
        var score = 0
        if (password.length >= MIN_PASSWORD) score++
        if (password.any { it.isUpperCase() } && password.any { it.isLowerCase() }) score++
        if (password.any { it.isDigit() }) score++
        if (password.any { !it.isLetterOrDigit() }) score++

        val on = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_green))
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
