package com.ticketsystem.generator.util;

import java.util.List;
import java.util.Random;

/**
 * Gerçekçi görünen sahte veri üretici.
 */
public class FakeData {

    private static final Random RNG = new Random();

    // ---------------------------------------------------------------
    // Bilet başlıkları — ürün kategorisine göre
    // ---------------------------------------------------------------
    private static final List<String> TICKET_TITLES = List.of(
        "VPN bağlantısı kurulamıyor",
        "Uygulama açılırken hata veriyor",
        "Şifre sıfırlama maili gelmiyor",
        "Ekrana erişim yetkisi verilmiyor",
        "Yazıcı ağda görünmüyor",
        "E-posta gönderilemiyor",
        "Sistem yavaş çalışıyor",
        "Lisans hatası alıyorum",
        "Dosya paylaşımı çalışmıyor",
        "Uzak masaüstü bağlantısı kesildi",
        "Veritabanı bağlantı hatası",
        "Rapor oluşturulurken hata",
        "Kullanıcı hesabı kilitlendi",
        "İki faktörlü doğrulama çalışmıyor",
        "Mobil uygulama senkronize olmuyor",
        "Tarayıcı eklentisi yüklenemiyor",
        "Ses/mikrofon çalışmıyor",
        "Kamera görüntüsü gelmiyor",
        "Güncelleme sonrası sorun yaşıyorum",
        "Yeni kullanıcı oluşturulamıyor",
        "Fatura modülüne erişilemiyor",
        "CRM verileri yüklenmiyor",
        "API entegrasyonu hata veriyor",
        "Yedekleme işlemi başarısız",
        "Disk alanı doldu uyarısı"
    );

    private static final List<String> TICKET_DESCRIPTIONS = List.of(
        "Sabahtan beri bu sorunla karşılaşıyorum. Birkaç kez denedim ama düzelmiyor. Acil çözüm gerekiyor.",
        "Dün akşamdan beri çalışmıyor. Ekibimden de aynı şikayeti alanlar var.",
        "Hata mesajı: 'Connection timed out'. Teknik destek ekibine bildiriyorum.",
        "Sistem güncellemesinden sonra bu sorun başladı. Öncesinde sorunsuz çalışıyordu.",
        "Birden fazla kullanıcı aynı sorunu yaşıyor. Toplu bir sorun olabilir.",
        "Ekran görüntüsü ekliyorum. Hata kodu: ERR_ACCESS_DENIED.",
        "Yeniden başlatmayı denedim, sorun devam ediyor.",
        "IT departmanına bildirdim ama çözüm bulunamadı, destek talebi açıyorum.",
        "Müşteri toplantısı öncesinde acil çözüm gerekiyor.",
        "Sadece benim bilgisayarımda mı yoksa genel bir sorun mu bilmiyorum.",
        "Tarayıcıyı değiştirdim, sorun devam ediyor. Altyapı kaynaklı olabilir.",
        "Log dosyasını inceledim, 'null pointer exception' hatası görüyorum.",
        "Yetki sorunu gibi görünüyor, admin onayı gerekebilir.",
        "Geçici çözüm olarak eski sürümü kullanıyorum ama bu sürdürülebilir değil.",
        "Hafta sonu yapılan bakım sonrası bu sorun başladı."
    );

    private static final List<String> COMMENT_MESSAGES = List.of(
        "Sorununuzu inceliyorum, kısa süre içinde geri döneceğim.",
        "Lütfen sistemi yeniden başlatıp tekrar deneyin.",
        "Hesabınızı kontrol ettim, yetki eksikliği görüyorum. Düzeltiyorum.",
        "Sunucu tarafında bir güncelleme yapıldı, şimdi tekrar deneyin.",
        "Sorun tespit edildi ve çözüm üzerinde çalışıyoruz.",
        "Ek bilgi gerekiyor: Hangi tarayıcı ve işletim sistemi kullanıyorsunuz?",
        "Ekibimizle görüştüm, 2 saat içinde çözüme kavuşacak.",
        "Geçici çözüm: Ayarlar > Gelişmiş > Önbelleği temizleyin.",
        "Sorun altyapı ekibine iletildi, takip ediyoruz.",
        "Güncelleme uygulandı, lütfen kontrol edin."
    );

    private static final List<String> RESOLUTION_NOTES = List.of(
        "Kullanıcının VPN yapılandırması güncellendi ve bağlantı sorunu giderildi.",
        "Hesap kilidi kaldırıldı, şifre sıfırlama maili gönderildi.",
        "Yazıcı sürücüsü yeniden yüklendi, ağ bağlantısı sağlandı.",
        "Lisans yenilendi ve kullanıcıya atandı.",
        "Yetki eksikliği giderildi, kullanıcı ilgili modüle erişebiliyor.",
        "Sunucu tarafındaki yapılandırma hatası düzeltildi.",
        "Uygulama önbelleği temizlendi ve yeniden başlatıldı.",
        "Ağ ayarları güncellendi, bağlantı sorunu çözüldü.",
        "Veritabanı bağlantı havuzu yeniden yapılandırıldı.",
        "Güvenlik duvarı kuralı güncellendi, erişim sağlandı."
    );

    private static final List<String> CLOSE_NOTES = List.of(
        "Sorun çözüldü, bilet kapatılıyor.",
        "Kullanıcı onayladı, işlem tamamlandı.",
        "Çözüm uygulandı ve doğrulandı.",
        "Sorun giderildi, takip gerekmiyor.",
        "Kullanıcı memnun, bilet kapatılıyor."
    );

    private static final List<String> PRIORITIES = List.of("LOW", "MEDIUM", "HIGH", "CRITICAL");

    // ---------------------------------------------------------------
    // Public metodlar
    // ---------------------------------------------------------------

    public static String randomTitle() {
        return pick(TICKET_TITLES);
    }

    public static String randomDescription() {
        return pick(TICKET_DESCRIPTIONS);
    }

    public static String randomComment() {
        return pick(COMMENT_MESSAGES);
    }

    public static String randomResolutionNote() {
        return pick(RESOLUTION_NOTES);
    }

    public static String randomCloseNote() {
        return pick(CLOSE_NOTES);
    }

    public static String randomPriority() {
        // Ağırlıklı dağılım: LOW %20, MEDIUM %40, HIGH %30, CRITICAL %10
        int r = RNG.nextInt(100);
        if (r < 20) return "LOW";
        if (r < 60) return "MEDIUM";
        if (r < 90) return "HIGH";
        return "CRITICAL";
    }

    public static int randomCsatRating() {
        // Ağırlıklı dağılım: çoğunlukla yüksek puan
        int r = RNG.nextInt(100);
        if (r < 5)  return 1;
        if (r < 10) return 2;
        if (r < 20) return 3;
        if (r < 45) return 4;
        return 5;
    }

    public static <T> T pick(List<T> list) {
        return list.get(RNG.nextInt(list.size()));
    }

    public static int nextInt(int bound) {
        return RNG.nextInt(bound);
    }
}
