package com.example.inwentarz

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

/**
 * Własny obiekt z definicją stałej wersji
 * (zamiast BuildConfig.VERSION_CODE).
 */
object AppConstants {
    // Ustaw tu swoją obecną wersję aplikacji
    const val VERSION_CODE = 1
}

/**
 * Fragment profilu (awatar, e-mail, status, zgłaszanie uwag, sprawdzanie aktualizacji)
 */
class ProfilFragment : Fragment(R.layout.fragment_profil) {

    private lateinit var imageAvatar: ImageView
    private lateinit var textUserEmail: TextView
    private lateinit var textUserStatus: TextView
    private lateinit var buttonZglosUwagi: Button
    private lateinit var buttonCheckUpdate: Button

    // Firebase
    private lateinit var databasePracownicy: DatabaseReference
    private lateinit var databaseUpdates: DatabaseReference

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_profil, container, false)

        // Inicjalizacja widoków
        imageAvatar = view.findViewById(R.id.imageViewAvatar)
        textUserEmail = view.findViewById(R.id.textUserEmail)
        textUserStatus = view.findViewById(R.id.textUserStatus)
        buttonZglosUwagi = view.findViewById(R.id.buttonZglosUwagi)
        buttonCheckUpdate = view.findViewById(R.id.buttonCheckUpdate)

        // Firebase referencje (np. "Pracownicy", "updates")
        databasePracownicy = FirebaseDatabase.getInstance().getReference("Pracownicy")
        databaseUpdates = FirebaseDatabase.getInstance().getReference("updates")

        // Pobieramy zalogowanego usera
        val user = FirebaseAuth.getInstance().currentUser
        val email = user?.email

        if (email != null) {
            textUserEmail.text = email
            loadUserStatus(email)
        } else {
            textUserEmail.text = "Niezalogowany"
            textUserStatus.text = ""
        }

        // "Zgłoś uwagi" -> mail
        buttonZglosUwagi.setOnClickListener {
            sendEmailUwagi()
        }

        // "Sprawdź aktualizację"
        buttonCheckUpdate.setOnClickListener {
            checkForUpdate()
        }

        return view
    }

    /**
     * Pobiera status użytkownika z bazy (np. "kierownik" / "pracownik")
     */
    private fun loadUserStatus(email: String) {
        val emailKey = email.replace(".", "_").replace("@", "_at_")
        databasePracownicy.child(emailKey).child("status")
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java)
                    if (!status.isNullOrBlank()) {
                        textUserStatus.text = "Status: $status"
                    } else {
                        textUserStatus.text = "Status: nieznany"
                    }
                }
                override fun onCancelled(error: DatabaseError) {}
            })
    }

    /**
     * Wysyła maila z uwagami do marcin.radzikowski94@gmail.com
     */
    private fun sendEmailUwagi() {
        val recipient = "marcin.radzikowski94@gmail.com"
        val subject = "Uwagi do aplikacji Inwentarz"
        val body = "Wpisz swoje uwagi..."

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf(recipient))
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }

        if (intent.resolveActivity(requireContext().packageManager) != null) {
            startActivity(intent)
        } else {
            Toast.makeText(requireContext(), "Brak aplikacji do e-mail!", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Sprawdza aktualizację:
     *  - Bieżąca wersja = AppConstants.VERSION_CODE
     *  - W "updates/latestVersionCode" w Firebase -> int
     *  - W "updates/downloadUrl" -> link do .apk / Sklepu
     */
    private fun checkForUpdate() {
        val currentVersion = AppConstants.VERSION_CODE // <-- Własna stała

        // Odczyt z "updates"
        databaseUpdates.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Zakładamy, że latestVersionCode jest int w Firebase
                val latestVersionCode = snapshot.child("latestVersionCode")
                    .getValue(Int::class.java) ?: 1
                val downloadUrl = snapshot.child("downloadUrl")
                    .getValue(String::class.java) ?: ""

                if (latestVersionCode > currentVersion) {
                    Toast.makeText(requireContext(),
                        "Nowa wersja dostępna (v$latestVersionCode)!",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Otwieramy link w przeglądarce (pobranie .apk lub Play Store)
                    if (downloadUrl.isNotEmpty()) {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                        startActivity(intent)
                    }
                } else {
                    Toast.makeText(requireContext(),
                        "Masz najnowszą wersję (v$currentVersion).",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(requireContext(),
                    "Błąd: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
}
