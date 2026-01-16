--
-- PostgreSQL database dump
--

-- Dumped from database version 17.5
-- Dumped by pg_dump version 17.5

-- Started on 2026-01-15 09:49:15

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

--
-- TOC entry 232 (class 1255 OID 16826)
-- Name: add_and_assign_task(character varying, integer, integer); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.add_and_assign_task(IN p_baslik character varying, IN p_puan integer, IN p_creator_id integer)
    LANGUAGE plpgsql
    AS $$
DECLARE
    v_new_task_id INT;
    v_family_id INT;
    v_target_user_id INT;
BEGIN
    -- 1. Görevi ekle ve ID'sini al
    INSERT INTO Tasks (baslik, puan_degeri) 
    VALUES (p_baslik, p_puan) 
    RETURNING gorev_id INTO v_new_task_id;

    -- 2. Ekleyen kullanıcının aile ID'sini bul
    SELECT aile_id INTO v_family_id FROM Users WHERE kullanici_id = p_creator_id;

    -- 3. Yük Paylaşımı: Aynı aileden, üzerinde 'Beklemede' görev sayısı en az olan kullanıcıyı seç
    SELECT u.kullanici_id INTO v_target_user_id
    FROM Users u
    LEFT JOIN Assignments a ON u.kullanici_id = a.kullanici_id AND a.durum = 'Beklemede'
    WHERE u.aile_id = v_family_id
    GROUP BY u.kullanici_id
    ORDER BY COUNT(a.atama_id) ASC
    LIMIT 1;

    -- 4. Görevi seçilen kişiye ata
    IF v_target_user_id IS NOT NULL THEN
        INSERT INTO Assignments (gorev_id, kullanici_id, durum) 
        VALUES (v_new_task_id, v_target_user_id, 'Beklemede');
    END IF;

    COMMIT;
END;
$$;


