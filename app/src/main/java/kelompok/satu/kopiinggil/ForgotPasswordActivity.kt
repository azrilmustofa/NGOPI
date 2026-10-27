package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class ForgotPasswordActivity : AppCompatActivity() {
    private lateinit var steps: List<View>
    private lateinit var tvEyebrow: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvResend: TextView
    private lateinit var tvCodeInfo: TextView
    private var step = 0
    private var email = ""
    private var resendJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)
        applyEdgeToEdge(R.id.main)

        steps = listOf(R.id.stepEmail, R.id.stepCode, R.id.stepPassword).map { findViewById(it) }
        tvEyebrow = findViewById(R.id.tvEyebrow)
        tvTitle = findViewById(R.id.tvTitle)
        tvSubtitle = findViewById(R.id.tvSubtitle)
        tvResend = findViewById(R.id.tvResend)
        tvCodeInfo = findViewById(R.id.tvCodeInfo)

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilCode = findViewById<TextInputLayout>(R.id.tilCode)
        val tilNew = findViewById<TextInputLayout>(R.id.tilNewPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirmPassword)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etCode = findViewById<TextInputEditText>(R.id.etCode)
        val etNew = findViewById<TextInputEditText>(R.id.etNewPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirmPassword)
        val btnSendCode = findViewById<View>(R.id.btnSendCode)
        val btnVerify = findViewById<View>(R.id.btnVerify)
        val btnSave = findViewById<View>(R.id.btnSave)

        // Email yang sudah diketik di form login ikut terisi.
        intent.getStringExtra(EXTRA_EMAIL)?.takeIf { it.isValidEmail() }?.let(etEmail::setText)

        etEmail.doOnTextChanged { _, _, _, _ -> tilEmail.error = null }
        etCode.doOnTextChanged { _, _, _, _ -> tilCode.error = null }
        etNew.doOnTextChanged { _, _, _, _ -> tilNew.error = null }
        etConfirm.doOnTextChanged { _, _, _, _ -> tilConfirm.error = null }

        findViewById<View>(R.id.btnBack).setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        findViewById<View>(R.id.tvBackToLogin).setOnClickListener { finish() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when (step) {
                    1 -> showStep(0)
                    2 -> lifecycleScope.launch {
                        // Verifikasi kode sudah membuat sesi; batalkan kalau user keluar sebelum menyimpan.
                        runCatching { supabase.auth.signOut(SignOutScope.LOCAL) }
                        finish()
                    }
                    else -> finish()
                }
            }
        })

        btnSendCode.setOnClickListener {
            val input = etEmail.text?.toString().orEmpty().trim()
            if (!input.isValidEmail()) {
                tilEmail.error = getString(R.string.register_error_email)
                return@setOnClickListener
            }
            email = input
            sendCode(btnSendCode, firstTime = true)
        }

        tvResend.setOnClickListener { sendCode(tvResend, firstTime = false) }

        btnVerify.setOnClickListener {
            val code = etCode.text?.toString().orEmpty().trim()
            if (code.length !in CODE_LENGTH) {
                tilCode.error = getString(R.string.forgot_error_code)
                return@setOnClickListener
            }
            btnVerify.isEnabled = false
            lifecycleScope.launch {
                try {
                    supabase.auth.verifyEmailOtp(type = OtpType.Email.RECOVERY, email = email, token = code)
                    showStep(2)
                } catch (e: Exception) {
                    tilCode.error = getString(R.string.forgot_code_invalid)
                } finally {
                    btnVerify.isEnabled = true
                }
            }
        }

        btnSave.setOnClickListener {
            val password = etNew.text?.toString().orEmpty()
            val confirm = etConfirm.text?.toString().orEmpty()
            var valid = true
            if (password.length < MIN_PASSWORD) {
                tilNew.error = getString(R.string.register_error_password)
                valid = false
            }
            if (confirm != password) {
                tilConfirm.error = getString(R.string.forgot_error_confirm)
                valid = false
            }
            if (!valid) return@setOnClickListener

            btnSave.isEnabled = false
            lifecycleScope.launch {
                try {
                    supabase.auth.updateUser { this.password = password }
                    setRememberMe(true)
                    Toast.makeText(this@ForgotPasswordActivity, R.string.forgot_success, Toast.LENGTH_SHORT).show()
                    openMainAndClearStack()
                } catch (e: Exception) {
                    Toast.makeText(this@ForgotPasswordActivity, e.toUserMessage(this@ForgotPasswordActivity, R.string.err_password_save_failed), Toast.LENGTH_LONG).show()
                } finally {
                    btnSave.isEnabled = true
                }
            }
        }

        showStep(0)
    }

    private fun sendCode(trigger: View, firstTime: Boolean) {
        trigger.isEnabled = false
        lifecycleScope.launch {
            try {
                supabase.auth.resetPasswordForEmail(email)
                tvCodeInfo.text = getString(R.string.forgot_code_info, email)
                Toast.makeText(this@ForgotPasswordActivity, R.string.forgot_code_sent, Toast.LENGTH_SHORT).show()
                if (firstTime) showStep(1)
                startResendCountdown()
            } catch (e: Exception) {
                Toast.makeText(this@ForgotPasswordActivity, e.toUserMessage(this@ForgotPasswordActivity, R.string.err_send_code_failed), Toast.LENGTH_LONG).show()
                if (!firstTime) startResendCountdown()
            } finally {
                if (firstTime) trigger.isEnabled = true
            }
        }
    }

    private fun startResendCountdown() {
        resendJob?.cancel()
        tvResend.isEnabled = false
        resendJob = lifecycleScope.launch {
            for (left in RESEND_SECONDS downTo 1) {
                tvResend.text = getString(R.string.forgot_resend_wait, left)
                delay(1000)
            }
            tvResend.text = getString(R.string.forgot_resend)
            tvResend.isEnabled = true
        }
    }

    private fun showStep(index: Int) {
        step = index
        steps.forEachIndexed { i, v -> v.visibility = if (i == index) View.VISIBLE else View.GONE }
        tvEyebrow.text = getString(R.string.forgot_step_format, index + 1)
        tvTitle.setText(TITLES[index])
        tvSubtitle.setText(SUBTITLES[index])
    }

    companion object {
        const val EXTRA_EMAIL = "email"
        private val CODE_LENGTH = 6..10
        private const val MIN_PASSWORD = 8
        private const val RESEND_SECONDS = 60
        private val TITLES = intArrayOf(R.string.forgot_title_email, R.string.forgot_title_code, R.string.forgot_title_password)
        private val SUBTITLES = intArrayOf(R.string.forgot_subtitle_email, R.string.forgot_subtitle_code, R.string.forgot_subtitle_password)
    }
}
