package com.example.inwentarz

object SharedData {

    // --------------------- RAPORTY ---------------------
    /** Flaga, czy RaportyFragment przekierowuje do WZFragment */
    var raportySource: Boolean = false

    /** Lista wybranych WZ (np. numery WZ), zwracana z WZFragment do RaportyFragment */
    var selectedWZList: MutableList<String>? = null

    /** Suma towarów wybranych WZ – jeśli potrzebne do RaportyFragment */
    var sumaTowarow: List<Map<String, Any>>? = null

    // --------------------- WZ PODGLĄD ---------------------
    /** Pole do podglądu WZ */
    var currentWZ: WZFragment.WZ? = null

    // --------------------- TRYB TOWARY ---------------------
    /** Czy TowaryFragment jest otwierany w trybie WZ (checkboxy i ilości) */
    var towaryForWZMode: Boolean = false

    /** Lista towarów wybranych w TowaryFragment do WZ (checkbox + ilość) */
    var selectedTowary: List<Map<String, Any>>? = null
    var kontrahentMode: String? = null
    var selectedKontrahent: String? = null
    // --------------------- STAN DIALOGU "DODAJ WZ" ---------------------
    /** Obiekt przechowujący stan dialogu, by można go odtworzyć po powrocie z TowaryFragment */
    object DialogState {
        var numerWZ: String = ""
        var odbiorca: String = ""
        var dostawa: String = ""
        var komentarz: String = ""
        var isDialogVisible: Boolean = false  // czy dialog ma być ponownie wyświetlony w WZFragment
    }

}
