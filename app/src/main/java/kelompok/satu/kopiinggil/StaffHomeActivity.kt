package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.SignOutScope
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch

class StaffHomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_staff_home)
        applyEdgeToEdge(R.id.main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.staffBottomNav)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.staffFragmentContainer, StaffAntreanFragment(), TAG_ANTREAN)
                .add(R.id.staffFragmentContainer, StaffRiwayatFragment(), TAG_RIWAYAT)
                .commitNow()
            showTab(TAG_ANTREAN)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_staff_antrean -> {
                    showTab(TAG_ANTREAN)
                    true
                }
                R.id.nav_staff_riwayat -> {
                    showTab(TAG_RIWAYAT)
                    true
                }
                R.id.nav_staff_akun -> {
                    showLogoutDialog()
                    false
                }
                else -> false
            }
        }
    }

    private fun showTab(tag: String) {
        val tabs = listOf(TAG_ANTREAN, TAG_RIWAYAT).mapNotNull<String, Fragment> {
            supportFragmentManager.findFragmentByTag(it)
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            tabs.forEach { if (it.tag == tag) show(it) else hide(it) }
        }.commit()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Akun Staff")
            .setMessage("Apakah Anda yakin ingin keluar dari akun Barista?")
            .setPositiveButton("Keluar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        supabase.auth.signOut(SignOutScope.LOCAL)
                    } catch (_: Exception) {}
                    setRememberMe(false)
                    startActivity(
                        Intent(this@StaffHomeActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    companion object {
        private const val TAG_ANTREAN = "tag_staff_antrean"
        private const val TAG_RIWAYAT = "tag_staff_riwayat"
    }
}
