# SunCzat

[English](README.md) · **Polski**

Plugin czatu na Paper 26.2: rangi, prywatne wiadomości, @wzmianki, pokazywanie przedmiotu z ręki, anty-spam z filtrem słów, czasowe wyciszenia i własna lista graczy pod TAB.

## Funkcje

- **Rangi**: każda ma prefiks, kolor nicku i wiadomości, wagę, według której sortuje się lista pod TAB, oraz uprawnienia nadawane razem z rangą. W domyślnym configu jest 14 rang, od Ownera do Gracza. Gracz bez rangi dostaje domyślną.
- **Plakietki rang**: ranga może mieć mały obrazek zamiast prefiksu tekstowego. Plugin wysyła paczkę zasobów na ekranie ładowania i zapamiętuje, kto ją przyjął, więc gracze bez paczki dalej widzą na czacie prefiksy tekstowe.
- **Prywatne wiadomości**: `/msg` i `/r` z dźwiękiem. Admin może włączyć podgląd cudzych prywatnych wiadomości.
- **@wzmianki**: `@nick` jest podświetlony, a oznaczony gracz słyszy dźwięk i widzi powiadomienie na pasku akcji. Kliknięcie nicku na czacie zaczyna `/msg` do tego gracza.
- **[item]**: wpisz `[item]` (albo `[i]`, `[przedmiot]`), żeby pokazać przedmiot z ręki, z podglądem po najechaniu
- **Anty-spam**: minimalny odstęp między wiadomościami, limit wiadomości w oknie czasu, blokada powtarzania tej samej wiadomości i zamiana wiadomości pisanych CAPS LOCKIEM na małe litery
- **Filtr słów**: gwiazdkuje słowa zawierające rdzenie z configu. Wielkie litery, polskie znaki i powtórzone litery nie pomagają go obejść, część zamian cyfr (1→i, 3→e, 4→a, 0→o) też jest łapana, a do tego jest lista dozwolonych wyjątków.
- **Ignorowanie**: `/ignoruj` ukrywa wiadomości gracza na czacie i jego prywatne wiadomości
- **Wyciszenia** z powodem, na `30s`, `10m`, `2h`, `1d` albo na zawsze
- **Ustawienia powiadomień**: `/powiadomienia` otwiera menu, w którym każdy gracz może wyłączyć pojedyncze typy powiadomień ze wszystkich pluginów SunCraft (przez SunCore)
- **Lista graczy (TAB)**: nagłówek i stopka z animowanym złotym gradientem, liczbą graczy, pingiem i rangą gracza
- Linie czatu rozsyła serwer jako wiadomości systemowe, więc filtr i `[item]` wyglądają u wszystkich tak samo, a klient nie oznacza wiadomości jako zmodyfikowanych

## Wymagania

- Paper **26.2**
- Java **25**
- [SunCore](https://github.com/oonnyyx/SunCore)

## Instalacja

1. Pobierz `SunCzat.jar` i `SunCore.jar` z zakładki [Releases](../../releases/latest).
2. Wrzuć oba pliki do `plugins/` i zrestartuj serwer.
3. Nadawaj rangi komendą `/ranga daj <gracz> <ranga>`.

## Komendy

| Komenda | Opis |
|---|---|
| `/msg <gracz> <wiadomość>` | Prywatna wiadomość (aliasy: `/w`, `/pw`, `/tell`, `/whisper`, `/napisz`) |
| `/r <wiadomość>` | Odpowiedź na ostatnią prywatną wiadomość |
| `/ignoruj [gracz]` | Ignoruj gracza albo przestań go ignorować; bez nicku pokazuje, kogo ignorujesz |
| `/powiadomienia` | Wybór powiadomień, które chcesz widzieć |
| `/wycisz <gracz> [czas] [powód]` | Wyciszenie; bez czasu jest na zawsze |
| `/odcisz <gracz>` | Zdjęcie wyciszenia |
| `/ranga daj <gracz> <ranga>` | Nadanie rangi |
| `/ranga gracz <gracz>` | Sprawdzenie rangi gracza |
| `/ranga lista` | Wszystkie rangi z liczbą graczy |
| `/podglad` | Włączenie i wyłączenie podglądu prywatnych wiadomości |
| `/czat reload` | Przeładowanie configu |

| Uprawnienie | Opis |
|---|---|
| `czat.admin` | Rangi, podgląd prywatnych wiadomości i reload (domyślnie op) |
| `czat.wycisz` | Wyciszanie i odciszanie (domyślnie op) |
| `czat.bypass` | Omija anty-spam i filtr słów (domyślnie op) |

## Konfiguracja

Wszystko jest w `config.yml`: format linii czatu (MiniMessage), rangi, limity anty-spamu, filtrowane słowa i wyjątki, dźwięki wzmianek i prywatnych wiadomości, paczka zasobów oraz nagłówek i stopka listy graczy. Zmiany wchodzą po `/czat reload`.

**Plakietki rang** korzystają z czcionki `suncraft:rangi` z paczki zasobów SunCraft (`SunCraft-pack.zip` w [release SunPrzedmioty](https://github.com/oonnyyx/SunPrzedmioty/releases/latest)). Wrzuć zip na dowolny hosting, wpisz jego adres i SHA-1 w sekcji `paczka`, a `resource-pack` w `server.properties` zostaw pusty. Jeśli nie używasz paczki, ustaw `obrazek: ""` we wszystkich rangach, żeby prefiksy tekstowe pojawiały się także pod TAB i nad głowami.

## Budowanie

```bash
git clone https://github.com/oonnyyx/SunCore && cd SunCore && mvn install && cd ..
git clone https://github.com/oonnyyx/SunCzat && cd SunCzat && mvn package
```

## Licencja

[MIT](LICENSE)
