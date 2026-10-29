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

class KasirHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_home)
        applyEdgeToEdge(R.id.main)

        val bottomNav = findViewById<BottomNavigationView>(R.id.kasirBottomNav)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.kasirFragmentContainer, KasirRingkasanFragment(), TAG_BERANDA)
                .add(R.id.kasirFragmentContainer, KasirTransaksiFragment(), TAG_TRANSAKSI)
                .add(R.id.kasirFragmentContainer, KasirBantuanFragment(), TAG_BANTUAN)
                .commitNow()
            showTab(TAG_BERANDA)
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_kasir_beranda -> {
                    showTab(TAG_BERANDA)
                    true
                }
                R.id.nav_kasir_transaksi -> {
                    showTab(TAG_TRANSAKSI)
                    true
                }
                R.id.nav_kasir_bantuan -> {
                    showTab(TAG_BANTUAN)
                    true
                }
                R.id.nav_kasir_akun -> {
                    showLogoutDialog()
                    false
                }
                else -> false
            }
        }
    }

    fun selectTransaksiTab(filterKey: String = "TERTUNDA") {
        findViewById<BottomNavigationView>(R.id.kasirBottomNav).selectedItemId = R.id.nav_kasir_transaksi
        val txFragment = supportFragmentManager.findFragmentByTag(TAG_TRANSAKSI) as? KasirTransaksiFragment
        txFragment?.setFilter(filterKey)
    }

    private fun showTab(tag: String) {
        val tabs = listOf(TAG_BERANDA, TAG_TRANSAKSI, TAG_BANTUAN).mapNotNull<String, Fragment> {
            supportFragmentManager.findFragmentByTag(it)
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            tabs.forEach { if (it.tag == tag) show(it) else hide(it) }
        }.commit()
    }

    private fun showLogoutDialog() {
        AlertDialog.Builder(this)
            .setTitle("Keluar Akun Kasir")
            .setMessage("Apakah Anda yakin ingin keluar dari akun Kasir?")
            .setPositiveButton("Keluar") { _, _ ->
                lifecycleScope.launch {
                    try {
                        supabase.auth.signOut(SignOutScope.LOCAL)
                    } catch (_: Exception) {}
                    setRememberMe(false)
                    startActivity(
                        Intent(this@KasirHomeActivity, LoginActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                    )
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    companion object {
        private const val TAG_BERANDA = "tag_kasir_beranda"
        private const val TAG_TRANSAKSI = "tag_kasir_transaksi"
        private const val TAG_BANTUAN = "tag_kasir_bantuan"
    }
}