--
-- TOC entry 244 (class 1255 OID 16833)
-- Name: distribute_tasks_load_sharing(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.distribute_tasks_load_sharing(p_family_id integer) RETURNS void
    LANGUAGE plpgsql
    AS $$
DECLARE
    task_record RECORD;
    v_target_user_id INT;
BEGIN
    -- 1. Bu aileye (p_family_id) henüz atanmamış olan görevleri bul
    FOR task_record IN 
        SELECT gorev_id FROM Tasks 
        WHERE gorev_id NOT IN (
            SELECT a.gorev_id 
            FROM Assignments a 
            JOIN Users u ON a.kullanici_id = u.kullanici_id 
            WHERE u.aile_id = p_family_id
        )
    LOOP
        -- 2. YÜK PAYLAŞIMI ALGORİTMASI:
        -- Bu aile içindeki kullanıcıları bekleyen görev sayılarına göre sırala.
        SELECT u.kullanici_id INTO v_target_user_id
        FROM Users u
        LEFT JOIN Assignments a ON u.kullanici_id = a.kullanici_id AND a.durum = 'Beklemede'
        WHERE u.aile_id = p_family_id
        GROUP BY u.kullanici_id
        ORDER BY COUNT(a.atama_id) ASC, RANDOM() 
        LIMIT 1;

        -- 3. Atamayı gerçekleştir
        IF v_target_user_id IS NOT NULL THEN
            INSERT INTO Assignments (gorev_id, kullanici_id, durum) 
            VALUES (task_record.gorev_id, v_target_user_id, 'Beklemede');
        END IF;
    END LOOP;
END;
$$;


--
-- TOC entry 230 (class 1255 OID 16817)
-- Name: fnc_new_task_notice(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fnc_new_task_notice() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    RAISE NOTICE 'Yeni görev sisteme eklendi: %', NEW.baslik;
    RETURN NEW;
END;
$$;


--
-- TOC entry 229 (class 1255 OID 16815)
-- Name: fnc_update_puan(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.fnc_update_puan() RETURNS trigger
    LANGUAGE plpgsql
    AS $$
BEGIN
    IF (NEW.durum = 'Tamamlandı' AND OLD.durum != 'Tamamlandı') THEN
        UPDATE Users 
        SET puan = puan + (SELECT puan_degeri FROM Tasks WHERE gorev_id = NEW.gorev_id)
        WHERE kullanici_id = NEW.kullanici_id;
        RAISE NOTICE 'Puan başarıyla eklendi!'; -- Arayüze mesaj
    END IF;
    RETURN NEW;
END;
$$;


--
-- TOC entry 228 (class 1255 OID 16814)
-- Name: get_user_tasks_cursor(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.get_user_tasks_cursor(u_id integer) RETURNS TABLE(t_baslik character varying, a_durum character varying)
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


--
-- TOC entry 248 (class 1255 OID 16896)
-- Name: sp_complete_task(integer); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_complete_task(IN p_atama_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE Assignments SET durum = 'Tamamlandı' WHERE atama_id = p_atama_id;
END;
$$;


--
-- TOC entry 249 (class 1255 OID 16897)
-- Name: sp_delete_assignment(integer, integer); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_delete_assignment(IN p_atama_id integer, IN p_family_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    DELETE FROM Assignments WHERE atama_id = p_atama_id AND kullanici_id IN 
    (SELECT kullanici_id FROM Users WHERE aile_id = p_family_id);
END;
$$;


--
-- TOC entry 251 (class 1255 OID 16899)
-- Name: sp_delete_global_task(integer); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_delete_global_task(IN p_gorev_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    -- Eğer Assignments tablosunda ON DELETE CASCADE yoksa 
    -- önce atamaları silmek gerekebilir, ancak senin şemanda CASCADE var.
    DELETE FROM Tasks WHERE gorev_id = p_gorev_id;
END;
$$;


--
-- TOC entry 245 (class 1255 OID 16903)
-- Name: sp_get_family_details(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sp_get_family_details(p_family_id integer) RETURNS TABLE(k_adi character varying, t_puan integer, g_adi character varying, g_puani integer, g_durumu character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT kullanici_adi, toplam_puan, gorev_adi, gorev_puani, gorev_durumu 
    FROM vw_family_task_details 
    WHERE aile_id = p_family_id
    ORDER BY kullanici_adi ASC;
END;
$$;


--
-- TOC entry 246 (class 1255 OID 16894)
-- Name: sp_get_unified_users(); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sp_get_unified_users() RETURNS TABLE(isim character varying, meslek character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT ad, rol FROM Users WHERE rol IN ('Baba', 'Anne')
    UNION
    SELECT u.ad, u.rol FROM Users u JOIN Assignments a ON u.kullanici_id = a.kullanici_id;
END;
$$;


--
-- TOC entry 227 (class 1255 OID 16909)
-- Name: sp_get_user_by_id(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sp_get_user_by_id(p_id integer) RETURNS TABLE(id integer, isim character varying, gorev_rol character varying, skorpuan integer, aile integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT kullanici_id, ad, rol, puan, aile_id 
    FROM Users 
    WHERE kullanici_id = p_id;
END;
$$;


--
-- TOC entry 247 (class 1255 OID 16895)
-- Name: sp_get_user_pending_tasks(integer); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sp_get_user_pending_tasks(p_uid integer) RETURNS TABLE(atama_id integer, baslik character varying, puan_degeri integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT a.atama_id, t.baslik, t.puan_degeri 
    FROM Assignments a JOIN Tasks t ON a.gorev_id = t.gorev_id
    WHERE a.kullanici_id = p_uid AND a.durum = 'Beklemede';
END;
$$;


--
-- TOC entry 252 (class 1255 OID 16907)
-- Name: sp_login_user(character varying); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sp_login_user(p_ad character varying) RETURNS TABLE(id integer, isim character varying, gorev_rol character varying, skorpuan integer, aile integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY 
    SELECT kullanici_id, ad, rol, puan, aile_id 
    FROM Users 
    WHERE ad = p_ad;
END;
$$;


--
-- TOC entry 226 (class 1255 OID 16908)
-- Name: sp_register_user(character varying, character varying, integer); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_register_user(IN p_ad character varying, IN p_rol character varying, IN p_aile_id integer)
    LANGUAGE plpgsql
    AS $$
BEGIN
    INSERT INTO Users (ad, rol, aile_id, puan) 
    VALUES (p_ad, p_rol, p_aile_id, 0);
END;
$$;


--
-- TOC entry 250 (class 1255 OID 16898)
-- Name: sp_reset_system(); Type: PROCEDURE; Schema: public; Owner: -
--

CREATE PROCEDURE public.sp_reset_system()
    LANGUAGE plpgsql
    AS $$
BEGIN
    UPDATE Users SET puan = 0;
    TRUNCATE TABLE Assignments RESTART IDENTITY;
END;
$$;


--
-- TOC entry 231 (class 1255 OID 16892)
-- Name: sp_search_tasks(text); Type: FUNCTION; Schema: public; Owner: -
--

CREATE FUNCTION public.sp_search_tasks(p_keyword text) RETURNS TABLE(id integer, isim character varying)
    LANGUAGE plpgsql
    AS $$
BEGIN
    RETURN QUERY SELECT gorev_id, baslik FROM Tasks WHERE baslik ILIKE '%' || p_keyword || '%';
END;
$$;


SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- TOC entry 224 (class 1259 OID 16790)
-- Name: assignments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.assignments (
    atama_id integer NOT NULL,
    gorev_id integer,
    kullanici_id integer,
    durum character varying(50) DEFAULT 'Beklemede'::character varying
);


--
-- TOC entry 223 (class 1259 OID 16789)
-- Name: assignments_atama_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.assignments_atama_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 4958 (class 0 OID 0)
-- Dependencies: 223
-- Name: assignments_atama_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.assignments_atama_id_seq OWNED BY public.assignments.atama_id;


--
-- TOC entry 218 (class 1259 OID 16757)
-- Name: families; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.families (
    aile_id integer NOT NULL,
    aile_adi character varying(100) NOT NULL
);


--
-- TOC entry 217 (class 1259 OID 16756)
-- Name: families_aile_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.families_aile_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 4959 (class 0 OID 0)
-- Dependencies: 217
-- Name: families_aile_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.families_aile_id_seq OWNED BY public.families.aile_id;


--
-- TOC entry 221 (class 1259 OID 16781)
-- Name: gorev_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.gorev_id_seq
    START WITH 100
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 222 (class 1259 OID 16782)
-- Name: tasks; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.tasks (
    gorev_id integer DEFAULT nextval('public.gorev_id_seq'::regclass) NOT NULL,
    baslik character varying(200) NOT NULL,
    puan_degeri integer,
    zorluk_seviyesi character varying(50),
    CONSTRAINT tasks_puan_degeri_check CHECK ((puan_degeri > 0))
);


--
-- TOC entry 220 (class 1259 OID 16764)
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    kullanici_id integer NOT NULL,
    ad character varying(50) NOT NULL,
    rol character varying(20),
    aile_id integer,
    puan integer DEFAULT 0,
    CONSTRAINT users_puan_check CHECK ((puan >= 0))
);


--
-- TOC entry 219 (class 1259 OID 16763)
-- Name: users_kullanici_id_seq; Type: SEQUENCE; Schema: public; Owner: -
--

CREATE SEQUENCE public.users_kullanici_id_seq
    AS integer
    START WITH 1
    INCREMENT BY 1
    NO MINVALUE
    NO MAXVALUE
    CACHE 1;


--
-- TOC entry 4960 (class 0 OID 0)
-- Dependencies: 219
-- Name: users_kullanici_id_seq; Type: SEQUENCE OWNED BY; Schema: public; Owner: -
--

ALTER SEQUENCE public.users_kullanici_id_seq OWNED BY public.users.kullanici_id;


--
-- TOC entry 225 (class 1259 OID 16885)
-- Name: vw_family_task_details; Type: VIEW; Schema: public; Owner: -
--

CREATE VIEW public.vw_family_task_details AS
 SELECT u.aile_id,
    u.ad AS kullanici_adi,
    u.puan AS toplam_puan,
    t.baslik AS gorev_adi,
    t.puan_degeri AS gorev_puani,
    a.durum AS gorev_durumu
   FROM ((public.users u
     JOIN public.assignments a ON ((u.kullanici_id = a.kullanici_id)))
     JOIN public.tasks t ON ((a.gorev_id = t.gorev_id)));


--
-- TOC entry 4781 (class 2604 OID 16879)
-- Name: assignments atama_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignments ALTER COLUMN atama_id SET DEFAULT nextval('public.assignments_atama_id_seq'::regclass);


--
-- TOC entry 4777 (class 2604 OID 16880)
-- Name: families aile_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.families ALTER COLUMN aile_id SET DEFAULT nextval('public.families_aile_id_seq'::regclass);


--
-- TOC entry 4778 (class 2604 OID 16881)
-- Name: users kullanici_id; Type: DEFAULT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users ALTER COLUMN kullanici_id SET DEFAULT nextval('public.users_kullanici_id_seq'::regclass);


--
-- TOC entry 4952 (class 0 OID 16790)
-- Dependencies: 224
-- Data for Name: assignments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.assignments (atama_id, gorev_id, kullanici_id, durum) FROM stdin;
\.


--
-- TOC entry 4946 (class 0 OID 16757)
-- Dependencies: 218
-- Data for Name: families; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.families (aile_id, aile_adi) FROM stdin;
1	Gündüz Ailesi
2	Yılmaz Ailesi
3	Kaya Ailesi
4	Demir Ailesi
5	Çelik Ailesi
6	Yıldız Ailesi
7	Öztürk Ailesi
8	Aydın Ailesi
9	Arslan Ailesi
10	Polat Ailesi
\.


--
-- TOC entry 4950 (class 0 OID 16782)
-- Dependencies: 222
-- Data for Name: tasks; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.tasks (gorev_id, baslik, puan_degeri, zorluk_seviyesi) FROM stdin;
100	Market	20	Orta
101	Çöp	10	Kolay
102	Fatura	30	Zor
103	Temizlik	50	Zor
104	Bulaşık	15	Kolay
105	Çamaşır	25	Orta
106	Yemek	45	Zor
107	Bahçe	15	Kolay
108	Ütü	40	Orta
109	Tamirat	60	Zor
114	süpürge	30	\N
115	Kapı	20	\N
\.


--
-- TOC entry 4948 (class 0 OID 16764)
-- Dependencies: 220
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (kullanici_id, ad, rol, aile_id, puan) FROM stdin;
2	Başar	Çocuk	1	0
12	Nisa	Anne	2	0
3	Ahmet	Çocuk	2	0
5	Zeynep	Çocuk	3	0
6	Ali	Çocuk	4	0
7	Ayşe	Çocuk	5	0
8	Mehmet	Çocuk	6	0
9	Can	Çocuk	7	0
10	Ece	Çocuk	8	0
11	Selen	Anne	1	0
14	Hüseyin	Baba	3	0
15	Meryem	Anne	4	0
16	İbrahim	Baba	4	0
17	Nermin	Anne	5	0
18	Mustafa	Baba	5	0
19	Gülcan	Anne	6	0
20	Osman	Baba	6	0
21	Lale	Anne	7	0
22	Süleyman	Baba	7	0
23	Arzu	Anne	8	0
24	Ramazan	Baba	8	0
25	Figen	Anne	9	0
26	Yakup	Baba	9	0
27	Hale	Anne	10	0
28	Bekir	Baba	10	0
4	Fatma	Anne	2	0
29	Zeynep	Anne	3	0
30	Yıldız	Çocuk	9	0
31	Defne	Çocuk	10	0
1	Yusuf	Baba	1	0
\.


--
-- TOC entry 4961 (class 0 OID 0)
-- Dependencies: 223
-- Name: assignments_atama_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.assignments_atama_id_seq', 1, false);


--
-- TOC entry 4962 (class 0 OID 0)
-- Dependencies: 217
-- Name: families_aile_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.families_aile_id_seq', 10, true);


--
-- TOC entry 4963 (class 0 OID 0)
-- Dependencies: 221
-- Name: gorev_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.gorev_id_seq', 116, true);


--
-- TOC entry 4964 (class 0 OID 0)
-- Dependencies: 219
-- Name: users_kullanici_id_seq; Type: SEQUENCE SET; Schema: public; Owner: -
--

SELECT pg_catalog.setval('public.users_kullanici_id_seq', 31, true);


--
-- TOC entry 4793 (class 2606 OID 16796)
-- Name: assignments assignments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT assignments_pkey PRIMARY KEY (atama_id);


--
-- TOC entry 4786 (class 2606 OID 16762)
-- Name: families families_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.families
    ADD CONSTRAINT families_pkey PRIMARY KEY (aile_id);


--
-- TOC entry 4791 (class 2606 OID 16788)
-- Name: tasks tasks_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.tasks
    ADD CONSTRAINT tasks_pkey PRIMARY KEY (gorev_id);


--
-- TOC entry 4788 (class 2606 OID 16772)
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (kullanici_id);


--
-- TOC entry 4789 (class 1259 OID 16807)
-- Name: idx_task_title; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_task_title ON public.tasks USING btree (baslik);


--
-- TOC entry 4798 (class 2620 OID 16816)
-- Name: assignments trg_assignment_puan; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_assignment_puan AFTER UPDATE ON public.assignments FOR EACH ROW EXECUTE FUNCTION public.fnc_update_puan();


--
-- TOC entry 4797 (class 2620 OID 16818)
-- Name: tasks trg_new_task; Type: TRIGGER; Schema: public; Owner: -
--

CREATE TRIGGER trg_new_task AFTER INSERT ON public.tasks FOR EACH ROW EXECUTE FUNCTION public.fnc_new_task_notice();


--
-- TOC entry 4795 (class 2606 OID 16797)
-- Name: assignments assignments_gorev_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT assignments_gorev_id_fkey FOREIGN KEY (gorev_id) REFERENCES public.tasks(gorev_id) ON DELETE CASCADE;


--
-- TOC entry 4796 (class 2606 OID 16802)
-- Name: assignments assignments_kullanici_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.assignments
    ADD CONSTRAINT assignments_kullanici_id_fkey FOREIGN KEY (kullanici_id) REFERENCES public.users(kullanici_id) ON DELETE CASCADE;


--
-- TOC entry 4794 (class 2606 OID 16773)
-- Name: users users_aile_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_aile_id_fkey FOREIGN KEY (aile_id) REFERENCES public.families(aile_id) ON DELETE CASCADE;


-- Completed on 2026-01-15 09:49:15

--
-- PostgreSQL database dump complete
--

