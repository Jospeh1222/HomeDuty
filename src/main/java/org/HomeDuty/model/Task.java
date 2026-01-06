package org.HomeDuty.model;

public class Task {
    private int id;
    private String baslik;
    private int puanDegeri;
    private String zorlukSeviyesi; // "Kolay", "Orta", "Zor" gibi

    // Boş Constructor (Bazı kütüphaneler için gerekli olabilir)
    public Task() {}

    // Tüm alanları içeren Constructor
    public Task(int id, String baslik, int puanDegeri, String zorlukSeviyesi) {
        this.id = id;
        this.baslik = baslik;
        this.puanDegeri = puanDegeri;
        this.zorlukSeviyesi = zorlukSeviyesi;
    }

    // Getter ve Setter Metotları
    // (Arayüzde tabloya veri basarken bu metotlar kullanılacak)

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getBaslik() {
        return baslik;
    }

    public void setBaslik(String baslik) {
        this.baslik = baslik;
    }

    public int getPuanDegeri() {
        return puanDegeri;
    }

    public void setPuanDegeri(int puanDegeri) {
        this.puanDegeri = puanDegeri;
    }

    public String getZorlukSeviyesi() {
        return zorlukSeviyesi;
    }

    public void setZorlukSeviyesi(String zorlukSeviyesi) {
        this.zorlukSeviyesi = zorlukSeviyesi;
    }

    // Raporunda veya konsol testlerinde kolaylık sağlaması için toString()
    @Override
    public String toString() {
        return "Görev: " + baslik + " (Puan: " + puanDegeri + ", Zorluk: " + zorlukSeviyesi + ")";
    }
}