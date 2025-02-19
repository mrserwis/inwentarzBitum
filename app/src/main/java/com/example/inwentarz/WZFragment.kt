package com.example.inwentarz

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import java.io.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class WZFragment : Fragment(R.layout.fragment_wz) {

    // Główne przyciski
    private lateinit var btnDodaj: Button
    private lateinit var btnPodglad: Button
    private lateinit var btnZatwierdzWyborWZ: Button
    private lateinit var btnUsunWZ: Button

    // Filtry
    private lateinit var btnToggleFiltry: Button
    private lateinit var filtersContainer: LinearLayout
    private var filtersVisible = true

    private lateinit var buttonFiltruj: Button
    private lateinit var checkBoxDataOd: CheckBox
    private lateinit var editTextDataOd: EditText
    private lateinit var btnDataOd: ImageButton
    private lateinit var checkBoxDataDo: CheckBox
    private lateinit var editTextDataDo: EditText
    private lateinit var btnDataDo: ImageButton

    private lateinit var checkBoxPracownikFilter: CheckBox
    private lateinit var spinnerPracownikFilter: Spinner
    private lateinit var checkBoxDostawaOd: CheckBox
    private lateinit var spinnerDostawaOd: Spinner
    private lateinit var checkBoxDostawaDo: CheckBox
    private lateinit var spinnerDostawaDo: Spinner

    private lateinit var tableLayout: TableLayout

    // Firebase
    private lateinit var databaseWZki: DatabaseReference
    private lateinit var databaseOdbiorcy: DatabaseReference
    private lateinit var databasePracownicy: DatabaseReference

    // Dane
    private var currentEmployeeName: String? = null
    private val wzDataList = mutableListOf<Pair<WZ, String>>()

    // Selekcja
    private var selectedRow: TableRow? = null
    private var selectedWZ: WZ? = null
    private var lastClickTime = 0L

    // Raporty
    private val selectedWZListForRaport = mutableListOf<String>()
    private var dialogWZ: AlertDialog? = null

    // Model WZ
    data class WZ(
        val numerWZ: String = "",
        val data: String = "",
        val ktoDodal: String = "",
        val odbiorca: String = "",
        val dostawa: String = "",
        val komentarz: String = "",
        val towary: List<Map<String, Any>> = emptyList(),
        val zwrot: Boolean = false  // ewentualnie jeśli chcesz przechowywać flagę
    ) : Serializable

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_wz, container, false)

        // Inicjalizacja widoków
        btnDodaj = view.findViewById(R.id.btn_dodaj)
        btnPodglad = view.findViewById(R.id.btn_podglad)
        btnZatwierdzWyborWZ = view.findViewById(R.id.btnZatwierdzWyborWZ)
        btnUsunWZ = view.findViewById(R.id.btn_usun_wz)

        btnToggleFiltry = view.findViewById(R.id.btnToggleFiltry)
        filtersContainer = view.findViewById(R.id.filtersContainer)
        buttonFiltruj = view.findViewById(R.id.buttonFiltruj)

        checkBoxDataOd = view.findViewById(R.id.checkBoxDataOd)
        editTextDataOd = view.findViewById(R.id.editTextDataOd)
        btnDataOd = view.findViewById(R.id.btnDataOd)
        checkBoxDataDo = view.findViewById(R.id.checkBoxDataDo)
        editTextDataDo = view.findViewById(R.id.editTextDataDo)
        btnDataDo = view.findViewById(R.id.btnDataDo)

        checkBoxPracownikFilter = view.findViewById(R.id.checkBoxPracownikFilter)
        spinnerPracownikFilter = view.findViewById(R.id.spinner_pracownik)
        checkBoxDostawaOd = view.findViewById(R.id.checkBoxDostawaOd)
        spinnerDostawaOd = view.findViewById(R.id.spinner_odbiorca1)
        checkBoxDostawaDo = view.findViewById(R.id.checkBoxDostawaDo)
        spinnerDostawaDo = view.findViewById(R.id.spinner_dostawa1)

        tableLayout = view.findViewById(R.id.table_layout)

        // Firebase
        databaseWZki = Firebase.database.getReference("WZki")
        databaseOdbiorcy = Firebase.database.getReference("Odbiorcy")
        databasePracownicy = Firebase.database.getReference("Pracownicy")

        // Ładowanie danych do filtrów
        loadCurrentEmployeeName()
        loadPracownicyData()
        loadOdbiorcyDataForOd()
        loadOdbiorcyDataForDo()

        // Ładujemy WZ
        loadWZkiData()

        // Obsługa przycisków
        btnDodaj.setOnClickListener {
            showAddWZDialog()
        }
        btnPodglad.setOnClickListener {
            openPodgladFor(selectedWZ)
        }
        btnUsunWZ.setOnClickListener {
            if (selectedRow == null || selectedWZ == null) {
                Toast.makeText(requireContext(), "Nie zaznaczono WZ do usunięcia", Toast.LENGTH_SHORT).show()
            } else {
                AlertDialog.Builder(requireContext())
                    .setMessage("Czy na pewno chcesz usunąć zaznaczoną WZ?")
                    .setPositiveButton("Tak") { _, _ ->
                        usunZaznaczonaWZ()
                    }
                    .setNegativeButton("Nie", null)
                    .show()
            }
        }

        btnToggleFiltry.setOnClickListener {
            filtersVisible = !filtersVisible
            filtersContainer.visibility = if (filtersVisible) View.VISIBLE else View.GONE
            btnToggleFiltry.text = if (filtersVisible) "Ukryj filtry" else "Pokaż filtry"
        }

        btnDataOd.setOnClickListener {
            pickDate { editTextDataOd.setText(it) }
        }
        btnDataDo.setOnClickListener {
            pickDate { editTextDataDo.setText(it) }
        }

        buttonFiltruj.setOnClickListener {
            applyFilter()
        }

        // Raporty -> Zatwierdź WZ
        if (SharedData.raportySource) {
            btnZatwierdzWyborWZ.visibility = View.VISIBLE
            btnZatwierdzWyborWZ.setOnClickListener {
                SharedData.selectedWZList = selectedWZListForRaport
                SharedData.sumaTowarow = sumujTowaryWybranychWZ()
                SharedData.raportySource = false
                // Przejście do Raporty (index 4)
                (activity as? OknoPracownika)?.viewPager?.currentItem = 4
            }
        } else {
            btnZatwierdzWyborWZ.visibility = View.GONE
        }

        return view
    }

    // JEDYNA definicja onResume()
    override fun onResume() {
        super.onResume()
        // Jeśli mieliśmy otwarty dialog, przywracamy go
        if (SharedData.DialogState.isDialogVisible) {
            showAddWZDialog()
        }
    }

    // pickDate
    private fun pickDate(callback: (String) -> Unit) {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
            val c2 = Calendar.getInstance()
            c2.set(y, m, d)
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            callback(sdf.format(c2.time))
        }, year, month, day)
        dp.show()
    }

    // Ładowanie "ktoDodal"
    private fun loadCurrentEmployeeName() {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: return
        val emailKey = email.replace(".", "_").replace("@", "_at_")

        databasePracownicy.child(emailKey).child("nazwa")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val nazwa = snapshot.getValue(String::class.java)
                    if (!nazwa.isNullOrBlank()) {
                        currentEmployeeName = nazwa
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    // Spinner pracownik
    private fun loadPracownicyData() {
        databasePracownicy.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val pracownicyList = mutableListOf<String>()
                for (child in snapshot.children) {
                    val nazwa = child.child("nazwa").getValue(String::class.java)
                    if (!nazwa.isNullOrEmpty()) {
                        pracownicyList.add(nazwa)
                    }
                }
                context?.let { ctx ->
                    val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, pracownicyList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerPracownikFilter.adapter = adapter
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Spinner Odbiorca (DostawaOd)
    private fun loadOdbiorcyDataForOd() {
        databaseOdbiorcy.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val odList = mutableListOf<String>()
                for (child in snapshot.children) {
                    val nazwa = child.child("nazwa").getValue(String::class.java)
                    if (!nazwa.isNullOrBlank()) {
                        odList.add(nazwa)
                    }
                }
                context?.let { ctx ->
                    val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, odList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerDostawaOd.adapter = adapter
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Spinner DostawaDo
    private fun loadOdbiorcyDataForDo() {
        databaseOdbiorcy.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val doList = mutableListOf<String>()
                for (child in snapshot.children) {
                    val nazwa = child.child("nazwa").getValue(String::class.java)
                    if (!nazwa.isNullOrBlank()) {
                        doList.add(nazwa)
                    }
                }
                context?.let { ctx ->
                    val adapter = ArrayAdapter(ctx, android.R.layout.simple_spinner_item, doList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerDostawaDo.adapter = adapter
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Ładowanie WZ
    private fun loadWZkiData() {
        databaseWZki.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                wzDataList.clear()
                for (child in snapshot.children) {
                    val wz = child.getValue(WZ::class.java)
                    if (wz != null) {
                        val wzKey = child.key ?: ""
                        wzDataList.add(wz to wzKey)
                    }
                }
                displayWZTable(wzDataList)
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    // Wyświetlanie WZ
    private fun displayWZTable(dataList: List<Pair<WZ, String>>) {
        tableLayout.removeAllViews()

        val headerRow = TableRow(context)
        if (SharedData.raportySource) {
            val checkHeader = TextView(context).apply {
                text = ""
                setPadding(8,8,8,8)
            }
            headerRow.addView(checkHeader)
        }

        val headerNum = TextView(context).apply {
            text = "Numer WZ"
            setPadding(16,8,16,8)
        }
        val headerKto = TextView(context).apply {
            text = "Kto dodał"
            setPadding(16,8,16,8)
        }
        val headerData = TextView(context).apply {
            text = "Data"
            setPadding(16,8,16,8)
        }
        val headerOdb = TextView(context).apply {
            text = "Odbiorca"
            setPadding(16,8,16,8)
        }
        val headerDos = TextView(context).apply {
            text = "Dostawa"
            setPadding(16,8,16,8)
        }
        val headerKom = TextView(context).apply {
            text = "Komentarz"
            setPadding(16,8,16,8)
        }

        headerRow.addView(headerNum)
        headerRow.addView(headerKto)
        headerRow.addView(headerData)
        headerRow.addView(headerOdb)
        headerRow.addView(headerDos)
        headerRow.addView(headerKom)

        tableLayout.addView(headerRow)

        for ((wz, key) in dataList) {
            val row = TableRow(context)

            if (SharedData.raportySource) {
                val checkBox = CheckBox(context)
                checkBox.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) selectedWZListForRaport.add(wz.numerWZ)
                    else selectedWZListForRaport.remove(wz.numerWZ)
                }
                row.addView(checkBox)
            }

            val tvNum = TextView(context).apply {
                text = wz.numerWZ
                setPadding(16,8,16,8)
            }
            val tvKto = TextView(context).apply {
                text = wz.ktoDodal
                setPadding(16,8,16,8)
            }
            val tvData = TextView(context).apply {
                text = wz.data
                setPadding(16,8,16,8)
            }
            val tvOdb = TextView(context).apply {
                text = wz.odbiorca
                setPadding(16,8,16,8)
            }
            val tvDos = TextView(context).apply {
                text = wz.dostawa
                setPadding(16,8,16,8)
            }
            val tvKom = TextView(context).apply {
                text = wz.komentarz
                setPadding(16,8,16,8)
            }

            row.addView(tvNum)
            row.addView(tvKto)
            row.addView(tvData)
            row.addView(tvOdb)
            row.addView(tvDos)
            row.addView(tvKom)

            row.tag = key

            if (!SharedData.raportySource) {
                row.setOnClickListener {
                    val currTime = System.currentTimeMillis()
                    val delta = currTime - lastClickTime
                    lastClickTime = currTime

                    if (delta < 300) {
                        // double click
                        openPodgladFor(wz)
                    } else {
                        // single click
                        selectedRow?.setBackgroundResource(
                            ContextCompat.getColor(requireContext(), android.R.color.transparent)
                        )
                        row.setBackgroundResource(R.color.light_blue)
                        selectedRow = row
                        selectedWZ = wz
                    }
                }
            }
            tableLayout.addView(row)
        }
    }

    private fun openPodgladFor(wz: WZ?) {
        if (wz == null) {
            Toast.makeText(requireContext(), "Nie wybrano WZ!", Toast.LENGTH_SHORT).show()
            return
        }
        SharedData.currentWZ = wz
        (activity as? OknoPracownika)?.viewPager?.currentItem = 3
    }

    private fun usunZaznaczonaWZ() {
        val wzKey = selectedRow?.tag as? String ?: return
        databaseWZki.child(wzKey).removeValue()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), "Usunięto WZ", Toast.LENGTH_SHORT).show()
                tableLayout.removeView(selectedRow)
                selectedRow = null
                selectedWZ = null
            }
            .addOnFailureListener {
                Toast.makeText(requireContext(), "Błąd usuwania: ${it.message}", Toast.LENGTH_SHORT).show()
            }
    }

    // Funkcja do filtrowania
    private fun applyFilter() {
        var filtered = wzDataList.toList()

        // Data od
        if (checkBoxDataOd.isChecked) {
            val dataOdStr = editTextDataOd.text.toString().trim()
            if (dataOdStr.isNotEmpty()) {
                filtered = filtered.filter { (wz, _) -> wz.data >= dataOdStr }
            }
        }
        // Data do
        if (checkBoxDataDo.isChecked) {
            val dataDoStr = editTextDataDo.text.toString().trim()
            if (dataDoStr.isNotEmpty()) {
                filtered = filtered.filter { (wz, _) -> wz.data <= dataDoStr }
            }
        }
        // Pracownik
        if (checkBoxPracownikFilter.isChecked) {
            val p = spinnerPracownikFilter.selectedItem?.toString() ?: ""
            if (p.isNotEmpty()) {
                filtered = filtered.filter { (wz, _) -> wz.ktoDodal == p }
            }
        }
        // Dostawa od (odbiorca)
        if (checkBoxDostawaOd.isChecked) {
            val od = spinnerDostawaOd.selectedItem?.toString() ?: ""
            if (od.isNotEmpty()) {
                filtered = filtered.filter { (wz, _) -> wz.odbiorca == od }
            }
        }
        // Dostawa do
        if (checkBoxDostawaDo.isChecked) {
            val dd = spinnerDostawaDo.selectedItem?.toString() ?: ""
            if (dd.isNotEmpty()) {
                filtered = filtered.filter { (wz, _) -> wz.dostawa == dd }
            }
        }

        displayWZTable(filtered)
    }

    // Suma towarów dla raportu
    private fun sumujTowaryWybranychWZ(): List<Map<String, Any>> {
        val towarMap = mutableMapOf<String, Int>()
        for ((wz, _) in wzDataList) {
            if (selectedWZListForRaport.contains(wz.numerWZ)) {
                for (t in wz.towary) {
                    val nazwa = t["Nazwa"]?.toString() ?: ""
                    val iloscAny = t["Ilosc"]
                    val ilosc = if (iloscAny is Number) iloscAny.toInt() else 0
                    towarMap[nazwa] = (towarMap[nazwa] ?: 0) + ilosc
                }
            }
        }
        return towarMap.map { (n, s) ->
            mapOf("Nazwa" to n, "Ilosc" to s)
        }
    }

    // Dialog do dodawania WZ
    private fun showAddWZDialog() {
        if (dialogWZ != null && dialogWZ!!.isShowing) {
            return
        }
        val dialogView = layoutInflater.inflate(R.layout.dialog_add_wz, null)

        val editTextNumerWZ = dialogView.findViewById<EditText>(R.id.editTextNumerWZ)
        val spinnerOdbiorcaDialog = dialogView.findViewById<Spinner>(R.id.SpinnerOdbiorca)
        val spinnerDostawaDialog = dialogView.findViewById<Spinner>(R.id.SpinnerDostawa)
        val editTextKomentarz = dialogView.findViewById<EditText>(R.id.editTextKomentarz)

        val checkBoxZwrot = dialogView.findViewById<CheckBox>(R.id.checkBoxZwrot)
        val btnWybierzTowary = dialogView.findViewById<Button>(R.id.btn_wybierz_towary)
        val btnZapisz = dialogView.findViewById<Button>(R.id.buttonAddWZ)
        val btnCancel = dialogView.findViewById<Button>(R.id.buttonCancel)

        val builder = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setCancelable(true)
        val dialog = builder.create()
        dialogWZ = dialog
        dialog.show()

        // Przywracamy stan
        editTextNumerWZ.setText(SharedData.DialogState.numerWZ)
        editTextKomentarz.setText(SharedData.DialogState.komentarz)
        checkBoxZwrot.isChecked = false

        // Ładowanie Odbiorców do spinnerów w dialogu
        databaseOdbiorcy.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!isAdded) return
                val odbiorcyList = mutableListOf<String>()
                for (child in snapshot.children) {
                    val nazwa = child.child("nazwa").getValue(String::class.java)
                    if (!nazwa.isNullOrBlank()) {
                        odbiorcyList.add(nazwa)
                    }
                }
                context?.let { c ->
                    val adapter = ArrayAdapter(c, android.R.layout.simple_spinner_item, odbiorcyList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerOdbiorcaDialog.adapter = adapter
                    spinnerDostawaDialog.adapter = adapter

                    val idxOdb = odbiorcyList.indexOf(SharedData.DialogState.odbiorca)
                    if (idxOdb >= 0) spinnerOdbiorcaDialog.setSelection(idxOdb)

                    val idxDos = odbiorcyList.indexOf(SharedData.DialogState.dostawa)
                    if (idxDos >= 0) spinnerDostawaDialog.setSelection(idxDos)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })

        // Numer WZ = (count+1) jeśli pusty
        if (SharedData.DialogState.numerWZ.isEmpty()) {
            databaseWZki.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (!isAdded) return
                    val count = snapshot.childrenCount
                    editTextNumerWZ.setText((count + 1).toString())
                    SharedData.DialogState.numerWZ = (count + 1).toString()
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }

        // Przycisk "Wybierz Towary"
        btnWybierzTowary.setOnClickListener {
            SharedData.DialogState.numerWZ = editTextNumerWZ.text.toString()
            SharedData.DialogState.komentarz = editTextKomentarz.text.toString()
            SharedData.DialogState.odbiorca = spinnerOdbiorcaDialog.selectedItem?.toString() ?: ""
            SharedData.DialogState.dostawa = spinnerDostawaDialog.selectedItem?.toString() ?: ""
            SharedData.DialogState.isDialogVisible = true

            SharedData.towaryForWZMode = true
            SharedData.selectedTowary = null

            dialog.dismiss()
            dialogWZ = null
            (activity as? OknoPracownika)?.viewPager?.currentItem = 2
        }

        // "Zapisz"
        btnZapisz.setOnClickListener {
            val numerWZ = editTextNumerWZ.text.toString().trim()
            val komentarz = editTextKomentarz.text.toString().trim()
            val odbiorca = spinnerOdbiorcaDialog.selectedItem?.toString() ?: ""
            val dostawa = spinnerDostawaDialog.selectedItem?.toString() ?: ""
            val isZwrot = checkBoxZwrot.isChecked

            if (numerWZ.isBlank()) {
                Toast.makeText(requireContext(), "Brak numeru WZ", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val currentDateStr = dateFormat.format(System.currentTimeMillis())
            val towaryList = SharedData.selectedTowary ?: emptyList()

            val newWZ = mapOf(
                "numerWZ" to numerWZ,
                "data" to currentDateStr,
                "ktoDodal" to (currentEmployeeName ?: ""),
                "odbiorca" to odbiorca,
                "dostawa" to dostawa,
                "komentarz" to komentarz,
                "towary" to towaryList,
                "zwrot" to isZwrot
            )

            // Zapis WZ do bazy
            databaseWZki.push().setValue(newWZ)
                .addOnSuccessListener {
                    // Aktualizacja stanów -> UŻYWAMY "dostawa"
                    updateStanyMagazynowe(dostawa, towaryList, isZwrot)
                }
                .addOnFailureListener {
                    Toast.makeText(requireContext(), "Błąd zapisu WZ: ${it.message}", Toast.LENGTH_SHORT).show()
                }

            // Czyścimy stan dialogu
            SharedData.DialogState.numerWZ = ""
            SharedData.DialogState.odbiorca = ""
            SharedData.DialogState.dostawa = ""
            SharedData.DialogState.komentarz = ""
            SharedData.DialogState.isDialogVisible = false
            SharedData.selectedTowary = null

            dialog.dismiss()
            dialogWZ = null
        }

        // "Anuluj"
        btnCancel.setOnClickListener {
            dialog.dismiss()
            dialogWZ = null
            SharedData.DialogState.isDialogVisible = false
        }
    }

    // Aktualizacja stanów -> klucz to "dostawa"
    private fun updateStanyMagazynowe(
        dostawa: String,
        towaryList: List<Map<String, Any>>,
        isZwrot: Boolean
    ) {
        if (dostawa.isBlank()) return
        val stanRef = FirebaseDatabase.getInstance().getReference("Stany").child(dostawa)

        for (t in towaryList) {
            val nazwaTowaru = t["Nazwa"]?.toString() ?: continue
            val iloscAny = t["Ilosc"]
            val ilosc = if (iloscAny is Number) iloscAny.toInt() else 0

            val towarRef = stanRef.child(nazwaTowaru)
            towarRef.addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val currentStan = snapshot.getValue(Int::class.java) ?: 0
                    val newStan = if (isZwrot) (currentStan - ilosc) else (currentStan + ilosc)
                    towarRef.setValue(newStan)
                }
                override fun onCancelled(error: DatabaseError) {}
            })
        }
    }
}
