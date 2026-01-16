package org.HomeDuty.model;

public class Task {
    private int id;
    private String baslik;
    private int puanDegeri;
    private String zorlukSeviyesi;

    public Task() {}


    public Task(int id, String baslik, int puanDegeri, String zorlukSeviyesi) {
        this.id = id;
        this.baslik = baslik;
        this.puanDegeri = puanDegeri;
        this.zorlukSeviyesi = zorlukSeviyesi;
    }



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

    @Override
    public String toString() {
        return "Görev: " + baslik + " (Puan: " + puanDegeri + ", Zorluk: " + zorlukSeviyesi + ")";
    }
}