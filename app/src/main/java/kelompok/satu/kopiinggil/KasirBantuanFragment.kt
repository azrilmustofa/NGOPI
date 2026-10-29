package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment

class KasirBantuanFragment : Fragment(R.layout.fragment_kasir_bantuan) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.cardKasirActionBantuPesan).setOnClickListener {
            startActivity(Intent(requireContext(), KasirBantuPesananActivity::class.java))
        }

        view.findViewById<View>(R.id.cardKasirActionKirimStruk).setOnClickListener {
            startActivity(Intent(requireContext(), KasirStrukWaActivity::class.java))
        }

        view.findViewById<View>(R.id.cardKasirActionCancelRefund).setOnClickListener {
            (activity as? KasirHomeActivity)?.selectTransaksiTab("SEMUA")
            android.widget.Toast.makeText(
                requireContext(),
                "Pilih pesanan yang ingin dibatalkan dari daftar transaksi",
                android.widget.Toast.LENGTH_SHORT
            ).show()
        }

        view.findViewById<View>(R.id.cardKasirActionSop).setOnClickListener {
            showSopDialog()
        }
    }

    private fun showSopDialog() {
        AlertDialog.Builder(requireContext())
            .setTitle("SOP Layanan & Bantuan Kasir")
            .setMessage(
                "1. Pembayaran Digital:\n" +
                "Status QRIS, E-Wallet, dan VA diperbarui secara otomatis. Jika status masih tertunda, periksa detail transaksi dan tekan 'Periksa ulang ke Midtrans'.\n\n" +
                "2. Pembayaran Tunai:\n" +
                "Jika pelanggan membayar cash di kasir, pilih transaksi tertunda tersebut lalu tekan 'Terima Pembayaran Tunai'. Pesanan akan otomatis berubah LUNAS dan masuk antrean barista.\n\n" +
                "3. Struk Transaksi:\n" +
                "Struk otomatis dikirim ke nomor WhatsApp pelanggan. Gunakan menu 'Kirim Ulang Struk WA' bila nomor pelanggan perlu diperbarui atau struk belum terkirim.\n\n" +
                "4. Pemesanan Langsung di Kasir:\n" +
                "Gunakan 'Bantu Buat Pesanan' untuk memilih meja dan mencatatkan menu bagi pelanggan yang pesan di meja kasir."
            )
            .setPositiveButton("Mengerti", null)
            .show()
    }
}
