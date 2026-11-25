package de.jeanlucmakiola.calendula.hebrew

data class HebCity(
    val id: String,
    val name: String,
    val lat: Double,
    val lon: Double,
    val tz: String,
    val il: Boolean,
    val mins: Int,
    val group: String
)

val HEB_CITIES = listOf(
    HebCity("jerusalem", "ירושלים", 31.7683, 35.2137, "Asia/Jerusalem", true, 40, "ישראל"),
    HebCity("telaviv", "תל אביב-יפו", 32.0853, 34.7818, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("haifa", "חיפה", 32.794, 34.9896, "Asia/Jerusalem", true, 30, "ישראל"),
    HebCity("beersheva", "באר שבע", 31.2518, 34.7913, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("bneibrak", "בני ברק", 32.0809, 34.8338, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("bethshemesh", "בית שמש", 31.7469, 34.9881, "Asia/Jerusalem", true, 30, "ישראל"),
    HebCity("ashdod", "אשדוד", 31.8044, 34.6553, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("netanya", "נתניה", 32.3215, 34.8532, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("petahtikva", "פתח תקווה", 32.0841, 34.8878, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("rishon", "ראשון לציון", 31.973, 34.7925, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("safed", "צפת", 32.9646, 35.496, "Asia/Jerusalem", true, 30, "ישראל"),
    HebCity("tiberias", "טבריה", 32.7922, 35.5312, "Asia/Jerusalem", true, 30, "ישראל"),
    HebCity("eilat", "אילת", 29.5577, 34.9519, "Asia/Jerusalem", true, 20, "ישראל"),
    HebCity("newyork", "ניו יורק", 40.7128, -74.006, "America/New_York", false, 18, "חוץ לארץ"),
    HebCity("losangeles", "לוס אנג׳לס", 34.0522, -118.2437, "America/Los_Angeles", false, 18, "חוץ לארץ"),
    HebCity("miami", "מיאמי", 25.7617, -80.1918, "America/New_York", false, 18, "חוץ לארץ"),
    HebCity("toronto", "טורונטו", 43.6532, -79.3832, "America/Toronto", false, 18, "חוץ לארץ"),
    HebCity("montreal", "מונטריאול", 45.5017, -73.5673, "America/Toronto", false, 18, "חוץ לארץ"),
    HebCity("london", "לונדון", 51.5074, -0.1278, "Europe/London", false, 18, "חוץ לארץ"),
    HebCity("paris", "פריז", 48.8566, 2.3522, "Europe/Paris", false, 18, "חוץ לארץ"),
    HebCity("antwerp", "אנטוורפן", 51.2194, 4.4025, "Europe/Brussels", false, 18, "חוץ לארץ"),
    HebCity("buenosaires", "בואנוס איירס", -34.6037, -58.3816, "America/Argentina/Buenos_Aires", false, 18, "חוץ לארץ"),
    HebCity("melbourne", "מלבורן", -37.8136, 144.9631, "Australia/Melbourne", false, 18, "חוץ לארץ")
)
