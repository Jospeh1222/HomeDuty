-- Görev ekler ve yük dengeleme ile atar
CREATE PROCEDURE add_and_assign_task(IN p_baslik character varying, IN p_puan integer, IN p_creator_id integer)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_new_task_id INT;
    v_family_id INT;
    v_target_user_id INT;
BEGIN
    --Görevi ekle ve ID'sini al
    INSERT INTO Tasks (baslik, puan_degeri) 
    VALUES (p_baslik, p_puan) 
    RETURNING gorev_id INTO v_new_task_id;

    --Ekleyen kullanıcının aile ID'sini bul
    SELECT aile_id INTO v_family_id FROM Users WHERE kullanici_id = p_creator_id;

    --Aynı aileden, üzerinde 'Beklemede' görev sayısı en az olan kullanıcıyı seç
    SELECT u.kullanici_id INTO v_target_user_id
    FROM Users u
    LEFT JOIN Assignments a ON u.kullanici_id = a.kullanici_id AND a.durum = 'Beklemede'
    WHERE u.aile_id = v_family_id
    GROUP BY u.kullanici_id
    ORDER BY COUNT(a.atama_id) ASC
    LIMIT 1;

    --Görevi seçilen kişiye ata
    IF v_target_user_id IS NOT NULL THEN
        INSERT INTO Assignments (gorev_id, kullanici_id, durum) 
        VALUES (v_new_task_id, v_target_user_id, 'Beklemede');
    END IF;

    COMMIT;
END;
$$;

--Program çalıştırıldığında görevleri aile içi yük dengeleme ile atar
CREATE FUNCTION distribute_tasks_load_sharing(p_family_id integer) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    task_record RECORD;
    v_target_user_id INT;
BEGIN
    --Bu aileye (p_family_id) henüz atanmamış olan görevleri bul
    FOR task_record IN 
        SELECT gorev_id FROM Tasks 
        WHERE gorev_id NOT IN (
            SELECT a.gorev_id 
            FROM Assignments a 
            JOIN Users u ON a.kullanici_id = u.kullanici_id 
            WHERE u.aile_id = p_family_id
        )
    LOOP
        -- Bu aile içindeki kullanıcıları bekleyen görev sayılarına göre sırala.
        SELECT u.kullanici_id INTO v_target_user_id
        FROM Users u
        LEFT JOIN Assignments a ON u.kullanici_id = a.kullanici_id AND a.durum = 'Beklemede'
        WHERE u.aile_id = p_family_id
        GROUP BY u.kullanici_id
        ORDER BY COUNT(a.atama_id) ASC, RANDOM() 
        LIMIT 1;

        --Atamayı gerçekleştir
        IF v_target_user_id IS NOT NULL THEN
            INSERT INTO Assignments (gorev_id, kullanici_id, durum) 
            VALUES (task_record.gorev_id, v_target_user_id, 'Beklemede');
        END IF;
    END LOOP;
END;
$$;

--Görev eklendiğinde bildirim verir triger ile
CREATE FUNCTION fnc_new_task_notice() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE NOTICE 'Yeni görev sisteme eklendi: %', NEW.baslik;
    RETURN NEW;
END;
$$;

-- Atama durumu 'Tamamlandı' olduğunda kullanıcının puanını günceller ve bildirim verir
CREATE OR REPLACE FUNCTION fnc_update_puan() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF (TRIM(NEW.durum) = 'Tamamlandı' AND (OLD.durum IS NULL OR TRIM(OLD.durum) != 'Tamamlandı')) THEN
        -- Kullanıcı puanını güncelle
        UPDATE Users 
        SET puan = puan + (SELECT puan_degeri FROM Tasks WHERE gorev_id = NEW.gorev_id)
        WHERE kullanici_id = NEW.kullanici_id;
        RAISE NOTICE 'SİSTEM: Puan başarıyla eklendi!';
    ELSE
        RAISE NOTICE 'UYARI: Şart sağlanmadığı için puan eklenmedi. Mevcut durum: %', NEW.durum;
    END IF;
    
    RETURN NEW;
END; $$;

--Databaseden kullanıcı görevlerini cursor ile getirir
CREATE FUNCTION get_user_tasks_cursor(u_id integer) RETURNS TABLE(t_baslik character varying, a_durum character varying)
    LANGUAGE plpgsql
    AS $$
DECLARE
    task_record RECORD;
    task_cursor CURSOR FOR 
        SELECT t.baslik, a.durum 
        FROM Assignments a 
        JOIN Tasks t ON a.gorev_id = t.gorev_id 
        WHERE a.kullanici_id = u_id;
BEGIN
    OPEN task_cursor;
    LOOP
        FETCH task_cursor INTO task_record;
        EXIT WHEN NOT FOUND;
        t_baslik := task_record.baslik;
        a_durum := task_record.durum;
        RETURN NEXT;
    END LOOP;
    CLOSE task_cursor;
