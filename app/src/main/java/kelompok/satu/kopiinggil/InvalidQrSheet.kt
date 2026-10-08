package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class InvalidQrSheet : BottomSheetDialogFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.sheet_qr_invalid, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        (view.parent as? View)?.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_background))

        view.findViewById<View>(R.id.btnRescan).setOnClickListener {
            parentFragmentManager.setFragmentResult(RESULT_KEY, Bundle())
            dismiss()
        }
        view.findViewById<View>(R.id.btnCallCashier).setOnClickListener {
            Toast.makeText(requireContext(), R.string.invalid_call_cashier_todo, Toast.LENGTH_SHORT).show()
        }
    }

    companion object {
        const val TAG = "InvalidQrSheet"
        const val RESULT_KEY = "invalid_qr_rescan"
    }
}
