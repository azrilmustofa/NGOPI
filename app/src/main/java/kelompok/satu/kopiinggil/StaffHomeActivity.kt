package kelompok.satu.kopiinggil

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

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
                .add(R.id.staffFragmentContainer, ProfilFragment(), TAG_AKUN)
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
                    showTab(TAG_AKUN)
                    true
                }
                else -> false
            }
        }
    }

    private fun showTab(tag: String) {
        val tabs = listOf(TAG_ANTREAN, TAG_RIWAYAT, TAG_AKUN).mapNotNull<String, Fragment> {
            supportFragmentManager.findFragmentByTag(it)
        }
        supportFragmentManager.beginTransaction().setReorderingAllowed(true).apply {
            tabs.forEach { if (it.tag == tag) show(it) else hide(it) }
        }.commit()
    }

    companion object {
        private const val TAG_ANTREAN = "tag_staff_antrean"
        private const val TAG_RIWAYAT = "tag_staff_riwayat"
        private const val TAG_AKUN = "tag_staff_akun"
    }
}
