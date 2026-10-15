package kelompok.satu.kopiinggil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

// Host untuk tab pelanggan: hanya berisi container fragment dan bottom navigation.
class HomeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_home)
        applyEdgeToEdge(R.id.main)

        val table = intent.getIntExtra(EXTRA_TABLE, 0)
        CartManager.tableNumber = table
        CartManager.tableId = intent.getStringExtra(EXTRA_TABLE_ID).orEmpty()

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .setReorderingAllowed(true)
                .add(R.id.fragmentContainer, MenuFragment.newInstance(table), TAG_MENU)
                .add(R.id.fragmentContainer, PesananFragment(), TAG_PESANAN)
                .add(R.id.fragmentContainer, ProfilFragment(), TAG_PROFIL)
                .commitNow()
            showTab(TAG_MENU)
        }

        findViewById<BottomNavigationView>(R.id.bottomNav).setOnItemSelectedListener { item ->
            val tag = when (item.itemId) {
                R.id.nav_menu -> TAG_MENU
                R.id.nav_orders -> TAG_PESANAN
                R.id.nav_profile -> TAG_PROFIL
                else -> return@setOnItemSelectedListener false
            }
            showTab(tag)
            true
        }
    }

    fun selectMenuTab() {
        findViewById<BottomNavigationView>(R.id.bottomNav).selectedItemId = R.id.nav_menu
    }

    // Semua tab tetap hidup (show/hide) supaya pencarian dan filter di Menu tidak hilang saat pindah tab.
    private fun showTab(tag: String) {
        val tabs = listOf(TAG_MENU, TAG_PESANAN, TAG_PROFIL).mapNotNull<String, Fragment> {
            supportFragmentManager.findFragmentByTag(it)
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            tabs.forEach { if (it.tag == tag) show(it) else hide(it) }
        }.commit()
    }

    companion object {
        const val EXTRA_TABLE = "extra_table"
        const val EXTRA_TABLE_ID = "extra_table_id"
        private const val TAG_MENU = "tab_menu"
        private const val TAG_PESANAN = "tab_pesanan"
        private const val TAG_PROFIL = "tab_profil"
    }
}
