package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.github.jan.supabase.auth.auth

// Host untuk tab pelanggan: berisi container fragment dan bottom navigation (Menu, Keranjang, Pesanan, Profil).
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        applyEdgeToEdge(R.id.main)

        val table = intent.getIntExtra(EXTRA_TABLE, 0)
        val assistedMode = intent.getBooleanExtra(EXTRA_ASSISTED_ORDER, false)
        CartManager.restore(
            this,
            tableNumber = table,
            tableId = intent.getStringExtra(EXTRA_TABLE_ID).orEmpty(),
            ownerUid = supabase.auth.currentUserOrNull()?.id.orEmpty(),
        )
        if (savedInstanceState == null && assistedMode) {
            // Pesanan bantuan selalu dimulai dari keranjang kosong agar tidak membawa item kasir sebelumnya.
            CartManager.clear()
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, MenuFragment.newInstance(table), TAG_MENU)
                .add(R.id.fragmentContainer, KeranjangFragment(), TAG_KERANJANG)
                .add(R.id.fragmentContainer, PesananFragment(), TAG_PESANAN)
                .add(R.id.fragmentContainer, ProfilFragment(), TAG_PROFIL)
                .commitNow()
            showTab(TAG_MENU)
        }

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        if (assistedMode) {
            findViewById<View>(R.id.assistedCashierHeader).visibility = View.VISIBLE
            findViewById<View>(R.id.btnExitAssistedOrder).setOnClickListener { finish() }
            val customer = AssistedOrderSession.forTable(CartManager.tableId)
            findViewById<TextView>(R.id.tvAssistedOrderContext).text =
                "${customer?.name ?: "Pelanggan"} · Meja %02d".format(table)
            bottomNav.menu.findItem(R.id.nav_menu).title = "Menu Kasir"
            bottomNav.menu.findItem(R.id.nav_cart).title = "Pesanan"
            bottomNav.menu.findItem(R.id.nav_orders).isVisible = false
            bottomNav.menu.findItem(R.id.nav_profile).isVisible = false
        }

        bottomNav.setOnItemSelectedListener { item ->
            val tag = when (item.itemId) {
                R.id.nav_menu -> TAG_MENU
                R.id.nav_cart -> TAG_KERANJANG
                R.id.nav_orders -> TAG_PESANAN
                R.id.nav_profile -> TAG_PROFIL
                else -> return@setOnItemSelectedListener false
            }
            showTab(tag)
            true
        }
        if (!assistedMode && intent.getBooleanExtra(EXTRA_OPEN_ORDERS, false)) {
            bottomNav.selectedItemId = R.id.nav_orders
        }

        updateCartBadge()
    }

    override fun onResume() {
        super.onResume()
        updateCartBadge()
        // Diminta layar lain sebelum ditutup (mis. "Pesan lagi" / "Lihat pesanan sebelumnya" di Pesanan selesai).
        pendingTab?.let {
            pendingTab = null
            findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = it
        }
    }

    override fun onDestroy() {
        if (isFinishing && intent.getBooleanExtra(EXTRA_ASSISTED_ORDER, false)) {
            AssistedOrderSession.clear()
        }
        super.onDestroy()
    }

    fun selectMenuTab() {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_menu
    }

    fun selectCartTab() {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_cart
    }

    fun updateCartBadge() {
        val nav = findViewById<BottomNavigationView>(R.id.bottomNav) ?: return
        val count = CartManager.getTotalItemCount()
        if (count > 0) {
            nav.getOrCreateBadge(R.id.nav_cart).apply {
                number = count
                isVisible = true
            }
        } else {
            nav.removeBadge(R.id.nav_cart)
        }
    }

    // Semua tab tetap hidup (show/hide) supaya data pencarian, filter, dan keranjang tidak hilang saat pindah tab.
    private fun showTab(tag: String) {
        val tabs = listOf(TAG_MENU, TAG_KERANJANG, TAG_PESANAN, TAG_PROFIL).mapNotNull<String, Fragment> {
            supportFragmentManager.findFragmentByTag(it)
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            tabs.forEach { if (it.tag == tag) show(it) else hide(it) }
        }.commit()
    }

    companion object {
        // Id item bottom nav yang dibuka saat HomeActivity tampil lagi.
        var pendingTab: Int? = null

        const val EXTRA_TABLE = "extra_table"
        const val EXTRA_TABLE_ID = "extra_table_id"
        const val EXTRA_ASSISTED_ORDER = "extra_assisted_order"
        const val EXTRA_OPEN_ORDERS = "extra_open_orders"
        private const val TAG_MENU = "tab_menu"
        private const val TAG_KERANJANG = "tab_keranjang"
        private const val TAG_PESANAN = "tab_pesanan"
        private const val TAG_PROFIL = "tab_profil"
    }
}
