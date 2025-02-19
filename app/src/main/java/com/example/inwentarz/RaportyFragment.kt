package com.example.inwentarz

import android.app.DatePickerDialog
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class RaportyFragment : Fragment() {

    // Widoki
    private lateinit var editTextNumerRaportu: EditText
    private lateinit var editTextDataRaportu: EditText
    private lateinit var btnWybierzDate: ImageView
    private lateinit var editTextZakresPrac: EditText
    private lateinit var btnWybierzWZ: Button
    private lateinit var tableTowaryRaport: TableLayout

    private lateinit var btnZapiszRaport: Button
    private lateinit var btnGenerujPDF: Button
    private lateinit var btnEmailRaport: Button
    private lateinit var btnPokazRaporty: Button

    // Firebase
    private val databaseRaporty: DatabaseReference = FirebaseDatabase.getInstance().getReference("Raporty")

    // Baza "Pracownicy" – potrzebne do pobrania imienia i nazwiska
    private lateinit var databasePracownicy: DatabaseReference

    // Przechowujemy imię i nazwisko aktualnie zalogowanego pracownika
    private var currentEmployeeName: String = "Nieznany"

    // Launchery do tworzenia PDF
    private val createPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            savePdfToUri(uri, sendEmail = false)
        } else {
            Toast.makeText(requireContext(), "Anulowano tworzenie PDF", Toast.LENGTH_SHORT).show()
        }
    }
    private val createPdfAndSendEmailLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri: Uri? ->
        if (uri != null) {
            savePdfToUri(uri, sendEmail = true)
        } else {
            Toast.makeText(requireContext(), "Anulowano tworzenie PDF (Email)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_raporty, container, false)

        // Inicjalizacja widoków
        editTextNumerRaportu = view.findViewById(R.id.editTextNumerRaportu)
        editTextDataRaportu = view.findViewById(R.id.editTextDataRaportu)
        btnWybierzDate = view.findViewById(R.id.btnWybierzDate)
        editTextZakresPrac = view.findViewById(R.id.editTextZakresPrac)
        btnWybierzWZ = view.findViewById(R.id.btnWybierzWZ)
        tableTowaryRaport = view.findViewById(R.id.tableTowaryRaport)

        btnZapiszRaport = view.findViewById(R.id.btnZapiszRaport)
        btnGenerujPDF = view.findViewById(R.id.btnGenerujPDF)
        btnEmailRaport = view.findViewById(R.id.btnEmailRaport)
        btnPokazRaporty = view.findViewById(R.id.btnPokazRaporty)

        // Konfiguracja bazy "Pracownicy"
        databasePracownicy = FirebaseDatabase.getInstance().getReference("Pracownicy")

        // Pobieramy imię i nazwisko aktualnego pracownika
        loadCurrentEmployeeName()

        // Domyślna data = dzisiaj
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        editTextDataRaportu.setText(sdf.format(System.currentTimeMillis()))

        // Numer raportu = count+1
        ustalNumerRaportu()

        // Obsługa datepicker
        btnWybierzDate.setOnClickListener {
            pokazDatePicker()
        }

        // Wybierz WZ (przechodzimy do WZFragment)
        btnWybierzWZ.setOnClickListener {
            SharedData.raportySource = true
            (activity as? OknoPracownika)?.viewPager?.currentItem = 0
        }

        // Zapis do bazy
        btnZapiszRaport.setOnClickListener {
            zapiszRaportWBazie()
        }

        // Generuj PDF
        btnGenerujPDF.setOnClickListener {
            val nr = editTextNumerRaportu.text.toString()
            val defaultName = "raport_$nr.pdf"
            createPdfLauncher.launch(defaultName)
        }

        // Email
        btnEmailRaport.setOnClickListener {
            val nr = editTextNumerRaportu.text.toString()
            val defaultName = "raport_$nr.pdf"
            createPdfAndSendEmailLauncher.launch(defaultName)
        }

        // Pokaż Raporty
        btnPokazRaporty.setOnClickListener {
            showRaportyDialog()
        }

        return view
    }

    // Gdy wracamy do RaportyFragment (np. z WZFragment)
    override fun onResume() {
        super.onResume()

        val selectedWZ = SharedData.selectedWZList
        val sumTowary = SharedData.sumaTowarow

        tableTowaryRaport.removeAllViews()

        if (!selectedWZ.isNullOrEmpty()) {
            displayNumeryWZ(selectedWZ)
        }
        if (!sumTowary.isNullOrEmpty()) {
            displaySumaTowarow(sumTowary)
        }
    }

    /**
     * Pobranie imienia i nazwiska z bazy Pracownicy, przypisanie do currentEmployeeName
     */
    private fun loadCurrentEmployeeName() {
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email ?: return
        val emailKey = email.replace(".", "_").replace("@", "_at_")

        databasePracownicy.child(emailKey).child("nazwa")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val nazwa = snapshot.getValue(String::class.java)
                    if (!nazwa.isNullOrBlank()) {
                        currentEmployeeName = nazwa
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Ustalenie numeru raportu = count+1
     */
    private fun ustalNumerRaportu() {
        databaseRaporty.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val count = snapshot.childrenCount
                editTextNumerRaportu.setText((count + 1).toString())
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Zapis raportu do bazy z polem "ktoDodal"
     */
    private fun zapiszRaportWBazie() {
        val nr = editTextNumerRaportu.text.toString()
        val dt = editTextDataRaportu.text.toString()
        val zakr = editTextZakresPrac.text.toString()

        if (nr.isEmpty() || dt.isEmpty()) {
            Toast.makeText(requireContext(), "Numer i Data nie mogą być puste", Toast.LENGTH_SHORT).show()
            return
        }

        val wzList = SharedData.selectedWZList ?: emptyList()
        val sumTowary = SharedData.sumaTowarow ?: emptyList()

        val rap = mapOf(
            "numerRaportu" to nr,
            "data" to dt,
            "zakresPrac" to zakr,
            "wybraneWZ" to wzList,
            "towary" to sumTowary,
            "ktoDodal" to currentEmployeeName  // <--- najprostsze użycie
        )
        databaseRaporty.push().setValue(rap)
        Toast.makeText(requireContext(), "Raport zapisany w bazie", Toast.LENGTH_SHORT).show()
    }

    private fun pokazDatePicker() {
        val cal = Calendar.getInstance()
        val year = cal.get(Calendar.YEAR)
        val month = cal.get(Calendar.MONTH)
        val day = cal.get(Calendar.DAY_OF_MONTH)

        val dp = DatePickerDialog(requireContext(), { _, y, m, d ->
            val c2 = Calendar.getInstance()
            c2.set(y, m, d)
            val f = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            editTextDataRaportu.setText(f.format(c2.time))
        }, year, month, day)

        dp.show()
    }

    /**
     * Tworzenie PDF i ew. wysłanie email
     * Dodajemy logo firmy i pole "ktoDodal"
     */
    private fun savePdfToUri(uri: Uri, sendEmail: Boolean) {
        val pdfDoc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDoc.startPage(pageInfo)
        val canvas = page.canvas

        // Painty
        val paintTitle = android.graphics.Paint().apply {
            textSize = 16f
            isFakeBoldText = true
        }
        val paintText = Paint().apply {
            textSize = 12f
        }

        // Rysowanie logo
        val logoBitmap = BitmapFactory.decodeResource(resources, R.drawable.logo)
        var currentY = 50f
        logoBitmap?.let {
            val scaled = Bitmap.createScaledBitmap(it, 100, 100, true)
            canvas.drawBitmap(scaled, 50f, currentY, paintText)
            currentY += 110f
        }

        // Tytuł
        canvas.drawText("RAPORT", 50f, currentY, paintTitle)
        currentY += 30f

        // Pola
        val nr = editTextNumerRaportu.text.toString()
        val dt = editTextDataRaportu.text.toString()
        val zakr = editTextZakresPrac.text.toString()

        // Kto dodał
        canvas.drawText("Numer Raportu: $nr", 50f, currentY, paintText)
        currentY += 20f
        canvas.drawText("Data: $dt", 50f, currentY, paintText)
        currentY += 20f
        canvas.drawText("Kto dodał: $currentEmployeeName", 50f, currentY, paintText)
        currentY += 20f

        canvas.drawText("Zakres prac:", 50f, currentY, paintText)
        currentY += 20f
        canvas.drawText(zakr, 70f, currentY, paintText)
        currentY += 40f

        // Wybrane WZ
        val wzList = SharedData.selectedWZList ?: emptyList()
        if (wzList.isNotEmpty()) {
            canvas.drawText("Wybrane WZ: ${wzList.joinToString(", ")}", 50f, currentY, paintText)
            currentY += 20f
        }

        // Zsumowane towary
        val sumTowary = SharedData.sumaTowarow ?: emptyList()
        if (sumTowary.isNotEmpty()) {
            canvas.drawText("Towary:", 50f, currentY, paintText)
            currentY += 20f

            for (mapT in sumTowary) {
                val nazwa = mapT["Nazwa"]?.toString() ?: ""
                val iloscStr = mapT["Ilosc"]?.toString() ?: "0"
                canvas.drawText("$nazwa (x$iloscStr)", 70f, currentY, paintText)
                currentY += 20f
            }
        }

        pdfDoc.finishPage(page)
        try {
            val outStream: OutputStream? = requireContext().contentResolver.openOutputStream(uri)
            if (outStream != null) {
                pdfDoc.writeTo(outStream)
                outStream.close()
                Toast.makeText(requireContext(), "PDF zapisany do $uri", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(requireContext(), "Nie udało się otworzyć strumienia do PDF", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(requireContext(), "Błąd zapisu PDF: ${e.message}", Toast.LENGTH_SHORT).show()
        } finally {
            pdfDoc.close()
        }

        if (sendEmail) {
            sendEmailWithAttachment(uri)
        }
    }

    /**
     * Wysyłanie PDF mailem
     */
    private fun sendEmailWithAttachment(pdfUri: Uri) {
        val emailIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "Raport")
            putExtra(android.content.Intent.EXTRA_TEXT, "W załączniku przesyłam raport w formacie PDF.")
            putExtra(android.content.Intent.EXTRA_STREAM, pdfUri)
        }
        if (emailIntent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(emailIntent)
        } else {
            Toast.makeText(requireContext(), "Brak aplikacji do wysyłania emaili", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Wyświetlanie listy raportów w dialogu
     */
    private fun showRaportyDialog() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_lista_raportow, null)
        val tableRaportyList = dialogView.findViewById<TableLayout>(R.id.tableRaportyList)

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Lista Raportów")
            .setView(dialogView)
            .setNegativeButton("Zamknij", null)
        val dialog = builder.create()
        dialog.show()

        // Odczyt raportów
        databaseRaporty.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tableRaportyList.removeAllViews()

                // Nagłówek
                val headerRow = TableRow(context)
                val tvHNum = TextView(context).apply {
                    text = "Nr"
                    setPadding(16, 8, 16, 8)
                }
                val tvHData = TextView(context).apply {
                    text = "Data"
                    setPadding(16, 8, 16, 8)
                }
                val tvHKto = TextView(context).apply {
                    text = "Kto dodał"
                    setPadding(16, 8, 16, 8)
                }
                val tvHDet = TextView(context).apply {
                    text = "" // kolumna na przycisk
                    setPadding(16, 8, 16, 8)
                }

                headerRow.addView(tvHNum)
                headerRow.addView(tvHData)
                headerRow.addView(tvHKto)
                headerRow.addView(tvHDet)
                tableRaportyList.addView(headerRow)

                // Wiersze
                for (child in snapshot.children) {
                    val rap = child.getValue(Raport::class.java)
                    if (rap != null) {
                        val row = TableRow(context)

                        val tvNum = TextView(context).apply {
                            text = rap.numerRaportu
                            setPadding(16, 8, 16, 8)
                        }
                        val tvData = TextView(context).apply {
                            text = rap.data
                            setPadding(16, 8, 16, 8)
                        }
                        val tvKto = TextView(context).apply {
                            text = rap.ktoDodal
                            setPadding(16, 8, 16, 8)
                        }
                        val btnDetails = Button(context).apply {
                            text = "Szczegóły"
                            setPadding(16, 8, 16, 8)
                            setOnClickListener {
                                showRaportDetailsDialog(rap)
                            }
                        }

                        row.addView(tvNum)
                        row.addView(tvData)
                        row.addView(tvKto)
                        row.addView(btnDetails)

                        tableRaportyList.addView(row)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    /**
     * Drugi dialog: szczegóły raportu
     */
    private fun showRaportDetailsDialog(rap: Raport) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_szczegoly_raportu, null)

        val tvNumer = dialogView.findViewById<TextView>(R.id.tvNumerRaportu)
        val tvData = dialogView.findViewById<TextView>(R.id.tvDataRaportu)
        val tvKto = dialogView.findViewById<TextView>(R.id.tvKtoDodalRaport)
        val tvZakres = dialogView.findViewById<TextView>(R.id.tvZakresPracRaport)

        val tableWZList = dialogView.findViewById<TableLayout>(R.id.tableWZRaport)
        val tableTowary = dialogView.findViewById<TableLayout>(R.id.tableTowaryRaportSzczegoly)

        // Uzupełnienie
        tvNumer.text = rap.numerRaportu
        tvData.text = rap.data
        tvKto.text = rap.ktoDodal
        tvZakres.text = rap.zakresPrac

        // WZ
        tableWZList.removeAllViews()
        if (rap.wybraneWZ.isNotEmpty()) {
            val rowHeader = TableRow(context)
            val tvH = TextView(context).apply {
                text = "Numery WZ:"
                setPadding(8, 8, 8, 8)
            }
            rowHeader.addView(tvH)
            tableWZList.addView(rowHeader)

            val rowVals = TableRow(context)
            val tvV = TextView(context).apply {
                text = rap.wybraneWZ.joinToString(", ")
                setPadding(8, 8, 8, 8)
            }
            rowVals.addView(tvV)
            tableWZList.addView(rowVals)
        }

        // Towary
        tableTowary.removeAllViews()
        if (rap.towary.isNotEmpty()) {
            val rowH = TableRow(context)
            val tvHNazwa = TextView(context).apply {
                text = "Nazwa"
                setPadding(8, 8, 8, 8)
            }
            val tvHIlosc = TextView(context).apply {
                text = "Ilość"
                setPadding(8, 8, 8, 8)
            }
            rowH.addView(tvHNazwa)
            rowH.addView(tvHIlosc)
            tableTowary.addView(rowH)

            for (t in rap.towary) {
                val rowX = TableRow(context)
                val nazwa = t["Nazwa"]?.toString() ?: ""
                val iloscAny = t["Ilosc"]
                val ilosc = if (iloscAny is Number) iloscAny.toInt() else 0

                val tvN = TextView(context).apply {
                    text = nazwa
                    setPadding(8, 8, 8, 8)
                }
                val tvI = TextView(context).apply {
                    text = ilosc.toString()
                    setPadding(8, 8, 8, 8)
                }
                rowX.addView(tvN)
                rowX.addView(tvI)
                tableTowary.addView(rowX)
            }
        }

        val builder = AlertDialog.Builder(requireContext())
            .setTitle("Szczegóły Raportu")
            .setView(dialogView)
            .setPositiveButton("OK", null)
        builder.create().show()
    }

    // Prosty model Raport: z "ktoDodal"
    data class Raport(
        val numerRaportu: String = "",
        val data: String = "",
        val zakresPrac: String = "",
        val wybraneWZ: List<String> = emptyList(),
        val towary: List<Map<String, Any>> = emptyList(),
        val ktoDodal: String = ""  // <--- najprostsze
    )

    /**
     * Wyświetlanie WZ
     */
    private fun displayNumeryWZ(list: List<String>) {
        val rowHeader = TableRow(context)
        val tvHeader = TextView(context).apply {
            text = "Numery WZ:"
            setPadding(16,8,16,8)
        }
        rowHeader.addView(tvHeader)
        tableTowaryRaport.addView(rowHeader)

        val row = TableRow(context)
        val tv = TextView(context).apply {
            text = list.joinToString(", ")
            setPadding(16,8,16,8)
        }
        row.addView(tv)
        tableTowaryRaport.addView(row)
    }

    /**
     * Wyświetlanie sumy towarów
     */
    private fun displaySumaTowarow(sumTowary: List<Map<String, Any>>) {
        val rowHeader = TableRow(context)
        val tvH1 = TextView(context).apply {
            text = "Nazwa"
            setPadding(16,8,16,8)
        }
        val tvH2 = TextView(context).apply {
            text = "Ilość"
            setPadding(16,8,16,8)
        }
        rowHeader.addView(tvH1)
        rowHeader.addView(tvH2)
        tableTowaryRaport.addView(rowHeader)

        for (m in sumTowary) {
            val nazwa = m["Nazwa"]?.toString() ?: ""
            val iloscAny = m["Ilosc"]
            val ilosc = if (iloscAny is Number) iloscAny.toInt() else 0

            val row = TableRow(context)
            val tvN = TextView(context).apply {
                text = nazwa
                setPadding(16,8,16,8)
            }
            val tvI = TextView(context).apply {
                text = ilosc.toString()
                setPadding(16,8,16,8)
            }
            row.addView(tvN)
            row.addView(tvI)
            tableTowaryRaport.addView(row)
        }
    }
}
