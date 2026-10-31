package ir.meelano.android;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

/** Coverage for the bundled visual catalogue used by nut and dried-fruit products. */
public class MeelanoProductPhotosTest {
    @Test public void mapsNutAndDriedFruitNamesToTheirDedicatedPhotos() {
        assertPhoto("پسته اکبری شور", "pistachio");
        assertPhoto("بادام درختی ممتاز", "almond");
        assertPhoto("فندق برشته", "hazelnut");
        assertPhoto("مغز گردو سفید", "walnut");
        assertPhoto("بادام هندی برشته", "cashew");
        assertPhoto("بادام زمینی شور", "peanut");
        assertPhoto("تخمه کدو گوشتی", "pumpkin_seeds");
        assertPhoto("تخمه آفتابگردان شور", "seeds");
        assertPhoto("کشمش سبز قلمی", "raisin");
        assertPhoto("خرما مضافتی بم", "dates");
        assertPhoto("انجیر خشک", "fig");
        assertPhoto("برگه زردآلو", "dried_apricot");
        assertPhoto("آلو بخارا", "dried_fruit");
        assertPhoto("زرشک پفکی", "barberry");
        assertPhoto("زعفران نگین", "saffron");
        assertPhoto("آجیل مخلوط چهارمغز", "nuts");
    }

    @Test public void recognizesArabicAndPersianSpellingsAndKeepsSpecificTypesAheadOfGenericNuts() {
        assertPhoto("اجيل مخلوط چهارمغز", "nuts");
        assertPhoto("نخودچی کشمش", "nuts");
        assertPhoto("شکلات فندقی", "chocolate");
        assertPhoto("نبات زعفرانی", "candy");
        assertPhoto("تخمه زعفرانی", "seeds");
        assertPhoto("مغز بادام", "almond");
    }

    @Test public void doesNotUseWholeNutPhotosForProductsMadeFromNuts() {
        assertNull(MeelanoProductPhotos.keyFor("کره بادام زمینی"));
        assertNull(MeelanoProductPhotos.keyFor("روغن گردو"));
        assertNull(MeelanoProductPhotos.keyFor("شیر بادام"));
        assertNull(MeelanoProductPhotos.keyFor("ویفر با کرم فندق"));
    }

    @Test public void mapsTheUploadedBrandSpecificProductPhotosBeforeGenericCategories() {
        assertPhoto("آلوچه فرآوری‌شده دلبوث", "delbos_processed_plum");
        assertPhoto("گیلاس فرآوری شده دلبوث", "delbos_sour_cherry");
        assertPhoto("برگ زردآلو دلبوث", "delbos_golden_plum");
        assertPhoto("آلوچه ساحل", "sahil_prune");
        assertPhoto("آلوچه شاندیز ۱۰ عددی", "shandiz_ten_pack");
        assertPhoto("آلوچه جنگلی شاندیز", "shandiz_forest_plum");
        assertPhoto("آلو سانتریزه شاندیز", "shandiz_sun_plum");
        assertPhoto("قهوه اسپرسو مولتی کافه بلک گلد", "multicafe_black_gold");
        assertPhoto("سس شکلاتی فرمند", "farmand_chocolate_sauce");
        assertPhoto("سس کارامل فرمند", "farmand_caramel_sauce");
        assertPhoto("سس شکلاتی و کارامل فرمند", "farmand_sauce_pair");
        assertPhoto("پاستیل جلیتو کولا", "gelito_cola_gummy");
        assertPhoto("هات چاکلت دیسترو", "distro_hot_chocolate");
        assertPhoto("کاپوچینو دیسترو فرمند", "distro_cappuccino");
        assertPhoto("جلیتو فوم‌دار آیدین", "aidin_gelito_foam");
        assertPhoto("لواشک رولی باغتام", "baghtam_fruit_roll");
        assertPhoto("لواشک استیکی هایفروت باغبان", "baghban_highfruit_bar");
    }

    @Test public void keepsGeneralFallbacksForUnbrandedProductsAndRejectsSimilarNames() {
        assertPhoto("آلوچه جنگلی", "dried_fruit");
        assertPhoto("شکلات صبحانه فرمند دورنگ ۲۰۰ گرمی", "chocolate");
        assertNull(MeelanoProductPhotos.keyFor("کاپوچینو ساده"));
        assertNull(MeelanoProductPhotos.keyFor("قهوه اسپرسو معمولی"));
    }

    @Test public void manualNutVisualUsesTheMixedNutPhotoOnly() {
        assertEquals("nuts", MeelanoProductPhotos.keyForType("nuts_bag"));
        assertNull(MeelanoProductPhotos.keyForType("unknown_visual"));
    }

    private static void assertPhoto(String name, String expected) {
        assertEquals(name, expected, MeelanoProductPhotos.keyFor(name));
    }
}
