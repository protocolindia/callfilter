package pro.onephone.callfilter;

public class CountryData {
    public final String iso;
    public final String name;
    public final String dialCode;

    public CountryData(String iso, String name, String dialCode) {
        this.iso = iso;
        this.name = name;
        this.dialCode = dialCode;
    }

    public static CountryData[] LIST = {
        new CountryData("IN", "India", "+91"),
        new CountryData("US", "United States", "+1"),
        new CountryData("GB", "United Kingdom", "+44"),
        new CountryData("CA", "Canada", "+1"),
        new CountryData("AU", "Australia", "+61"),
        new CountryData("AE", "UAE", "+971"),
        new CountryData("SG", "Singapore", "+65"),
        new CountryData("DE", "Germany", "+49"),
        new CountryData("FR", "France", "+33"),
        new CountryData("BR", "Brazil", "+55"),
        new CountryData("JP", "Japan", "+81"),
        new CountryData("CN", "China", "+86"),
        new CountryData("ID", "Indonesia", "+62"),
        new CountryData("PK", "Pakistan", "+92"),
        new CountryData("BD", "Bangladesh", "+880"),
        new CountryData("NG", "Nigeria", "+234"),
        new CountryData("ZA", "South Africa", "+27"),
        new CountryData("KE", "Kenya", "+254"),
        new CountryData("MX", "Mexico", "+52"),
        new CountryData("IT", "Italy", "+39"),
        new CountryData("ES", "Spain", "+34"),
        new CountryData("RU", "Russia", "+7"),
        new CountryData("TR", "Turkey", "+90"),
        new CountryData("PH", "Philippines", "+63"),
        new CountryData("MY", "Malaysia", "+60"),
        new CountryData("TH", "Thailand", "+66"),
        new CountryData("VN", "Vietnam", "+84"),
        new CountryData("LK", "Sri Lanka", "+94"),
        new CountryData("NP", "Nepal", "+977"),
    };

    public static int findIndexByIso(String iso) {
        if (iso == null) return 0;
        for (int i = 0; i < LIST.length; i++) {
            if (LIST[i].iso.equalsIgnoreCase(iso)) return i;
        }
        return 0;
    }

    @Override public String toString() {
        return name + " (" + dialCode + ")";
    }
}
