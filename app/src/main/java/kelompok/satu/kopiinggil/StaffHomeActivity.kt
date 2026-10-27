package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
                .replace(R.id.staffFragmentContainer, StaffAntreanFragment(), TAG_ANTREAN)
                .commit()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_staff_antrean -> {
                    showAntreanFragment()
                    true
                }
                R.id.nav_staff_riwayat -> {
                    Toast.makeText(this, R.string.staff_history_soon, Toast.LENGTH_SHORT).show()
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

    private fun showAntreanFragment() {
        val existing = supportFragmentManager.findFragmentByTag(TAG_ANTREAN)
        if (existing == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.staffFragmentContainer, StaffAntreanFragment(), TAG_ANTREAN)
                .commit()
        } else {
            supportFragmentManager.beginTransaction()
                .show(existing)
                .commit()
        }
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
    }
}
