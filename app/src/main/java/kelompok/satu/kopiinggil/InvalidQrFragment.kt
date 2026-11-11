package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.launch

class InvalidQrFragment : BottomSheetDialogFragment() {
    private var sending = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_qr_invalid, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? View)?.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_background))

        view.findViewById<View>(R.id.btnRescan).setOnClickListener {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
            dismiss()
        }
        view.findViewById<View>(R.id.btnCallCashier).setOnClickListener {
            if (sending) return@setOnClickListener
            val qrCode = arguments?.getString(ARG_QR_CODE).orEmpty()
            if (qrCode.isBlank()) {
                Toast.makeText(requireContext(), R.string.err_help_failed, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            sending = true
            val button = it
            button.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val request = requestInvalidQrSupport(qrCode)
                    (button as? TextView)?.setText(R.string.invalid_cashier_called)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.invalid_cashier_called_detail, request.tableLabel),
                        Toast.LENGTH_LONG,
                    ).show()
                } catch (e: Exception) {
                    sending = false
                    button.isEnabled = true
                    Toast.makeText(
                        requireContext(),
                        e.toUserMessage(requireContext(), R.string.err_help_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    companion object {
        const val TAG = "InvalidQrFragment"
        const val RESULT_KEY = "invalid_qr_rescan"
        private const val ARG_QR_CODE = "invalid_qr_code"

        fun newInstance(qrCode: String) = InvalidQrFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_QR_CODE, qrCode.trim().take(500))
            }
        }
    }
}
