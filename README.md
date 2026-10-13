# Sonnet

Client Android pour [Jellyfin](https://jellyfin.org/), en noir et violet, pensé pour le Galaxy Z Fold.
Tu retrouves l'interface Jellyfin web (Accueil, Top 10, Continuer à regarder…) et, dès que tu appuies sur Lecture, un
**lecteur natif** (ExoPlayer) prend le relais. L'appli intègre aussi un espace pour un Bridge Hub.

Sonnet est un projet personnel, non affilié au projet Jellyfin.

## Télécharger l'APK

➡️ **[Télécharger Sonnet v0.3 (`sonnet.apk`)](https://github.com/apk-fold/sonnet/raw/main/sonnet.apk)**, soit 22 Mo. Le fichier est aussi visible
dans la liste des fichiers du dépôt.

Empreinte SHA-256 pour vérifier le fichier : `efa1f1e1c26613420b90739e5371e7fe58a1d52628bb996a91ecf6825df84a39`

Installation sur Android 8.0 ou plus récent :
1. Télécharge `sonnet.apk` directement sur le téléphone.
2. Ouvre le fichier. Android demande d'autoriser l'installation depuis cette source (navigateur ou gestionnaire de fichiers) : accepte.
3. Appuie sur **Installer**.

Remarques :
- C'est un APK de **debug** (signé avec la clé de debug d'Android Studio, identifiant `com.matheo.sonnet.debug`).
- **Aucune adresse n'est codée en dur** : au premier lancement, Sonnet te demande l'adresse de ton serveur Jellyfin (et, en option,
  celle de ton Bridge Hub). Tu peux les changer ensuite dans Réglages.
- L'appli autorise le HTTP non chiffré, ce qui est pratique sur un réseau privé (Tailscale, réseau local).

## Fonctions

### Lecteur natif
- **Position de l'image** : Haut, Centre ou Bas (bouton en haut à droite, ou glisser à deux doigts). Sur le Fold déplié, tu peux
  coller le film en haut pour rejeter les bandes noires en bas. La position est mémorisée séparément pour l'écran extérieur et
  l'écran déplié.
- **±10 secondes partout** : boutons ⟲10 et 10⟳, et double-tap à gauche ou à droite de la vidéo. Chaque tap en plus ajoute 10 s.
  Double-tap au centre = pause. La durée est réglable (5, 10, 15 ou 30 s).
- **Sous-titres dans la bande noire** : ils ne recouvrent plus l'image quand il y a de la place en dessous.
- **Gestes** : luminosité (côté gauche), volume (côté droit), glisser à l'horizontale pour chercher avec aperçu trickplay,
  pincer pour zoomer, appui long pour la vitesse 2×, verrouillage de l'écran.
- **Mode tabletop / livre** (Fold à moitié plié) : vidéo en haut, télécommande en bas.
- **Picture-in-picture automatique**, lecture qui continue quand on plie ou déplie, MediaSession (Bluetooth, PiP).
- **Passer l'intro ou le générique** (segments Jellyfin 10.10+ ou chapitres), en mode bouton ou automatique.
- **Épisode suivant** avec compte à rebours, liste des épisodes de la saison, chapitres avec images.
- Pistes audio et sous-titres, vitesse de 0,5 à 3×, minuteur de veille, qualité et débit maximum, boost du volume, infos de
  lecture (codec, lecture directe ou transcodage), retour automatique en transcodage si un fichier ne passe pas.
- Progression synchronisée avec Jellyfin (reprise, « Vu », Continuer à regarder).

### Options Sonnet (Réglages › Options Sonnet)
Chaque option s'active ou se coupe séparément, pour Jellyfin et pour le Bridge Hub :
- **Animations GSAP** : affiches en cascade, transition de page, effet de ressort au toucher, vibrations légères.
- **Tirer vers le bas** en haut d'une page : raccourcis (Accueil, Recherche, Favoris, Actualiser, Haut de page).
- **Double-tap sur l'affiche** d'une fiche : favori. **Glisser un épisode** : vers la droite = vu / non vu, vers la gauche = favori.
- **Thèmes** Sombre, AMOLED (noir pur) et Clair, plus une **teinte d'accent libre**.
- [GSAP](https://gsap.com) (GreenSock) est embarqué localement dans l'appli.

### Application
- WebView Jellyfin **persistante** : changer d'onglet est instantané, plier ou déplier ne recharge rien.
- **Rail de navigation** à gauche sur l'écran déplié, barre en bas sur l'écran extérieur.
- **Bibliothèques natives** (avec Recherche, Favoris, TV en direct) et un onglet Photos.
- **Tous les réglages Jellyfin** dans Réglages : profil, affichage, accueil, lecture, sous-titres, contrôles, Quick Connect, et
  toute l'administration (utilisateurs, médiathèques, transcodage, plugins, tâches, journaux, réseau, TV, clés d'API,
  sauvegardes…). Les routes ont été vérifiées sur jellyfin-web 10.11.
- **Accent violet injecté dans Jellyfin** (réglages et administration, ou partout), avec CSS personnalisé possible.
- Plein écran du lecteur web, envoi de fichiers (images, sous-titres) et « Télécharger » fonctionnels.
- Le bouton retour d'Android revient en arrière dans Jellyfin.

Le lecteur natif peut être désactivé dans Réglages › Lecteur Sonnet. En cas d'erreur de lecture, « Lire avec le lecteur Jellyfin
web » est disponible.

## Compiler soi-même

Prérequis : Android Studio (ou le SDK Android) et un JDK 17 ou plus récent.

1. Ouvre le dossier du projet (celui qui contient `app/`) dans Android Studio et laisse la synchronisation Gradle finir.
2. Lance **Build › Build APK(s)**, ou en ligne de commande :
   ```
   gradlew.bat assembleDebug
   ```
3. L'APK se trouve dans `app/build/outputs/apk/debug/` ou, selon la version d'Android Studio, dans
   `app/build/intermediates/apk/debug/`.

Si Gradle n'arrive pas à compiler, vérifie d'abord que `local.properties` contient bien le chemin de ton SDK Android
(`sdk.dir=...`) et que `JAVA_HOME` pointe vers un JDK compatible avec ta version de Gradle.

## Technique

Kotlin et Jetpack Compose, Media3 (ExoPlayer) pour la lecture, `androidx.window` pour les postures du Fold, Coil pour les
affiches, DataStore pour les réglages. Android 8.0 (API 26) minimum, cible API 34.
