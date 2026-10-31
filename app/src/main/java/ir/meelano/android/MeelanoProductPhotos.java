package ir.meelano.android;

import java.util.Locale;

/**
 * Chooses which bundled photo (assets/products/&lt;key&gt;.jpg) fits a product, from its name or group.
 * Pure text logic, split out of MainActivity so it can be read and extended on its own.
 *
 * To add a photo: put &lt;key&gt;.jpg in assets/products, add its words below and list it in
 * assets/products/README.md.
 */
final class MeelanoProductPhotos {
    private MeelanoProductPhotos() { }

    /** Photo for a visual type the visitor picked by hand ("اصلاح هوشمندی تصویر"); null keeps the drawn picture. */
    static String keyForType(String type) {
        if (type == null) return null;
        switch (type) {
            case "nuts_bag": return "nuts";
            default: return null;
        }
    }

    /**
     * Nuts and dried fruit (آجیل و خشکبار). Specific words come first; «آجیل/مخلوط/چهارمغز» shows the
     * mixed bowl. Products made from nuts (کره بادام زمینی، روغن گردو، شکلات فندقی…) keep the drawn picture.
     */
    static String keyFor(String text) {
        if (text == null || text.trim().isEmpty()) return null;
        String t = norm(text);
        // Things made from nuts or fruit that are not the nut itself keep the drawn picture.
        if (has(t, "کره", "روغن", "بستنی", "کیک", "بیسکو", "ویفر", "شوکو", "بایکیت", "تابلت", "کلوچه")
                // whole words only: «کرم» is inside «کرمان»
                || hasWord(t, "شیر", "کرم", "نان", "نانی", "آرد", "ارد")) return null;

        // Match the exact products in the supplied sales photos before falling back to generic food art.
        String exact = productSpecificKey(t);
        if (exact != null) return exact;

        // Chocolates, sweets and snacks (Atiran group «شکلات و آبنبات ها»).
        if (has(t, "شکلات", "کاکائو", "پرالین", "ترافل")) return "chocolate";
        if (has(t, "آبنبات", "ابنبات", "ابنیات", "آدامس", "ادامس", "تافی", "پاستیل", "دراژه", "اسمارتیز", "اچاچی", "آچاچی", "نبات", "سوهان", "گز ")
                || hasWord(t, "گز", "آبنبات")) return "candy";
        if (has(t, "چیپس", "پاپ کورن", "پاپکورن", "اسنک") || hasWord(t, "پفک", "ذرت بوداده")) return "snacks";
        if (has(t, "توت فرنگی", "توتفرنگی", "طعم")) return null;
        if (has(t, "میوه خشک", "خشک میوه")) return "dried_fruit";
        if (hasWord(t, "آجیل", "اجیل", "مخلوط", "چهارمغز", "چهار مغز", "شب یلدا", "نخودچی")) return "nuts";
        if (has(t, "بادام زمینی", "بادوم زمینی", "بادامزمینی")) return "peanut";
        if (has(t, "بادام هندی", "بادوم هندی", "بادامهندی", "کاجو", "کازو")) return "cashew";
        if (has(t, "پسته")) return "pistachio";
        if (has(t, "فندق")) return "hazelnut";
        if (has(t, "گردو", "گردوی")) return "walnut";
        if (has(t, "بادام", "بادوم")) return "almond";
        if (has(t, "تخمه کدو", "تخم کدو", "کدو گوشتی", "کدو ")) return "pumpkin_seeds";
        if (has(t, "تخمه", "تخم هندوانه", "تخم آفتابگردان", "آفتابگردان", "شاهدانه")) return "seeds";
        if (has(t, "زرشک")) return "barberry";
        if (has(t, "زعفران")) return "saffron";
        if (has(t, "کشمش", "مویز", "سبزه")) return "raisin";
        if (has(t, "خرما", "رطب", "مضافتی", "پیارم", "زاهدی", "کبکاب")) return "dates";
        if (has(t, "انجیر")) return "fig";
        if (has(t, "قیسی", "قیصی", "زردآلو", "زرد آلو") || hasWord(t, "برگه")) return "dried_apricot";
        if (has(t, "آلو بخارا", "آلوبخارا", "آلوچه", "الوچه", "برگ زرد", "برگه")
                || hasWord(t, "آلو", "الو", "توت", "توت خشک", "هلو خشک", "سیب خشک", "کیوی خشک")) return "dried_fruit";
        if (has(t, "خشکبار")) return "nuts";
        return null;
    }

