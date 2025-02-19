package com.example.inwentarz

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.fragment.app.Fragment

class PodgladFragment : Fragment() {

    private lateinit var editTextNumerWZ: EditText
    private lateinit var editTextData: EditText
    private lateinit var editTextKtoDodal: EditText
    private lateinit var editTextOdbiorca: EditText
    private lateinit var editTextDostawa: EditText
    private lateinit var editTextKomentarz: EditText

    // Tabela, w której wyświetlimy listę towarów (Nazwa, Ilość)
    private lateinit var tableTowaryPodglad: TableLayout

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Wczytujemy layout fragment_podglad.xml
        val view = inflater.inflate(R.layout.fragment_podglad, container, false)

        // Pola podstawowe
        editTextNumerWZ = view.findViewById(R.id.editTextNumerWZ_podglad)
        editTextData = view.findViewById(R.id.editTextData_podglad)
        editTextKtoDodal = view.findViewById(R.id.editTextKtoDodal_podglad)
        editTextOdbiorca = view.findViewById(R.id.editTextOdbiorca_podglad)
        editTextDostawa = view.findViewById(R.id.editTextDostawa_podglad)
        editTextKomentarz = view.findViewById(R.id.editTextKomentarz_podglad)

        // Tabela towarów
        tableTowaryPodglad = view.findViewById(R.id.table_towary_podglad)

        return view
    }

    override fun onResume() {
        super.onResume()

        // Odczytujemy obiekt z SharedData
        val wz = SharedData.currentWZ
        if (wz != null) {
            // Uzupełniamy pola podstawowe
            editTextNumerWZ.setText(wz.numerWZ)
            editTextData.setText(wz.data)
            editTextKtoDodal.setText(wz.ktoDodal)
            editTextOdbiorca.setText(wz.odbiorca)
            editTextDostawa.setText(wz.dostawa)
            editTextKomentarz.setText(wz.komentarz)

            // Wyświetlamy listę towarów
            displayTowary(wz.towary)
        }
    }

    /**
     * Wyświetla listę towarów (nazwa + ilość) w tableTowaryPodglad.
     */
    private fun displayTowary(towaryList: List<Map<String, Any>>) {
        // Najpierw czyścimy wszystkie wiersze oprócz wiersza nagłówka,
        // jeżeli w XML-u wstawiłeś taki wiersz "z góry".
        // Jeżeli w XML-u nie ma nic poza TableLayout,
        // to możesz po prostu zrobić tableTowaryPodglad.removeAllViews().

        val childCount = tableTowaryPodglad.childCount
        if (childCount > 1) {
            // Usuwamy wiersze od 1 do końca
            tableTowaryPodglad.removeViews(1, childCount - 1)
        }

        for (mapTowar in towaryList) {
            val row = TableRow(context)

            // Pobieramy nazwę
            val nazwaTowaru = mapTowar["Nazwa"]?.toString() ?: ""
            // Pobieramy ilość (jeśli to int lub double/long)
            val iloscAny = mapTowar["Ilosc"]
            val iloscTowaru = if (iloscAny is Number) iloscAny.toInt() else 0

            val tvNazwa = TextView(context).apply {
                text = nazwaTowaru
                setPadding(16, 8, 16, 8)
            }
            val tvIlosc = TextView(context).apply {
                text = iloscTowaru.toString()
                setPadding(16, 8, 16, 8)
            }

            row.addView(tvNazwa)
            row.addView(tvIlosc)

            tableTowaryPodglad.addView(row)
        }
    }
}
