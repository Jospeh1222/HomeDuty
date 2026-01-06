package org.HomeDuty.model;

public class User {
    private int id;
    private String ad;
    private String rol; // "Admin" veya "User"
    private int aileId;
    private int puan;

    // Parametreli Constructor, Getter ve Setter metotlarını ekle
    public User(int id, String ad, String rol, int puan, int aileId) {
        this.id = id;
        this.ad = ad;
        this.rol = rol;
        this.puan = puan;
        this.aileId = aileId;
    }

    // Getterlar (Hocan kodun temizliğine bakacaktır)
    public String getRol() { return rol; }
    public String getAd() { return ad; }
    public int getId() { return id; }
    public int getPuan() { return puan; }

    public String getBadgeName(int puan) {
        if (puan >= 500) return "🧹 Temizlik Şövalyesi";
        if (puan >= 200) return "💪 Sorumluluk Sahibi";
        if (puan >= 50) return "🌱 Çaylak Yardımcı";
        return "Yeni Üye";
    }
    public int getAileId() { return aileId; }
}