END;
$$;

--Task durumunu tamamlandı olarak günceller
CREATE PROCEDURE  sp_complete_task(IN p_atama_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE Assignments SET durum = 'Tamamlandı' WHERE atama_id = p_atama_id;
END;
$$;


--Kullanıcıya atanmış görevi siler
CREATE PROCEDURE  sp_delete_assignment(IN p_atama_id integer, IN p_family_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    DELETE FROM Assignments WHERE atama_id = p_atama_id AND kullanici_id IN 
    (SELECT kullanici_id FROM Users WHERE aile_id = p_family_id);
END;
$$;

--Task tableından görevi siler
CREATE PROCEDURE  sp_delete_global_task(IN p_gorev_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    DELETE FROM Tasks WHERE gorev_id = p_gorev_id;
END;
$$;

--Aggregate ve HAVING kullanımı ile aile detaylarını getirir
CREATE FUNCTION  sp_get_family_details(p_family_id integer) RETURNS TABLE(k_adi character varying, t_puan integer, g_adi character varying, g_puani integer, g_durumu character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT kullanici_adi, toplam_puan, gorev_adi, gorev_puani, gorev_durumu 
    FROM vw_family_task_details 
    WHERE aile_id = p_family_id
    AND kullanici_adi IN (

        SELECT kullanici_adi 
        FROM vw_family_task_details 
        WHERE aile_id = p_family_id
        GROUP BY kullanici_adi 
        HAVING SUM(gorev_puani) >= 0 -- Toplam puanı 0 ve üzeri olanları filtrele
    )
    ORDER BY kullanici_adi ASC;
END; $$;

--Kullanıcıyı ID ile getirir
CREATE FUNCTION  sp_get_user_by_id(p_id integer) RETURNS TABLE(id integer, isim character varying, gorev_rol character varying, skorpuan integer, aile integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT kullanici_id, ad, rol, puan, aile_id 
    FROM Users 
    WHERE kullanici_id = p_id;
END;
$$;

--Kullanıcının beklemede olan görevlerini getirir
CREATE FUNCTION  sp_get_user_pending_tasks(p_uid integer) RETURNS TABLE(atama_id integer, baslik character varying, puan_degeri integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT a.atama_id, t.baslik, t.puan_degeri 
    FROM Assignments a JOIN Tasks t ON a.gorev_id = t.gorev_id
    WHERE a.kullanici_id = p_uid AND a.durum = 'Beklemede';
END;
$$;

--Login için kullanıcıyı adı ile getirir
CREATE FUNCTION  sp_login_user(p_ad character varying) RETURNS TABLE(id integer, isim character varying, gorev_rol character varying, skorpuan integer, aile integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT kullanici_id, ad, rol, puan, aile_id 
    FROM Users 
    WHERE ad = p_ad;
END;
$$;

--Kullanıcıyı kaydeder
CREATE PROCEDURE  sp_register_user(IN p_ad character varying, IN p_rol character varying, IN p_aile_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO Users (ad, rol, aile_id, puan) 
    VALUES (p_ad, p_rol, p_aile_id, 0);
END;
$$;


--Sistemi resetler
CREATE PROCEDURE  sp_reset_system()
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE Users SET puan = 0;
    TRUNCATE TABLE Assignments RESTART IDENTITY;
END;
$$;


--Görev ve kullanıcı adında arama yapar
CREATE FUNCTION  sp_search_tasks(p_keyword text) RETURNS TABLE(id integer, isim character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    -- 1. Küme: Görevlerde ara
    SELECT gorev_id, baslik FROM Tasks WHERE baslik ILIKE '%' || p_keyword || '%'
    UNION 
    -- 2. Küme: Kullanıcılarda ara
    SELECT kullanici_id, ad FROM Users WHERE ad ILIKE '%' || p_keyword || '%';
END; $$;


-- Kullanıcılar tablosu
CREATE TABLE Users (
    kullanici_id SERIAL PRIMARY KEY,
    ad VARCHAR(50) NOT NULL,
    rol VARCHAR(20)
        CHECK (rol IN ('Anne', 'Baba', 'Çocuk')),
    aile_id INT REFERENCES Families(aile_id) ON DELETE CASCADE,
    puan INT DEFAULT 0 CHECK (puan >= 0)
);

-- Aileleri gruplamak için
CREATE TABLE Families (
    aile_id SERIAL PRIMARY KEY,
    aile_adi VARCHAR(100) NOT NULL
);

-- Görevler ve Otomatik ID 
CREATE SEQUENCE gorev_id_seq START 100;

-- Görevler tablosu
CREATE TABLE Tasks (
    gorev_id INT PRIMARY KEY DEFAULT nextval('gorev_id_seq'),
    baslik VARCHAR(200) NOT NULL,
    puan_degeri INT CHECK (puan_degeri > 0),
    zorluk_seviyesi VARCHAR(50)
);


-- Görev Atamaları
CREATE TABLE Assignments (
    atama_id SERIAL PRIMARY KEY,
    gorev_id INT REFERENCES Tasks(gorev_id) ON DELETE CASCADE,
    kullanici_id INT REFERENCES Users(kullanici_id) ON DELETE CASCADE,
    durum VARCHAR(50) DEFAULT 'Beklemede' 
);


-- Görünüm: Aile görev detayları
CREATE VIEW  vw_family_task_details AS
 SELECT u.aile_id,
    u.ad AS kullanici_adi,
    u.puan AS toplam_puan,
    t.baslik AS gorev_adi,
    t.puan_degeri AS gorev_puani,
    a.durum AS gorev_durumu
   FROM (( users u
     JOIN  assignments a ON ((u.kullanici_id = a.kullanici_id)))
     JOIN  tasks t ON ((a.gorev_id = t.gorev_id)));


-- Aileleri ekle
INSERT INTO families (aile_id, aile_adi) VALUES 
(1, 'Gündüz Ailesi'), (2, 'Yılmaz Ailesi'), (3, 'Kaya Ailesi'), (4, 'Demir Ailesi'), (5, 'Çelik Ailesi'), 
(6, 'Yıldız Ailesi'), (7, 'Öztürk Ailesi'), (8, 'Aydın Ailesi'), (9, 'Arslan Ailesi'), (10, 'Polat Ailesi');

-- Kullanıcıları ekle
INSERT INTO users (kullanici_id, ad, rol, aile_id, puan) VALUES 
(1, 'Yusuf', 'Baba', 1, 0), (2, 'Başar', 'Çocuk', 1, 0), (11, 'Selen', 'Anne', 1, 0),
(12, 'Nisa', 'Anne', 2, 0), (3, 'Ahmet', 'Çocuk', 2, 0), (4, 'Fatma', 'Anne', 2, 0),
(5, 'Zeynep', 'Çocuk', 3, 0), (14, 'Hüseyin', 'Baba', 3, 0), (29, 'Zeynep', 'Anne', 3, 0),
(15, 'Meryem', 'Anne', 4, 0), (16, 'İbrahim', 'Baba', 4, 0), (6, 'Ali', 'Çocuk', 4, 0),
(17, 'Nermin', 'Anne', 5, 0), (18, 'Mustafa', 'Baba', 5, 0), (7, 'Ayşe', 'Çocuk', 5, 0),
(19, 'Gülcan', 'Anne', 6, 0), (20, 'Osman', 'Baba', 6, 0), (8, 'Mehmet', 'Çocuk', 6, 0),
(21, 'Lale', 'Anne', 7, 0), (22, 'Süleyman', 'Baba', 7, 0), (9, 'Can', 'Çocuk', 7, 0),
(23, 'Arzu', 'Anne', 8, 0), (24, 'Ramazan', 'Baba', 8, 0), (10, 'Ece', 'Çocuk', 8, 0),
(25, 'Figen', 'Anne', 9, 0), (26, 'Yakup', 'Baba', 9, 0), (30, 'Yıldız', 'Çocuk', 9, 0),
(27, 'Hale', 'Anne', 10, 0), (28, 'Bekir', 'Baba', 10, 0), (31, 'Defne', 'Çocuk', 10, 0);

-- Görevleri ekle
INSERT INTO tasks (gorev_id, baslik, puan_degeri, zorluk_seviyesi) VALUES 
(100, 'Market', 20, 'Orta'), (101, 'Çöp', 10, 'Kolay'), (102, 'Fatura', 30, 'Zor'),
(103, 'Temizlik', 50, 'Zor'), (104, 'Bulaşık', 15, 'Kolay'), (105, 'Çamaşır', 25, 'Orta'),
(106, 'Yemek', 45, 'Zor'), (107, 'Bahçe', 15, 'Kolay'), (108, 'Ütü', 40, 'Orta'),
(109, 'Tamirat', 60, 'Zor'), (114, 'süpürge', 30, NULL), (115, 'Kapı', 20, NULL);

-- Sequence Senkronizasyonu
SELECT setval('families_aile_id_seq', 10, true);
SELECT setval('users_kullanici_id_seq', 31, true);
SELECT setval('gorev_id_seq', 116, true);

SELECT pg_catalog.setval(' assignments_atama_id_seq', 14, true);

--Indexleme: Görev başlığına göre arama için
CREATE INDEX idx_task_title ON  tasks USING btree (baslik);


--Trigger: Atama puan güncelleme
CREATE TRIGGER trg_assignment_puan AFTER INSERT OR UPDATE ON  assignments FOR EACH ROW EXECUTE FUNCTION  fnc_update_puan();


-- Trigger: Yeni görev bildirimi
CREATE TRIGGER trg_new_task AFTER INSERT ON  tasks FOR EACH ROW EXECUTE FUNCTION  fnc_new_task_notice();

