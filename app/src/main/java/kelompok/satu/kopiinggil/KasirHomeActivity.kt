package kelompok.satu.kopiinggil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class KasirHomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_home)
        applyEdgeToEdge(R.id.main)
        initializePushNotifications()

        val bottomNav = findViewById<BottomNavigationView>(R.id.kasirBottomNav)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.kasirFragmentContainer, KasirRingkasanFragment(), TAG_BERANDA)
                .add(R.id.kasirFragmentContainer, KasirTransaksiFragment(), TAG_TRANSAKSI)
                .add(R.id.kasirFragmentContainer, KasirBantuanFragment(), TAG_BANTUAN)
                .add(R.id.kasirFragmentContainer, ProfilFragment(), TAG_AKUN)
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
                    showTab(TAG_AKUN)
                    true
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
        val tabs = listOf(TAG_BERANDA, TAG_TRANSAKSI, TAG_BANTUAN, TAG_AKUN).mapNotNull<String, Fragment> {
            supportFragmentManager.findFragmentByTag(it)
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            tabs.forEach { if (it.tag == tag) show(it) else hide(it) }
        }.commit()
    }

    companion object {
        private const val TAG_BERANDA = "tag_kasir_beranda"
        private const val TAG_TRANSAKSI = "tag_kasir_transaksi"
        private const val TAG_BANTUAN = "tag_kasir_bantuan"
        private const val TAG_AKUN = "tag_kasir_akun"
    }
}
