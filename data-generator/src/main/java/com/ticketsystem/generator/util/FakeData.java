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
        "Disk alanı doldu uyarısı",
        "Outlook profili bozuk, e-postalar gönderilemiyor",
        "Ekran paylaşımı uygulaması çöküyor",
        "SharePoint dosyalarına erişilemiyor",
        "Active Directory hesabı devre dışı bırakıldı",
        "Otomatik yedekleme çalışmıyor",
        "Office 365 lisansı atanmamış",
        "DNS çözümleme hatası — iç sunuculara erişilemiyor",
        "SSO girişi çalışmıyor — yetkilendirme hatası",
        "ERP modülü yavaş yükleniyor",
        "Şirket Wi-Fi'ına bağlanılamıyor",
        "Bulut depolama kotası doldu",
        "Çoklu monitör kurulumu tanınmıyor",
        "Ticket sistemi bildirimleri gelmiyor",
        "Sanal makine başlatılamıyor"
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
        "Hafta sonu yapılan bakım sonrası bu sorun başladı.",
        "Hata kodu 0x80070005 alıyorum; kaynak erişimi reddedildi mesajı çıkıyor.",
        "Son güncellemenin ardından sistem günlüğünde ACL hatası görüyorum.",
        "Farklı kullanıcı hesabıyla giriş denedim, aynı sorun devam ediyor.",
        "Müşteri irtibatımız saatlerdir bu konuyu bekliyor, iş süreci durma noktasında.",
        "Ağ trafiğini izledim; paketin güvenlik duvarında düştüğünü görüyorum."
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
        "Güncelleme uygulandı, lütfen kontrol edin.",
        "Sorununuzu öncelik listesine aldım, en kısa sürede dönüş yapacağım.",
        "Uzaktan bağlanıp cihazınıza bakabilir miyiz? Uygun olduğunuzda haber verin.",
        "Yapılandırmayı kontrol ettim, düzeltme sırasında kısa bir kesinti yaşanabilir.",
        "Sorun diğer kullanıcılarda da oluşuyor mu? Etkilenen kişilerin listesini paylaşabilir misiniz?",
        "Logları topladım, kök neden analizi yapılıyor."
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
        "Güvenlik duvarı kuralı güncellendi, erişim sağlandı.",
        "Active Directory politikası yeniden uygulandı, hesap aktifleştirildi.",
        "DNS kaydı düzeltildi, servis erişimi yeniden sağlandı.",
        "SSO sertifikası yenilendi, oturum açma sorunu giderildi.",
        "Disk kotası artırıldı, yedekleme süreçleri yeniden başlatıldı.",
        "API kimlik doğrulama anahtarı yenilendi, entegrasyon normale döndü."
    );

    private static final List<String> CLOSE_NOTES = List.of(
        "Sorun çözüldü, bilet kapatılıyor.",
        "Kullanıcı onayladı, işlem tamamlandı.",
        "Çözüm uygulandı ve doğrulandı.",
        "Sorun giderildi, takip gerekmiyor.",
        "Kullanıcı memnun, bilet kapatılıyor."
    );

    // ---------------------------------------------------------------
    // Worklog açıklamaları
    // ---------------------------------------------------------------
    private static final List<String> WORKLOG_DESCRIPTIONS = List.of(
        "Hata logları incelendi, sorunun kaynağı tespit edildi.",
        "Kullanıcıyla uzak oturum kuruldu, adımlar birlikte takip edildi.",
        "Ağ yapılandırması gözden geçirildi, tutarsız bir kural bulundu.",
        "Uygulama yeniden başlatıldı, hata kodu araştırıldı.",
        "Servis hesabı yetkileri kontrol edildi ve düzenlendi.",
        "Güvenlik duvarı kuralları gözden geçirildi ve güncellendi.",
        "Active Directory üzerinde hesap durumu incelendi.",
        "Kullanıcıya geçici çözüm sağlandı ve belgelendi.",
        "Ağ topolojisi üzerinden sorun izlendi.",
        "SSL sertifika yapılandırması doğrulandı.",
        "Sunucu kaynak kullanımı (CPU/RAM/Disk) izlendi.",
        "API çağrısı hata yanıtları analiz edildi.",
        "Yedek sistem üzerinde test yapıldı, sorun doğrulandı.",
        "Güncelleme öncesi ve sonrası sistem karşılaştırıldı.",
        "Bilet için çözüm adımları dokümante edildi."
    );

    // ---------------------------------------------------------------
    // CSAT yorumları
    // ---------------------------------------------------------------
    private static final List<String> CSAT_COMMENTS = List.of(
        "Sorunum hızlıca çözüldü, teşekkürler.",
        "Destek ekibi çok yardımseverdi, teşekkür ederim.",
        "Beklediğimden daha çabuk çözüldü.",
        "Süreç biraz uzun sürdü ama sonuç tatmin ediciydi.",
        "Çözüm için teşekkürler; öneri: daha hızlı geri dönüş yapılabilir.",
        "Mükemmel destek, her şey yolunda.",
        "İkinci kez aynı sorunla karşılaştım ama yine de iyi hizmet.",
        "Destek için teşekkürler.",
        "Teknik ekip bilgiliydi, sorunum kısa sürede çözüldü.",
        "Ortalama bir deneyimdi, geliştirilmesi gereken alanlar var.",
        "Çok profesyonel ve hızlı bir destek aldım.",
        "Bekleme sürem uzun oldu ancak çözüm kaliteliydi.",
        "Her şey gayet iyiydi, teşekkürler."
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

    public static String randomWorklogDescription() {
        return pick(WORKLOG_DESCRIPTIONS);
    }

    public static String randomCsatComment() {
        return pick(CSAT_COMMENTS);
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

    /**
     * Gerçekçi IT destek çalışma süresi (dakika).
     * Ağırlıklı dağılım: kısa görevler daha sık.
     */
    public static int randomWorklogMinutes() {
        int r = RNG.nextInt(100);
        if (r < 25) return 15 + RNG.nextInt(16);   // 15–30 dk  (%25)
        if (r < 55) return 30 + RNG.nextInt(31);   // 30–60 dk  (%30)
        if (r < 80) return 60 + RNG.nextInt(61);   // 60–120 dk (%25)
        if (r < 95) return 120 + RNG.nextInt(61);  // 120–180 dk (%15)
        return 180 + RNG.nextInt(61);               // 180–240 dk (%5)
    }

    public static <T> T pick(List<T> list) {
        return list.get(RNG.nextInt(list.size()));
    }

    public static int nextInt(int bound) {
        return RNG.nextInt(bound);
    }
}
