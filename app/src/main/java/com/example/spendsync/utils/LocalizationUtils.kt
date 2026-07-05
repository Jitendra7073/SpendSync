package com.example.spendsync.utils

import java.time.LocalDate
import java.time.format.DateTimeFormatter

object LocalizationUtils {

    // Dynamic translation provider supporting English, Spanish, French, German, and Hindi
    fun getTranslation(key: String, language: String): String {
        val en = mapOf(
            "home" to "Home",
            "total_balance" to "Total Balance",
            "recent_transactions" to "Recent Transactions",
            "income" to "Income",
            "expenses" to "Expenses",
            "savings" to "Savings",
            "analytics" to "Analytics",
            "budget" to "Budget",
            "profile" to "Profile",
            "settings" to "Settings",
            "all" to "All",
            "personalisation" to "Personalisation",
            "general" to "General",
            "data" to "Data",
            "support" to "Support",
            "dark_mode" to "Dark Mode",
            "accent_colour" to "Accent Colour",
            "language" to "Language",
            "currency" to "Currency",
            "date_format" to "Date Format",
            "push_notifications" to "Push Notifications",
            "biometric_lock" to "Biometric Lock",
            "change_pin" to "Change PIN",
            "auto_backup" to "Auto Backup",
            "export_data" to "Export Data",
            "privacy_policy" to "Privacy Policy",
            "delete_account" to "Delete Account",
            "send_feedback" to "Send Feedback",
            "rate_app" to "Rate the App",
            "about_spendsync" to "About SpendSync",
            "sign_out" to "Sign Out"
        )
        val es = mapOf(
            "home" to "Inicio",
            "total_balance" to "Saldo Total",
            "recent_transactions" to "Transacciones Recientes",
            "income" to "Ingresos",
            "expenses" to "Gastos",
            "savings" to "Ahorros",
            "analytics" to "Analítica",
            "budget" to "Presupuesto",
            "profile" to "Perfil",
            "settings" to "Ajustes",
            "all" to "Todos",
            "personalisation" to "Personalización",
            "general" to "General",
            "data" to "Datos",
            "support" to "Soporte",
            "dark_mode" to "Modo Oscuro",
            "accent_colour" to "Color de Acento",
            "language" to "Idioma",
            "currency" to "Moneda",
            "date_format" to "Formato de Fecha",
            "push_notifications" to "Notificaciones Push",
            "biometric_lock" to "Bloqueo Biométrico",
            "change_pin" to "Cambiar PIN",
            "auto_backup" to "Copia Automática",
            "export_data" to "Exportar Datos",
            "privacy_policy" to "Política de Privacidad",
            "delete_account" to "Eliminar Cuenta",
            "send_feedback" to "Enviar Comentarios",
            "rate_app" to "Calificar la Aplicación",
            "about_spendsync" to "Sobre SpendSync",
            "sign_out" to "Cerrar Sesión"
        )
        val fr = mapOf(
            "home" to "Accueil",
            "total_balance" to "Solde Total",
            "recent_transactions" to "Transactions Récentes",
            "income" to "Revenus",
            "expenses" to "Dépenses",
            "savings" to "Épargne",
            "analytics" to "Analytique",
            "budget" to "Budget",
            "profile" to "Profil",
            "settings" to "Paramètres",
            "all" to "Tout",
            "personalisation" to "Personnalisation",
            "general" to "Général",
            "data" to "Données",
            "support" to "Assistance",
            "dark_mode" to "Mode Sombre",
            "accent_colour" to "Couleur d'Accent",
            "language" to "Langue",
            "currency" to "Devise",
            "date_format" to "Format de Date",
            "push_notifications" to "Notifications Push",
            "biometric_lock" to "Verrouillage Biométrique",
            "change_pin" to "Modifier le PIN",
            "auto_backup" to "Sauvegarde Auto",
            "export_data" to "Exporter les Données",
            "privacy_policy" to "Politique de Confidentialité",
            "delete_account" to "Supprimer le Compte",
            "send_feedback" to "Envoyer des Commentaires",
            "rate_app" to "Évaluer l'Application",
            "about_spendsync" to "À Propos de SpendSync",
            "sign_out" to "Se Déconnecter"
        )
        val de = mapOf(
            "home" to "Startseite",
            "total_balance" to "Gesamtsaldo",
            "recent_transactions" to "Letzte Transaktionen",
            "income" to "Einnahmen",
            "expenses" to "Ausgaben",
            "savings" to "Ersparnisse",
            "analytics" to "Analysen",
            "budget" to "Budget",
            "profile" to "Profil",
            "settings" to "Einstellungen",
            "all" to "Alle",
            "personalisation" to "Personalisierung",
            "general" to "Allgemein",
            "data" to "Daten",
            "support" to "Unterstützung",
            "dark_mode" to "Dunkelmodus",
            "accent_colour" to "Akzentfarbe",
            "language" to "Sprache",
            "currency" to "Währung",
            "date_format" to "Datumsformat",
            "push_notifications" to "Push-Benachrichtigungen",
            "biometric_lock" to "Biometrische Sperre",
            "change_pin" to "PIN ändern",
            "auto_backup" to "Auto-Sicherung",
            "export_data" to "Daten exportieren",
            "privacy_policy" to "Datenschutz-Bestimmungen",
            "delete_account" to "Konto löschen",
            "send_feedback" to "Feedback senden",
            "rate_app" to "App bewerten",
            "about_spendsync" to "Über SpendSync",
            "sign_out" to "Abmelden"
        )
        val hi = mapOf(
            "home" to "होम",
            "total_balance" to "कुल जमा राशि",
            "recent_transactions" to "हाल के लेन-देन",
            "income" to "आय",
            "expenses" to "व्यय",
            "savings" to "बचत",
            "analytics" to "विश्लेषण",
            "budget" to "बजट",
            "profile" to "प्रोफ़ाइल",
            "settings" to "सेटिंग्स",
            "all" to "सभी",
            "personalisation" to "निजीकरण",
            "general" to "सामान्य",
            "data" to "डेटा",
            "support" to "समर्थन",
            "dark_mode" to "डार्क मोड",
            "accent_colour" to "मुख्य रंग",
            "language" to "भाषा",
            "currency" to "मुद्रा",
            "date_format" to "तारीख का प्रारूप",
            "push_notifications" to "पुश सूचनाएं",
            "biometric_lock" to "बायोमेट्रिक लॉक",
            "change_pin" to "पिन बदलें",
            "auto_backup" to "ऑटो बैकअप",
            "export_data" to "डेटा निर्यात",
            "privacy_policy" to "गोपनीयता नीति",
            "delete_account" to "खाता हटाएं",
            "send_feedback" to "प्रतिक्रिया दें",
            "rate_app" to "ऐप को रेट करें",
            "about_spendsync" to "SpendSync के बारे में",
            "sign_out" to "साइन आउट"
        )

        return when (language) {
            "Spanish" -> es[key] ?: en[key] ?: key
            "French"  -> fr[key] ?: en[key] ?: key
            "German"  -> de[key] ?: en[key] ?: key
            "Hindi"   -> hi[key] ?: en[key] ?: key
            else      -> en[key] ?: key
        }
    }

    // Dynamic date pattern mapper
    fun getDateFormatPattern(formatPattern: String): String {
        return when (formatPattern) {
            "MM / DD / YYYY" -> "MM/dd/yyyy"
            "YYYY - MM - DD" -> "yyyy-MM-dd"
            else             -> "dd/MM/yyyy" // Default: DD / MM / YYYY
        }
    }
}
