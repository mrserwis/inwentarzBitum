package com.example.inwentarz

import android.os.Bundle
import android.text.InputType
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class TowaryFragment : Fragment(R.layout.fragment_towary) {

    private lateinit var tableLayoutTowary: TableLayout
    private lateinit var databaseTowary: DatabaseReference

    private lateinit var btnDodajTowar: Button
    private lateinit var btnUsunTowar: Button
    private lateinit var btnZatwierdzWyborTowarow: Button

    // Czy działamy w trybie WZ (checkbox + ilość), czy normalnym
    private var isWZMode = false

    // Zaznaczony wiersz (do usuwania w trybie normalnym)
    private var selectedRow: TableRow? = null

    // Lista towarów pobrana z bazy
    private val allTowaryList = mutableListOf<Towar>()

    // Słownik (nazwa -> ilość) dla trybu WZ
    private val selectedTowaryForWZ = mutableMapOf<String, Int>()

    data class Towar(
        val key: String = "",  // klucz w bazie
        val nazwa: String = "" // nazwa towaru
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        Log.d("TowaryFragment", "onCreateView called.")
        val view = inflater.inflate(R.layout.fragment_towary, container, false)

        // Inicjalizacja widoków
        tableLayoutTowary = view.findViewById(R.id.table_layout_towary)
        databaseTowary = Firebase.database.getReference("Towary")

        btnDodajTowar = view.findViewById(R.id.btn_dodaj_towar)
        btnUsunTowar = view.findViewById(R.id.btn_usun_towar)
        btnZatwierdzWyborTowarow = view.findViewById(R.id.btn_zatwierdz_wybor_towarow)

        // Sprawdzamy, czy WZFragment ustawił tryb "towaryForWZMode"
        isWZMode = SharedData.towaryForWZMode
        Log.d("TowaryFragment", "isWZMode from SharedData = $isWZMode")

        // Przycisk Zatwierdź -> widoczny tylko w trybie WZ
        btnZatwierdzWyborTowarow.visibility = if (isWZMode) View.VISIBLE else View.GONE

        // Ładujemy towary z bazy
        loadTowaryData()

        // Obsługa przycisków
        btnDodajTowar.setOnClickListener {
            showAddTowarDialog()
        }
        btnUsunTowar.setOnClickListener {
            if (selectedRow == null) {
                Toast.makeText(requireContext(), "Nie wybrano towaru do usunięcia", Toast.LENGTH_SHORT).show()
            } else {
                showDeleteConfirmationDialog(selectedRow!!)
            }
        }
        btnZatwierdzWyborTowarow.setOnClickListener {
            val result = mutableListOf<Map<String, Any>>()
            for ((nazwa, ilosc) in selectedTowaryForWZ) {
                result.add(mapOf("Nazwa" to nazwa, "Ilosc" to ilosc))
            }
            SharedData.selectedTowary = result
            // Wyłącz tryb WZ
            SharedData.towaryForWZMode = false
            // Wracamy do WZFragment (zakładam index 0)
            (activity as? OknoPracownika)?.viewPager?.currentItem = 0
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        // Za każdym razem, gdy wchodzimy do fragmentu, zaktualizuj isWZMode
        // (jeśli w innym miejscu coś się zmieniło)
        isWZMode = SharedData.towaryForWZMode
        Log.d("TowaryFragment", "onResume -> isWZMode = $isWZMode")

        // Odśwież widok
        refreshTable()
    }

    /**
     * Funkcja pobiera towary z bazy i przechowuje je w allTowaryList.
     * Gdy dane się zmienią, wywołujemy refreshTable().
     */
    private fun loadTowaryData() {
        databaseTowary.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                allTowaryList.clear()
                // Dodajemy każdy towar do listy
                for (child in snapshot.children) {
                    val key = child.key ?: ""
                    val nazwa = child.getValue(String::class.java) ?: ""
                    allTowaryList.add(Towar(key, nazwa))
                }
                // Po wczytaniu danych -> odśwież
                refreshTable()
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(), "Błąd ładowania towarów", Toast.LENGTH_SHORT).show()
            }
        })
    }

    /**
     * Usuwamy wszystkie wiersze z tableLayout,
     * dodajemy nagłówek i wiersze w zależności od isWZMode i allTowaryList.
     */
    private fun refreshTable() {
        tableLayoutTowary.removeAllViews()

        // Nagłówek
        addTableHeader()

        // Wiersze z danymi
        for (t in allTowaryList) {
            addTowarRow(t)
        }

        // Zaktualizuj widoczność przycisku Zatwierdź (gdyby w trakcie coś się zmieniło)
        btnZatwierdzWyborTowarow.visibility = if (isWZMode) View.VISIBLE else View.GONE
    }

    private fun addTableHeader() {
        val headerRow = TableRow(context)

        if (isWZMode) {
            // 3 kolumny: [Check], [Towar], [Ilość]
            val tvCheck = TextView(context).apply {
                text = ""
                setPadding(16, 8, 16, 8)
            }
            headerRow.addView(tvCheck)

            val tvTowar = TextView(context).apply {
                text = "Towar"
                setPadding(16, 8, 16, 8)
            }
            headerRow.addView(tvTowar)

            val tvIlosc = TextView(context).apply {
                text = "Ilość"
                setPadding(16, 8, 16, 8)
            }
            headerRow.addView(tvIlosc)

        } else {
            // Tylko 1 kolumna: [Towar]
            val tvTowar = TextView(context).apply {
                text = "Towar"
                setPadding(16, 8, 16, 8)
            }
            headerRow.addView(tvTowar)
        }

        tableLayoutTowary.addView(headerRow)
    }

    private fun addTowarRow(towar: Towar) {
        val row = TableRow(context)

        if (isWZMode) {
            // Kolumny: [CheckBox], [Nazwa], [EditText(ilość)]
            val checkBox = CheckBox(context)
            val tvTowar = TextView(context).apply {
                text = towar.nazwa
                setPadding(16, 8, 16, 8)
            }
            val editIlosc = EditText(context).apply {
                hint = "0"
                inputType = InputType.TYPE_CLASS_NUMBER
                setPadding(8, 8, 8, 8)
                setText("0")
            }

            checkBox.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    val ilosc = editIlosc.text.toString().toIntOrNull() ?: 0
                    selectedTowaryForWZ[towar.nazwa] = ilosc
                } else {
                    selectedTowaryForWZ.remove(towar.nazwa)
                }
            }

            // Gdy user przestaje edytować ilość, aktualizujemy mapę (o ile checkbox jest zaznaczony)
            editIlosc.setOnFocusChangeListener { _, hasFocus ->
                if (!hasFocus && checkBox.isChecked) {
                    val ilosc = editIlosc.text.toString().toIntOrNull() ?: 0
                    selectedTowaryForWZ[towar.nazwa] = ilosc
                }
            }

            row.addView(checkBox)
            row.addView(tvTowar)
            row.addView(editIlosc)

        } else {
            // Tryb normalny: tylko nazwa
            val tvTowar = TextView(context).apply {
                text = towar.nazwa
                setPadding(16, 8, 16, 8)
            }
            row.addView(tvTowar)

            // Klik -> zaznaczenie do usunięcia
            row.setOnClickListener {
                toggleRowSelection(row)
            }
        }

        // Do usuwania
        row.tag = towar.key
        tableLayoutTowary.addView(row)
    }

    private fun toggleRowSelection(row: TableRow) {
        // Jeśli klikniemy ponownie w ten sam wiersz -> odznacz
        if (selectedRow == row) {
            row.setBackgroundResource(android.R.color.transparent)
            selectedRow = null
        } else {
            // Odznacz poprzedni
            selectedRow?.setBackgroundResource(android.R.color.transparent)
            // Zaznacz nowy
            row.setBackgroundResource(android.R.color.holo_blue_light)
            selectedRow = row
        }
    }

    private fun showAddTowarDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_towar, null)
        val editTextTowar = dialogView.findViewById<EditText>(R.id.editTextTowar)

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
        val dialog = builder.create()
        dialog.show()

        val buttonAddTowar = dialogView.findViewById<Button>(R.id.btn_dodaj)
        val buttonCancel = dialogView.findViewById<Button>(R.id.btn_anuluj)

        buttonAddTowar.setOnClickListener {
            val towar = editTextTowar.text.toString().trim()
            if (towar.isNotBlank()) {
                // Zapis do bazy
                databaseTowary.push().setValue(towar)
                dialog.dismiss()
            } else {
                Toast.makeText(requireContext(), "Wprowadź nazwę towaru!", Toast.LENGTH_SHORT).show()
            }
        }

        buttonCancel.setOnClickListener {
            dialog.dismiss()
        }
    }

    private fun showDeleteConfirmationDialog(row: TableRow) {
        val key = row.tag as? String ?: return
        val builder = AlertDialog.Builder(requireContext())
            .setMessage("Czy na pewno chcesz usunąć towar?")
            .setPositiveButton("Tak") { _, _ ->
                databaseTowary.child(key).removeValue()
                tableLayoutTowary.removeView(row)
                selectedRow = null
            }
            .setNegativeButton("Nie", null)
        builder.create().show()
    }
}
