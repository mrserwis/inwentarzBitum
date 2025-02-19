package com.example.inwentarz

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.Serializable

class OdbiorcyFragment : Fragment() {

    private lateinit var btnDodajOdbiorce: Button
    private lateinit var tableOdbiorcy: TableLayout

    private lateinit var databaseOdbiorcy: DatabaseReference

    // Lista z bazy
    private val allOdbiorcyList = mutableListOf<Odbiorca>()

    // Do zaznaczania wierszy (opcjonalnie)
    private var selectedRow: TableRow? = null

    // Sprawdzamy, czy przychodzimy w trybie wyboru kontrahenta
    private var isSelectMode = false

    data class Odbiorca(
        val nazwa: String = "",
        val adres: String = ""
    ) : Serializable

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_odbiorcy, container, false)

        btnDodajOdbiorce = view.findViewById(R.id.btn_dodaj_odbiorce)
        tableOdbiorcy = view.findViewById(R.id.table_odbiorcy)

        // Referencja do węzła "Odbiorcy" w Firebase
        databaseOdbiorcy = Firebase.database.getReference("Odbiorcy")

        // Ustalamy tryb (czy przychodzimy z WZ, by wybrać kontrahenta)
        isSelectMode = (SharedData.kontrahentMode == "odbiorca" || SharedData.kontrahentMode == "dostawca")

        // Ładujemy listę odbiorców
        loadOdbiorcyData()

        // Klik "Dodaj odbiorcę"
        btnDodajOdbiorce.setOnClickListener {
            showAddOdbiorcaDialog()
        }

        return view
    }

    private fun loadOdbiorcyData() {
        // Nasłuch na "Odbiorcy"
        databaseOdbiorcy.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allOdbiorcyList.clear()
                for (child in snapshot.children) {
                    val odbiorca = child.getValue(Odbiorca::class.java)
                    if (odbiorca != null) {
                        allOdbiorcyList.add(odbiorca)
                    }
                }
                displayOdbiorcyTable(allOdbiorcyList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun displayOdbiorcyTable(odbiorcyList: List<Odbiorca>) {
        // Czyścimy tabelę
        tableOdbiorcy.removeAllViews()

        // Wiersz nagłówka
        val headerRow = TableRow(context)

        val headerNazwa = TextView(context).apply {
            text = "Nazwa"
            setPadding(16, 8, 16, 8)
        }
        val headerAdres = TextView(context).apply {
            text = "Adres"
            setPadding(16, 8, 16, 8)
        }

        headerRow.addView(headerNazwa)
        headerRow.addView(headerAdres)
        tableOdbiorcy.addView(headerRow)

        // Wiersze z danymi
        for (odbiorca in odbiorcyList) {
            val row = TableRow(context)

            val tvNazwa = TextView(context).apply {
                text = odbiorca.nazwa
                setPadding(16, 8, 16, 8)
            }
            val tvAdres = TextView(context).apply {
                text = odbiorca.adres
                setPadding(16, 8, 16, 8)
            }

            row.addView(tvNazwa)
            row.addView(tvAdres)

            // Kliknięcie w wiersz
            row.setOnClickListener {
                if (isSelectMode) {
                    // Tryb wyboru -> zapisujemy w SharedData i wracamy do WZ
                    SharedData.selectedKontrahent = odbiorca.nazwa
                    // Powrót do WZ (np. index 0)
                    (activity as? OknoPracownika)?.viewPager?.currentItem = 0
                } else {
                    // Tryb normalny -> zaznaczamy
                    selectedRow?.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), android.R.color.transparent)
                    )
                    row.setBackgroundColor(
                        ContextCompat.getColor(requireContext(), R.color.light_blue)
                    )
                    selectedRow = row
                }
            }

            tableOdbiorcy.addView(row)
        }
    }

    private fun showAddOdbiorcaDialog() {
        // Inflater do dialogu, np. dialog_add_odbiorca.xml
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_odbiorca, null)

        val editTextNazwa = dialogView.findViewById<EditText>(R.id.editTextNazwaOdbiorcy)
        val editTextAdres = dialogView.findViewById<EditText>(R.id.editTextAdresOdbiorcy)

        // Budowa i wyświetlenie dialogu
        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
        val dialog = builder.create()
        dialog.show()

        // Klik "Zapisz"
        dialogView.findViewById<Button>(R.id.buttonZapiszOdbiorce).setOnClickListener {
            val nazwa = editTextNazwa.text.toString().trim()
            val adres = editTextAdres.text.toString().trim()

            if (nazwa.isEmpty()) {
                Toast.makeText(requireContext(), "Wpisz nazwę odbiorcy!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (adres.isEmpty()) {
                Toast.makeText(requireContext(), "Wpisz adres odbiorcy!", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Zapis do bazy: Odbiorcy -> push() -> { nazwa, adres }
            val newOdbiorca = mapOf(
                "nazwa" to nazwa,
                "adres" to adres
            )
            databaseOdbiorcy.push().setValue(newOdbiorca)

            dialog.dismiss()
        }

        // Klik "Anuluj"
        dialogView.findViewById<Button>(R.id.buttonAnulujOdbiorce).setOnClickListener {
            dialog.dismiss()
        }
    }
}
