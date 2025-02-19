package com.example.inwentarz

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.database.*

class StanyFragment : Fragment() {

    private lateinit var tableStany: TableLayout
    private lateinit var databaseStany: DatabaseReference
    private lateinit var spinnerOdbiorcaStany: Spinner
    private lateinit var btnWyswietlStany: Button

    private lateinit var databaseOdbiorcy: DatabaseReference
    private val odbiorcyList = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_stany, container, false)

        tableStany = view.findViewById(R.id.table_stany)
        spinnerOdbiorcaStany = view.findViewById(R.id.spinnerOdbiorcaStany)
        btnWyswietlStany = view.findViewById(R.id.btnWyswietlStany)

        databaseStany = FirebaseDatabase.getInstance().getReference("Stany")
        databaseOdbiorcy = FirebaseDatabase.getInstance().getReference("Odbiorcy")

        loadOdbiorcyList()

        btnWyswietlStany.setOnClickListener {
            val odb = spinnerOdbiorcaStany.selectedItem?.toString() ?: ""
            if (odb.isNotEmpty()) {
                loadStanFor(odb)
            }
        }

        return view
    }

    private fun loadOdbiorcyList() {
        databaseOdbiorcy.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                odbiorcyList.clear()
                for (child in snapshot.children) {
                    val nazwa = child.child("nazwa").getValue(String::class.java)
                    if (!nazwa.isNullOrBlank()) {
                        odbiorcyList.add(nazwa)
                    }
                }
                context?.let {
                    val adapter = ArrayAdapter(it, android.R.layout.simple_spinner_item, odbiorcyList)
                    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                    spinnerOdbiorcaStany.adapter = adapter
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun loadStanFor(odbiorca: String) {
        // Odczyt "Stany/odbiorca"
        databaseStany.child(odbiorca).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                tableStany.removeAllViews()

                // Nagłówek
                val headerRow = TableRow(context)
                val tvHTowar = TextView(context).apply {
                    text = "Towar"
                    setPadding(16,8,16,8)
                }
                val tvHStan = TextView(context).apply {
                    text = "Stan"
                    setPadding(16,8,16,8)
                }
                headerRow.addView(tvHTowar)
                headerRow.addView(tvHStan)
                tableStany.addView(headerRow)

                // Wiersze
                for (child in snapshot.children) {
                    val towarName = child.key ?: continue
                    val stan = child.getValue(Int::class.java) ?: 0

                    val row = TableRow(context)
                    val tvN = TextView(context).apply {
                        text = towarName
                        setPadding(16,8,16,8)
                    }
                    val tvS = TextView(context).apply {
                        text = stan.toString()
                        setPadding(16,8,16,8)
                    }

                    row.addView(tvN)
                    row.addView(tvS)
                    tableStany.addView(row)
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }
}
