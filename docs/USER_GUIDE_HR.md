# PrimaBarcode — Korisnički priručnik

**Za koga je:** skladišno i prodajno osoblje koje svakodnevno koristi aplikaciju PrimaBarcode za skeniranje i bilježenje kretanja robe (otpreme, primke, prijenosi).

Ovaj priručnik objašnjava čemu služi svaki ekran, što znači svaki gumb i prekidač te što učiniti kad nešto ne izgleda kako treba. Ne pretpostavlja nikakvo tehničko predznanje — ako znate koristiti pametni telefon, znate koristiti i ovaj priručnik.

Nazivi gumba i ekrana navedeni su onako kako ih aplikacija prikazuje na hrvatskom. Ovo je prijevod priručnika [USER_GUIDE.md](USER_GUIDE.md); kad se on promijeni, treba ažurirati i ovaj.

---

## Sadržaj

1. [Što aplikacija radi](#1-što-aplikacija-radi)
2. [Osnovni pojmovi koje ćete viđati posvuda](#2-osnovni-pojmovi-koje-ćete-viđati-posvuda)
3. [Prvo pokretanje](#3-prvo-pokretanje)
4. [Glavni izbornik](#4-glavni-izbornik)
5. [Odabir lokacije i centra odgovornosti](#5-odabir-lokacije-i-centra-odgovornosti)
6. [Vrste dokumenata](#6-vrste-dokumenata)
7. [Popis dokumenata (Nalozi / Greške)](#7-popis-dokumenata-nalozi--greške)
7a. [Odjeljak ZAPISI](#7a-odjeljak-zapisi)
8. [Preuzimanje dokumenata](#8-preuzimanje-dokumenata)
9. [Skeniranje dokumenta (ekran skeniranja)](#9-skeniranje-dokumenta-ekran-skeniranja)
10. [Upozorenja koja možete vidjeti](#10-upozorenja-koja-možete-vidjeti)
11. [Posebni format barkoda (Barkod\|JM\|Količina)](#11-posebni-format-barkoda-barkodjmkoličina)
12. [Slanje dokumenata](#12-slanje-dokumenata)
13. [Rješavanje grešaka pri slanju](#13-rješavanje-grešaka-pri-slanju)
14. [Pregled dokumenata](#14-pregled-dokumenata)
15. [Filtriranje dokumenata](#15-filtriranje-dokumenata)
16. [Postavke — objašnjenje svake opcije](#16-postavke--objašnjenje-svake-opcije)
17. [Prijava i odjava](#17-prijava-i-odjava)
18. [Česte situacije i što učiniti](#18-česte-situacije-i-što-učiniti)
18a. [Uklanjanje operatera s uređaja](#18a-uklanjanje-operatera-s-uređaja)
18b. [QR kodovi za prijavu (za administratore)](#18b-qr-kodovi-za-prijavu-za-administratore)
19. [Pojmovnik](#19-pojmovnik)

---

## 1. Što aplikacija radi

PrimaBarcode je aplikacija za ručne terminale kojom se bilježi što je fizički izuzeto, zaprimljeno ili premješteno u skladištu ili trgovini. Kad završite, ti se podaci šalju u središnji poslovni sustav tvrtke (Dynamics NAV / Business Central). U aplikaciji se on zove **vanjski sustav**, pa ga tako zovemo i ovdje.

Svakodnevni rad izgleda ovako:

```
1. PREUZIMANJE — dohvatite popis dokumenata (naloga) na kojima trebate raditi
2. SKENIRANJE  — prođite skladištem i skenirajte barkodove prema stavkama svakog dokumenta
3. SLANJE      — pošaljite završen (ili djelomičan) posao natrag u vanjski sustav
```

Dokument uvijek morate preuzeti prije nego što po njemu skenirate — ne postoji skeniranje bez veze ili prije preuzimanja. Svaki skenirani barkod odmah se uspoređuje s očekivanim stavkama tog dokumenta, a sve što se ne poklapa odmah se odbija (vidi [§2.3](#23-neusklađena-skeniranja)).

---

## 2. Osnovni pojmovi koje ćete viđati posvuda

### 2.1 Četiri statusa skeniranja (jezik boja)

Svaka stavka dokumenta — i dokument u cjelini — uvijek je u jednom od četiri stanja, svugdje u aplikaciji prikazanom istom bojom:

| Status | Značenje | Boja |
|---|---|---|
| **Prazno** | Još ništa nije skenirano (0 od očekivane količine) | 🔴 Crvena |
| **Djelomično** | Skeniran je dio očekivane količine, ali ne sva | 🟠 Narančasta |
| **Gotovo** (na ekranu skeniranja „Točno”) | Skenirana količina točno odgovara očekivanoj | 🟢 Zelena |
| **Prekoračenje** | Skenirali ste *više* od očekivane količine | 🔵 Plava |

Ukupni status dokumenta slijedi jednostavna pravila:
- Ako je **ijedna stavka** u prekoračenju, **cijeli dokument** prikazuje se kao Prekoračenje — čak i ako su sve ostale stavke savršene.
- Dokument je **Gotovo** (zeleno) samo kad je svaka stavka točna.
- Inače je **Djelomično**.

### 2.2 Životni ciklus dokumenta

U pozadini svaki dokument tijekom rada prolazi kroz ove faze:

`Preuzet → U tijeku → Završen → (Čeka slanje) → nestaje (nakon uspješnog slanja)`

Ako slanje ne uspije, dokument umjesto toga postaje **Slanje neuspješno**, prikazuje se na kartici **GREŠKE** i ostaje na uređaju dok ne pokušate ponovno (ili dok ured ne riješi uzrok problema).

### 2.3 Neusklađena skeniranja

Ako skenirate barkod koji nije naveden kao očekivani artikl na trenutnom dokumentu, aplikacija ga **uopće ne bilježi**. Vidjet ćete poruku „Barkod nije pronađen”, traka za skeniranje kratko zatreperi crveno, a uređaj zavibrira. Ništa se nigdje ne dodaje; provjerite jeste li na pravom dokumentu i jeste li skenirali pravi artikl, pa pokušajte ponovno.

**Iznimka je inventura.** Tamo je artikl koji nije na seriji nešto što ste našli na polici, pa aplikacija umjesto toga pita treba li ga brojati (vidi [Inventura](#inventura)).

---

## 3. Prvo pokretanje

**Prijavljujete se svaki put kad otvorite aplikaciju.** Svojim imenom i lozinkom; ništa drugo u aplikaciji nije dostupno dok se ne prijavite. Nije riječ samo o privatnosti — svako skeniranje bilježi tko ga je napravio, u trenutku kad ga napravite, pa aplikacija mora znati tko drži uređaj prije prvog skeniranja.

**Vaš posao je vaš.** Dokumente koje preuzmete i skeniranja koja napravite vidite samo vi. Drugi operater koji se prijavi na isti uređaj vidi svoje, ne vaše — i ne može poslati vaše. Ako zaboravite poslati na kraju smjene, ništa se ne gubi; čeka vas kad se sljedeći put prijavite na taj uređaj.

Dvoje vas može istovremeno raditi na istom dokumentu. Svatko ima svoju kopiju sa svojim skeniranjima, oboje ide u vanjski sustav i ondje se količine zbrajaju — a to i želite kad dijelite jedan posao.

**Postavljanje potpuno novog uređaja dolazi prije svega ovoga.** Uređaj tek izvađen iz kutije ne zna gdje je vanjski sustav, a ekran za prijavu bez njega ne može provjeriti lozinku. Zato ekran za prijavu ima gumb **Postava vanjskog sustava**, koji otvara postavke veze bez prijave. Obično IT ili konzultant ondje jednom odabere tvrtku, čime se popune sve adrese, a od tada se svi samo prijavljuju. Na istom se ekranu uključuju vrste dokumenata s kojima lokacija radi; nova instalacija nema nijednu uključenu. Uz njega je gumb **O aplikaciji**, koji također bez prijave prikazuje podatke o aplikaciji (vidi [§16](#16-postavke--objašnjenje-svake-opcije), Informacije o sustavu).

**Svaka prijava traži da je vanjski sustav dostupan**, jer samo on može potvrditi vašu lozinku. Ako ne radi, nitko ne može ući u aplikaciju — ni vi, pa ni do posla koji ste već skenirali. Taj posao nije izgubljen: ostaje na uređaju i čeka da sustav ponovno odgovori.

Kad prvi put otvorite aplikaciju:

1. Prijavite se korisničkim imenom i lozinkom. Imena koja su se već koristila na tom uređaju su navedena, pa možete dodirnuti svoje umjesto da ga upisujete. Ime upišite kako god želite — `alice`, `PRIMA\alice` i `alice@prima.hr` za aplikaciju su ista osoba. **Ako imate QR kod za prijavu, pritisnite okidač ili dodirnite *Skeniraj QR kod*.** Na uređaju sa skenerom gumb pokreće skener; kameru otvara samo uređaj bez skenera. Kod popuni oba polja; za ulazak i dalje dodirnete gumb.
2. Upišite **Korisničko ime** i **Lozinku**, zatim dodirnite prikazani gumb. Natpis mu ovisi o tome odakle je prijava pozvana — „Prijavi se”, „Testiraj vezu” ili „Prijavi se i sinkroniziraj”. Ako je za vašu tvrtku postavljena Windows domena (Postavke → Konfiguracija vanjskog sustava), upisujete samo korisničko ime; inače ga upišite kao `korisnik@domena` ili `DOMENA\korisnik`. Prije nego što vas prijavi, aplikacija vaše podatke stvarno provjerava u vanjskom sustavu. Ako budu odbijeni, na istom ekranu vidjet ćete zašto, pa možete ispraviti i pokušati ponovno.
3. Prijava ostaje aktivna zadano razdoblje (obično 24 sata, ponekad dulje — navedeno je u tekstu ispod gumba, npr. *„Vjerodajnice pohranjene šifrirano s AES-256-GCM za 24 sata.”*). Nakon tog razdoblja jednostavno ćete se morati ponovno prijaviti sljedeći put kad bude potrebno — vaše vjerodajnice cijelo su vrijeme šifrirane na uređaju.
4. Odaberite **centar odgovornosti** i **lokaciju** (vidi [§5](#5-odabir-lokacije-i-centra-odgovornosti)). Time aplikacija zna iz kojeg skladišta ili trgovine radite i sve što vidite filtrira na to područje.

---

## 4. Glavni izbornik

Ovo je početni ekran na koji dolazite svaki put kad otvorite aplikaciju.

- **Gornja traka**: prikazuje vaše ime. Okrugli gumb s vašim inicijalima gore lijevo otvara **Podatke o korisniku** — ID korisnika, ime, centar odgovornosti, lokaciju i gumb za odjavu. Ikona zupčanika ⚙️ gore desno otvara **Postavke**.
- **Kartica DANAS** (dodirnite bilo gdje na njoj): sažetak današnje aktivnosti — ukupan broj skeniranih stavki te broj dokumenata u stanju Gotovo / Djelomično / Prekoračenje / Greška. Dodir na karticu otvara **Pregled**.
- **Traka lokacije i CC-a**: dvije oznake jedna uz drugu, sa šifrom trenutnog centra odgovornosti i šifrom lokacije. Dodirnite **bilo koju** da otvorite odabir lokacije i CC-a i promijenite ih. Uz njih, kad je uređaj postavljen za neki sustav, piše s kojim radi: **Test** (narančasto) ili **Produkcija**. Na Testu skeniranja idu u testnu tvrtku, a ne na stvarnu robu.
- **Popis vrsta dokumenata**: jedan redak po vrsti dokumenta koja je na ovom uređaju uključena (Skladišna otpremnica, Skladišna primka, MP skladišna otpremnica, MP skladišna primka, Transportni list, Otprema reklamacija, Zaprimanje reklamacija, Inventura). Svaki redak prikazuje:
  - ikonu i naziv vrste,
  - tanku traku u bojama (ako ima aktivnosti) koja pokazuje omjer statusa dokumenata te vrste,
  - broj dokumenata te vrste koje trenutno imate.
  - Dodir na redak otvara **popis dokumenata** te vrste. Ako se vrsta još ne može otvoriti — nije odabrana lokacija ni centar odgovornosti — aplikacija vam to kaže umjesto da ne učini ništa. Otprema reklamacija i Zaprimanje reklamacija traže centar odgovornosti, a sve ostale vrste lokaciju.
  - **Na novoj instalaciji sve su vrste isključene.** Popis to kaže i nudi gumb za konfiguraciju vanjskog sustava, gdje svaka vrsta ima svoj prekidač. Uređaj nadograđen sa starije verzije zadržava vrste koje je imao; dvije vrste reklamacija i ondje su na početku isključene.
- **Redak ZAPISI**, pod vlastitim naslovom: otvara popis svih dokumenata u koje ste išta skenirali, svih vrsta. Kad niste ništa skenirali, pokazuje nulu. Vidi [§7a](#7a-odjeljak-zapisi).

---

## 5. Odabir lokacije i centra odgovornosti

> **Nakon ažuriranja aplikacije najprije učinite ovo.** Popis lokacija ponovno se dohvaća umjesto da se prenese, a vaša se radna lokacija sada pamti po osobi, a ne po uređaju — pa oboje počinje prazno. Otvorite ovaj ekran, dodirnite **Osvježi** da dohvatite lokacije, pa odaberite svoju. Dok to ne učinite, traka na vrhu glavnog izbornika pokazuje `—` i nijedan se popis dokumenata ne otvara: dodir na vrstu dokumenta kaže da najprije odaberete lokaciju ili centar odgovornosti. Sve što ste već skenirali cijelo vrijeme ostaje vidljivo u **ZAPISIMA**.

Ovaj ekran otvarate dodirom na bilo koju od dvije oznake na glavnom izborniku.

- Redak **Centar odgovornosti** — dodirnite da otvorite popis CC-ova s pretraživanjem. Tipkajte za filtriranje po nazivu ili šifri. Dok je pretraga prazna, na vrhu se pojavljuje dodatna opcija **„Bilo koji centar odgovornosti”** kojom poništavate odabir.
  - ⚠️ **Promjena CC-a briše trenutno odabranu lokaciju** — morat ćete ponovno odabrati lokaciju, jer lokacije pripadaju određenom CC-u.
- Redak **Lokacija** — dodirnite da otvorite popis lokacija s pretraživanjem (već sužen na odabrani CC, ako ga ima). Odabirom lokacije automatski se postavlja i odgovarajući CC, ako ga još niste postavili.
- **Osvježi** (gore desno): dohvaća svjež popis lokacija i CC-ova iz vanjskog sustava. Ako niste prijavljeni, najprije će zatražiti prijavu (gumb glasi „Prijavi se i sinkroniziraj”). Tijekom osvježavanja ikonu zamjenjuje mali kotačić. Podnaslov ispod naslova pokazuje kad je popis zadnji put sinkroniziran, npr. *„Sinkronizirano 07.08.2026 · 14:32”*.
- Gumb **Primijeni** na dnu potvrđuje odabir i vraća vas tamo odakle ste došli.

---

## 6. Vrste dokumenata

| Vrsta | Što predstavlja |
|---|---|
| **Skladišna otpremnica** | Roba koja izlazi iz skladišta prema trgovini |
| **Skladišna primka** | Roba koja od dobavljača ulazi u skladište |
| **MP skladišna otpremnica** | Roba koja izlazi iz trgovine prema kupcu |
| **MP skladišna primka** | Roba i povrati koji iz trgovine dolaze natrag u skladište |
| **Transportni list** | Dokumenti prijenosa između lokacija |
| **Otprema reklamacija** | Paketi reklamacija koji izlaze na otpremi reklamacija (zadano se filtrira po centru odgovornosti) |
| **Zaprimanje reklamacija** | Zaprimanje paketa iste otpreme reklamacija (zadano se filtrira po centru odgovornosti) |
| **Inventura** | Brojanje zaliha: jedna serija temeljnice inventure iz vanjskog sustava |

Koje od njih vidite (i filtriraju li se po lokaciji ili po centru odgovornosti) ovisi o tome kako je vaša tvrtka postavila aplikaciju. Ako se neka vrsta ponaša neočekivano, pitajte administratora ili konzultanta.

### Otprema reklamacija i Zaprimanje reklamacija

Obje su ista otprema reklamacija u vanjskom sustavu, u dvije faze.

- **Otprema reklamacija** prikazuje otpremu reklamacija dok je otpuštena i čeka slanje. **Zaprimanje reklamacija** prikazuje je kad je otpremljena i čeka zaprimanje. Najprije odaberite centar odgovornosti; ni jedna od ovih vrsta ne koristi lokaciju.
- **Jedan redak po reklamaciji** na otpremi. Broj retka je broj reklamacije, a očekivana količina je broj njezinih **paketa**. Naljepnicu svakog paketa skenirajte jednom.
- **Svaki paket mora biti skeniran.** Vanjski sustav skeniranje označava završenim tek kad su skenirani svi paketi svih reklamacija, a otprema reklamacija ne može se zaprimiti prije toga. Ako paket nije stigao, za njega se otvara nova reklamacija; otprema se ne zaprima djelomično.
- Centri odgovornosti koji rade preko cross-dock skladišta ne koriste ove dvije vrste.

### Inventura

Dokument inventure je jedna **serija temeljnice inventure** iz vanjskog sustava: artikli na vašoj lokaciji, s količinom koju sustav očekuje.

- **Seriju birate pri preuzimanju** (vidi [§8](#8-preuzimanje-dokumenata)). Može ih biti otvoreno više odjednom, a svaka se preuzima zasebno.
- **Prebrojite sve na polici.** Očekivana količina je prikazana, pa vidite gdje se vaše brojanje razlikuje od sustava.
- **Artikl koji nije na seriji svejedno se broji.** Aplikacija pita „Nije na seriji – brojati?”; potvrdite s **Broji**. Artikl se pojavljuje na vrhu popisa kao svoj barkod, označen s „Nije na seriji”, a daljnja skeniranja broje se bez pitanja. Ako ste ga dodali greškom, otvorite ga i postavite količinu na 0 pa nestaje.
- **Istu seriju može brojati više ljudi**, svatko na svom uređaju. Vanjski sustav zbraja skeniranja svih; svaki uređaj prikazuje samo svoja.
- Redci koje nitko nije skenirao računaju se kao **0** kad vanjski sustav obradi inventuru.
- **Seriju na skeniranje šalje ured, a ured i završava brojanje.** Serija se pojavljuje na popisu za preuzimanje kad je poslana i nestaje kad je brojanje završeno. Nakon toga uređaj za nju više ne može slati skeniranja, pa pošaljite svoja prije nego što ured završi.
- **Komplet se broji ili po svojoj naljepnici ili po paketima, ne oboje.** Na seriji su i komplet i njegovi paketi, pa način koji ne koristite ostaje na 0. Kad se broji po paketima, vanjski sustav komplet broji tek kad su svi njegovi paketi tu.
- **Naljepnice s količinom** (BARKOD|JM|KOLIČINA) broje količinu otisnutu na njima.
- **Očekivana količina može biti 0 ili negativna** kad je zaliha u sustavu pogrešna. Takav redak postaje plav već pri prvom skeniranju, bez upozorenja na višak.

---

## 7. Popis dokumenata (Nalozi / Greške)

Do njega dolazite dodirom na vrstu dokumenta na glavnom izborniku. Prikazuje sve dokumente te vrste, podijeljene u dvije kartice:

| Kartica | Prikazuje |
|---|---|
| **NALOZI** | Dokumente koje još niste završili — tek preuzete, u tijeku, u slanju ili neuspjelo poslane |
| **GREŠKE** | Dokumente čije slanje nije uspjelo |

**Svaki redak dokumenta** prikazuje broj dokumenta, oznaku statusa u boji (Gotovo / Djelomično / Prazno / Prekoračenje), izvornu lokaciju i datum dokumenta. Na kartici Greške pojavljuje se i dodatna crvena oznaka „Greška”, zajedno s razlogom neuspjeha.

**Skeniranjem ili upisom broja dokumenta** u traku za skeniranje na vrhu odmah otvarate taj dokument, ako postoji. Ako ne postoji, vidjet ćete poruku da ga najprije treba preuzeti iz vanjskog sustava — dokument koji još nije službeno izdan ne može se izraditi niti se po njemu može skenirati.

**Brisanje zapisa dokumenta** premješteno je u odjeljak **ZAPISI** na glavnom izborniku — vidi [§7a](#7a-odjeljak-zapisi).

**Gumbi na dnu** mijenjaju se ovisno o kartici:
- **Nalozi**: `PREUZMI` i `POŠALJI`.
- **Greške**: `OBRIŠI GREŠKE` (uklanja neuspjele dokumente s uređaja bez slanja — oprezno) i `POŠALJI` (ponovni pokušaj).

`POŠALJI` je zasivljen i ne radi ništa kad nema ničega za slanje.

Ikona lijevka (gore desno) postaje **koraljna/narančasta** kad je filtar aktivan — vidi [§15](#15-filtriranje-dokumenata).

---

## 7a. Odjeljak ZAPISI

Ispod vrsta dokumenata na glavnom izborniku, pod vlastitim naslovom **ZAPISI SKENIRANJA**, nalazi se jedan redak **ZAPISI**. On pokazuje koliko skeniranja nosite i, desno, na koliko dokumenata. Ostaje ondje i kad niste ništa skenirali, i tada jednostavno pokazuje nulu — a na kraju smjene korisno je to moći provjeriti.

Dodirom se otvara popis: svaki dokument u koji ste išta skenirali, bilo koje vrste, od najnovijeg. Svaki redak prikazuje broj dokumenta, njegovu vrstu, koliko skeniranja sadrži i koliko ih je već otišlo u vanjski sustav. Dokumenti ostaju na popisu dok se ne pošalju — dokument koji prođe bez greške je završen i nestaje s popisa.

Dodirom na dokument otvarate njegova skeniranja kao **stablo**:

```
ARTIKL-A          2/5
   1 KOM   Čeka      12.09. 08:14
   1 KOM   Čeka      12.09. 08:15
ARTIKL-B          1/1
   1 KOM   Poslano   12.09. 08:17
ARTIKL-C          0/3
   Nema zapisa na ovoj stavci
```

Prikazuje se svaka stavka dokumenta, i one koje niste dirali — upravo je poanta da vidite `0/3`. Ispod svake stavke nalaze se pojedinačna skeniranja koja čine njezinu količinu, svako sa svojom količinom, vremenom i oznakom je li **Poslano** ili još **Čeka**.

**Brisanje jednog skeniranja**: dodirnite crvenu kantu u njegovom retku i potvrdite. Količina stavke smanjuje se za taj iznos.

Skeniranje koje je već **Poslano** umjesto kante prikazuje lokot. Ovdje se ne može obrisati: već je zabilježeno u vanjskom sustavu, a brisanjem s uređaja uređaj bi samo zaboravio što je poslao. Umjesto toga ispravite ga u vanjskom sustavu.

**Brisanje svih neposlanih skeniranja na dokumentu**: pritisnite i držite karticu sažetka na vrhu oko 5 sekundi (puni se kružić napretka; ako pustite ranije, radnja se otkazuje). Potvrda vam kaže koliko je skeniranja već poslano — ta ostaju.

Ako je vanjski sustav uklonio stavku *nakon* što ste je skenirali, ta se skeniranja pojavljuju na dnu, pod **Zapisi bez stavke**. Ovdje ih ne možete obrisati — na dokumentu pritisnite POŠALJI i pregled će vas pitati što s njima.

---

## 8. Preuzimanje dokumenata

Na kartici **Nalozi** dodirnite **PREUZMI**.

1. Ako niste prijavljeni, najprije se automatski pojavljuje ekran za prijavu.
2. Vidjet ćete ekran s filtrom kojim sužavate što se preuzima:
   - **Datum dokumenta** — raspon Od/Do.
   - **Šifra odredišta** — slobodan tekst.
   - **Šifra izvora** ili **Centar odgovornosti** — ovisno o tome kako je postavljena ova vrsta dokumenta, vidjet ćete *jedno* od to dvoje kao odabir (drugo je zaključano na vaš trenutni odabir i skriveno).
   - Za **Inventuru** umjesto raspona datuma vidite polje **Serija temeljnice inventure**, koje radi kao polje lokacije: dodirnite ga i otvara se popis serija spremnih za skeniranje na vašoj lokaciji, svaka s brojem, datumom i brojem redaka, a dugi popis možete pretraživati. Dodirnite seriju da je odaberete; **U redu** nije dostupan dok to ne učinite. Preuzima se samo ta serija, a serije koje su već na uređaju ostaju kakve jesu. Popis prati lokaciju koju odaberete, a gumb **Osvježi** pokraj pretraživanja ponovno pita vanjski sustav, za seriju koju je ured upravo poslao.
3. Dodirnite **Poništi** za brisanje polja filtra ili **U redu** za početak preuzimanja.
4. Dok se dokumenti preuzimaju, prikazuje se oznaka napretka („Preuzimanje…”). Ako nešto ne uspije, vidjet ćete poruku s popisom onoga što je pošlo krivo.

**Važno**: preuzimanje **nikad ne briše vaša postojeća skeniranja**. Ako već imate lokalni napredak na dokumentu koji više nije u novom preuzimanju (npr. završen je i uklonjen na poslužitelju), on se sigurno čuva na uređaju umjesto da se tiho odbaci — samo se neće pojaviti među nalozima dok se to ne riješi.

---

## 9. Skeniranje dokumenta (ekran skeniranja)

Ovo je glavni ekran — otvarate ga dodirom na bilo koji dokument s popisa.

### 9.1 Pregled ekrana

Vidjet ćete:
- **broj dokumenta** kao naslov ekrana, a ispod njega koliko je stavki točno od ukupnog broja,
- traku sažetka: šifre izvora i odredišta lijevo, ukupno skenirano/očekivano desno (postaje zelena kad je sve točno),
- tanku traku napretka koja na prvi pogled sažima status svake stavke,
- cijeli popis očekivanih stavki — broj artikla, naziv artikla i veliki brojač skenirano/očekivano u boji statusa te stavke,
- na dnu **traku za skeniranje** za upis ili skeniranje barkoda.

**Promjenom lokacije skrivaju se dokumenti druge lokacije**, uključujući one u koje ste već skenirali — dokument za CS165 nije na popisu dok radite na CS175. Ništa nije izgubljeno: odjeljak **ZAPISI** prikazuje sve što ste igdje skenirali, bez obzira na kojoj ste lokaciji sada, pa ondje tražite posao koji ste ostavili na drugoj lokaciji.

Za pregled onoga što ste skenirali, stavku po stavku i skeniranje po skeniranje, koristite odjeljak **ZAPISI** na glavnom izborniku ([§7a](#7a-odjeljak-zapisi)). On je zamijenio staru traku „zadnjih skeniranja”, koja je sve zaboravljala čim biste napustili ovaj ekran.

### 9.2 Skeniranje

Skenirati možete na tri načina:
1. **Okidač hardverskog skenera** (ako ga uređaj ima) — samo usmjerite i skenirajte; radi bilo gdje na ovom ekranu.
2. **Traka za skeniranje** na dnu — po potrebi dodirnite ikonu tipkovnice da barkod upišete ručno.
3. **Kamera** — samo na uređajima bez hardverskog skenera, gdje traka za skeniranje umjesto toga prikazuje ikonu kamere: dodirnite je i usmjerite kameru na barkod. Pri uspješnom čitanju pisne i zavibrira, a zatim se sama zatvori. Kamera je rezervni način unosa, pa uvijek radi jedno skeniranje odjednom; za niz artikala koristite hardverski okidač.

**Što se događa kad skenirate:**
- **Poklapa se sa stavkom** → skenirana količina te stavke raste (obično za 1 ili za onoliko koliko dokument određuje po skeniranju), a status stavke i dokumenta odmah se ažurira. **Popis također skoči na tu stavku i označi je** — boja joj se pojača oko pet sekundi, a zatim izblijedi natrag. Tako na dugom dokumentu na prvi pogled vidite što ste upravo skenirali, umjesto da to tražite. Ako je stavka već bila na ekranu, popis ostaje gdje jest i označava je samo boja.
- **Više stavki ima isti barkod** → pune se redom: skeniranje ide na prvu od njih kojoj još nedostaje količina, a kad su sve pune, višak ide na zadnju.
- **Ne poklapa se ni s jednom stavkom** → ništa se ne bilježi. Vidjet ćete poruku „Barkod nije pronađen”, a traka za skeniranje kratko zatreperi crveno, da znate da se nije poklopio ni s jednom stvarnom stavkom (vidi [§2.3](#23-neusklađena-skeniranja)). Na dokumentu **Inventure** aplikacija umjesto toga pita treba li ga brojati.
### 9.3 Ručna izmjena stavke

Dodirnite bilo koju stavku da otvorite njezin detaljni prikaz, gdje možete:
- gumbima **−1 / +1** brzo prilagoditi količinu,
- dodirnuti veliki broj da otvorite **numeričku tipkovnicu**, upisati točnu količinu i potvrditi,
- dodirnuti **Primijeni** da spremite promjenu i vratite se na pregled.

Upisana ukupna količina **zamjenjuje** količinu stavke, ne dodaje joj se.

**Što se događa s pojedinačnim zapisima.** Povećanje količine dodaje jedan novi zapis za razliku, točno kao što bi to učinilo skeniranje. Smanjenje poništava vaša **najnovija** skeniranja i tu staje — stariji zapisi ostaju točno kakvi su bili, sa svojim vremenom i imenom onoga tko ih je napravio. Ako stavku čine tri zasebna skeniranja po 1 i pritisnete −1, dva od ta tri ostaju netaknuta. Sve to možete vidjeti u stablu ZAPISA ([§7a](#7a-odjeljak-zapisi)).

**Što ne možete smanjiti.** Ako je dio ove stavke već poslan u vanjski sustav — što se događa kad je slanje prekinuto na pola puta — detaljni prikaz to kaže (*„3 već poslano”*) i količina neće pasti ispod tog broja. Gumb −1 tu staje, a upis manjeg broja odbija se uz objašnjenje.

Razlog je to što tablica zapisa u vanjskom sustavu sve prihvaća i ništa ne odbija: kad količina jednom ode, s uređaja je nema načina obrisati ni ispraviti. Zato se aplikacija nikad ne pretvara da je može vratiti. Količinu uvijek možete **povećati**, a ono što je već poslano mora se ispraviti u vanjskom sustavu.

### 9.4 Slanje s ovog ekrana

Ako na dokumentu ima bilo kakvog skeniranja, u gornjoj traci pojavljuje se mali gumb **Pošalji**, kojim šaljete bez vraćanja na popis.

### 9.5 Napuštanje ekrana

Dodir na natrag vraća vas izravno na popis dokumenata — napredak se sprema dok skenirate, pa nema što potvrđivati. Pošaljite zasebno kad budete spremni (vidi [§12](#12-slanje-dokumenata)).

---

## 10. Upozorenja koja možete vidjeti

| Upozorenje | Kada se pojavljuje | Što učiniti |
|---|---|---|
| **Prekoračeno** | Skenirali ste više od očekivane količine za stavku (ako je uključena postavka „Upozori pri prekoračenju”) | Samo informacija — dodirnite U redu. Višak je i dalje zabilježen (prikazuje se plavo, kao „Prekoračenje”). |
| **Neusklađena jedinica mjere** | Koristili ste [posebni format barkoda](#11-posebni-format-barkoda-barkodjmkoličina), a skenirana jedinica mjere ne odgovara onoj koju dokument očekuje za taj artikl | Informacija — skeniranje je zabilježeno sa skeniranom količinom. Dodirnite U redu i javite uredu ako izgleda kao stvarna razlika. |
| **Barkod nije pronađen** | Skenirali ste nešto što nije očekivani artikl na ovom dokumentu | Ništa nije zabilježeno — provjerite artikl i dokument, pa skenirajte ponovno. |
| **Nije na seriji** | Na inventuri ste skenirali artikl kojeg nema na seriji | Još ništa nije brojano. **Broji** ako je artikl stvarno na polici, **Odustani** ako je skeniranje bila greška. |

Nijedno od ovih upozorenja ne blokira niti poništava vaše skeniranje — sva su to obavijesti „obratite pažnju”. Kad vidite dijalog, skeniranje je već zabilježeno.

---

## 11. Posebni format barkoda (Barkod\|JM\|Količina)

Neke tiskane etikete kodiraju više od samog artikla — u barkodu mogu nositi i jedinicu mjere i količinu, odvojene okomitom crtom (`|`), na primjer:

```
NTR1234|M|5.6
```

To znači: barkod `NTR1234|M|5.6` (za uspoređivanje se koristi **cijeli niz**, ne samo dio `NTR1234`), jedinica mjere `M`, količina `5.6`.

- Ako se format prepozna (točno dvije crte `|`, s ispravnim brojem na kraju), aplikacija bilježi **upravo tu količinu** — neće tražiti da je upišete, čak ni ako vaše postavke inače traže količinu za neprepoznate barkodove.
- Ako jedinica mjere na etiketi ne odgovara onoj koju dokument očekuje za taj artikl, vidjet ćete upozorenje **Neusklađena jedinica mjere** (vidi gore) — ali skeniranje se svejedno bilježi kako je pročitano.
- Ako barkod ne odgovara točno ovom obrascu, tretira se kao običan barkod.
- **Na inventuri** serija sadrži sam barkod (`NTR1234`), jer naljepnica na polici nosi koju god količinu sadrži. Takva naljepnica pada na taj redak i broji količinu otisnutu na njoj.

**Napomena**: neke vrste barkodova (Code 39) fizički ne mogu kodirati znak `|`, pa se etikete u ovom formatu moraju tiskati kao **Code 128** (ili u drugoj simbologiji koja ga podržava). Ako se barkod u posebnom formatu skenira kao nerazumljiv tekst, provjerite vrstu barkoda na etiketi s uredom ili timom za ispis.

---

## 12. Slanje dokumenata

Dodirnite **POŠALJI** na bilo kojem popisu dokumenata (ili gumb za slanje na ekranu skeniranja). Time se vaša zabilježena skeniranja šalju natrag u vanjski sustav.

- Ako niste prijavljeni, najprije će se zatražiti prijava.
- Ovisno o postavkama aplikacije, slanje se odvija **u pozadini** (možete odmah nastaviti raditi dok se šalje) ili se do kraja prikazuje **ekran napretka** („Slanje…”).
- Nakon uspješnog slanja dokument se potpuno uklanja s uređaja — s njim ste gotovi.
- Ako nešto pođe krivo (greška poslužitelja, problem s mrežom, nedostaje konfiguracija), dokument umjesto toga prelazi na karticu **Greške**, uz zabilježen točan razlog. Ništa se ne gubi, a ponovno treba poslati samo ono što nije uspjelo (ono što je uspjelo prije greške ne šalje se ponovno).
- **Ako uređaj šalje po dokumentu** (postavlja IT), dokument ulazi cijeli ili nikako: vanjski sustav prihvaća sva njegova skeniranja ili nijedno, a dokument čeka na kartici **Greške** s razlogom. Dokument nikad nije napola poslan.

---

## 13. Rješavanje grešaka pri slanju

Otvorite karticu **Greške** (s popisa dokumenata ili iz Pregleda) i dodirnite neuspjeli dokument. Vidjet ćete:

- jasan naslov „Slanje neuspješno”,
- podatke o dokumentu (vrsta, izvor, odredište, CC, broj stavki, datum),
- **cijelu poruku o grešci** koju je vratio vanjski sustav — nju je najkorisnije proslijediti IT-u ili podršci ako problem nije očit (npr. „niste prijavljeni”, „poslužitelj je odbio zahtjev” ili određena poruka provjere iz poslovnog sustava),
- gumb **Pokušaj ponovo** na dnu — koristan kad je uzrok (mreža, poslužitelj, prijava) riješen.

Greške možete i skupno **obrisati** s kartice Greške na popisu dokumenata ili u Pregledu — time se **neuspjeli dokumenti uklanjaju s uređaja bez slanja**. Učinite to samo ako ste sigurni da podaci ne moraju stići u vanjski sustav (npr. bio je duplikat ili probni dokument).

---

## 14. Pregled dokumenata

Do njega dolazite dodirom na karticu DANAS na glavnom izborniku. To je prikaz preko svih vrsta dokumenata, s tri kartice:

- **Greške** — svaki neuspjeli dokument, svih vrsta.
- **Moja lokacija** — svaki dokument na vašoj trenutnoj lokaciji i svaki dokument reklamacije vašeg trenutnog centra odgovornosti. Prazno dok ništa nije odabrano.
- **Sve** — svaki dokument na uređaju, bez filtriranja.

Prazna kartica umjesto praznog ekrana prikazuje zelenu kvačicu i „Nema problema”. Ako na trenutnoj kartici ima dokumenata sa skeniranjima, na dnu se pojavljuje gumb **Pošalji** kojim ih šaljete sve odjednom.

---

## 15. Filtriranje dokumenata

Ikona lijevka (gore desno na popisu dokumenata ili u Pregledu) otvara ekran filtra kojim sužavate prikaz:

- **Status** — jedan ili više od Prazno / Djelomično / Gotovo / Prekoračenje.
- **Vrsta dokumenta** — jedna ili više vrsta (samo na ekranima koji obuhvaćaju više vrsta).
- **Datum dokumenta** — raspon Od/Do. Ako odaberete datum „Od” nakon datuma „Do”, ili obrnuto, drugi se automatski prilagodi da raspon ostane ispravan.
- **Šifra odredišta**, **Šifra izvora**, **Centar odgovornosti** — slobodan tekst ili odabir s popisa, ovisno o kontekstu (neka polja mogu biti zaključana ili unaprijed popunjena ako ste došli iz prikaza određene lokacije).

Dodirnite **Poništi** da sve obrišete ili **Primijeni** za potvrdu. Ikona lijevka postaje koraljna/narančasta kad je filtar aktivan, pa uvijek na prvi pogled znate gledate li filtrirani, a ne cijeli popis.

---

## 16. Postavke — objašnjenje svake opcije

Postavke otvarate ikonom zupčanika na glavnom izborniku. **Promjene se spremaju tek pri izlasku** — ništa se ne sprema dok ne napustite ekran. Ako ste nešto promijenili, aplikacija pita **„Spremiti postavke prije izlaska?”**, uz **Da** (spremi) i **Ne** (odbaci sve što ste upravo promijenili).

**Većina postavki pripada samo vama.** Veličina teksta, velika slova, jezik, ponašanje skeniranja i vaša trenutna lokacija i centar odgovornosti prate vas, pa ih sljedeća smjena ne nasljeđuje. Tri postavke pripadaju uređaju i dijele ih svi na njemu: koje su vrste dokumenata isključene, kako se svaka vrsta filtrira i debugger — one se postave jednom za cijelu lokaciju. Konfiguracija vanjskog sustava također vrijedi za cijeli uređaj.

### Izgled
| Postavka | Što radi |
|---|---|
| **Veličina teksta** | Povećava sav tekst u aplikaciji radi čitljivosti. Novi operater počinje s **Veće**. |
| **Tekst velikim slovima** | Prikazuje sve oznake sučelja VELIKIM SLOVIMA. |
| **Jezik** | Jezik prikaza aplikacije — hrvatski dok ne odaberete drugi, bez obzira na jezik postavljen na samom uređaju. Da bi se promjena potpuno primijenila posvuda, možda će trebati ponovno pokrenuti aplikaciju. |

### Skeniranje
| Postavka | Što radi |
|---|---|
| **Vrijeme čekanja** | Koliko se dugo isti barkod zanemaruje nakon što je pročitan (200 ms – 2 s; 200 ms dok ga ne promijenite). Sprječava da se jedno skeniranje broji dvaput kad isti kod odmah stigne ponovno — dvostruki pritisak okidača ili ista etiketa pročitana dvaput zaredom. Ako isti artikl namjerno skenirate ponovno, pričekajte barem toliko. |
| **Haptička povratna informacija** | Vibracija pri potvrdi skeniranja i pri greškama — jednako pri skeniranju dokumenata i pri prijavi QR kodom. |
| **Zvuk skeniranja** | Dva zvuka, da bez gledanja znate trebate li pogledati ekran: **jedan pisak** kad skeniranje normalno prođe i **glasan dvotonski zvuk koji pada, odsviran četiri puta** („di-du, di-du, di-du, di-du”) kad god skeniranje izazove upozorenje — barkod nije na dokumentu, skenirani broj dokumenta nije pronađen ili je skeniranje stavku dovelo iznad onoga što dokument očekuje (ovo zadnje samo dok je uključeno upozorenje pri prekoračenju). Zvukovi prate glasnoću *medija* na uređaju, a ne glasnoću obavijesti, pa se čuju i kad je skener na vibraciji; glasnoću podesite tipkama za glasnoću, a ovim prekidačem ih isključujete. Vrijede za ista dva načina unosa kao i vibracija. |
| **Upozori pri prekoračenju** | Prikazuje upozorenje kad skenirate više od očekivane količine za stavku. |

### Sinkronizacija
| Postavka | Što radi |
|---|---|
| **Omogući sinkronizaciju u pozadini** | Slanje se odvija u pozadini, pa možete nastaviti raditi umjesto da čekate na ekranu napretka. |

### Konfiguracija vanjskog sustava
Jedan redak, **„Poslužitelj i krajnje točke”**, koji otvara postavke veze s vanjskim sustavom. Obično ga dira samo IT ili konzultant pri postavljanju. Pojedinosti su u tehničkom priručniku. Ako se spremaju s adresom poslužitelja, a bez odabira radi li se o testnom ili produkcijskom sustavu, aplikacija najprije pita: **Odaberi** jedno ili **Spremi svejedno**.

### Otklanjanje pogrešaka
| Stavka | Što radi |
|---|---|
| **Debugger aktivan** | Prije svakog preuzimanja ili slanja prikazuje točne web adrese koje će aplikacija kontaktirati. Korisno je samo za otkrivanje problema s vezom zajedno s IT podrškom. U svakodnevnom radu ostavite isključeno. |
| **Izvezi podatke** | Sprema potpuni ispis svega što je na uređaju u datoteku — korisno ako IT podrška zatraži dijagnostičke podatke. |
| **Umetni zadane postavke sustava** | Omogućuje IT-u ili konzultantima da učitaju, preuzmu ili uvezu početnu konfiguraciju veze s vanjskim sustavom. Preuzeta kopija namjerno ne sadrži ključ za prijavu QR kodom, pa ju je sigurno dijeliti; uvoz takve datoteke ne dira ključ koji je već na uređaju. U svakodnevnom radu to obično ne trebate. |
| **Obriši predmemoriju** *(crveno — briše podatke)* | Briše **vašu** prijavu, dokumente i skeniranja. Drugi operateri na ovom uređaju i postava samog uređaja ostaju netaknuti. Traži potvrdu. Koristite samo kad vam to kaže podrška. |
| **Operateri na ovom uređaju** | Pod Korisničkim računom. Prikazuje sve koji su se ikad ovdje prijavili i omogućuje uklanjanje pojedinog operatera — vidi [§18a](#18a-uklanjanje-operatera-s-uređaja). |
| **Izbriši sve dokumente i zapise** *(crveno — briše podatke)* | Briše sve preuzete dokumente i skeniranja, ali **zadržava** vaše postavke i prijavu. Traži potvrdu. Koristite je kad želite potpuno očistiti radne podatke bez ponovne prijave. |

### Informacije o sustavu
Podaci samo za čitanje — verzija aplikacije, verzija Androida, SDK, verzija baze podataka, za koji je sustav uređaj postavljen (Test ili Produkcija) i za koju tvrtku — korisni kad prijavljujete grešku podršci. Isti podaci nalaze se i na ekranu za prijavu pod **O aplikaciji**, za slučaj kad se ne možete prijaviti.

### Korisnički račun
Prikazuje tko je trenutno prijavljen i opciju **Odjavi se** (odmah, bez potvrde).

---

## 17. Prijava i odjava

- Prijavljujete se svaki put kad otvorite aplikaciju — vidi [§3](#3-prvo-pokretanje).
- **Nakon nadogradnje na 3.6.28 svi se prijavljuju još jednom**, a **Postavke → Operateri na ovom uređaju** ponovno se pune kako se ljudi prijavljuju. Promijenio se način na koji uređaj čuva lozinke i ništa staro nije preneseno. Ničiji dokumenti ni neposlana skeniranja ne gube se: vraćaju se pri sljedećoj prijavi te osobe.
- Vaš pristup vanjskom sustavu čuva se šifriran na uređaju i istječe nakon zadanog razdoblja. Nakon toga aplikacija ponovno traži lozinku sljedeći put kad joj treba vanjski sustav (preuzimanje, slanje, osvježavanje lokacija) — ništa se ne gubi.
- Za odjavu idite na **Postavke → Korisnički račun → Odjavi se iz vanjskog sustava**. Time se vraćate na ekran za prijavu, spremni za sljedećeg operatera. Odjava nije moguća dok je prijenos još u tijeku — vidi [§18.2d](#182d-piše-da-je-prijenos-u-tijeku-i-ne-mogu-se-odjaviti).

---

## 18. Česte situacije i što učiniti

### 18.1 „Dodir na vrstu dokumenta kaže da treba odabrati lokaciju”
Svaka vrsta dokumenta traži da najprije nešto odaberete: **Otprema reklamacija** i **Zaprimanje reklamacija** centar odgovornosti, a sve ostale vrste lokaciju. Odaberite ih na ekranu lokacije i CC-a ([§5](#5-odabir-lokacije-i-centra-odgovornosti)) i pokušajte ponovno — odabirom lokacije postavlja se i njezin centar odgovornosti. Lokacija koje više nema na popisu računa se kao da nije odabrana, pa je odaberite ponovno. Ako nema lokacija za odabir, najprije na tom ekranu dodirnite **Osvježi**.

### 18.2 „Skenirana je pogrešna količina”
Dodirnite stavku pa upotrijebite −1/+1 ili dodirnite broj i izravno upišite točnu ukupnu količinu — ona zamjenjuje zabilježenu količinu stavke, ne dodaje joj se. Ne možete ići ispod onoga što je već poslano u vanjski sustav; vidi [§9.3](#93-ručna-izmjena-stavke).

### 18.2a „Ne mogu pronaći jučerašnji posao”
Provjerite ime s kojim ste se prijavili. Vaši dokumenti i skeniranja pripadaju vama, pa prijava pod tuđim imenom prikazuje tuđi posao, ne vaš. Odjavite se, prijavite se kao vi i sve će biti ondje gdje ste ostavili.

Ništa se ne briše odjavom, time što kolega koristi uređaj ni istekom vaše lozinke.

### 18.2b „Prethodna smjena ostavila je posao na uređaju”
Nećete ga vidjeti, i to namjerno — na njima je da ga završe i pošalju. Vratite im uređaj ili ih zamolite da se prijave i pošalju.

### 18.2c „Ne mogu se prijaviti, piše da vanjski sustav nije dostupan”
**Vanjski sustav je taj koji provjerava vašu lozinku, svaki put.** Dok nije dostupan, u aplikaciju se ne može ući — ni za koga, uključujući ljude koji ovaj uređaj koriste mjesecima. Ništa što ste skenirali u međuvremenu se ne gubi; ostaje na uređaju i čeka da se sustav vrati. Prijavite ispad umjesto da pokušavate drugu lozinku.

Ako je sustav *dostupan*, a lozinka se odbija, vjerojatno vam je promijenjena u vanjskom sustavu — prijavite se novom.

### 18.2d „Piše da je prijenos u tijeku i ne mogu se odjaviti”
Nešto još ide prema vanjskom sustavu ili dolazi iz njega. Pričekajte da završi. Promjena operatera usred prijenosa zabilježila bi „ovo je poslano” na krivu osobu ili bi preuzete podatke spremila kod krive osobe.

### 18.3 „Stalno dobivam ‚Barkod nije pronađen’ za artikl koji je očito na dokumentu”
Za odbijeno skeniranje nikad se ništa ne bilježi, pa nema što poništavati — samo otkrijte u čemu je razlika i skenirajte ponovno. Najčešći uzroci:
- na krivom ste dokumentu (provjerite broj dokumenta u gornjoj traci),
- barkod na etiketi ne odgovara znak po znak onome na dokumentu (ponovni ispis, druga simbologija ili suvišni znakovi),
- ako je riječ o posebnoj etiketi `Barkod|JM|Količina`, vidi [§18.7](#187-barkod-se-skenirao-kao-besmislica-ili-s-krivim-znakovima).

Ako ste sigurni da artikl doista pripada dokumentu, a i dalje se ne poklapa, javite uredu — podatke dokumenta možda treba ispraviti u vanjskom sustavu.

### 18.4 „Iz Naloga je nestao dokument na kojem se radilo”
Dokument ostaje na **Nalozima** cijelo vrijeme, i dok se šalje i nakon neuspjelog slanja. Ako je stvarno nestao, uspješno je poslan — dokumenti napuštaju uređaj kad je sve na njima prihvaćeno. Sve u što ste skenirali navedeno je i pod **ZAPISI** na glavnom izborniku, pa provjerite i ondje.

### 18.5 „Slanje nije uspjelo”
Otvorite dokument s kartice **Greške**, pročitajte točan razlog i dodirnite **Pokušaj ponovo**. Ako poruka nije jasna (npr. tehnička greška poslužitelja), proslijedite točan tekst IT podršci.

### 18.6 „Želim dokument početi potpuno ispočetka”
Otvorite ga iz **ZAPISA** na glavnom izborniku, pritisnite i držite karticu sažetka na vrhu oko 5 sekundi i potvrdite. Time se brišu sva skeniranja koja još nisu poslana. Skeniranja koja je vanjski sustav već prihvatio ostaju — ondje su zabilježena, a potvrda vam kaže koliko ih je.

### 18.7 „Barkod se skenirao kao besmislica ili s krivim znakovima”
Ako je riječ o posebnoj etiketi `Barkod|JM|Količina`, provjerite s uredom je li tiskana kao **Code 128** — starija simbologija Code 39 ne može ispravno prikazati znak `|` i skenira se kao besmislica.

---

## 18a. Uklanjanje operatera s uređaja

Kad netko ode ili se uređaj trajno preda drugome, njegovo se ime može ukloniti s uređaja: **Postavke → Korisnički račun → Operateri na ovom uređaju**, zatim crvena kanta pokraj imena.

To nije samo pospremanje popisa. Uklanjanjem operatera s tog uređaja brišu se i:

- svi dokumenti koje je preuzeo,
- **sva skeniranja koja je napravio**,
- njegova spremljena prijava i osobne postavke (veličina teksta, jezik, radna lokacija).

Prije nego što zatraži potvrdu, aplikacija prebroji što bi nestalo i kaže vam — uključujući koliko tih skeniranja **nikad nije stiglo u vanjski sustav**. Ta skeniranja postoje na tom uređaju i nigdje drugdje; kad nestanu, nema ih odakle vratiti. Ako taj broj nije nula, siguran redoslijed je: neka se ta osoba najprije prijavi i pošalje, a tek je onda uklonite.

Sebe ne možete ukloniti dok ste prijavljeni — u vašem retku nema kante. Uklanjanje nekoga ne dira ničiji drugi posao, kao ni postavu samog uređaja (adrese poslužitelja, lokacije).

Osoba uklonjena na ovaj način nije blokirana: može se ponovno prijaviti na taj uređaj kad god želi i počet će od praznog. Ta prva ponovna prijava traži da je vanjski sustav dostupan, kao i svaka prva prijava na uređaju.

---

## 18b. QR kodovi za prijavu (za administratore)

Operateri se mogu prijaviti skeniranjem QR koda umjesto upisivanja ([§3](#3-prvo-pokretanje)). Kodovi se izrađuju na računalu, stranicom `tools/login_qr.html` iz repozitorija aplikacije.

- Otvorite je dvoklikom, u Chromeu ili Edgeu. Radi bez interneta i ništa upisano ne napušta računalo.
- Jednom upišite **ključ**: `loginQrKey` iz konfiguracije uređaja. Vrijedi za obje kartice. Kod izrađen drugim ključem na uređaju ne radi.
- **„Jedan kod”:**
  1. Upišite korisničko ime, ime za prikaz i lozinku. Ime za prikaz je natpis ispod koda i prati korisničko ime dok ga ne promijenite.
  2. Dodirnite „Napravi QR kod”.
  3. Dodirnite „Ispiši” za ispis ili „Spremi PNG” za spremanje slike.
- **„Više kodova”:**
  1. Dodirnite „Preuzmi predložak” i popunite ga u Excelu: korisničko ime, lozinka, ime za prikaz.
  2. Učitajte ga s „Učitaj CSV” ili zalijepite retke izravno iz Excela. Prazno ime za prikaz izvodi se iz korisničkog imena, npr. `PRIMA\ana.kovacic` → Ana Kovacic.
  3. Dodirnite „Napravi kodove”. Tablica pokazuje koji su redovi postali kodovi i zašto su ostali preskočeni.
  4. „Ispiši” stavlja **dva koda po stranici A4**, s crtom za rezanje između. „Spremi ZIP” sprema sve slike u jednu datoteku.
- **Ispisani kod nosi lozinku.** Čuvajte ga kao samu lozinku, a CSV nakon ispisa obrišite.

Za naredbeni redak postoji i `tools/make_login_qr.py`.

## 19. Pojmovnik

| Pojam | Značenje |
|---|---|
| **CC (centar odgovornosti)** | Organizacijska cjelina (npr. regija ili poslovna jedinica) kojoj lokacija pripada. |
| **Lokacija / šifra izvora** | Određeno skladište ili trgovina iz koje radite. |
| **Šifra odredišta** | Kamo ide roba s dokumenta. |
| **Preuzet** | Dokument dohvaćen iz vanjskog sustava, na kojem se još nije radilo. |
| **U tijeku** | Dokument na kojem je skeniran dio, ali ne sve. |
| **Završen** | Svaka stavka skenirana je točno. |
| **Čeka slanje** | Dokument se upravo šalje u vanjski sustav. |
| **Slanje neuspješno** | Pokušaj slanja nije uspio — razlog je na kartici Greške. |
| **Serija temeljnice inventure** | Jedno brojanje zaliha pripremljeno u vanjskom sustavu za jednu lokaciju. Birate je pri preuzimanju inventure. |
| **Nije na seriji** | Artikl prebrojan na inventuri kojeg serija nije sadržavala; prikazuje se na vrhu popisa. |