    /**
     * Brand/SKU aliases from the visitor's product flyers. Keep this ahead of the broad
     * category rules so, for example, a branded hot chocolate never gets a generic candy photo.
     */
    private static String productSpecificKey(String t) {
        boolean delbos = has(t, "دلبوث", "دلبوس", "delbos");
        boolean plum = has(t, "آلوچه", "الوچه", "آلو", "الو", "آلو خشک", "آلوخشک");
        boolean shandiz = has(t, "شاندیز", "shandiz");

        if (delbos && has(t, "گیلاس", "آلبالو", "الوچه ترش", "sour cherry")) return "delbos_sour_cherry";
        if (delbos && has(t, "زردآلو", "زرد آلو", "برگه زردآلو", "آلو طلایی", "golden plum")) return "delbos_golden_plum";
        if (delbos && plum) return "delbos_processed_plum";
        if (has(t, "ساحل", "sahil") && plum) return "sahil_prune";
        if (shandiz && plum && has(t, "10", "۱۰", "ده عدد", "ده تایی", "ده‌تایی", "کارتن 8", "کارتن ۸"))
            return "shandiz_ten_pack";
        if (shandiz && has(t, "سانتریزه", "سانتریز", "سانتريزه", "سانتريز")) return "shandiz_sun_plum";
        if (shandiz && plum && has(t, "جنگلی", "جنگلى", "forest")) return "shandiz_forest_plum";

        if (has(t, "مولتی کافه", "مولتی‌کافه", "multicafe")
                && has(t, "بلک گلد", "black gold", "اسپرسو", "espresso")) return "multicafe_black_gold";
        boolean farmand = has(t, "فرمند", "formand");
        boolean chocolateSauce = has(t, "سس شکلاتی", "سس شکلات", "chocolate sauce");
        boolean caramelSauce = has(t, "سس کارامل", "کارامل", "caramel sauce");
        if (farmand && chocolateSauce && caramelSauce) return "farmand_sauce_pair";
        if (farmand && chocolateSauce) return "farmand_chocolate_sauce";
        if (farmand && caramelSauce) return "farmand_caramel_sauce";

        if (has(t, "جلیتو", "gelito") && has(t, "کولا", "cola")) return "gelito_cola_gummy";
        if (has(t, "هات چاکلت", "هات شکلات", "hot chocolate")
                && has(t, "دیسترو", "دیسترو", "distro", "دسترو")) return "distro_hot_chocolate";
        if (has(t, "کاپوچینو", "کاپوچينو", "cappuccino")
                && has(t, "دیسترو", "distro", "دسترو")) return "distro_cappuccino";
        if (has(t, "جلیتو", "gelito") && has(t, "فوم", "فومدار", "آیدین", "ايدين", "aidin"))
            return "aidin_gelito_foam";
        if (has(t, "لواشک رولی", "لواشک رول", "fruit leather roll")
                && has(t, "باغتام", "باغ تام", "baghtam")) return "baghtam_fruit_roll";
        if (has(t, "هایفروت", "های فروت", "highfruit", "hyfruit")
                && has(t, "لواشک", "استیکی", "استيكي", "fruit bar")) return "baghban_highfruit_bar";
        return null;
    }

    /** Lower-case, Arabic→Persian letters, punctuation and half-spaces → spaces, padded with one space. */
    static String norm(String value) {
        String t = value == null ? "" : value.toLowerCase(Locale.US);
        t = t.replace('ي', 'ی').replace('ك', 'ک').replace('أ', 'ا').replace('إ', 'ا').replace('ؤ', 'و').replace('ۀ', 'ه').replace('ة', 'ه');
        t = t.replace('\u200c', ' ').replace('\u200f', ' ').replace('\u200e', ' ').replace('\u00a0', ' ').replace('\u200d', ' ');
        t = t.replace('آ', 'ا').replaceAll("[\u0640\u064b-\u0652]", "");
        t = t.replaceAll("[\\p{Punct}\\[\\]{}()\\-_/\\\\|،؛]+", " ");
        return " " + t.replaceAll("\\s+", " ").trim() + " ";
    }

    /** Whole word, or (for keys longer than two letters) anywhere inside a word. */
    private static boolean has(String hay, String... keys) {
        for (String k : keys) {
            String key = norm(k).trim();
            if (key.isEmpty()) continue;
            if (hay.contains(" " + key + " ")) return true;
            if (key.length() > 2 && hay.contains(key)) return true;
        }
        return false;
    }

    private static boolean hasWord(String hay, String... keys) {
        for (String k : keys) {
            String key = norm(k).trim();
            if (!key.isEmpty() && hay.contains(" " + key + " ")) return true;
        }
        return false;
    }
}
